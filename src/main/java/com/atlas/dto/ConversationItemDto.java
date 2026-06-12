package com.atlas.dto;

/**
 * 会话列表单项 — API 返回用。
 *
 * <p>GET /api/agent/conversations 返回的数组元素类型，轻量化不携带消息内容。</p>
 *
 * <p>中文说明：这是前端侧边栏列表的只读 DTO，只暴露当前用户有权看到的会话元数据。</p>
 *
 * <p>安全边界：列表 DTO 不携带消息内容、prompt、Tool 参数、token、audit 或 Memory/RAG
 * 证据；Controller 必须先按当前可信 Principal 过滤 Conversation，再转换为该 DTO。</p>
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
