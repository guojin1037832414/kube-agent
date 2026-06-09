package com.atlas.memoryrag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Pure Java digest deriver for future Memory/RAG source evidence.
 *
 * <p>M5.60 只定义确定性摘要合同：不读取文档，不写入 memory，不调用向量库、
 * embedding、reranker、LLM、MCP tools/call 或 kube-manager。</p>
 */
public class MemoryRagSourceEvidenceDigestDeriver {

    public static final String SCHEMA_VERSION = "agent-memory-rag-source-evidence-digest.v1";
    public static final String DIGEST_SOURCE = "server-derived-sha256-redacted-source-evidence.v1";
    public static final String CITATION_SEED_PREFIX = "rag-cite-v1-";
    public static final String ALGORITHM = "SHA-256";

    public MemoryRagSourceEvidenceDigestResult derive(MemoryRagSourceEvidenceInput input) {
        String sourceDigest = sha256Hex(sourceCanonicalEvidence(input));
        String chunkDigest = sha256Hex(chunkCanonicalEvidence(input, sourceDigest));
        String evidenceDigest = sha256Hex(evidenceCanonical(input, sourceDigest, chunkDigest));
        return new MemoryRagSourceEvidenceDigestResult(
            SCHEMA_VERSION,
            "sha256:" + sourceDigest,
            "sha256:" + chunkDigest,
            "sha256:" + evidenceDigest,
            CITATION_SEED_PREFIX + evidenceDigest,
            DIGEST_SOURCE,
            ALGORITHM,
            false,
            false,
            false,
            false
        );
    }

    private String sourceCanonicalEvidence(MemoryRagSourceEvidenceInput input) {
        return String.join("\n",
            "schema=" + SCHEMA_VERSION,
            "scope=source",
            "sourceId=" + input.sourceId(),
            "sourceType=" + input.sourceType(),
            "sourceVersion=" + input.sourceVersion(),
            "sourceUriDigest=" + input.sourceUriDigest(),
            "tenantScopeDigest=" + input.tenantScopeDigest(),
            "sourceAclDigest=" + input.sourceAclDigest(),
            "redactionStatus=" + input.redactionStatus(),
            "redactionPolicyDigest=" + input.redactionPolicyDigest(),
            "retentionPolicy=" + input.retentionPolicy(),
            "sourceContentDigest=" + input.sourceContentDigest(),
            "sourceMetadataDigest=" + input.sourceMetadataDigest()
        );
    }

    private String chunkCanonicalEvidence(MemoryRagSourceEvidenceInput input, String sourceDigest) {
        return String.join("\n",
            "schema=" + SCHEMA_VERSION,
            "scope=chunk",
            "sourceDigest=sha256:" + sourceDigest,
            "chunkContentDigest=" + input.chunkContentDigest(),
            "retrievalPolicyDigest=" + input.retrievalPolicyDigest(),
            "tenantScopeDigest=" + input.tenantScopeDigest(),
            "redactionStatus=" + input.redactionStatus()
        );
    }

    private String evidenceCanonical(MemoryRagSourceEvidenceInput input,
                                     String sourceDigest,
                                     String chunkDigest) {
        return String.join("\n",
            "schema=" + SCHEMA_VERSION,
            "scope=evidence",
            "sourceDigest=sha256:" + sourceDigest,
            "chunkDigest=sha256:" + chunkDigest,
            "tenantScopeDigest=" + input.tenantScopeDigest(),
            "sourceAclDigest=" + input.sourceAclDigest(),
            "redactionStatus=" + input.redactionStatus(),
            "retentionPolicy=" + input.retentionPolicy(),
            "retrievalPolicyDigest=" + input.retrievalPolicyDigest()
        );
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        }
    }
}
