package com.atlas.observability;

/**
 * reviewed fixture 人工 Git 审阅门禁的输入模型。
 *
 * <p>中文说明：这个请求体只承载人已经在 Git review 流程里补齐的字段，用来让后端做
 * validate-only 校验。它不是 fixture 上传接口，也不是运行时授权入口；其中的
 * {@code selectedCandidateTraceId} 只能和当前 human review package 自动选出的候选做一致性比对，
 * 不能被调用方当成新的证据 trace 注入。</p>
 *
 * <p>安全边界：本 record 不包含 fixture JSON 文件内容，不接收 raw audit/replay/report，不携带 token、
 * password 或 kube-manager 参数；服务层只读取当前 redacted package 并校验 sha256，不写 catalog、
 * 不创建 reviewed fixture 文件、不触发 Tool/MCP/LLM/RAG/kube-manager，也不授予 CI/release 权力。</p>
 */
public record AgentReviewedTraceFixtureHumanReviewGateRequest(
    String selectedCandidateTraceId,
    String sourceCommitSha,
    String reviewer,
    String reviewTimestamp,
    String candidateEvidenceDigest,
    String evidenceDigest
) {
}
