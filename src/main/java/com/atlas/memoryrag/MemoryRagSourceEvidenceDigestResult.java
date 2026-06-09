package com.atlas.memoryrag;

/**
 * Deterministic digest result for future cited Memory/RAG evidence.
 *
 * <p>中文说明：sourceDigest 绑定来源级证据，chunkDigest 绑定检索片段级证据，
 * evidenceDigest 绑定完整证据输入。citationSeed 是未来回答引用可以复用的服务端种子，
 * 但当前阶段仍然不允许把检索证据写入 prompt。</p>
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
