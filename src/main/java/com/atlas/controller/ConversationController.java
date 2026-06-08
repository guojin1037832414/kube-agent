package com.atlas.controller;

import com.atlas.auth.AgentPrincipal;
import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.dto.*;
import com.atlas.store.ConversationStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 会话管理控制器 — 聊天会话的 CRUD。
 *
 * <p>管理前端聊天会话的元数据（不存储消息内容，消息由前端 Pinia/IndexedDB 管理）：</p>
 * <ul>
 *   <li>POST /api/agent/conversations — 创建会话</li>
 *   <li>GET /api/agent/conversations — 列表（updatedAt 倒序）</li>
 *   <li>GET /api/agent/conversations/{id} — 详情</li>
 *   <li>DELETE /api/agent/conversations/{id} — 删除</li>
 *   <li>PUT /api/agent/conversations/{id}/title — 更新标题</li>
 * </ul>
 *
 * <p><b>会话绑定：</b>M5.29-5 起通过 {@link AgentPrincipalResolver} 读取服务端可信主体，
 * 不再把客户端持有的 {@code X-Session-Id} 字符串当作会话 owner。前端仍可携带
 * {@code X-Session-Id}，由 Spring Security 过滤器反查 {@code SessionStore} 后恢复
 * 标准 Authentication。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
@RestController
@RequestMapping("/api/agent")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationStore conversationStore;
    private final AgentPrincipalResolver principalResolver;

    public ConversationController(ConversationStore conversationStore) {
        this(conversationStore, null);
    }

    @Autowired
    public ConversationController(ConversationStore conversationStore,
                                  AgentPrincipalResolver principalResolver) {
        this.conversationStore = conversationStore;
        this.principalResolver = principalResolver;
    }

    // ═══════════════════════════════════════════════════════════
    //  ① 创建会话
    // ═══════════════════════════════════════════════════════════

    /**
     * 创建新会话。
     *
     * @param body 可选 { title: "..." }，不传默认 "新会话"
     * @return 创建的会话信息（id, title, createdAt）
     */
    @PostMapping("/conversations")
    public ResponseEntity<?> create(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestBody(required = false) Map<String, String> body) {

        Optional<String> userId = resolveUserId();
        if (userId.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("未找到可信用户身份"));
        }
        String title = (body != null) ? body.get("title") : null;

        Conversation created = conversationStore.create(userId.get(), title);

        // 转为前端期望的 CreateConversationResponse 格式
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", created.id());
        result.put("title", created.title());
        result.put("createdAt", created.createdAt());

        log.debug("[Conversation] 会话已创建: convId={}, user={}, title={}",
                created.id(), userId.get(), created.title());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ═══════════════════════════════════════════════════════════
    //  ② 列表（updatedAt 倒序）
    // ═══════════════════════════════════════════════════════════

    /**
     * 列出当前用户的全部会话，按 updatedAt 倒序排列（最新会话在前）。
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> list(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        Optional<String> userId = resolveUserId();
        if (userId.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("未找到可信用户身份"));
        }
        List<Conversation> convs = conversationStore.findByUser(userId.get());

        List<ConversationItemDto> items = convs.stream()
                .map(c -> new ConversationItemDto(
                        c.id(), c.title(), c.messageCount(), c.createdAt(), c.updatedAt()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    // ═══════════════════════════════════════════════════════════
    //  ③ 详情
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取单个会话详情。
     *
     * <p>messages 字段返回空数组（后端不存储消息，前端从 Pinia 加载）。</p>
     */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<?> detail(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @PathVariable String id) {

        Optional<String> userId = resolveUserId();
        if (userId.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("未找到可信用户身份"));
        }
        Optional<Conversation> opt = conversationStore.findByUserAndId(userId.get(), id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.fail("会话不存在"));
        }

        Conversation c = opt.get();
        ConversationDetailDto dto = new ConversationDetailDto(
                c.id(), c.title(), List.of(), c.createdAt(), c.updatedAt());

        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    // ═══════════════════════════════════════════════════════════
    //  ④ 删除会话
    // ═══════════════════════════════════════════════════════════

    /**
     * 删除会话。
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @PathVariable String id) {

        Optional<String> userId = resolveUserId();
        if (userId.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("未找到可信用户身份"));
        }
        boolean removed = conversationStore.removeForUser(userId.get(), id);
        if (!removed) {
            return ResponseEntity.status(404).body(ApiResponse.fail("会话不存在"));
        }
        return ResponseEntity.ok(ApiResponse.ok("删除成功"));
    }

    // ═══════════════════════════════════════════════════════════
    //  ⑤ 更新标题
    // ═══════════════════════════════════════════════════════════

    /**
     * 更新会话标题（AI 自动总结后调用）。
     */
    @PutMapping("/conversations/{id}/title")
    public ResponseEntity<ApiResponse<Void>> updateTitle(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String newTitle = body.get("title");
        if (newTitle == null || newTitle.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("标题不能为空"));
        }

        Optional<String> userId = resolveUserId();
        if (userId.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("未找到可信用户身份"));
        }
        boolean updated = conversationStore.updateTitleForUser(userId.get(), id, newTitle);
        if (!updated) {
            return ResponseEntity.status(404).body(ApiResponse.fail("会话不存在"));
        }

        return ResponseEntity.ok(ApiResponse.ok("标题更新成功"));
    }

    // ═══════════════════════════════════════════════════════════
    //  内部辅助
    // ═══════════════════════════════════════════════════════════

    /**
     * 从服务端可信主体解析会话 owner。
     * <p>注意：{@code X-Session-Id} 是会话索引，不是身份事实；缺少可信主体时必须拒绝。</p>
     */
    private Optional<String> resolveUserId() {
        if (principalResolver == null) {
            return Optional.empty();
        }
        return principalResolver.current()
            .filter(AgentPrincipal::isAuthenticated)
            .map(AgentPrincipal::username)
            .filter(username -> username != null && !username.isBlank());
    }
}
