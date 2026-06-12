package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 发布 reviewed redacted trace fixture 接入合同。
 *
 * <p>中文说明：这个服务只回答“一个脱敏 trace fixture 在进入人审、Git review 和后续目录晋升前，
 * 必须具备哪些字段、证据和禁止项”。它是 intake-spec-only / read-only / contract-only 层，
 * 不是上传接口，也不是 traceId 提交通道。</p>
 *
 * <p>安全边界：本服务不接收请求体、不接受调用方 traceId、不读取 raw audit、不修改
 * `eval-trace-sets.json`，不运行 eval/replay，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 audit/memory，也不打开 CI blocking 或 release authority。</p>
 */
@Service
public class AgentReviewedTraceFixtureIntakeContractService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;
    private final Clock clock;

    @Autowired
    public AgentReviewedTraceFixtureIntakeContractService(AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this(traceSetCatalogService, Clock.systemUTC());
    }

    AgentReviewedTraceFixtureIntakeContractService(AgentEvalTraceSetCatalogService traceSetCatalogService,
                                                   Clock clock) {
        this.traceSetCatalogService = traceSetCatalogService;
        this.clock = clock;
    }

    /**
     * 返回 reviewed trace fixture 接入规范。
     *
     * <p>中文说明：generatedAt 只表示 read model 生成时间；traceSet catalog 只用于计算当前待补
     * fixture 的目录范围，不会被本服务写回或晋升。</p>
     */
    public AgentReviewedTraceFixtureIntakeContractResponse contract() {
        return AgentReviewedTraceFixtureIntakeContractResponse.of(
            Instant.now(clock),
            traceSetCatalogService.catalog()
        );
    }
}
