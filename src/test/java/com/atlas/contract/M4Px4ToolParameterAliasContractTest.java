package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4-PX.4 第七小批：ToolParameterSpec alias 安全契约测试。
 *
 * <p>Plan-and-Execute 的自动执行入口会把 LLM/Plan 生成的参数视为“不可信候选业务参数”，
 * 并由 {@code SafeToolExecutor} 在运行时过滤、归一化和覆盖服务端可信上下文。运行时防御之外，
 * Tool 自身声明的 {@code ToolParameterSpec.aliases()} 也必须保持“纯业务字段别名”的语义，
 * 不能把 token、orgId、userId、conversationId 等受保护上下文字段声明成 alias。</p>
 *
 * <p>原因是 alias 是 schema 设计层面的白名单入口：一旦某个 Tool 误把 {@code orgId} 或 {@code token}
 * 声明为业务 alias，后续维护者可能误以为这些字段可以从 Plan/LLM/前端输入中被信任地接收，
 * 从而削弱多租户、身份与 HITL 边界。因此本测试只做源码扫描，不启动 Spring、不调用 kube-manager、
 * 不执行任何真实 Tool，用快速失败的方式阻止 schema 漂移。</p>
 */
class M4Px4ToolParameterAliasContractTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");

    /**
     * 受保护上下文字段只允许来自服务端可信上下文，不允许出现在 ToolParameterSpec 的 alias 声明中。
     *
     * <p>这里同时覆盖历史字段与常见变体，避免后续有人用 {@code tenantId}、{@code access_token}
     * 等同义名称绕过契约。canonical 参数名是否允许使用这些字段由更高层的 Tool 设计审查决定；
     * 本小批只收紧 aliases，因为第六小批的 unknown-field 检查会把声明 alias 视为“已知输入”。</p>
     */
    private static final Set<String> PROTECTED_CONTEXT_ALIASES = Set.of(
        "token",
        "authorization",
        "access_token",
        "auth_token",
        "orgid",
        "organizationid",
        "org_id",
        "organization_id",
        "tenantid",
        "tenant_id",
        "userid",
        "user_id",
        "conversationid",
        "conversation_id"
    );

    /**
     * 定位 {@code ToolParameterSpec.stringParam} 调用起点。真正的调用体通过括号深度匹配截取，
     * 不依赖逗号或分号作为终止符，避免漏扫 {@code return List.of(...)} 中最后一个 stringParam。
     */
    private static final Pattern STRING_PARAM_START_PATTERN = Pattern.compile("ToolParameterSpec\\.stringParam\\s*\\(");

    /**
     * 匹配 stringParam 调用块中的 List.of(...) alias 参数。
     */
    private static final Pattern LIST_OF_PATTERN = Pattern.compile("List\\.of\\s*\\((.*?)\\)", Pattern.DOTALL);

    /**
     * 匹配字符串字面量。alias 不应通过动态表达式声明；动态 alias 本身会降低审计可见性，后续如出现应另行收紧。
     */
    private static final Pattern STRING_LITERAL_PATTERN = Pattern.compile("\\\"([^\\\"]+)\\\"");

    @Test
    void toolParameterSpecAliases_shouldNotUseProtectedContextFields() throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(MAIN_JAVA)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> scanFile(path, violations));
        }

        assertThat(violations)
            .as("ToolParameterSpec.aliases() 不得声明 token/orgId/userId/conversationId 等受保护上下文字段；"
                + "这些字段必须由服务端可信上下文覆盖，不能从 Plan/LLM/前端参数 alias 进入业务参数。违规项：%s", violations)
            .isEmpty();
    }

    @Test
    void scannerSelfCheck_shouldCatchLastStringParamAndCaseVariants() {
        String syntheticSource = """
            package com.example;
            class SyntheticTool {
                java.util.List<ToolParameterSpec> specs() {
                    return java.util.List.of(
                        ToolParameterSpec.stringParam("namespace", "命名空间", false, java.util.List.of("ns")),
                        ToolParameterSpec.stringParam("keyword", "关键词", false, java.util.List.of("OrgId", "USERID", "ConversationId"))
                    );
                }
            }
            """;
        List<String> violations = new ArrayList<>();

        scanSource(Path.of("src/main/java/com/example/SyntheticTool.java"), syntheticSource, violations);

        assertThat(violations)
            .as("扫描器自身必须能抓住最后一个 stringParam 中的大小写变体 protected alias")
            .hasSize(3)
            .anyMatch(item -> item.contains("alias=\"OrgId\""))
            .anyMatch(item -> item.contains("alias=\"USERID\""))
            .anyMatch(item -> item.contains("alias=\"ConversationId\""));
    }

    private void scanFile(Path path, List<String> violations) {
        scanSource(path, readUnchecked(path), violations);
    }

    private void scanSource(Path path, String source, List<String> violations) {
        if (!source.contains("ToolParameterSpec") || !source.contains("List.of(")) {
            return;
        }

        Matcher stringParamMatcher = STRING_PARAM_START_PATTERN.matcher(source);
        while (stringParamMatcher.find()) {
            int openParenIndex = source.indexOf('(', stringParamMatcher.start());
            int closeParenIndex = findMatchingParen(source, openParenIndex);
            String stringParamCallBody = source.substring(openParenIndex + 1, closeParenIndex);
            Matcher aliasMatcher = LIST_OF_PATTERN.matcher(stringParamCallBody);
            while (aliasMatcher.find()) {
                String listBody = aliasMatcher.group(1);
                List<String> aliases = extractStringLiterals(listBody);
                for (String alias : aliases) {
                    if (isProtectedContextAlias(alias)) {
                        violations.add(formatViolation(path, source, stringParamMatcher.start(), alias, listBody));
                    }
                }
            }
        }
    }

    private List<String> extractStringLiterals(String source) {
        List<String> values = new ArrayList<>();
        Matcher matcher = STRING_LITERAL_PATTERN.matcher(source);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private boolean isProtectedContextAlias(String alias) {
        return PROTECTED_CONTEXT_ALIASES.contains(alias.toLowerCase(Locale.ROOT));
    }

    private int findMatchingParen(String source, int openParenIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = openParenIndex; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("ToolParameterSpec.stringParam 调用括号未闭合，起始位置: " + openParenIndex);
    }

    private String formatViolation(Path path, String source, int offset, String alias, String listBody) {
        String normalized = path.toString().replace('\\', '/');
        int line = lineNumber(source, offset);
        String snippet = listBody.replace('\n', ' ').replace('\r', ' ').trim();
        if (snippet.length() > 160) {
            snippet = snippet.substring(0, 160) + "...";
        }
        return "file=" + normalized
            + ", line=" + line
            + ", alias=\"" + alias + "\""
            + ", source=List.of(" + snippet + ")";
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

    private String readUnchecked(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取源码失败: " + path, ex);
        }
    }
}
