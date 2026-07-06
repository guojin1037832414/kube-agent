package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4-PX.4 Tool 执行入口源码契约测试。
 *
 * <p>本测试只读取 {@code src/main/java} 下的 Java 源码，不启动 Spring、不调用 LLM、
 * 不访问 kube-manager，也不会执行任何真实 Tool。它的目标是把生产代码中所有
 * {@code BaseTool#execute(Map)} 直接调用点固定成一份可审计清单。</p>
 *
 * <p>重要安全语义：{@link com.atlas.tool.execution.SafeToolExecutor} 是唯一永久允许
 * 真实调用 {@code tool.execute(toolParams)} 的统一安全边界。其它历史入口进入
 * {@code TEMPORARY_DIRECT_EXECUTE_ALLOWLIST} 只是为了显式登记迁移债务，绝不是安全豁免。
 * 后续每迁移一个入口，就应该从临时白名单中删除对应项，让白名单持续收敛。</p>
 */
class M4Px4ToolExecuteEntrypointContractTest {

    /** 生产源码根目录。测试仅做静态读取，不会加载任何生产 Bean。 */
    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");

    /** SafeToolExecutor 源码路径，用于单独锁定统一安全边界内部结构。 */
    private static final Path SAFE_TOOL_EXECUTOR = Path.of(
        "src/main/java/com/atlas/tool/execution/SafeToolExecutor.java");

    /** Graph / ReAct / ToolCallback 等入口源码路径，用于锁定终极 Agent 的统一执行入口矩阵。 */
    private static final Path GRAPH_CONFIG = Path.of("src/main/java/com/atlas/graph/config/AtlasGraphConfig.java");
    private static final Path REACT_ENGINE = Path.of("src/main/java/com/atlas/react/ReActEngine.java");
    private static final Path GRAPH_BRIDGE_CALLBACK = Path.of("src/main/java/com/atlas/graph/bridge/AtlasToolCallback.java");
    private static final Path CORE_CALLBACK = Path.of("src/main/java/com/atlas/tool/core/AtlasToolCallback.java");
    private static final Path ORCHESTRATOR = Path.of("src/main/java/com/atlas/orchestrator/AtlasOrchestrator.java");
    private static final Path EXECUTION_SOURCE = Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutionSource.java");

    /**
     * 行级调用表达式扫描规则。
     *
     * <p>这里故意只匹配当前 BaseTool 执行链路中已知的三类接收者：
     * {@code tool.execute(...)}、{@code baseTool.execute(...)}、
     * {@code meta.instance().execute(...)}。这样可以避免把线程池
     * {@code executor.execute(Runnable)}、CompletableFuture 等非 Tool 调用误判为业务 Tool 执行入口。</p>
     */
    private static final Pattern BASE_TOOL_EXECUTE_PATTERN = Pattern.compile(
        "(?:meta\\.instance\\(\\)|baseTool|tool)\\.execute\\s*\\([^;]*?\\)",
        Pattern.DOTALL);

    /**
     * 宽松 execute 扫描规则，用来辅助发现未来可能改名的 BaseTool 变量。
     *
     * <p>该规则会命中所有 {@code xxx.execute(...)} 形态，因此不能直接作为失败依据，
     * 但可以通过 {@code SUSPICIOUS_EXECUTE_ALLOWLIST} 固定当前非 Tool 调用基线。
     * 如果未来新增了 {@code atlasTool.execute(...)}、{@code selectedTool.execute(...)} 之类调用，
     * 它会先进入可疑集合并让测试失败，迫使开发者判断是接入 SafeToolExecutor 还是登记临时债务。</p>
     */
    private static final Pattern ANY_DOT_EXECUTE_PATTERN = Pattern.compile(
        "(?:meta\\.instance\\(\\)|[A-Za-z_$][\\w$]*)\\.execute\\s*\\([^;]*?\\)",
        Pattern.DOTALL);

    /**
     * 当前源码中已知的非 BaseTool execute 调用。
     *
     * <p>这份清单不是安全白名单，而是宽松扫描的误报基线：它们分别属于异步执行器、
     * ArchUnit 测试样例或文档/提示词中的普通文本，不是真实业务 Tool 执行入口。</p>
     */
    private static final Set<SourceExecuteKey> SUSPICIOUS_EXECUTE_ALLOWLIST = Set.of(
        new SourceExecuteKey("src/main/java/com/atlas/auth/async/DelegatingExecutor.java",
            "delegate.execute(AsyncContextHolder.wrap(command, token, orgId)")
    );

    /**
     * 唯一永久允许的直接执行边界。
     *
     * <p>该边界在真正执行 Tool 前必须完成：可信租户上下文校验、受保护参数过滤、
     * HITL fail-closed 校验、ThreadLocal 绑定与 finally 恢复。</p>
     */
    private static final SourceExecuteKey PERMANENT_SAFE_EXECUTE_BOUNDARY = new SourceExecuteKey(
        "src/main/java/com/atlas/tool/execution/SafeToolExecutor.java",
        "tool.execute(toolParams)"
    );

    /**
     * 历史直接执行入口临时白名单。
     *
     * <p>注意：白名单命名中包含 TEMPORARY，表示这是一份待消除的迁移债务清单。
     * 任何新增裸 {@code execute(...)} 调用都不应直接加入这里，除非经过 Review 明确说明原因、
     * 风险、优先级和迁移到 SafeToolExecutor 的路径。</p>
     */
    private static final List<TemporaryAllowedExecuteCall> TEMPORARY_DIRECT_EXECUTE_ALLOWLIST = List.of();

    /**
     * 生产代码中的 BaseTool 直接执行入口必须全部可见、可解释、可收口。
     */
    @Test
    void productionBaseToolExecuteCalls_shouldBeEitherSafeExecutorOrTemporaryAllowlist() throws IOException {
        List<SourceExecuteCall> actualCalls = scanProductionBaseToolExecuteCalls();
        Set<SourceExecuteKey> actualKeys = toKeySet(actualCalls);
        Set<SourceExecuteKey> expectedKeys = expectedExecuteKeys();

        assertThat(actualKeys)
            .as("""
                M4-PX.4 Tool 执行入口契约失败：生产代码出现未登记的 BaseTool.execute 直接调用，
                或已有入口被改名/移动但未同步治理清单。

                当前扫描结果：
                %s

                处理建议：
                1. 新入口优先接入 SafeToolExecutor；
                2. 若确为迁移前历史债务，必须在 TEMPORARY_DIRECT_EXECUTE_ALLOWLIST 中补充 reason、migrationTarget、priority、risk；
                3. 不允许把临时白名单当成长期安全豁免。
                """.formatted(formatCalls(actualCalls)))
            .containsExactlyInAnyOrderElementsOf(expectedKeys);
        assertThat(actualCalls)
            .as("BaseTool.execute 直接调用数量也必须精确匹配，避免同一文件重复新增相同表达式时被 Set 去重吞掉")
            .hasSize(expectedKeys.size());
    }

    /**
     * 宽松 execute 扫描用于发现可能被变量改名隐藏的新裸执行入口。
     */
    @Test
    void suspiciousExecuteCalls_shouldBeEitherKnownToolEntrypointsOrDocumentedNonToolCalls() throws IOException {
        List<SourceExecuteCall> suspiciousCalls = scanExecuteCalls(ANY_DOT_EXECUTE_PATTERN);
        Set<SourceExecuteKey> expectedKeys = new LinkedHashSet<>(expectedExecuteKeys());
        expectedKeys.addAll(SUSPICIOUS_EXECUTE_ALLOWLIST);

        assertThat(toKeySet(suspiciousCalls))
            .as("""
                M4-PX.4 宽松 execute 扫描发现未登记调用点。
                如果这是新的 BaseTool 变量名，请接入 SafeToolExecutor 或登记临时迁移债务；
                如果这是非 Tool 调用，请把它加入 SUSPICIOUS_EXECUTE_ALLOWLIST 并说明原因。

                当前宽松扫描结果：
                %s
                """.formatted(formatCalls(suspiciousCalls)))
            .containsExactlyInAnyOrderElementsOf(expectedKeys);
        assertThat(suspiciousCalls)
            .as("宽松 execute 扫描也必须校验数量，防止同文件同表达式重复调用被去重隐藏")
            .hasSize(expectedKeys.size());
    }

    /**
     * SafeToolExecutor 必须保持唯一永久执行边界，并且 HITL 校验必须位于真实 execute 之前。
     */
    @Test
    void safeToolExecutor_shouldRemainOnlyPermanentBaseToolExecuteBoundary() throws IOException {
        String source = read(SAFE_TOOL_EXECUTOR);

        int bindIndex = source.indexOf("bindThreadLocalContext(");
        int guardIndex = source.indexOf("hitlGuard.verifyByIntentId(");
        int trustedParamsIndex = source.indexOf("buildTrustedToolParams(");
        int executeIndex = source.indexOf("tool.execute(toolParams)");
        int restoreIndex = source.indexOf("restoreThreadLocalContext(");

        assertThat(source)
            .as("SafeToolExecutor 必须继续集中承载受保护参数、HITL 和 ThreadLocal 安全语义")
            .contains("public class SafeToolExecutor")
            .contains("ProtectedToolParameterFilter.isProtected")
            .contains("缺失 orgId、未注册 Tool、权限不足、HITL 未确认均 fail-closed");
        assertThat(bindIndex).as("SafeToolExecutor 必须绑定可信 ThreadLocal 上下文").isGreaterThanOrEqualTo(0);
        assertThat(guardIndex).as("SafeToolExecutor 必须在执行前调用 HitlGuard").isGreaterThanOrEqualTo(0);
        assertThat(trustedParamsIndex).as("SafeToolExecutor 必须构造可信 Tool 参数").isGreaterThanOrEqualTo(0);
        assertThat(executeIndex).as("SafeToolExecutor 必须包含唯一永久真实 tool.execute 调用").isGreaterThanOrEqualTo(0);
        assertThat(restoreIndex).as("SafeToolExecutor 必须在 finally 中恢复 ThreadLocal 上下文").isGreaterThanOrEqualTo(0);
        assertThat(guardIndex)
            .as("HITL fail-closed 校验必须发生在真实 tool.execute(toolParams) 之前")
            .isLessThan(executeIndex);
    }

    /**
     * 终极 Agent 多入口执行矩阵必须全部回到 SafeToolExecutor。
     *
     * <p>中文说明：Graph tool_call、Plan execute_node、ReAct Action、Spring AI ToolCallback
     * 和旧 orchestrator fallback 是最容易发生安全漂移的入口。本测试只读源码，不启动 Spring、
     * 不调用 LLM、不访问 kube-manager；它把“所有入口只构造 SafeToolExecutionRequest，真实执行
     * 只能在 SafeToolExecutor 内部发生”固定成一张交接矩阵。</p>
     *
     * <p>安全边界：来源枚举只用于审计、回放和治理，不能被任何入口当成放宽 HITL、ToolPermission、
     * durable audit、租户上下文或 kube-manager 权限的依据。</p>
     */
    @Test
    void primaryAgentExecutionEntrypoints_shouldDelegateThroughSafeToolExecutorWithSourceMarkers() throws IOException {
        String graph = read(GRAPH_CONFIG);
        String react = read(REACT_ENGINE);
        String bridgeCallback = read(GRAPH_BRIDGE_CALLBACK);
        String coreCallback = read(CORE_CALLBACK);
        String orchestrator = read(ORCHESTRATOR);
        String sources = read(EXECUTION_SOURCE);

        assertEntrypointDelegates(graph,
            "Graph tool_call",
            "SafeToolExecutionSource.GRAPH_TOOL_CALL",
            "return safeToolExecutor.executeIntent(request).toGraphUpdates()");
        assertEntrypointDelegates(graph,
            "Plan execute_node",
            "SafeToolExecutionSource.PLAN_EXECUTE_NODE",
            "SafeToolExecutionResult result = safeToolExecutor.executeIntent(request)");
        assertEntrypointDelegates(react,
            "ReAct Action",
            "SafeToolExecutionSource.REACT_ENGINE",
            "SafeToolExecutionResult executionResult = safeToolExecutor.executeIntent(request)");
        assertEntrypointDelegates(bridgeCallback,
            "Graph Bridge ToolCallback",
            "SafeToolExecutionSource.TOOL_CALLBACK",
            "SafeToolExecutionResult result = safeToolExecutor.executeIntent(request)");
        assertEntrypointDelegates(coreCallback,
            "legacy core ToolCallback",
            "SafeToolExecutionSource.TOOL_CALLBACK",
            "SafeToolExecutionResult result = safeToolExecutor.executeIntent(new SafeToolExecutionRequest");
        assertEntrypointDelegates(orchestrator,
            "Orchestrator fallback",
            "SafeToolExecutionSource.ORCHESTRATOR_FALLBACK",
            "safeToolExecutor.executeIntent(executionRequest)");

        assertThat(sources)
            .as("SafeToolExecutionSource 必须显式列出所有主入口，供审计、回放和后续 Vue workbench 区分来源")
            .contains("GRAPH_TOOL_CALL")
            .contains("PLAN_EXECUTE_NODE")
            .contains("REACT_ENGINE")
            .contains("TOOL_CALLBACK")
            .contains("ORCHESTRATOR_FALLBACK")
            .contains("绝不能作为绕过 SafeToolExecutor");
    }
    /**
     * 临时白名单必须带完整治理信息，避免被误用成“允许绕过 SafeToolExecutor”的永久配置。
     */
    @Test
    void temporaryAllowlist_shouldDocumentReasonAndMigrationTarget() {
        assertThat(TEMPORARY_DIRECT_EXECUTE_ALLOWLIST)
            .as("Graph Bridge AtlasToolCallback、ReActEngine、core AtlasToolCallback 与 AtlasOrchestrator fallback 已迁移到 SafeToolExecutor；不应再保留历史直接执行入口")
            .isEmpty();

        for (TemporaryAllowedExecuteCall allowed : TEMPORARY_DIRECT_EXECUTE_ALLOWLIST) {
            assertThat(allowed.entranceName()).as(allowed.file() + " 必须说明入口名称").isNotBlank();
            assertThat(allowed.file()).as(allowed.entranceName() + " 必须登记文件路径").startsWith("src/main/java/");
            assertThat(allowed.callExpression()).as(allowed.entranceName() + " 必须登记具体调用表达式").contains(".execute(");
            assertThat(allowed.reason()).as(allowed.entranceName() + " 必须说明暂留原因").isNotBlank();
            assertThat(allowed.migrationTarget())
                .as(allowed.entranceName() + " 必须说明迁移到 SafeToolExecutor 的目标方案")
                .contains("SafeToolExecutor");
            assertThat(allowed.priority()).as(allowed.entranceName() + " 必须标注迁移优先级").matches("P[0-9]");
            assertThat(allowed.risk()).as(allowed.entranceName() + " 必须说明继续裸执行的风险").isNotBlank();
        }
    }

    private void assertEntrypointDelegates(String source,
                                          String entrypointName,
                                          String expectedSourceMarker,
                                          String expectedDelegateCall) {
        assertThat(source)
            .as(entrypointName + " 必须构造统一安全执行请求并标注来源")
            .contains("new SafeToolExecutionRequest(")
            .contains(expectedSourceMarker)
            .contains(expectedDelegateCall);
    }
    private List<SourceExecuteCall> scanProductionBaseToolExecuteCalls() throws IOException {
        return scanExecuteCalls(BASE_TOOL_EXECUTE_PATTERN);
    }

    private List<SourceExecuteCall> scanExecuteCalls(Pattern executePattern) throws IOException {
        List<SourceExecuteCall> calls = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MAIN_SOURCE_ROOT)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.comparing(path -> normalize(path)))
                .toList();
            for (Path javaFile : javaFiles) {
                scanFile(javaFile, calls, executePattern);
            }
        }
        return calls;
    }

    private void scanFile(Path javaFile, List<SourceExecuteCall> calls, Pattern executePattern) throws IOException {
        String source = Files.readString(javaFile, StandardCharsets.UTF_8);
        Matcher matcher = executePattern.matcher(source);
        while (matcher.find()) {
            int line = lineNumber(source, matcher.start());
            String sourceLine = lineText(source, matcher.start()).trim();
            if (sourceLine.startsWith("//") || sourceLine.startsWith("*") || sourceLine.startsWith("/*")) {
                continue;
            }
            calls.add(new SourceExecuteCall(
                normalize(javaFile),
                line,
                matcher.group().replaceAll("\\s+", " "),
                sourceLine
            ));
        }
    }

    private Set<SourceExecuteKey> expectedExecuteKeys() {
        Set<SourceExecuteKey> expected = new LinkedHashSet<>();
        expected.add(PERMANENT_SAFE_EXECUTE_BOUNDARY);
        TEMPORARY_DIRECT_EXECUTE_ALLOWLIST.stream()
            .map(TemporaryAllowedExecuteCall::toKey)
            .forEach(expected::add);
        return expected;
    }

    private Set<SourceExecuteKey> toKeySet(List<SourceExecuteCall> calls) {
        Set<SourceExecuteKey> keys = new LinkedHashSet<>();
        for (SourceExecuteCall call : calls) {
            keys.add(call.toKey());
        }
        return keys;
    }

    private String formatCalls(List<SourceExecuteCall> calls) {
        StringBuilder builder = new StringBuilder();
        for (SourceExecuteCall call : calls) {
            builder.append("- ")
                .append(call.file())
                .append(':')
                .append(call.line())
                .append(" -> ")
                .append(call.expression())
                .append(" | ")
                .append(call.sourceLine())
                .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private int lineNumber(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private String lineText(String source, int offset) {
        int start = source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int end = source.indexOf('\n', offset);
        if (end < 0) {
            end = source.length();
        }
        return source.substring(start, end);
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    /** 源码扫描得到的真实调用点，包含行号和原始行，便于失败时快速定位。 */
    private record SourceExecuteCall(String file, int line, String expression, String sourceLine) {
        private SourceExecuteKey toKey() {
            return new SourceExecuteKey(file, expression);
        }
    }

    /** 比较用键值，只比较文件和调用表达式，避免正常行号变动导致契约测试过脆。 */
    private record SourceExecuteKey(String file, String expression) {
    }

    /** 临时白名单项，必须携带完整治理信息，防止“只加白名单不迁移”的安全债务扩大。 */
    private record TemporaryAllowedExecuteCall(String entranceName,
                                               String file,
                                               String callExpression,
                                               String reason,
                                               String migrationTarget,
                                               String priority,
                                               String risk) {
        private SourceExecuteKey toKey() {
            return new SourceExecuteKey(file, callExpression);
        }
    }
}
