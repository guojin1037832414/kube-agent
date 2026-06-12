package com.atlas.memoryrag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Memory/RAG 来源证据摘要推导器。
 *
 * <p>中文说明：这是未来 RAG 可信引用链的“证据指纹层”，负责把已经脱敏、
 * 已经规整好的来源、片段和租户边界信息变成稳定 SHA-256 摘要。这样前端、评测、
 * 审计和后续向量检索都可以引用同一组 digest，而不是复制原始文档或敏感上下文。</p>
 *
 * <p>安全边界：M5.60 只定义确定性摘要合同；本类不读取文档、不写入 memory、
 * 不执行检索、不调用向量库、embedding、reranker、LLM、MCP tools/call 或
 * kube-manager。它生成的 citationSeed 只是未来引用锚点，不是当前 prompt 权威，
 * 也不能绕过 reviewed trace、source custody 与 eval gate。</p>
 */
public class MemoryRagSourceEvidenceDigestDeriver {

    public static final String SCHEMA_VERSION = "agent-memory-rag-source-evidence-digest.v1";
    public static final String DIGEST_SOURCE = "server-derived-sha256-redacted-source-evidence.v1";
    public static final String CITATION_SEED_PREFIX = "rag-cite-v1-";
    public static final String ALGORITHM = "SHA-256";

    /**
     * 根据已脱敏的来源证据输入生成三层 digest。
     *
     * <p>中文说明：sourceDigest 绑定文档/来源级证据，chunkDigest 绑定未来检索片段级证据，
     * evidenceDigest 绑定完整证据上下文。三者分层是为了让后续 GraphRAG、reranker、
     * 多 Agent 审阅或引用修复可以定位“到底是哪一层证据变化了”。</p>
     */
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

    /**
     * 构造来源级 canonical evidence。
     *
     * <p>中文说明：这里故意只拼接 digest、枚举和稳定 ID，不拼接原始 URI、原始 ACL、
     * 原始租户 ID 或文档正文，保证输出可以进入审计/评测而不扩大敏感面。</p>
     */
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

    /**
     * 构造片段级 canonical evidence。
     *
     * <p>中文说明：chunk 依赖 sourceDigest，避免片段摘要脱离来源边界后被跨租户复用；
     * retrievalPolicyDigest 只描述未来检索策略，不会在这里执行检索。</p>
     */
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

    /**
     * 构造完整证据 canonical evidence。
     *
     * <p>中文说明：完整证据把来源、片段、租户、ACL、脱敏与保留策略重新绑定，
     * 这是未来回答引用、回放评测和 durable memory 生命周期审计共享的证据锚点。</p>
     */
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

    /**
     * 使用 JDK 内置 SHA-256 生成十六进制摘要。
     *
     * <p>安全边界：这里不引入外部加密服务或网络调用，避免摘要推导依赖运行时环境。</p>
     */
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
