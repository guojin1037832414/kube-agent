package com.atlas.memoryrag;

/**
 * Memory/RAG 来源证据摘要结果。
 *
 * <p>中文说明：sourceDigest 绑定来源级证据，chunkDigest 绑定检索片段级证据，
 * evidenceDigest 绑定完整证据输入。citationSeed 是未来回答引用可以复用的服务端种子，
 * 让前端、评测、审计和 RAG 引用可以围绕同一个证据锚点协作。</p>
 *
 * <p>安全边界：rawSourceAccepted、promptEvidenceAllowedNow、boundToIngestionRuntime、
 * reusableAcrossTenantScope 当前都必须保持 false。也就是说，本结果不是 prompt 权威，
 * 不代表检索运行时已经接入，不允许跨租户复用，也不能替代 reviewed trace、source custody
 * 与 release-blocking eval gate。</p>
 */
public record MemoryRagSourceEvidenceDigestResult(
    String schemaVersion,
    String sourceDigest,
    String chunkDigest,
    String evidenceDigest,
    String citationSeed,
    String digestSource,
    String algorithm,
    boolean rawSourceAccepted,
    boolean promptEvidenceAllowedNow,
    boolean boundToIngestionRuntime,
    boolean reusableAcrossTenantScope
) {
}
