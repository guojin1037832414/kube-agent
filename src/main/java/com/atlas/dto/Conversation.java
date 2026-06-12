package com.atlas.dto;

import java.util.List;

/**
 * 会话（Conversation）实体 — 内部存储用。
 *
 * <p>ConversationStore 的缓存值类型，保存会话元数据（不存消息内容，
 * 消息由前端 Pinia / sessionStorage 管理）。</p>
 *
 * <p>中文说明：Conversation 是“聊天会话元数据”，不是 Agent 运行 trace，也不是审计证据。
 * userId 表示该会话归属的服务端可信用户；所有详情、改名、删除都必须再次按当前 Principal 收敛。</p>
 *
 * <p>安全边界：id 只是资源定位符，不是访问令牌；title 和 messageCount 是前端/用户体验字段，
 * 不能被当作 prompt、HITL、audit、eval 或 release 证据。</p>
 *
 * @param id            会话唯一 ID
 * @param userId        所属用户标识（来源于 sessionId 或 username）
 * @param title         会话标题（AI 自动总结或用户自定义）
 * @param messageCount  消息条数（前端更新）
 * @param createdAt     创建时间戳
 * @param updatedAt     最后更新时间戳
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
public record Conversation(
    String id,
    String userId,
    String title,
    int messageCount,
    long createdAt,
    long updatedAt
) {}
