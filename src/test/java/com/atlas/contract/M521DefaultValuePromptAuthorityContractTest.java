package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.21-82 Tool prompt 默认值权限边界契约。
 *
 * <p>默认值可以帮助表单草稿补全，但 LLM 工具目录不能把默认值渲染成 HITL、审计、
 * 发布或写入授权证据，也不能把默认值注册表暴露成 prompt 证据源。</p>
 */
class M521DefaultValuePromptAuthorityContractTest {

    private static final Path TOOL_REGISTRY = Path.of(
        "src/main/java/com/atlas/tool/core/ToolRegistry.java");

    @Test
    void toolRegistryPrompt_shouldSayDefaultsAreNotAuthority() throws IOException {
        String source = read(TOOL_REGISTRY);

        assertThat(source)
            .contains("默认/可选")
            .contains("只表示表单草稿或前端填充提示")
            .contains("不代表用户已确认")
            .contains("HITL 通过")
            .contains("发布批准")
            .contains("审计成功")
            .contains("写入授权")
            .contains("真实 HTTP 执行许可")
            .contains("requiresConfirmation=false 只表示该 Tool 不需要额外 HITL")
            .contains("不代表绕过登录、RBAC、租户隔离、发布门禁或后端鉴权")
            .contains("不要在 Action.params 主动生成认证、租户、HITL、审计、发布或写入控制字段");
    }

    @Test
    void toolRegistryPrompt_shouldNotImportOrRenderDefaultRegistryEvidence()
        throws IOException {
        String source = read(TOOL_REGISTRY);
        String promptMethod = methodBody(source, "buildSystemPromptForCurrentUser");

        assertThat(source)
            .doesNotContain("import com.atlas.tool.defaults.DefaultValueRegistry")
            .doesNotContain("import com.atlas.tool.defaults.DefaultValueApplier")
            .doesNotContain("import com.atlas.tool.defaults.IntentDefaults");

        assertThat(promptMethod)
            .doesNotContain("DefaultValueRegistry")
            .doesNotContain("DefaultValueApplier")
            .doesNotContain("IntentDefaults")
            .doesNotContain("defaults.yml")
            .doesNotContain("getDefaults(")
            .doesNotContain("hasDefaults(")
            .doesNotContain("registry.apply(")
            .doesNotContain("safeToPost")
            .doesNotContain("writePermitted")
            .doesNotContain("releaseDecision")
            .doesNotContain("Authorization");
    }

    private String methodBody(String source, String methodName) {
        int signature = source.indexOf(methodName + "()");
        assertThat(signature).as("method should exist: " + methodName).isGreaterThanOrEqualTo(0);
        int start = source.indexOf('{', signature);
        assertThat(start).as("method body should start: " + methodName).isGreaterThanOrEqualTo(0);

        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError("method body should close: " + methodName);
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
