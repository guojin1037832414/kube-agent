package com.atlas.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 发布 reviewed redacted trace fixture 的仓库模板和 schema。
 *
 * <p>中文说明：上一层 manifest 只能告诉人审“当前缺哪些 fixture 文件”，本服务继续给出
 * 真实 fixture 入仓前应填写的 JSON 结构、命名规则和每个 trace set 的待补模板行。
 * 输入只来自 trace-set catalog；输出给前端工作台、人审者和学习文档渲染，不会创建任何文件。</p>
 *
 * <p>安全边界：这是 template-only / read-only / schema-only 服务，不创建 fixture 文件、不写
 * {@code eval-trace-sets.json}、不接收 caller traceId、不接受上传，不运行 eval/replay，
 * 不调用 Tool/MCP/LLM/RAG/kube-manager，不写 HITL/audit/memory，也不打开 CI blocking 或 release authority。</p>
 */
@Service
public class AgentReviewedTraceFixtureTemplateService {

    private final AgentEvalTraceSetCatalogService traceSetCatalogService;
    private final Clock clock;

    @Autowired
    public AgentReviewedTraceFixtureTemplateService(AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this(traceSetCatalogService, Clock.systemUTC());
    }

    AgentReviewedTraceFixtureTemplateService(AgentEvalTraceSetCatalogService traceSetCatalogService,
                                             Clock clock) {
        this.traceSetCatalogService = traceSetCatalogService;
        this.clock = clock;
    }

    /**
     * 返回 fixture 作者模板。
     *
     * <p>中文说明：这里生成的是“如何写真实 reviewed fixture”的说明和结构化骨架，
     * 不是可以直接提交的 fixture。骨架中的 traceId 使用占位符，防止为了通过 manifest 而制造假证据。</p>
     */
    public AgentReviewedTraceFixtureTemplateResponse template() {
        return AgentReviewedTraceFixtureTemplateResponse.of(
            Instant.now(clock),
            traceSetCatalogService.catalog()
        );
    }
}
