package com.atlas.orchestrator;

import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.auth.UserPermissionContext;
import com.atlas.hitl.HitlGuard;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.intent.IntentRouter;
import com.atlas.intent.core.IntentResult;
import com.atlas.orchestrator.polish.ToolResultPolishingService;
import com.atlas.react.ReActEventSinkRegistry;
import com.atlas.store.ConversationStore;
import com.atlas.store.SessionStore;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.execution.SafeToolExecutionRequest;
import com.atlas.tool.execution.SafeToolExecutionResult;
import com.atlas.tool.execution.SafeToolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * M5.29-6 Chat/SSE 运行时身份契约。
 *
 * <p>教学重点：HTTP 过滤链负责把 Bearer / X-Session-Id 桥接成 Authentication，
 * 但编排器本身仍要 fail-closed。否则未来测试夹具、内部调用或新增入口绕过过滤链时，
 * 仍可能把请求体 userId / conversationId 当成可信事实。</p>
 */
class AtlasOrchestratorRuntimeIdentityTest {

    private final UserPermissionContext userPermissionContext = new UserPermissionContext();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        userPermissionContext.unbind();
    }

    @Test
    void streamChat_shouldRejectWhenTrustedPrincipalIsMissing() {
        RecordingStreamingEmitter streamingEmitter = new RecordingStreamingEmitter();
        SafeToolExecutor safeToolExecutor = mock(SafeToolExecutor.class);
        AtlasOrchestrator orchestrator = orchestrator(
            streamingEmitter,
            mock(IntentRouter.class),
            mock(ToolRegistry.class),
            safeToolExecutor,
            new SessionStore(),
            new ConversationStore()
        );

        orchestrator.streamChat(
            new AtlasOrchestrator.ChatRequest("", "查看节点", "forged-user"),
            new MockHttpServletRequest()
        );

        assertThat(streamingEmitter.events)
            .anySatisfy(event -> {
                assertThat(event.type()).isEqualTo("error");
                assertThat(event.content()).contains("未找到可信用户身份");
            });
        verifyNoInteractions(safeToolExecutor);
    }

    @Test
    void streamChat_shouldUsePrincipalOwnedConversationAndServerSessionContext() {
        SessionStore sessionStore = new SessionStore();
        ConversationStore conversationStore = new ConversationStore();
        String sessionId = sessionStore.createSession(
            "session-token",
            "alice",
            "100002",
            "user",
            Set.of("agent:query")
        );
        String ownConversationId = conversationStore.create("alice", "owned").id();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "alice",
            null,
            "ROLE_USER",
            "agent:query"
        ));

        userPermissionContext.bind("stale-token", "stale-org");
        IntentRouter intentRouter = mock(IntentRouter.class);
        when(intentRouter.route("查看节点")).thenReturn(new IntentResult(
            "node_query", "查询节点", 1.0, "L2", "query", "p0", "查看节点"
        ));
        BaseTool tool = mock(BaseTool.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        when(toolRegistry.findByIntentId("node_query")).thenReturn(Optional.of(tool));
        when(toolRegistry.canExecuteIntent("node_query")).thenReturn(true);

        AtomicReference<SafeToolExecutionRequest> captured = new AtomicReference<>();
        SafeToolExecutor safeToolExecutor = mock(SafeToolExecutor.class);
        when(safeToolExecutor.executeIntent(any())).thenAnswer(invocation -> {
            SafeToolExecutionRequest request = invocation.getArgument(0);
            captured.set(request);
            return SafeToolExecutionResult.executed(
                true,
                "ok",
                Map.of("success", true, "message", "ok", "data", Map.of()),
                request.traceId()
            );
        });

        ToolResultPolishingService polishingService = mock(ToolResultPolishingService.class);
        when(polishingService.polishSync(any(), eq("查看节点"))).thenReturn("节点正常");
        RecordingStreamingEmitter streamingEmitter = new RecordingStreamingEmitter();
        AtlasOrchestrator orchestrator = orchestrator(
            streamingEmitter,
            intentRouter,
            toolRegistry,
            safeToolExecutor,
            sessionStore,
            conversationStore,
            polishingService
        );

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Session-Id", sessionId);
        orchestrator.streamChat(
            new AtlasOrchestrator.ChatRequest(ownConversationId, "查看节点", "mallory"),
            httpRequest
        );

        assertThat(captured).hasValueSatisfying(request -> {
            assertThat(request.userId()).isEqualTo("alice");
            assertThat(request.token()).isEqualTo("session-token");
            assertThat(request.orgId()).isEqualTo("100002");
            assertThat(request.conversationId()).isEqualTo(ownConversationId);
            assertThat(request.conversationId()).doesNotStartWith("ses_");
        });
        assertThat(streamingEmitter.createdIds)
            .singleElement()
            .satisfies(runId -> assertThat(runId).startsWith("run-").doesNotContain("ses_"));
    }

    @Test
    void streamChat_shouldRejectForgedConversationIdFromAnotherPrincipal() {
        SessionStore sessionStore = new SessionStore();
        ConversationStore conversationStore = new ConversationStore();
        String sessionId = sessionStore.createSession("session-token", "alice", "100002", "user", Set.of());
        String othersConversationId = conversationStore.create("bob", "foreign").id();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "alice",
            null,
            "ROLE_USER"
        ));
        RecordingStreamingEmitter streamingEmitter = new RecordingStreamingEmitter();
        SafeToolExecutor safeToolExecutor = mock(SafeToolExecutor.class);
        AtlasOrchestrator orchestrator = orchestrator(
            streamingEmitter,
            mock(IntentRouter.class),
            mock(ToolRegistry.class),
            safeToolExecutor,
            sessionStore,
            conversationStore
        );

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Session-Id", sessionId);
        orchestrator.streamChat(
            new AtlasOrchestrator.ChatRequest(othersConversationId, "查看节点", "alice"),
            httpRequest
        );

        assertThat(streamingEmitter.events)
            .anySatisfy(event -> {
                assertThat(event.type()).isEqualTo("error");
                assertThat(event.content()).contains("会话不存在或不属于当前用户");
            });
        verifyNoInteractions(safeToolExecutor);
    }

    private AtlasOrchestrator orchestrator(RecordingStreamingEmitter streamingEmitter,
                                           IntentRouter intentRouter,
                                           ToolRegistry toolRegistry,
                                           SafeToolExecutor safeToolExecutor,
                                           SessionStore sessionStore,
                                           ConversationStore conversationStore) {
        return orchestrator(
            streamingEmitter,
            intentRouter,
            toolRegistry,
            safeToolExecutor,
            sessionStore,
            conversationStore,
            mock(ToolResultPolishingService.class)
        );
    }

    private AtlasOrchestrator orchestrator(RecordingStreamingEmitter streamingEmitter,
                                           IntentRouter intentRouter,
                                           ToolRegistry toolRegistry,
                                           SafeToolExecutor safeToolExecutor,
                                           SessionStore sessionStore,
                                           ConversationStore conversationStore,
                                           ToolResultPolishingService polishingService) {
        return new AtlasOrchestrator(
            intentRouter,
            streamingEmitter,
            toolRegistry,
            userPermissionContext,
            new AgentPrincipalResolver(userPermissionContext),
            mock(KubeManagerHttpClient.class),
            mock(HitlGuard.class),
            safeToolExecutor,
            new ReActEventSinkRegistry(),
            new TimedDecisionCache(),
            polishingService,
            Runnable::run,
            sessionStore,
            conversationStore,
            null,
            null
        );
    }

    private static class RecordingStreamingEmitter extends StreamingEmitter {
        private final List<String> createdIds = new ArrayList<>();
        private final List<SseEvent> events = new ArrayList<>();

        @Override
        public org.springframework.web.servlet.mvc.method.annotation.SseEmitter createEmitter(String sessionId) {
            createdIds.add(sessionId);
            return new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);
        }

        @Override
        public void send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, SseEvent event) {
            events.add(event);
        }

        @Override
        public void error(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter, String message) {
            events.add(new SseEvent("error", message));
        }

        @Override
        public void complete(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
            events.add(new SseEvent("done", "{\"type\":\"done\"}"));
        }
    }
}
