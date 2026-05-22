package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查询类 Tool 的参数契约加固测试。
 *
 * <p>这些 Tool 在业务上虽然都是“列表查询”，但 LLM 经常会携带 page、limit、keyword、name 等筛选词。
 * 如果 Tool 不声明结构化参数契约，ReAct 工具目录就只能暴露模糊说明，容易导致 Action.params 使用错误字段。
 * 本测试用小批代表性 Tool 锁定分页与关键词参数契约，先验证一组高频查询工具，再逐步铺开到其它 Tool。</p>
 */
class ListToolParameterSpecContractTest {

    @Test
    void listTools_shouldExposePaginationAndKeywordContractForReact() {
        assertListQueryContract(new MpiJobListTool(null), "mpi_job_list");
        assertListQueryContract(new PytorchJobListTool(null), "pytorch_job_list");
        assertListQueryContract(new FileMaterialListTool(null), "file_material_list");
        assertListQueryContract(new GpuDetailListTool(null), "gpu_detail_list");
        assertListQueryContract(new DataSetListTool(null), "data_set_list");
        assertListQueryContract(new ModelListTool(null), "model_list");
        assertListQueryContract(new FileListTool(null), "file_list");
        assertListQueryContract(new RegistryListTool(null), "registry_list");
        assertListQueryContract(new TensorBoardListTool(null), "tensorboard_list");
        assertListQueryContract(new JobTemplateListTool(null), "job_template_list");
        assertListQueryContract(new TemplateListTool(null), "template_list");
        assertListQueryContract(new ResourcePresetListTool(null), "resource_preset_list");
    }

    /**
     * 校验列表查询 Tool 必须向 ReAct/LLM 暴露统一的 page、limit、keyword 参数契约。
     */
    private void assertListQueryContract(com.atlas.tool.core.BaseTool tool, String toolName) {
        Map<String, ToolParameterSpec> specs = tool.getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertTrue(specs.containsKey("page"), toolName + " 应声明 page 参数，避免 LLM 手写 URL query");
        assertTrue(specs.containsKey("limit"), toolName + " 应声明 limit 参数，避免 LLM 手写 URL query");
        assertTrue(specs.containsKey("keyword"), toolName + " 应声明 keyword 参数，承接用户自然语言筛选词");

        assertEquals("string", specs.get("page").type());
        assertEquals("string", specs.get("limit").type());
        assertEquals("string", specs.get("keyword").type());

        assertFalse(specs.get("page").required(), toolName + " 的 page 应为可选参数");
        assertFalse(specs.get("limit").required(), toolName + " 的 limit 应为可选参数");
        assertFalse(specs.get("keyword").required(), toolName + " 的 keyword 应为可选参数");

        assertTrue(specs.get("page").aliases().containsAll(List.of("pageNo", "page_no", "current")));
        assertTrue(specs.get("limit").aliases().containsAll(List.of("pageSize", "page_size", "size")));
        assertTrue(specs.get("keyword").aliases().containsAll(List.of("name", "search", "kw")));
    }
}
