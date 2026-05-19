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
