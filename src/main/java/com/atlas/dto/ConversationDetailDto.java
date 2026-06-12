package com.atlas.dto;

import java.util.List;

/**
 * 会话详情响应 — API 返回用。
 *
 * <p>GET /api/agent/conversations/{id} 返回此结构。
 * messages 字段为空数组（后端不存储消息内容，由前端管理）。</p>
 *
 * <p>中文说明：当前后端只管理会话元数据，详情接口保留 messages 空数组是为了兼容前端结构，
 * 不是服务端长期记忆或 RAG 文档来源。</p>
 *
 * <p>安全边界：后端不恢复历史 prompt、Tool 输入、HITL 决策或审计原文。
 * 如果未来要服务端持久化消息，必须先补租户隔离、脱敏、删除/导出、Memory/RAG source custody
 * 和 eval gate。</p>
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
