package com.atlas.dto;

/**
 * 会话列表单项 — API 返回用。
 *
 * <p>GET /api/agent/conversations 返回的数组元素类型，轻量化不携带消息内容。</p>
 *
 * @param id           会话唯一 ID
 * @param title        会话标题
 * @param messageCount 消息条数
 * @param createdAt    创建时间戳
 * @param updatedAt    最后更新时间戳
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
public record ConversationItemDto(
    String id,
    String title,
    int messageCount,
    long createdAt,
    long updatedAt
) {}
