package com.atlas.react;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.impl.MpiJobSubmitTool;
import com.atlas.tool.impl.NimCreateTool;
import com.atlas.tool.impl.PodQueryTool;
import com.atlas.tool.impl.StorageCreateTool;
import com.atlas.tool.impl.StorageDeleteTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReAct 风险元数据提示词契约测试。
 *
 * <p>M5.21-83 锁定 ReAct Prompt 的安全语义：ToolRegistry 已经把 Tool 的
 * operationType/httpMethod/requiresConfirmation 暴露为紧凑风险标签，ReAct 顶层规则必须明确告诉
 * LLM 这些标签是 Action 前的权威刹车，而不是只靠 delete/scale 等关键词示例判断高危操作。</p>
 */
class ReActPromptBuilderRiskMetadataContractTest {

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void buildSystemPrompt_shouldUseToolRiskMetadataAsHighRiskActionGate() {
        UserPermissionContext userPermissionContext = new UserPermissionContext();
        userPermissionContext.onLogin("token-admin", "admin", "sys_admin", Set.of());
        userPermissionContext.bind("token-admin");

        ToolRegistry registry = new ToolRegistry(List.of(
            new PodQueryTool(null),
            new StorageCreateTool(null),
            new UpdateContractTool(),
            new StorageDeleteTool(null),
            new MpiJobSubmitTool(null),
            new NimCreateTool()
        ), userPermissionContext);
        registry.init();

        ReActPromptBuilder builder = new ReActPromptBuilder(registry);
        String prompt = builder.buildSystemPrompt(
            "查询 Pod 后帮我创建一个存储卷，然后提交 MPI 任务；如果不用了也帮我删除旧存储。",
            new ReActMemory(new ObjectMapper())
        );

        assertTrue(prompt.contains("pod_status"), "READ 对照工具应仍在可用工具目录中");
        assertTrue(prompt.contains("operationType=READ, httpMethod=GET, requiresConfirmation=false"),
            "READ 工具风险标签应作为低风险对照出现");
        assertTrue(prompt.contains("storage_create"), "CREATE 示例工具应出现在可用工具目录中");
        assertTrue(prompt.contains("operationType=CREATE, httpMethod=POST, requiresConfirmation=true"),
            "CREATE 工具必须暴露风险标签");
        assertTrue(prompt.contains("update_contract_tool"), "UPDATE 内嵌契约工具应出现在可用工具目录中");
        assertTrue(prompt.contains("operationType=UPDATE, httpMethod=PUT, requiresConfirmation=true"),
            "UPDATE 工具必须暴露风险标签");
        assertTrue(prompt.contains("storage_delete"), "DELETE 示例工具应出现在管理员可见工具目录中");
        assertTrue(prompt.contains("operationType=DELETE, httpMethod=DELETE, requiresConfirmation=true"),
            "DELETE 工具必须暴露风险标签");
        assertTrue(prompt.contains("mpi_job_submit"), "ACTION 示例工具应出现在可用工具目录中");
        assertTrue(prompt.contains("operationType=ACTION, httpMethod=POST, requiresConfirmation=true"),
            "ACTION 工具必须暴露风险标签");
        assertTrue(prompt.contains("nim_create"), "PLACEHOLDER 示例工具应出现在可用工具目录中");
        assertTrue(prompt.contains("operationType=PLACEHOLDER, httpMethod=NONE, requiresConfirmation=true"),
            "PLACEHOLDER 工具必须暴露 httpMethod=NONE 风险标签");

        assertTrue(prompt.contains("风险标签是权威风险提示"), "ReAct 顶层规则必须以风险标签作为权威提示");
        assertTrue(prompt.contains("operationType=CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER"),
            "规则必须覆盖所有非 READ 写入/动作/占位类型");
        assertTrue(prompt.contains("requiresConfirmation=true"), "规则必须覆盖显式 HITL 风险标签");
        assertTrue(prompt.contains("禁止直接输出 Action"), "高危 Tool 不得由 ReAct 直接 Action");
        assertTrue(prompt.contains("必须输出模式C"), "高危 Tool 应要求模式C/HITL");
        assertTrue(prompt.contains("参数已补全、默认值回填"), "参数完整和默认值不得替代 HITL");
        assertTrue(prompt.contains("用户自然语言表达“确认”"), "用户口头确认不得伪装成服务端 HITL");
        assertTrue(prompt.contains("都不能替代服务端 HITL"), "必须明确 HITL 不能被参数状态绕过");
        assertTrue(prompt.contains("不要在 Action.params 中主动生成 token/orgId/userId"),
            "ReAct Prompt 必须禁止 LLM 主动生成认证/租户上下文字段");
        assertTrue(prompt.contains("confirmed/hitlConfirmed/approval/auditReceipt/releaseDecision/writePermitted"),
            "ReAct Prompt 必须禁止 LLM 主动生成 HITL/审计/发布/写入控制字段");
        assertTrue(prompt.contains("operationType=PLACEHOLDER 或 httpMethod=NONE"),
            "占位/无 HTTP 方法必须作为未开放执行链路处理");
        assertTrue(prompt.contains("未开放真实后端执行链路"), "必须避免把 PLACEHOLDER 包装成真实执行");
        assertTrue(prompt.contains("不得声称已经创建/删除/提交/变更成功"),
            "Prompt 必须禁止占位 Tool 伪造成功状态");
        assertTrue(prompt.contains("关键词类高危表达"), "旧关键词规则应作为补充保留");
    }

    @AtlasToolMapping(
        name = "update_contract_tool",
        agent = "contract",
        intentId = "update_contract_tool",
        description = "测试专用更新类 Tool",
        httpMethod = "PUT",
        operationType = AtlasToolMapping.OperationType.UPDATE,
        requiresConfirmation = true
    )
    private static class UpdateContractTool extends BaseTool {
        UpdateContractTool() {
            super("update_contract_tool", "测试专用更新类 Tool");
        }

        @Override
        protected Set<String> getRequiredParams() {
            return Set.of();
        }

        @Override
        protected AtlasToolResult doExecute(Map<String, Object> params) {
            return AtlasToolResult.ok("ok", Map.of());
        }
    }
}
