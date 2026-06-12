package com.atlas.dto;

import java.util.Set;

/**
 * 认证会话数据 — SessionStore 的缓存值类型。
 *
 * <p>每个登录成功的用户在 Caffeine 缓存中对应一条 SessionData，
 * 包含身份凭证（JWT Token）、用户名、组织 ID 等信息。</p>
 *
 * <p>TTL = 30min，无访问自动过期。</p>
 *
 * <p>中文说明：这是 kube-agent 内部的服务端会话事实，来源是登录成功后的 kube-manager token
 * 和可信 orgId 解析结果。后续 AuthTokenFilter、AgentPrincipal、Tool 执行边界会围绕这些字段
 * 恢复当前用户上下文。</p>
 *
 * <p>安全边界：token 是敏感凭证，只能在服务端内存中用于调用 kube-manager，不能返回给前端、
 * 写入普通日志、写入 Memory/RAG、写入 eval fixture 或出现在 prompt 中。organizationId 也必须
 * 由服务端可信链路写入，不能来自前端任意 claim。</p>
 *
 * @param token        kube-manager 返回的 JWT Token（后续透传给后端 API）
 * @param username     用户名
 * @param organizationId 组织/租户 ID
 * @param role         角色标识
 * @param permissions  额外权限集合
 * @param createdAt    会话创建时间戳
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
public record SessionData(
    String token,
    String username,
    String organizationId,
    String role,
    Set<String> permissions,
    long createdAt
) {}
