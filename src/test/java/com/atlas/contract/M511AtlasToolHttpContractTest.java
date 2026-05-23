package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.11 Atlas Tool HTTP 元数据契约测试。
 *
 * <p>本测试只做源码级静态检查，不启动 Spring 容器，不注入 Bean，不调用 kube-manager，
 * 因此不会触发任何真实 GET/POST/DELETE 数据面请求。</p>
 *
 * <p>设计边界：M5.11 采用“先小样本验证，再分批铺开”的治理方式。历史 Tool 的
 * {@code httpMethod} 默认仍为空，本测试暂不强迫 110 个 Tool 一次性补齐；但凡已经声明
 * {@code httpMethod} 的 Tool，都必须满足声明方法、业务风险语义与源码实际 HTTP 调用一致。</p>
 */
class M511AtlasToolHttpContractTest {

    /** Tool 实现类所在目录。 */
    private static final Path TOOL_IMPL_DIR = Path.of("src/main/java/com/atlas/tool/impl");

    /** 声明为无真实 HTTP 的占位方法。 */
    private static final String NONE_METHOD = "NONE";

    /** 匹配 AtlasToolMapping 注解块。 */
    private static final Pattern MAPPING_PATTERN = Pattern.compile("@AtlasToolMapping\\s*\\((.*?)\\)\\s*(?:@ToolPermission|public class)", Pattern.DOTALL);

    /** 匹配注解中的字符串属性。 */
    private static final Pattern STRING_ATTRIBUTE_PATTERN_TEMPLATE = Pattern.compile("%s\\s*=\\s*\\\"([^\\\"]*)\\\"");

    /** 匹配注解中的 apiEndpoints 数组。 */
    private static final Pattern API_ENDPOINTS_PATTERN = Pattern.compile("apiEndpoints\\s*=\\s*\\{(.*?)\\}", Pattern.DOTALL);

    /** 匹配注解中的 operationType 枚举。 */
    private static final Pattern OPERATION_TYPE_PATTERN = Pattern.compile("operationType\\s*=\\s*AtlasToolMapping\\.OperationType\\.([A-Z_]+)");

    /** 匹配注解中的 requiresConfirmation 布尔值。 */
    private static final Pattern REQUIRES_CONFIRMATION_PATTERN = Pattern.compile("requiresConfirmation\\s*=\\s*(true|false)");

    /** 匹配 KubeManagerHttpClient 字段名，兼容 httpClient/kubeManagerHttpClient 等变量。 */
    private static final Pattern CLIENT_FIELD_PATTERN = Pattern.compile("KubeManagerHttpClient\\s+(\\w+)\\s*;");

    @Test
    void declaredAtlasToolHttpMetadata_shouldMatchActualKubeManagerClientCalls() throws IOException {
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(TOOL_IMPL_DIR)) {
            files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith("Tool.java"))
                .sorted()
                .forEach(path -> verifyToolFile(path, violations));
        }

        assertThat(violations)
            .as("M5.11 Atlas Tool HTTP 元数据契约违规:\n%s", String.join("\n", violations))
            .isEmpty();
    }

    /**
     * 校验单个 Tool 文件。
     *
     * <p>没有声明 {@code httpMethod} 的历史 Tool 暂时跳过；已经声明的 Tool 进入严格校验。
     * 这样可以在不一次性改动 110 个 Tool 的前提下，把新增/迁移 Tool 纳入强契约。</p>
     */
    private void verifyToolFile(Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            Matcher mappingMatcher = MAPPING_PATTERN.matcher(source);
            if (!mappingMatcher.find()) {
                return;
            }

            String annotation = mappingMatcher.group(1);
            String toolName = readStringAttribute(annotation, "name");
            String declaredMethod = readStringAttribute(annotation, "httpMethod").toUpperCase(Locale.ROOT);
            if (declaredMethod.isBlank()) {
                return;
            }

            String operationType = readOperationType(annotation);
            boolean requiresConfirmation = readRequiresConfirmation(annotation);
            Set<String> declaredEndpoints = readApiEndpoints(annotation);
            Set<String> clientFieldNames = readClientFieldNames(source);
            Set<String> actualMethods = readActualHttpMethods(source, clientFieldNames);

            verifyMethodConsistency(path, toolName, declaredMethod, actualMethods, violations);
            verifyRiskMetadata(path, toolName, declaredMethod, operationType, requiresConfirmation, violations);
            verifyEndpointMetadata(path, toolName, declaredMethod, declaredEndpoints, violations);
        } catch (IOException e) {
            violations.add(format(path, "READ_FILE_FAILED", "读取 Tool 源码失败: " + e.getMessage()));
        }
    }

    /**
     * 校验注解声明的 HTTP 方法与 doExecute 中真实 KubeManagerHttpClient 调用一致。
     */
    private void verifyMethodConsistency(Path path, String toolName, String declaredMethod,
                                         Set<String> actualMethods, List<String> violations) {
        if (NONE_METHOD.equals(declaredMethod)) {
            if (!actualMethods.isEmpty()) {
                violations.add(format(path, "PLACEHOLDER_CALLS_HTTP",
                    "tool=" + toolName + ", declared=NONE, actualMethods=" + actualMethods
                        + ", suggestion=占位 Tool 若已接入真实 HTTP，请改为真实 httpMethod 并更新 operationType"));
            }
            return;
        }

        if (actualMethods.isEmpty()) {
            violations.add(format(path, "MISSING_HTTP_CALL",
                "tool=" + toolName + ", declared=" + declaredMethod
                    + ", actualMethods=[], suggestion=非 NONE Tool 必须在 doExecute 中调用 KubeManagerHttpClient"));
            return;
        }

        if (!actualMethods.equals(Set.of(declaredMethod))) {
            violations.add(format(path, "HTTP_METHOD_MISMATCH",
                "tool=" + toolName + ", declared=" + declaredMethod + ", actualMethods=" + actualMethods
                    + ", suggestion=修正 @AtlasToolMapping.httpMethod 或真实 httpClient 调用"));
        }
    }

    /**
     * 校验风险元数据，避免把写入/删除/占位 Tool 暴露成普通只读 Tool。
     */
    private void verifyRiskMetadata(Path path, String toolName, String declaredMethod,
                                    String operationType, boolean requiresConfirmation,
                                    List<String> violations) {
        if (operationType.isBlank() || "UNKNOWN".equals(operationType)) {
            violations.add(format(path, "UNKNOWN_OPERATION_TYPE",
                "tool=" + toolName + ", declaredMethod=" + declaredMethod
                    + ", suggestion=已声明 HTTP 契约的 Tool 必须显式声明 operationType"));
        }

        if (Set.of("POST", "PUT", "PATCH", "DELETE").contains(declaredMethod)
            && "READ".equals(operationType)) {
            violations.add(format(path, "WRITE_METHOD_MARKED_READ",
                "tool=" + toolName + ", declaredMethod=" + declaredMethod
                    + ", operationType=READ, suggestion=写入/删除类 HTTP 方法不得标记为 READ"));
        }

        if (Set.of("DELETE", "ACTION", "PLACEHOLDER").contains(operationType) && !requiresConfirmation) {
            violations.add(format(path, "HIGH_RISK_TOOL_WITHOUT_CONFIRMATION",
                "tool=" + toolName + ", operationType=" + operationType
                    + ", suggestion=DELETE/ACTION/PLACEHOLDER Tool 必须 requiresConfirmation=true"));
        }
    }

    /**
     * 校验 endpoint 元数据存在性。M5.11 小样本阶段暂不做复杂 Java 表达式路径反解。
     */
    private void verifyEndpointMetadata(Path path, String toolName, String declaredMethod,
                                        Set<String> declaredEndpoints, List<String> violations) {
        if (!NONE_METHOD.equals(declaredMethod) && declaredEndpoints.isEmpty()) {
            violations.add(format(path, "MISSING_API_ENDPOINTS",
                "tool=" + toolName + ", declaredMethod=" + declaredMethod
                    + ", suggestion=非 NONE Tool 必须声明 apiEndpoints，支持多路径 fallback"));
        }
    }

    /**
     * 读取 KubeManagerHttpClient 字段变量名，确保不会误把其他对象的 get/post/delete 当成 HTTP 调用。
     */
    private Set<String> readClientFieldNames(String source) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = CLIENT_FIELD_PATTERN.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * 读取源码中真实调用的 KubeManagerHttpClient HTTP 方法集合。
     */
    private Set<String> readActualHttpMethods(String source, Set<String> clientFieldNames) {
        Set<String> methods = new LinkedHashSet<>();
        for (String fieldName : clientFieldNames) {
            Pattern callPattern = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\.(get|post|delete|put|patch)\\s*\\(");
            Matcher matcher = callPattern.matcher(source);
            while (matcher.find()) {
                methods.add(matcher.group(1).toUpperCase(Locale.ROOT));
            }
        }
        return methods;
    }

    private String readStringAttribute(String annotation, String attributeName) {
        Pattern pattern = Pattern.compile(String.format(STRING_ATTRIBUTE_PATTERN_TEMPLATE.pattern(), attributeName));
        Matcher matcher = pattern.matcher(annotation);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private Set<String> readApiEndpoints(String annotation) {
        Matcher matcher = API_ENDPOINTS_PATTERN.matcher(annotation);
        if (!matcher.find()) {
            return Set.of();
        }
        Set<String> endpoints = new LinkedHashSet<>();
        Arrays.stream(matcher.group(1).split(","))
            .map(String::strip)
            .map(value -> value.replace("\"", ""))
            .filter(value -> !value.isBlank())
            .forEach(endpoints::add);
        return endpoints;
    }

    private String readOperationType(String annotation) {
        Matcher matcher = OPERATION_TYPE_PATTERN.matcher(annotation);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean readRequiresConfirmation(String annotation) {
        Matcher matcher = REQUIRES_CONFIRMATION_PATTERN.matcher(annotation);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }

    private String format(Path path, String type, String detail) {
        return "[" + type + "] file=" + path + ", " + detail;
    }
}
