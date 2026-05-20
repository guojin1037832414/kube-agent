package com.atlas.tool.core;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.impl.DiagnosePodTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolRegistry 生成给 ReActPromptBuilder 使用的工具目录参数契约测试。
 */
class ToolRegistryPromptContractTest {

    @Test
    void buildSystemPrompt_shouldExposeCanonicalContractWithoutAliasList() {
        DiagnosePodTool diagnosePodTool = new DiagnosePodTool(null);
        ToolRegistry registry = new ToolRegistry(List.of(diagnosePodTool), new UserPermissionContext());
        registry.init();

        String prompt = registry.buildSystemPromptForCurrentUser();

        assertTrue(prompt.contains("参数契约: podName(string,可选"));
        assertTrue(prompt.contains("namespace(string,可选"));
        assertTrue(prompt.contains("Action.params 必须优先使用参数契约中的 canonical 参数名"));
        assertTrue(prompt.contains("历史 alias 仅用于系统兼容归一化"));

        assertFalse(prompt.contains("pod_name"), "工具目录不应逐项输出 alias，避免诱导 LLM 生成 alias");
        assertFalse(prompt.contains("target_name"), "工具目录不应逐项输出 alias，避免 prompt 膨胀");
        assertFalse(prompt.contains("name_space"), "工具目录不应逐项输出 alias，避免 prompt 膨胀");
        assertFalse(prompt.contains(" ns"), "工具目录不应逐项输出 alias，避免 prompt 膨胀");
    }

    @Test
    void buildSystemPrompt_shouldKeepLegacyToolCompatibilityHintWhenSpecsMissing() {
        ToolRegistry registry = new ToolRegistry(List.of(new LegacyNoSpecTool()), new UserPermissionContext());
        registry.init();

        String prompt = registry.buildSystemPromptForCurrentUser();

        assertTrue(prompt.contains("legacy_no_spec"));
        assertTrue(prompt.contains("参数契约: 未声明结构化参数；按工具说明传入 JSON 对象"));
        assertTrue(prompt.contains("历史 alias 仅用于系统兼容归一化"));
    }

    @AtlasToolMapping(
        name = "legacy_no_spec",
        description = "未声明结构化参数的旧工具",
        intentId = "legacy_no_spec",
        agent = "query"
    )
    private static class LegacyNoSpecTool extends BaseTool {
        LegacyNoSpecTool() {
            super("legacy_no_spec", "未声明结构化参数的旧工具");
        }

        @Override
        protected Set<String> getRequiredParams() {
            return Set.of();
        }

        @Override
        protected AtlasToolResult doExecute(java.util.Map<String, Object> params) {
            return AtlasToolResult.ok("ok", java.util.Map.of());
        }
    }
}
