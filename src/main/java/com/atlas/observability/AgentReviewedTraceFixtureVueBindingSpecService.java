package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * reviewed fixture 工作流的 Vue 绑定规格服务。
 *
 * <p>中文说明：本服务把 eval workbench 已有的 capability manifest 和 overview 组合成前端实现规格，
 * 让 `vue-kube-manager` 后续能按同一套字段渲染自动候选、人审包、人审门禁、readiness 和禁用动作。
 * 它只发布绑定契约，不写前端仓库，也不触发候选发现、Tool 执行或 kube-manager 调用。</p>
 *
 * <p>安全边界：admin-only / read-only / binding-spec-only；这里只读取后端已有只读模型，
 * 不接收 caller traceId，不上传 fixture，不写 catalog，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 HITL/audit/memory，也不授予 CI/release 或 Phase 2 运行时权力。</p>
 */
@Service
public class AgentReviewedTraceFixtureVueBindingSpecService {

    private final AgentEvalWorkbenchCapabilitiesService capabilitiesService;
    private final AgentEvalWorkbenchOverviewService overviewService;
    private final Clock clock;

    // 生产上下文必须显式选择无 Clock 参数的构造器；下面的 Clock 构造器只服务确定性测试，不能让 Spring 误判为默认构造路径。
    @Autowired
    public AgentReviewedTraceFixtureVueBindingSpecService(AgentEvalWorkbenchCapabilitiesService capabilitiesService,
                                                          AgentEvalWorkbenchOverviewService overviewService) {
        this(capabilitiesService, overviewService, Clock.systemUTC());
    }

    AgentReviewedTraceFixtureVueBindingSpecService(AgentEvalWorkbenchCapabilitiesService capabilitiesService,
                                                   AgentEvalWorkbenchOverviewService overviewService,
                                                   Clock clock) {
        this.capabilitiesService = capabilitiesService;
        this.overviewService = overviewService;
        this.clock = clock;
    }

    /**
     * 生成前端只读绑定规格。
     *
     * <p>中文说明：source capabilities 告诉前端有哪些后端 endpoint 和 schema；source overview 告诉前端
     * trace set 行如何进入 reviewed fixture 工作流。两者都只是治理读模型，不会因为被组合到这里而获得运行时权力。</p>
     */
    public AgentReviewedTraceFixtureVueBindingSpecResponse spec() {
        return AgentReviewedTraceFixtureVueBindingSpecResponse.of(
            Instant.now(clock),
            capabilitiesService.capabilities(),
            overviewService.overview()
        );
    }
}
