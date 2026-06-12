package com.atlas.memoryrag;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Memory/RAG 来源证据输入模型。
 *
 * <p>中文说明：这个 record 是未来持久记忆与 RAG 引用进入 Agent Core 前的证据闸门。
 * 它只接收已经脱敏、已经摘要化或已经计算好的证据指纹，用于推导 deterministic digest。
 * 它故意不包含原始文档、原始 prompt、原始租户标识、Authorization header 或 token。</p>
 *
 * <p>安全边界：当前模型不是检索输入，不会触发向量库、embedding、reranker、LLM、
 * MCP Tool 或 kube-manager。未来接入向量库、GraphRAG、reranker 或多 Agent 互操作时，
 * 所有证据都要先落到这个可审计的“来源证据指纹”模型上，再经过 reviewed trace 和 eval gate，
 * 不能把 caller-submitted summary 当作 prompt 权威。</p>
 */
public record MemoryRagSourceEvidenceInput(
    String sourceId,
    String sourceType,
    String sourceVersion,
    String sourceUriDigest,
    String tenantScopeDigest,
    String sourceAclDigest,
    String redactionStatus,
    String redactionPolicyDigest,
    String retentionPolicy,
    String sourceContentDigest,
    String sourceMetadataDigest,
    String chunkContentDigest,
    String retrievalPolicyDigest
) {

    private static final Pattern STABLE_ID = Pattern.compile("[a-zA-Z0-9._:-]{1,128}");
    private static final Pattern SHA256 = Pattern.compile("(?i)(sha256:)?[a-f0-9]{64}");
    private static final List<String> FORBIDDEN_RAW_MARKERS = List.of(
        "authorization",
        "bearer ",
        "password",
        "secret",
        "token=",
        "raw-prompt",
        "raw-document",
        "raw-conversation"
    );
    private static final Set<String> SOURCE_TYPES = Set.of(
        "kube-manager-doc",
        "runbook",
        "audit-summary",
        "conversation-summary",
        "operator-note",
        "tool-manifest",
        "evaluation-trace",
        "agent-architecture-doc"
    );
    private static final Set<String> REDACTION_STATUSES = Set.of(
        "REDACTED",
        "SUMMARY_ONLY",
        "NO_SENSITIVE_CONTENT"
    );
    private static final Set<String> RETENTION_POLICIES = Set.of(
        "EPHEMERAL_30D",
        "DURABLE_POLICY_BOUND",
        "DELETE_EXPORT_SUPPORTED"
    );

    /**
     * 构造时立即规整并校验所有证据字段。
     *
     * <p>中文说明：把校验放在 canonical constructor 中，是为了保证对象一旦创建成功，
     * 就已经满足“稳定 ID + SHA-256 digest + 受控枚举”的契约。后续 digest 推导器无需再次猜测
     * 字段是否包含原始 secret、raw-document 或 raw-prompt。</p>
     */
    public MemoryRagSourceEvidenceInput {
        sourceId = stableId("sourceId", sourceId);
        sourceType = sourceType(sourceType);
        sourceVersion = stableId("sourceVersion", sourceVersion);
        sourceUriDigest = digest("sourceUriDigest", sourceUriDigest);
        tenantScopeDigest = digest("tenantScopeDigest", tenantScopeDigest);
        sourceAclDigest = digest("sourceAclDigest", sourceAclDigest);
        redactionStatus = enumValue("redactionStatus", redactionStatus, REDACTION_STATUSES);
        redactionPolicyDigest = digest("redactionPolicyDigest", redactionPolicyDigest);
        retentionPolicy = enumValue("retentionPolicy", retentionPolicy, RETENTION_POLICIES);
        sourceContentDigest = digest("sourceContentDigest", sourceContentDigest);
        sourceMetadataDigest = digest("sourceMetadataDigest", sourceMetadataDigest);
        chunkContentDigest = digest("chunkContentDigest", chunkContentDigest);
        retrievalPolicyDigest = digest("retrievalPolicyDigest", retrievalPolicyDigest);
    }

    /**
     * 校验安全稳定 ID。
     *
     * <p>安全边界：稳定 ID 只能用于证据定位，不允许把原始租户、用户、token 或文档正文塞进来。</p>
     */
    private static String stableId(String name, String value) {
        String normalized = required(name, value);
        if (!STABLE_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be a stable id with 1-128 safe characters");
        }
        rejectRawMarkers(name, normalized);
        return normalized;
    }

    /**
     * 校验来源类型白名单。
     *
     * <p>中文说明：来源类型必须显式枚举，避免未来把任意业务字符串误当作可进入 RAG 的证据类型。</p>
     */
    private static String sourceType(String value) {
        String normalized = required("sourceType", value).toLowerCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("sourceType must be one of " + SOURCE_TYPES);
        }
        return normalized;
    }

    /**
     * 校验受控枚举字段。
     *
     * <p>中文说明：redactionStatus 与 retentionPolicy 是合规语义，不允许调用方自由发挥。</p>
     */
    private static String enumValue(String name, String value, Set<String> allowed) {
        String normalized = required(name, value).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(name + " must be one of " + allowed);
        }
        return normalized;
    }

    /**
     * 校验 SHA-256 指纹字段。
     *
     * <p>安全边界：这里只接受 digest，不接受 URI、正文、ACL 原文或 prompt 片段，
     * 因此错误输入会 fail-fast，而不是被静默写入证据链。</p>
     */
    private static String digest(String name, String value) {
        String normalized = required(name, value).toLowerCase(Locale.ROOT);
        rejectRawMarkers(name, normalized);
        if (!SHA256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be a SHA-256 digest");
        }
        if (!normalized.startsWith("sha256:")) {
            normalized = "sha256:" + normalized;
        }
        return normalized;
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    /**
     * 拦截明显的原始敏感材料标记。
     *
     * <p>中文说明：这不是完整 DLP，而是证据模型的低成本防呆线；真正接入生产 RAG 前，
     * 仍需要专门的内容分级、脱敏、租户隔离和删除/导出机制。</p>
     */
    private static void rejectRawMarkers(String name, String value) {
        String lowered = value.toLowerCase(Locale.ROOT);
        for (String marker : FORBIDDEN_RAW_MARKERS) {
            if (lowered.contains(marker)) {
                throw new IllegalArgumentException(name + " must not contain raw secret or document markers");
            }
        }
    }
}
