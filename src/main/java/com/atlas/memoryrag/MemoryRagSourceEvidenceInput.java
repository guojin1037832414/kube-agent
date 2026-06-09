package com.atlas.memoryrag;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Future Memory/RAG source evidence used for deterministic digest derivation.
 *
 * <p>中文说明：这个输入模型只接收已经脱敏、已经摘要化或已经计算好的证据指纹。
 * 它故意不包含原始文档、原始 prompt、原始租户标识、Authorization header 或 token。
 * 未来接入向量库、GraphRAG、reranker 或多 Agent 互操作时，所有证据都要先落到这个
 * 可审计的“来源证据指纹”模型上。</p>
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

    private static String stableId(String name, String value) {
        String normalized = required(name, value);
        if (!STABLE_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be a stable id with 1-128 safe characters");
        }
        rejectRawMarkers(name, normalized);
        return normalized;
    }

    private static String sourceType(String value) {
        String normalized = required("sourceType", value).toLowerCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("sourceType must be one of " + SOURCE_TYPES);
        }
        return normalized;
    }

    private static String enumValue(String name, String value, Set<String> allowed) {
        String normalized = required(name, value).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(name + " must be one of " + allowed);
        }
        return normalized;
    }

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

    private static void rejectRawMarkers(String name, String value) {
        String lowered = value.toLowerCase(Locale.ROOT);
        for (String marker : FORBIDDEN_RAW_MARKERS) {
            if (lowered.contains(marker)) {
                throw new IllegalArgumentException(name + " must not contain raw secret or document markers");
            }
        }
    }
}
