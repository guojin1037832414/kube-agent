package com.atlas.dto;

import java.util.List;

/**
 * 会话详情响应 — API 返回用。
 *
 * <p>GET /api/agent/conversations/{id} 返回此结构。
 * messages 字段为空数组（后端不存储消息内容，由前端管理）。</p>
 *
 * @param id        会话唯一 ID
 * @param title     会话标题
 * @param messages  消息列表（后端返回空数组，前端从自身 Pinia 加载）
 * @param createdAt 创建时间戳
 * @param updatedAt 最后更新时间戳
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
public record ConversationDetailDto(
    String id,
    String title,
    List<Object> messages,
    long createdAt,
    long updatedAt
) {}
