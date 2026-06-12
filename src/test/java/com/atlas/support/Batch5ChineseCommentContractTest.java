package com.atlas.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 5 中文教学注释契约测试。
 *
 * <p>中文说明：本批先覆盖 DTO / store / config 里最容易被低估的支撑代码。
 * 这些类看起来不像 Agent 推理核心，但它们承载前端响应契约、登录输入、Session 事实、
 * Conversation 归属、异步安全上下文和可选 AI 能力降级，是顶级 Agent 安全边界的地基。</p>
 *
 * <p>安全边界：本测试只读取源码，不启动 Spring、不登录 kube-manager、不创建 Session、
 * 不执行异步任务、不调用 LLM/Embedding/Tool，也不访问 kube-manager。它只保护中文教学说明，
 * 防止后续重构把“前端契约不是权限事实、请求体 orgId 不可信、sessionId 不是 JWT、
 * conversationId 不是授权”这些学习要点删掉。</p>
 */
class Batch5ChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/dto/ApiResponse.java"),
            List.of("中文说明", "安全边界", "前端展示契约", "不是权限契约",
                "不代表 Tool 已授权", "不能泄露 token")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/dto/LoginRequest.java"),
            List.of("中文说明", "安全边界", "候选参数", "不能被 kube-agent 本地 SessionStore",
                "可信 orgId", "password")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/dto/LoginResponse.java"),
            List.of("中文说明", "安全边界", "sessionId", "不是 kube-manager JWT",
                "不返回 password", "不能简单回显")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/dto/SessionData.java"),
            List.of("中文说明", "安全边界", "服务端会话事实", "token 是敏感凭证",
                "不能返回给前端", "不能来自前端任意 claim")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/dto/Conversation.java"),
            List.of("中文说明", "安全边界", "聊天会话元数据", "不是 Agent 运行 trace",
                "id 只是资源定位符", "不能被当作 prompt")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/dto/ConversationItemDto.java"),
            List.of("中文说明", "安全边界", "只读 DTO", "不携带消息内容",
                "Controller 必须先按当前可信 Principal 过滤")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/dto/ConversationDetailDto.java"),
            List.of("中文说明", "安全边界", "messages 空数组", "不是服务端长期记忆",
                "不恢复历史 prompt", "source custody")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/store/SessionStore.java"),
            List.of("中文说明", "安全边界", "服务端可信身份上下文", "sessionId 只是 kube-agent 会话句柄",
                "不是 JWT", "token 不会返回给前端", "不能把前端请求体里的 orgId 原样缓存")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/store/ConversationStore.java"),
            List.of("中文说明", "安全边界", "不是长期记忆", "不是 RAG 文档库",
                "conversationId 只用于定位资源", "不能当授权凭证")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/config/AtlasAsyncConfig.java"),
            List.of("中文说明", "安全边界", "token/orgId", "异步线程",
                "任务结束必须恢复旧值", "不能从请求体、LLM 参数或前端字段")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/config/AtlasConfiguration.java"),
            List.of("中文说明", "安全边界", "可选 AI 能力失败时应降级",
                "不能绕过 SafeToolExecutor", "HITL", "审计", "kube-manager 权限")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/core/ToolParameterSpec.java"),
            List.of("中文说明", "安全边界", "业务参数长什么样", "不负责身份",
                "SafeToolExecutor", "受保护控制面字段")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutionResult.java"),
            List.of("中文说明", "安全边界", "统一执行回执", "不是新的权限来源",
                "success=true", "不能反向证明 HITL")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutionSource.java"),
            List.of("中文说明", "安全边界", "执行来源只用于记录",
                "绝不能作为绕过 SafeToolExecutor", "PLAN_EXECUTE_NODE")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentTraceContext.java"),
            List.of("中文说明", "安全边界", "traceId 只能用于关联观测证据",
                "不是用户身份", "日志注入", "线程池")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/config/IntentDefinition.java"),
            List.of("中文说明", "安全边界", "来自 {@code intents.yml}",
                "不是 Tool 权限表", "二期运行时权力")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/core/IntentResult.java"),
            List.of("中文说明", "安全边界", "智能路由候选", "不是执行许可",
                "来自用户自然语言")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/core/ScoreNormalizer.java"),
            List.of("中文说明", "安全边界", "转换到同一排序空间",
                "不是安全门禁", "不产生审计或记忆写入")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/llm/L3ClassificationResult.java"),
            List.of("中文说明", "安全边界", "模型自报结果", "LLM 输出不可信",
                "不能成为 Tool 权限")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/embedding/EmbeddingConfig.java"),
            List.of("中文说明", "安全边界", "输入来自部署环境或 application 配置",
                "只增强意图候选", "供应链行为")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/defaults/IntentDefaults.java"),
            List.of("中文说明", "安全边界", "表单草稿默认值",
                "不能生成 token", "不可变 Map")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/defaults/DefaultValueRegistry.java"),
            List.of("中文说明", "安全边界", "defaults.yml",
                "不是安全授权", "已有用户参数不被覆盖")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/defaults/DefaultValueApplier.java"),
            List.of("中文说明", "安全边界", "补参辅助器",
                "不是权限系统", "参数 Map")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/exception/AtlasToolValidationException.java"),
            List.of("中文说明", "安全边界", "候选业务参数",
                "不是权限系统", "不能包含 token")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/exception/PermissionDeniedException.java"),
            List.of("中文说明", "安全边界", "服务端权限证据不足",
                "fail-closed", "不能被 LLM 重新解释")
        )
    );

    @Test
    void batch5SupportFiles_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep Batch 5 Chinese teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
