package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 4 中文教学注释契约测试。
 *
 * <p>中文说明：本批覆盖 Memory / MemoryRAG / Audit / Observability / Replay / Eval
 * 这条证据链。它们共同回答一个顶级 Agent 的核心问题：哪些材料可以被当作只读证据，
 * 哪些材料不能被误用成 prompt 权威、运行时授权或发布许可。</p>
 *
 * <p>安全边界：本测试只读取源码，不启动 Spring、不访问 kube-manager、不调用 LLM、
 * 不执行 Tool、不打开 MCP、不读写 audit JSONL，也不运行 eval。它只锁定关键中文教学 marker，
 * 防止后续重构删掉“只读证据、脱敏、admin-only、不是 release authority”这些学习解释。</p>
 */
class Batch4ChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/memory/MemoryController.java"),
            List.of("中文说明", "安全边界", "caller-submitted bounded summary",
                "RAG 证据", "自动注入 prompt")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/memory/ConversationSummaryMemoryStore.java"),
            List.of("安全边界", "redaction 不是 DLP", "source custody",
                "reviewed trace", "eval gate")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/memoryrag/MemoryRagSourceEvidenceDigestDeriver.java"),
            List.of("中文说明", "安全边界", "证据指纹层", "不执行检索",
                "不调用向量库", "LLM", "MCP", "kube-manager", "不是当前 prompt 权威")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/memoryrag/MemoryRagSourceEvidenceInput.java"),
            List.of("中文说明", "安全边界", "不是检索输入", "不会触发向量库",
                "LLM", "MCP Tool", "不能把 caller-submitted summary 当作 prompt 权威")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/memoryrag/MemoryRagSourceEvidenceDigestResult.java"),
            List.of("中文说明", "安全边界", "不是 prompt 权威",
                "不允许跨租户复用", "release-blocking eval gate")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/audit/AgentAuditRecorder.java"),
            List.of("安全边界", "脱敏审计事件", "不重新授权 Tool",
                "不读取原始参数值", "不是执行许可", "fail-closed")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/audit/AgentAuditEventFactory.java"),
            List.of("中文说明", "安全边界", "服务端可信主体",
                "参数“存在性摘要”", "不执行 Tool", "不调用 kube-manager")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/audit/InMemoryAgentAuditRecorder.java"),
            List.of("中文说明", "安全边界", "redacted read model",
                "admin-only", "不访问 kube-manager", "fail-closed")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/audit/JsonlAgentAuditDurableSink.java"),
            List.of("中文说明", "安全边界", "只存脱敏证据",
                "raw parameter values", "不执行 Tool", "不调用 MCP",
                "不访问 kube-manager", "release authority")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/audit/JsonlAgentAuditQueryService.java"),
            List.of("中文说明", "安全边界", "admin-only", "redacted read model",
                "不提供原文导出", "有界扫描", "metadata-only")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/ObservabilityController.java"),
            List.of("中文说明", "安全边界", "admin-only", "只读证据",
                "不执行 Tool", "不调用 MCP", "不访问 kube-manager", "不调用 LLM",
                "不改变 prompt", "不授予 release authority")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentReplayTimelineService.java"),
            List.of("中文说明", "安全边界", "只读脱敏审计视图", "不读取 raw audit",
                "不重新执行 Tool", "不调用 MCP", "不访问 kube-manager", "不是 prompt 权威")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalReportService.java"),
            List.of("中文说明", "安全边界", "只读 replay DTO", "不调用 LLM",
                "不访问 kube-manager", "不参与 Tool 放行", "不授予", "release authority")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentReviewedEvalTraceEvidenceService.java"),
            List.of("中文说明", "安全边界", "只读方式", "不修改 catalog",
                "不运行 eval", "不提升 trace set", "release authority")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentMemoryRagReadinessService.java"),
            List.of("中文说明", "安全边界", "admin-only", "只读证据",
                "不是 prompt 权威", "不查向量库")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentMemoryRagSourceEvidenceDigestContractService.java"),
            List.of("中文说明", "安全边界", "synthetic sample", "不执行检索",
                "不调用向量库/LLM/Tool/MCP/kube-manager", "不是 prompt 引用授权")
        )
    );

    private static final List<String> MOJIBAKE_MARKERS = List.of(
        "鎸", "涓", "璇", "鑴", "绾", "杈", "鏉", "瀛",
        "鐢", "鍙", "鍔", "姝", "闂", "娴", "搴", "鏁", "闀"
    );

    @Test
    void batch4EvidenceChainFiles_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep Batch 4 Chinese teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }

    @Test
    void batch4TouchedFiles_shouldNotContainCommonMojibakeMarkers() throws Exception {
        for (Path path : REQUIRED_MARKERS.keySet()) {
            String source = Files.readString(path, StandardCharsets.UTF_8);

            assertThat(source)
                .as(path + " should not contain common mojibake fragments in teaching comments")
                .doesNotContain(MOJIBAKE_MARKERS.toArray(String[]::new));
        }
    }
}
