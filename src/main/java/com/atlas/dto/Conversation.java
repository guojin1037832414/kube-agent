package com.atlas.dto;

import java.util.List;

/**
 * 会话（Conversation）实体 — 内部存储用。
 *
 * <p>ConversationStore 的缓存值类型，保存会话元数据（不存消息内容，
 * 消息由前端 Pinia / sessionStorage 管理）。</p>
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
