package com.atlas.tool.core;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.impl.DeploymentDetailTool;
import com.atlas.tool.impl.DeploymentQueryTool;
import com.atlas.tool.impl.DiagnosePodTool;
import com.atlas.tool.impl.EventQueryTool;
import com.atlas.tool.impl.FileSelectStorageTool;
import com.atlas.tool.impl.HelmChartInfoTool;
import com.atlas.tool.impl.HelmChartSearchTool;
import com.atlas.tool.impl.ImageDetailByNameTool;
import com.atlas.tool.impl.LogQueryTool;
import com.atlas.tool.impl.NodeDetailTool;
import com.atlas.tool.impl.PodQueryTool;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolParameterNormalizer 参数归一化契约测试。
 *
 * <p>这些测试用于锁定 LLM 参数别名兼容规则，尤其防止 {@code name} 这类高歧义字段
 * 被全局误映射到错误资源类型。后续引入 Tool Schema 时，也必须保持这些兼容性契约。</p>
 */
class ToolParameterNormalizerTest {

    private final ToolParameterNormalizer normalizer = new ToolParameterNormalizer();

    @Test
    void normalize_shouldMapPodAliasesForPodTools() {
        Map<String, Object> result = normalizer.normalize("diagnose_pod", Map.of(
            "pod_name", "nginx-1",
            "name_space", "default"
        ));

        assertEquals("nginx-1", result.get("podName"));
        assertEquals("default", result.get("namespace"));
        assertEquals("nginx-1", result.get("pod_name"), "原始 alias 字段应保留，便于审计和兼容");
    }

    @Test
    void normalize_schemaFirstShouldUseToolParameterSpecsWhenRegistryAvailable() {
        DiagnosePodTool diagnosePodTool = new DiagnosePodTool(null);
        ToolRegistry registry = new ToolRegistry(java.util.List.of(diagnosePodTool), new UserPermissionContext());
        registry.init();
        ToolParameterNormalizer schemaFirstNormalizer = new ToolParameterNormalizer(registry);

        Map<String, Object> result = schemaFirstNormalizer.normalize("diagnose_pod", Map.of(
            "target_name", "nginx-schema",
            "ns", "default"
        ));

        assertEquals("nginx-schema", result.get("podName"));
        assertEquals("default", result.get("namespace"));
        assertEquals("nginx-schema", result.get("target_name"), "schema-first 也必须保留原始字段，方便审计");
    }

    @Test
    void normalize_schemaFirstShouldNormalizeFirstBatchDiagnosticTools() {
        ToolRegistry registry = new ToolRegistry(java.util.List.of(
            new LogQueryTool(null),
            new DeploymentDetailTool(null),
            new NodeDetailTool(null)
        ), new UserPermissionContext());
        registry.init();
        ToolParameterNormalizer schemaFirstNormalizer = new ToolParameterNormalizer(registry);

        Map<String, Object> logResult = schemaFirstNormalizer.normalize("log_query", Map.of(
            "pod_name", "nginx-log",
            "tailLines", 200,
            "ns", "default"
        ));
        assertEquals("nginx-log", logResult.get("podName"));
        assertEquals(200, logResult.get("lines"));
        assertEquals("default", logResult.get("namespace"));

        Map<String, Object> deploymentResult = schemaFirstNormalizer.normalize("deployment_detail", Map.of(
            "deploymentName", "web-deploy"
        ));
        assertEquals("web-deploy", deploymentResult.get("name"), "deployment_detail 当前执行逻辑读取 name，应由 schema alias 补齐");
        assertFalse(deploymentResult.containsKey("deploymentName_extra"));

        Map<String, Object> nodeResult = schemaFirstNormalizer.normalize("node_detail", Map.of(
            "nodeName", "worker-1"
        ));
        assertEquals("worker-1", nodeResult.get("name"), "node_detail 当前执行逻辑读取 name，应由 schema alias 补齐");
    }

    @Test
    void normalize_schemaFirstShouldNormalizeSecondBatchStorageImageHelmTools() {
        ToolRegistry registry = new ToolRegistry(java.util.List.of(
            new FileSelectStorageTool(null),
            new ImageDetailByNameTool(null),
            new HelmChartInfoTool(null),
            new HelmChartSearchTool(null)
        ), new UserPermissionContext());
        registry.init();
        ToolParameterNormalizer schemaFirstNormalizer = new ToolParameterNormalizer(registry);

        Map<String, Object> storageResult = schemaFirstNormalizer.normalize("file_select_storage", Map.of(
            "storageName", "test-storage"
        ));
        assertEquals("test-storage", storageResult.get("name"), "file_select_storage 当前执行逻辑读取 name，应由 storageName alias 补齐");
        assertEquals("test-storage", storageResult.get("storageName"), "原始 alias 字段必须保留，方便审计");
        assertFalse(storageResult.containsKey("storageClass"), "storageName 不应误映射为 storageClass");

        Map<String, Object> imageResult = schemaFirstNormalizer.normalize("image_detail_by_name", Map.of(
            "imageName", "library/nginx:1.25"
        ));
        assertEquals("library/nginx:1.25", imageResult.get("name"), "image_detail_by_name 当前执行逻辑读取 name，应由 imageName alias 补齐");
        assertFalse(imageResult.containsKey("podName"), "镜像名称不能被误归一为 Pod 名称");
        assertFalse(imageResult.containsKey("deploymentName"), "镜像名称不能被误归一为 Deployment 名称");

        Map<String, Object> chartInfoResult = schemaFirstNormalizer.normalize("helm_chart_info", Map.of(
            "chartName", "bitnami/redis"
        ));
        assertEquals("bitnami/redis", chartInfoResult.get("chart"), "helm_chart_info 当前执行逻辑读取 chart，应由 chartName alias 补齐");
        assertFalse(chartInfoResult.containsKey("releaseName"), "Chart 名称不能被误归一为 Helm Release 名称");

        Map<String, Object> chartSearchResult = schemaFirstNormalizer.normalize("helm_chart_search", Map.of(
            "searchText", "redis"
        ));
        assertEquals("redis", chartSearchResult.get("keyword"), "helm_chart_search 当前执行逻辑读取 keyword，应由 searchText alias 补齐");
        assertFalse(chartSearchResult.containsKey("name"), "模糊搜索词不能被误归一为精确 name");
    }

    @Test
    void normalize_schemaFirstShouldNormalizeThirdBatchPodAndDeploymentListTools() {
        ToolRegistry registry = new ToolRegistry(java.util.List.of(
            new PodQueryTool(null),
            new DeploymentQueryTool(null),
            new EventQueryTool(null)
        ), new UserPermissionContext());
        registry.init();
        ToolParameterNormalizer schemaFirstNormalizer = new ToolParameterNormalizer(registry);

        Map<String, Object> podResult = schemaFirstNormalizer.normalize("pod_status", Map.of(
            "pod_name", "nginx-pod-1",
            "ns", "ns100002",
            "userName", "zhaotiandi",
            "phase", "Running"
        ));
        assertEquals("nginx-pod-1", podResult.get("podName"));
        assertEquals("ns100002", podResult.get("namespace"));
        assertEquals("zhaotiandi", podResult.get("username"));
        assertEquals("Running", podResult.get("status"));
        assertEquals("nginx-pod-1", podResult.get("pod_name"), "原始 alias 字段必须保留，方便审计");

        Map<String, Object> deploymentResult = schemaFirstNormalizer.normalize("deployment_status", Map.of(
            "deploymentName", "aaaa",
            "ns", "ns100002",
            "owner", "zhaotiandi",
            "instanceStatus", "Running"
        ));
        assertEquals("aaaa", deploymentResult.get("name"), "deployment_status 当前执行逻辑读取 name，应由 deploymentName alias 补齐");
        assertEquals("ns100002", deploymentResult.get("namespace"));
        assertEquals("zhaotiandi", deploymentResult.get("username"));
        assertEquals("Running", deploymentResult.get("status"));
        assertFalse(deploymentResult.containsKey("podName"), "实例/Deployment 名称不能误归一为 Pod 名称");

        Map<String, Object> eventResult = schemaFirstNormalizer.normalize("event_query", Map.of(
            "pod_name", "nginx-pod-1",
            "ns", "ns100002",
            "warningReason", "BackOff",
            "message", "pull image failed"
        ));
        assertEquals("nginx-pod-1", eventResult.get("podName"));
        assertEquals("ns100002", eventResult.get("namespace"));
        assertEquals("BackOff", eventResult.get("reason"));
        assertEquals("pull image failed", eventResult.get("keyword"));
        assertFalse(eventResult.containsKey("fieldSelector"), "event_query 不声明 Kubernetes 原生 Event 伪参数");
    }

    @Test
    void normalize_shouldMapNameByToolType() {
        Map<String, Object> podResult = normalizer.normalize("diagnose_pod", Map.of("name", "nginx-1"));
        Map<String, Object> nodeResult = normalizer.normalize("node_query", Map.of("name", "node-a"));
        Map<String, Object> deploymentResult = normalizer.normalize("deployment_query", Map.of("name", "deploy-a"));

        assertEquals("nginx-1", podResult.get("podName"));
        assertFalse(podResult.containsKey("nodeName"));

        assertEquals("node-a", nodeResult.get("nodeName"));
        assertFalse(nodeResult.containsKey("podName"));

        assertEquals("deploy-a", deploymentResult.get("deploymentName"));
        assertFalse(deploymentResult.containsKey("podName"));
    }

    @Test
    void normalize_shouldNotMapNameForUnknownTool() {
        Map<String, Object> result = normalizer.normalize("unknown_tool", Map.of("name", "ambiguous-name"));

        assertEquals("ambiguous-name", result.get("name"));
        assertFalse(result.containsKey("podName"));
        assertFalse(result.containsKey("nodeName"));
        assertFalse(result.containsKey("deploymentName"));
    }

    @Test
    void normalize_shouldNotOverrideCanonicalValue() {
        Map<String, Object> result = normalizer.normalize("diagnose_pod", Map.of(
            "podName", "canonical-pod",
            "pod_name", "alias-pod",
            "name", "name-pod"
        ));

        assertEquals("canonical-pod", result.get("podName"));
    }

    @Test
    void normalize_shouldPreserveFalsyAndUnknownValues() {
        Map<String, Object> params = new HashMap<>();
        params.put("enabled", false);
        params.put("replicas", 0);
        params.put("emptyText", "");
        params.put("vendor_custom_flag", "on");

        Map<String, Object> result = normalizer.normalize("diagnose_pod", params);

        assertEquals(false, result.get("enabled"));
        assertEquals(0, result.get("replicas"));
        assertEquals("", result.get("emptyText"));
        assertEquals("on", result.get("vendor_custom_flag"));
    }

    @Test
    void normalize_shouldReturnNewMapWithoutMutatingInput() {
        Map<String, Object> input = new HashMap<>();
        input.put("pod_name", "nginx-1");

        Map<String, Object> result = normalizer.normalize("diagnose_pod", input);

        assertNotSame(input, result);
        assertFalse(input.containsKey("podName"), "归一化器不应修改调用方传入的原始 Map");
        assertEquals("nginx-1", result.get("podName"));
    }
}
