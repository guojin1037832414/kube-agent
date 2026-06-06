package com.atlas.controller;

import com.atlas.dto.*;
import com.atlas.store.ConversationStore;
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
 * <p><b>会话绑定：</b>通过 {@code X-Session-Id} header 识别当前用户，
 * 只返回该用户创建的会话。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
@RestController
@RequestMapping("/api/agent")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationStore conversationStore;

    public ConversationController(ConversationStore conversationStore) {
        this.conversationStore = conversationStore;
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

        String userId = resolveUserId(sessionId);
        String title = (body != null) ? body.get("title") : null;

        Conversation created = conversationStore.create(userId, title);

        // 转为前端期望的 CreateConversationResponse 格式
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", created.id());
        result.put("title", created.title());
        result.put("createdAt", created.createdAt());

        log.debug("[Conversation] 会话已创建: convId={}, user={}, title={}",
                created.id(), userId, created.title());
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

        String userId = resolveUserId(sessionId);
        List<Conversation> convs = conversationStore.findByUser(userId);

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

        String userId = resolveUserId(sessionId);
        Optional<Conversation> opt = conversationStore.findByUserAndId(userId, id);
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

        String userId = resolveUserId(sessionId);
        boolean removed = conversationStore.removeForUser(userId, id);
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

        String userId = resolveUserId(sessionId);
        boolean updated = conversationStore.updateTitleForUser(userId, id, newTitle);
        if (!updated) {
            return ResponseEntity.status(404).body(ApiResponse.fail("会话不存在"));
        }

        return ResponseEntity.ok(ApiResponse.ok("标题更新成功"));
    }

    // ═══════════════════════════════════════════════════════════
    //  内部辅助
    // ═══════════════════════════════════════════════════════════

    /**
     * 从 sessionId 解析用户标识。
     * <p>未登录时返回 "anonymous" 作为降级 userId。</p>
     */
    private String resolveUserId(String sessionId) {
        return (sessionId != null && !sessionId.isBlank()) ? sessionId : "anonymous";
    }
}
