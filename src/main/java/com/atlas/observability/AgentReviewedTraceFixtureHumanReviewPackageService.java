package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * reviewed fixture 人审包组合服务。
 *
 * <p>中文说明：这个服务承接自动 candidate workbench，把“发现候选 + 生成预检草稿”进一步整理成
 * 人工 Git review 可使用的清单。它复用现有只读 workbench，不重新接受 traceId，也不绕过 candidate
 * preview 的 redacted replay / deterministic eval 证据判断。</p>
 *
 * <p>安全边界：本服务只生成人审读模型，不创建 JSON fixture，不写 reviewed fixture 目录或
 * {@code eval-trace-sets.json}，不执行 Tool/MCP/LLM/RAG/kube-manager，不写 HITL/audit/memory，
 * 也不启用 CI blocking 或 release authority。</p>
 */
@Service
public class AgentReviewedTraceFixtureHumanReviewPackageService {

    private final AgentReviewedTraceFixtureCandidateWorkbenchService candidateWorkbenchService;

    public AgentReviewedTraceFixtureHumanReviewPackageService(
        AgentReviewedTraceFixtureCandidateWorkbenchService candidateWorkbenchService) {
        this.candidateWorkbenchService = candidateWorkbenchService;
    }

    /**
     * 为某个 trace set 生成只读人审包。
     *
     * <p>中文说明：limit 继续只影响 redacted audit discovery 的扫描上限；请求本身不接受 caller
     * traceIds。未知 trace set 会沿用 workbench 的空 Optional 语义，让 Controller 返回 404。</p>
     */
    public Optional<AgentReviewedTraceFixtureHumanReviewPackageResponse> packageForTraceSet(String traceSetId,
                                                                                            Integer limit) {
        return candidateWorkbenchService.workbench(traceSetId, limit)
            .map(AgentReviewedTraceFixtureHumanReviewPackageResponse::from);
    }
}
