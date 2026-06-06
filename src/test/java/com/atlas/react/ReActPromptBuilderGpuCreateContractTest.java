package com.atlas.react;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.impl.DeployCreateTool;
import com.atlas.tool.impl.GpuQueryTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReAct GPU 创建链路提示词契约测试。
 *
 * <p>本测试不依赖真实 LLM，只锁定“先查组织级 GPU map，再让用户确认明确 gpuSpec”的专家会诊结论。
 * GPU/MIG 规格一旦猜错就可能创建到错误资源，因此提示词必须把 gpu_query 作为创建前的澄清动作。</p>
 */
class ReActPromptBuilderGpuCreateContractTest {

    @Test
    void buildSystemPrompt_shouldRequireGpuQueryBeforeGpuDeploymentCreation() {
        ToolRegistry registry = new ToolRegistry(List.of(
            new GpuQueryTool(null),
            new DeployCreateTool(null)
        ), new UserPermissionContext());
        registry.init();

        ReActPromptBuilder builder = new ReActPromptBuilder(registry);
        String prompt = builder.buildSystemPrompt(
            "帮我创建一个使用 A100 GPU 的训练实例",
            new ReActMemory(new ObjectMapper())
        );

        assertTrue(prompt.contains("GPU 实例创建工具调用规则"), "必须有明确的 GPU 创建编排规则");
        assertTrue(prompt.contains("gpu_query"), "必须提示先通过 gpu_query 查询组织级 GPU map");
        assertTrue(prompt.contains("deploy_create_instance"), "必须说明最终创建工具与 gpuSpec 的关系");
        assertTrue(prompt.contains("gpuSpec"), "必须强调使用明确 gpuSpec，避免只靠 gpuModel 猜测");
        assertTrue(prompt.contains("不能凭自然语言猜测 GPU 型号、MIG 配置或 gpuSpec"), "必须禁止 LLM 猜测 GPU/MIG");
        assertTrue(prompt.contains("返回 map 的 key"), "必须要求使用 gpu_query 返回 map key 作为 gpuSpec");
        assertTrue(prompt.contains("多个 MIG/整卡候选"), "必须覆盖多候选歧义场景");
        assertTrue(prompt.contains("Final Answer 中请用户选择明确 gpuSpec"), "多候选时必须澄清而不是创建");
        assertTrue(prompt.contains("CREATE 操作"), "创建行为必须继续服从高危/HITL 规则");
    }
}
