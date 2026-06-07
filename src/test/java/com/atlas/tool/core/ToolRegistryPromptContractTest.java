package com.atlas.tool.core;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.impl.DeploymentDetailTool;
import com.atlas.tool.impl.DeploymentQueryTool;
import com.atlas.tool.impl.DiagnosePodTool;
import com.atlas.tool.impl.EventQueryTool;
import com.atlas.tool.impl.FileSelectStorageTool;
import com.atlas.tool.impl.HelmChartInfoTool;
import com.atlas.tool.impl.HelmChartSearchTool;
import com.atlas.tool.impl.ImageDetailByNameTool;
import com.atlas.tool.impl.ImageDeleteTool;
import com.atlas.tool.impl.LogQueryTool;
import com.atlas.tool.impl.MpiJobSubmitTool;
import com.atlas.tool.impl.NodeDetailTool;
import com.atlas.tool.impl.PodQueryTool;
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
    void buildSystemPrompt_shouldExposeFirstBatchDiagnosticToolContracts() {
        ToolRegistry registry = new ToolRegistry(List.of(
            new LogQueryTool(null),
            new DeploymentDetailTool(null),
            new NodeDetailTool(null)
        ), new UserPermissionContext());
        registry.init();

        String prompt = registry.buildSystemPromptForCurrentUser();

        assertTrue(prompt.contains("log_query"));
        assertTrue(prompt.contains("podName(string,可选"));
        assertTrue(prompt.contains("namespace(string,可选"));
        assertTrue(prompt.contains("lines(integer,可选"));

        assertTrue(prompt.contains("deployment_detail"));
        assertTrue(prompt.contains("name(string,必填,要查询详情的 Deployment/实例名称"));

        assertTrue(prompt.contains("node_detail"));
        assertTrue(prompt.contains("name(string,可选,要查询详情的 Kubernetes Node 节点名称"));

        assertFalse(prompt.contains("deployment_name"), "ReAct prompt 不应展开 deployment alias");
        assertFalse(prompt.contains("node_name"), "ReAct prompt 不应展开 node alias");
        assertFalse(prompt.contains("tailLines"), "ReAct prompt 不应展开日志 alias");
    }

    @Test
    void buildSystemPrompt_shouldExposeThirdBatchPodAndDeploymentListContracts() {
        ToolRegistry registry = new ToolRegistry(List.of(
            new PodQueryTool(null),
            new DeploymentQueryTool(null),
            new EventQueryTool(null)
        ), new UserPermissionContext());
        registry.init();

        String prompt = registry.buildSystemPromptForCurrentUser();

        assertTrue(prompt.contains("pod_status"));
        assertTrue(prompt.contains("namespace(string,可选,Pod 所在命名空间"));
        assertTrue(prompt.contains("podName(string,可选,Pod 名称或名称片段"));
        assertTrue(prompt.contains("username(string,可选,创建或归属用户名称"));
        assertTrue(prompt.contains("status(string,可选,Pod 状态筛选条件"));

        assertTrue(prompt.contains("deployment_status"));
        assertTrue(prompt.contains("name(string,可选,Deployment/实例名称或名称片段"));
        assertTrue(prompt.contains("namespace(string,可选,Deployment 所在命名空间"));
        assertTrue(prompt.contains("status(string,可选,Deployment/实例状态筛选条件"));

        assertTrue(prompt.contains("event_query"));
        assertTrue(prompt.contains("podName(string,可选,Pod 名称或名称片段。用于在 kube-agent 本地筛选 warning 摘要所属 Pod"));
        assertTrue(prompt.contains("reason(string,可选,Warning/异常原因关键词"));
        assertTrue(prompt.contains("keyword(string,可选,Warning 文本关键词"));

        assertFalse(prompt.contains("pod_name"), "ReAct prompt 不应展开 pod alias");
        assertFalse(prompt.contains("deployment_name"), "ReAct prompt 不应展开 deployment alias");
        assertFalse(prompt.contains("instance_name"), "ReAct prompt 不应展开 instance alias");
        assertFalse(prompt.contains("fieldSelector"), "ReAct prompt 不应暴露 event_query 不支持的 Kubernetes 原生 Event 参数");
    }

    @Test
    void buildSystemPrompt_shouldExposeSecondBatchStorageImageHelmToolContracts() {
        UserPermissionContext userPermissionContext = new UserPermissionContext();
        userPermissionContext.onLogin("token-user", "zhangsan", "user", Set.of());
        userPermissionContext.bind("token-user");
        ToolRegistry registry = new ToolRegistry(List.of(
            new FileSelectStorageTool(null),
            new ImageDetailByNameTool(null),
            new HelmChartInfoTool(null),
            new HelmChartSearchTool(null)
        ), userPermissionContext);
        registry.init();

        String prompt = registry.buildSystemPromptForCurrentUser();

        assertTrue(prompt.contains("file_select_storage"));
        assertTrue(prompt.contains("operationType=SENSITIVE_READ"));
        assertTrue(prompt.contains("name(string,必填"));

        assertTrue(prompt.contains("image_detail_by_name"));
        assertTrue(prompt.contains("name(string,可选,要查询详情的容器镜像名称或镜像引用"));

        assertTrue(prompt.contains("helm_chart_info"));
        assertTrue(prompt.contains("chart(string,必填,要查询详情的 Helm Chart 名称或标识"));

        assertTrue(prompt.contains("helm_chart_search"));
        assertTrue(prompt.contains("keyword(string,可选,Helm Chart 模糊搜索关键字"));

        assertFalse(prompt.contains("storage_name"), "ReAct prompt 不应展开 storage alias");
        assertFalse(prompt.contains("image_name"), "ReAct prompt 不应展开 image alias");
        assertFalse(prompt.contains("chart_name"), "ReAct prompt 不应展开 chart alias");
        assertFalse(prompt.contains("searchText"), "ReAct prompt 不应展开 keyword alias");
    }

    @Test
    void buildSystemPrompt_shouldExposeRiskMetadataWithoutLeakingApiEndpoints() {
        UserPermissionContext userPermissionContext = new UserPermissionContext();
        userPermissionContext.onLogin("token-admin", "admin", "sys_admin", Set.of());
        userPermissionContext.bind("token-admin");
        ToolRegistry registry = new ToolRegistry(List.of(
            new EventQueryTool(null),
            new LogQueryTool(null),
            new MpiJobSubmitTool(null),
            new ImageDeleteTool(null)
        ), userPermissionContext);
        registry.init();

        String prompt = registry.buildSystemPromptForCurrentUser();

        assertTrue(prompt.contains("event_query"));
        assertTrue(prompt.contains("风险标签: operationType=READ, httpMethod=GET, requiresConfirmation=false"));
        assertTrue(prompt.contains("log_query"));
        assertTrue(prompt.contains("风险标签: operationType=SENSITIVE_READ, httpMethod=GET, requiresConfirmation=true"));
        assertTrue(prompt.contains("mpi_job_submit"));
        assertTrue(prompt.contains("风险标签: operationType=ACTION, httpMethod=POST, requiresConfirmation=true"));
        assertTrue(prompt.contains("image_delete"));
        assertTrue(prompt.contains("风险标签: operationType=DELETE, httpMethod=DELETE, requiresConfirmation=true"));
        assertTrue(prompt.contains("SENSITIVE_READ 为敏感读取"));
        assertTrue(prompt.contains("HITL fail-closed 守卫强制拦截"));

        assertFalse(prompt.contains("/api/"), "Prompt 不应泄露 kube-manager 内部 API endpoint");
        assertFalse(prompt.contains("apiEndpoints"), "Prompt 不应暴露注解字段名 apiEndpoints");
    }

    @Test
    void buildSystemPrompt_shouldStateDefaultsAreNotAuthorizationAndHideDefaultRegistry() {
        UserPermissionContext userPermissionContext = new UserPermissionContext();
        userPermissionContext.onLogin("token-user", "zhangsan", "user", Set.of());
        userPermissionContext.bind("token-user");
        ToolRegistry registry = new ToolRegistry(List.of(
            new com.atlas.tool.impl.DeployCreateTool(null),
            new com.atlas.tool.impl.NimCreateTool()
        ), userPermissionContext);
        registry.init();

        String prompt = registry.buildSystemPromptForCurrentUser();

        assertTrue(prompt.contains("默认/可选"));
        assertTrue(prompt.contains("只表示表单草稿或前端填充提示"));
        assertTrue(prompt.contains("不代表用户已确认"));
        assertTrue(prompt.contains("HITL 通过"));
        assertTrue(prompt.contains("发布批准"));
        assertTrue(prompt.contains("审计成功"));
        assertTrue(prompt.contains("写入授权"));
        assertTrue(prompt.contains("真实 HTTP 执行许可"));
        assertTrue(prompt.contains("requiresConfirmation=false 只表示该 Tool 不需要额外 HITL"));
        assertTrue(prompt.contains("不代表绕过登录、RBAC、租户隔离、发布门禁或后端鉴权"));
        assertTrue(prompt.contains("不要在 Action.params 主动生成认证、租户、HITL、审计、发布或写入控制字段"));

        assertFalse(prompt.contains("defaults.yml"), "Prompt 不应暴露默认值配置文件名，避免 LLM 把配置当作证据源");
        assertFalse(prompt.contains("DefaultValueRegistry"), "Prompt 不应暴露默认值注册中心实现细节");
        assertFalse(prompt.contains("DefaultValueSafety"), "Prompt 不应暴露默认值安全实现细节");
        assertFalse(prompt.contains("gpuPercentLimits=100"), "Prompt 不应渲染 defaults.yml 中的具体 NIM 默认值");
        assertFalse(prompt.contains("safeToPost"), "Prompt 不应诱导 LLM 生成写入控制字段");
        assertFalse(prompt.contains("releaseDecision"), "Prompt 不应诱导 LLM 生成发布决策字段");
        assertFalse(prompt.contains("writePermitted"), "Prompt 不应诱导 LLM 生成写入授权字段");
        assertFalse(prompt.contains("Authorization"), "Prompt 不应诱导 LLM 生成认证头字段");
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
