package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5.9 HTTP 安全边界源码契约测试。
 *
 * <p>哥哥特别强调：继续推进时必须避免影响 kube-manager 的真实数据。因此本测试只做源码级契约扫描，
 * 不启动服务、不调用 kube-manager、不执行任何真实删除/修改/查询 API。</p>
 *
 * <p>本契约承接 M5.8：业务 Tool 的 HTTP get/post/delete 已改为必须使用用户 ThreadLocal Token，
 * 缺失用户上下文时 fail-closed。M5.9 的目标是防止后续新增代码绕过统一 HTTP 出口，
 * 或重新把 sysadmin fallback token 接回业务默认路径。</p>
 */
class M59HttpSecurityBoundaryContractTest {

    /**
     * 允许直接持有 HTTP 客户端的生产类白名单。
     *
     * <p>说明：</p>
     * <ul>
     *   <li>{@code KubeManagerHttpClient} 是统一 kube-manager HTTP 出口，允许直接使用 RestClient。</li>
     *   <li>{@code AuthController} 是登录代理入口，必须直接调用 kube-manager /api/login 换取用户 token。</li>
     *   <li>{@code ModelDownloader} 只用于下载本地 Embedding 模型，不访问 kube-manager 数据面。</li>
     * </ul>
     * 其他业务 Tool / Orchestrator / Graph 节点不应直接创建 HTTP 客户端，否则会绕过 M5.8 Token 安全门。
     */
    private static final List<String> DIRECT_HTTP_CLIENT_ALLOWED_FILES = List.of(
        "src/main/java/com/atlas/http/KubeManagerHttpClient.java",
        "src/main/java/com/atlas/controller/AuthController.java",
        "src/main/java/com/atlas/intent/embedding/ModelDownloader.java"
    );

    /**
     * 扫描生产源码，禁止白名单外类直接创建或注入 HTTP 客户端。
     */
    @Test
    void productionSource_shouldNotCreateDirectHttpClientOutsideAllowedGateway() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> collectDirectHttpClientViolations(path, violations));
        }

        assertTrue(violations.isEmpty(),
            "M5.9: 业务代码不得绕过统一 HTTP 出口直接访问 kube-manager，违规位置：\n" +
                String.join("\n", violations));
    }

    /**
     * 锁定 KubeManagerHttpClient 业务入口：get/post/delete 必须调用 resolveUserTokenRequired，
     * 不能回退到允许 sysadmin fallback 的 resolveToken。
     */
    @Test
    void kubeManagerBusinessMethods_shouldRequireUserTokenAndNeverCallFallbackResolver() throws IOException {
        Path clientPath = Path.of("src/main/java/com/atlas/http/KubeManagerHttpClient.java");
        String source = Files.readString(clientPath, StandardCharsets.UTF_8);

        assertBusinessMethodUsesUserTokenOnly(source, "get");
        assertBusinessMethodUsesUserTokenOnly(source, "post");
        assertBusinessMethodUsesUserTokenOnly(source, "delete");
    }

    /**
     * 允许 resolveToken 作为系统任务保留能力，但生产源码中除自身声明与 refresh/fallback 登录内部链路外，
     * 不应新增业务调用点。当前契约用调用点数量保护：只有方法声明本身允许出现 {@code resolveToken()}。
     */
    @Test
    void productionSource_shouldNotCallResolveTokenFromBusinessFlow() throws IOException {
        Path clientPath = Path.of("src/main/java/com/atlas/http/KubeManagerHttpClient.java");
        String source = Files.readString(clientPath, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("resolveToken\\s*\\(").matcher(source);

        List<Integer> callLines = new ArrayList<>();
        while (matcher.find()) {
            int line = lineNumber(source, matcher.start());
            callLines.add(line);
        }

        assertTrue(callLines.size() == 1,
            "M5.9: resolveToken() 只能作为系统任务 fallback 能力保留，业务代码不得调用。出现行号=" + callLines);
    }

    private void collectDirectHttpClientViolations(Path path, List<String> violations) {
        String normalized = path.toString().replace('\\', '/');
        if (DIRECT_HTTP_CLIENT_ALLOWED_FILES.contains(normalized)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (containsDirectHttpClientPattern(line)) {
                    violations.add(normalized + ":" + (i + 1) + " -> " + line.trim());
                }
            }
        } catch (IOException e) {
            violations.add(normalized + ": read failed -> " + e.getMessage());
        }
    }

    private boolean containsDirectHttpClientPattern(String line) {
        return line.matches(".*import\\s+org\\.springframework\\.web\\.client\\.RestClient\\s*;.*")
            || line.matches(".*import\\s+org\\.springframework\\.web\\.client\\.RestTemplate\\s*;.*")
            || line.matches(".*import\\s+org\\.springframework\\.web\\.reactive\\.function\\.client\\.WebClient\\s*;.*")
            || line.matches(".*import\\s+java\\.net\\.HttpURLConnection\\s*;.*")
            || line.matches(".*import\\s+java\\.net\\.http\\.HttpClient\\s*;.*")
            || line.matches(".*import\\s+feign\\..*")
            || line.matches(".*import\\s+okhttp3\\.OkHttpClient\\s*;.*")
            || line.matches(".*import\\s+org\\.apache\\.hc\\.client5\\.http\\..*")
            || line.contains("RestClient.builder(")
            || line.contains("WebClient.builder(")
            || line.contains("HttpClient.newHttpClient(")
            || line.contains("HttpClient.newBuilder(")
            || line.contains("openConnection(")
            || line.contains("new URL(")
            || line.contains("new RestTemplate(")
            || line.contains("new OkHttpClient(");
    }

    private void assertBusinessMethodUsesUserTokenOnly(String source, String methodName) {
        String body = extractMethodBody(source, methodName);
        assertTrue(body.contains("resolveUserTokenRequired"),
            "M5.9: KubeManagerHttpClient#" + methodName + " 必须调用 resolveUserTokenRequired");
        assertTrue(!body.contains("resolveToken()"),
            "M5.9: KubeManagerHttpClient#" + methodName + " 不得调用允许 fallback 的 resolveToken()");
    }

    private String extractMethodBody(String source, String methodName) {
        Pattern signature = Pattern.compile("public\\s+Map<String, Object>\\s+" + methodName + "\\s*\\(");
        Matcher matcher = signature.matcher(source);
        assertTrue(matcher.find(), "未找到业务方法: " + methodName);

        int braceStart = source.indexOf('{', matcher.end());
        assertTrue(braceStart > 0, "业务方法缺少方法体: " + methodName);

        int depth = 0;
        for (int i = braceStart; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(braceStart, i + 1);
                }
            }
        }
        throw new AssertionError("业务方法方法体解析失败: " + methodName);
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
}
