package com.atlas.observability;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * reviewed fixture 人工 Git 审阅门禁服务。
 *
 * <p>中文说明：本服务接在 human review package 之后，用当前自动候选包和调用方提交的人审字段做只读校验。
 * 它解决的是“人已经填完字段后，机器能不能确认摘要和候选仍然一致”的问题，而不是替人创建 fixture 文件。</p>
 *
 * <p>安全边界：服务只复用 {@link AgentReviewedTraceFixtureHumanReviewPackageService} 的 redacted package，
 * 不重新接受 caller traceId 作为证据来源，不读取 raw audit，不执行 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 reviewed fixture 目录、不写 {@code eval-trace-sets.json}，也不写 HITL/audit/memory。</p>
 */
@Service
public class AgentReviewedTraceFixtureHumanReviewGateService {

    private final AgentReviewedTraceFixtureHumanReviewPackageService humanReviewPackageService;

    public AgentReviewedTraceFixtureHumanReviewGateService(
        AgentReviewedTraceFixtureHumanReviewPackageService humanReviewPackageService) {
        this.humanReviewPackageService = humanReviewPackageService;
    }

    /**
     * 校验某个 trace set 的人工 Git review 字段。
     *
     * <p>中文说明：{@code limit} 仍然只影响底层 redacted audit discovery 的扫描上限；请求体字段只用于校验，
     * 不会被写入仓库或运行时存储。未知 trace set 会沿用 package 服务的空 Optional，让 Controller 返回 404。</p>
     */
    public Optional<AgentReviewedTraceFixtureHumanReviewGateResponse> gate(String traceSetId,
                                                                           Integer limit,
                                                                           AgentReviewedTraceFixtureHumanReviewGateRequest request) {
        return humanReviewPackageService.packageForTraceSet(traceSetId, limit)
            .map(reviewPackage -> AgentReviewedTraceFixtureHumanReviewGateResponse.from(reviewPackage, request));
    }
}
