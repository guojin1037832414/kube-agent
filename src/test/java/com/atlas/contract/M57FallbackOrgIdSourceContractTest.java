package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5.7 fallbackOrgId 源码级安全契约测试。
 *
 * <p>多租户系统中 orgId 是认证/授权边界，不是普通默认值。M5.7 之后，生产代码不得再保留
 * {@code fallbackOrgId} 或 {@code atlas.backend.fallback-org-id} 这类“默认组织兜底”语义，
 * 防止未来新增 Tool / Graph / 登录链路时重新把配置值洗白成可信租户上下文。</p>
 */
class M57FallbackOrgIdSourceContractTest {

    /**
     * 扫描生产源码，禁止 fallbackOrgId 语义回流。
     */
    @Test
    void productionSource_shouldNotContainFallbackOrgIdTrustedContextSemantics() throws IOException {
        Path root = Path.of("src/main");
        List<String> forbiddenTerms = List.of(
            "fallbackOrgId",
            "getFallbackOrgId",
            "atlas.backend.fallback-org-id",
            "return fallbackOrgId",
            "回退到默认组织",
            "默认租户"
        );

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                .forEach(path -> collectViolations(path, forbiddenTerms, violations));
        }

        assertTrue(violations.isEmpty(),
            "M5.7: 生产代码不得再保留 fallbackOrgId 默认租户语义，违规位置：\n" + String.join("\n", violations));
    }

    private void collectViolations(Path path, List<String> forbiddenTerms, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (String term : forbiddenTerms) {
                    if (line.contains(term)) {
                        violations.add(path + ":" + (i + 1) + " contains '" + term + "' -> " + line.trim());
                    }
                }
            }
        } catch (IOException e) {
            violations.add(path + ": read failed -> " + e.getMessage());
        }
    }
}
