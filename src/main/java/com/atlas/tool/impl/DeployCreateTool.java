package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 创建标准实例(Deployment) Tool — 含默认参数回填。
 *
 * <p>调用 kube-manager API: POST /api/{orgId}/deployment</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 *
 * <p><b>默认参数(匹配前端表单)</b>:</p>
 * <ul>
 *   <li>cpuLimits = 2</li>
 *   <li>memLimits = 8</li>
 *   <li>gpuPercentLimits = 0</li>
 *   <li>replicas = 1</li>
 *   <li>bandwidth = 5</li>
 *   <li>enableWebSsh = true</li>
 *   <li>autoScaleSwitch = false</li>
 * </ul>
 */
@Component
@AtlasToolMapping(
    name = "deploy_create_instance",
    agent = "deploy",
    intentId = "deploy_create_instance",
    description = "创建标准实例(Deployment)，含前端表单默认参数",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/deployment"},
    operationType = AtlasToolMapping.OperationType.CREATE,
    requiresConfirmation = true
)
// P1 创建部署会修改集群状态，但不限定管理员；要求调用方已登录，后端继续按用户真实 Token 做细粒度鉴权。
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class DeployCreateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;
    private final GpuSelectionResolver gpuSelectionResolver;

    public DeployCreateTool(KubeManagerHttpClient httpClient) {
        super("deploy_create_instance", "创建标准实例(Deployment)，含前端表单默认参数");
        this.httpClient = httpClient;
        this.gpuSelectionResolver = new GpuSelectionResolver(httpClient);
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "image");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("cpuLimits", Double.class),
            Map.entry("memLimits", Double.class),
            Map.entry("gpuPercentLimits", Double.class),
            Map.entry("gpuMemLimits", Double.class),
            Map.entry("replicas", Integer.class),
            Map.entry("bandwidth", Integer.class),
            Map.entry("enableWebSsh", Boolean.class),
            Map.entry("autoScaleSwitch", Boolean.class)
        );
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("name",
                "部署实例名称，必须是用户希望创建的应用/实例名，例如 demo-ubuntu。",
                true,
                List.of("deploymentName", "deployment_name", "instanceName", "instance_name", "appName", "app_name")),
            ToolParameterSpec.stringParam("image",
                "容器镜像名称，例如 ubuntu:22.04 或 registry.example.com/ns/image:tag。",
                true,
                List.of("imageName", "image_name", "containerImage", "container_image", "镜像")),
            new ToolParameterSpec("cpuLimits", "number",
                "CPU 上限，面向用户使用“核”为单位，例如 2 表示 2 核；发送 kube-manager 前会转换为毫核。",
                false,
                List.of("cpu", "cpuCores", "cpu_cores")),
            new ToolParameterSpec("memLimits", "number",
                "内存上限，面向用户使用 GB 为单位，例如 8 表示 8GB；发送 kube-manager 前会转换为 MiB。",
                false,
                List.of("memory", "mem", "memoryGb", "memory_gb")),
            new ToolParameterSpec("replicas", "integer",
                "副本数，默认 1。普通实例通常保持 1，扩缩容请使用 deploy_scale。",
                false,
                List.of("replica", "replicaCount", "replica_count", "副本数")),
            new ToolParameterSpec("bandwidth", "integer",
                "网络带宽，单位 Mbps，默认 5；会同时写入 ingressBandwidth/egressBandwidth。",
                false,
                List.of("bandwidthMbps", "bandwidth_mbps", "带宽")),
            new ToolParameterSpec("enableWebSsh", "boolean",
                "是否启用 WebSSH，默认 true。",
                false,
                List.of("webSsh", "web_ssh", "ssh")),
            new ToolParameterSpec("gpuPercentLimits", "number",
                "GPU 数量或比例，0 表示不使用 GPU，1 表示整卡，0.5 表示半卡；大于 0 时必须同时提供 gpuModel。",
                false,
                List.of("gpu", "gpuCount", "gpu_count", "gpuNum", "gpu_num")),
            new ToolParameterSpec("gpuMemLimits", "number",
                "GPU 显存上限，单位 GB；整卡或 MIG 场景通常由系统置 0。",
                false,
                List.of("gpuMemory", "gpu_memory", "vram", "显存")),
            ToolParameterSpec.stringParam("gpuModel",
                "GPU 型号，例如 A100、V100。使用 GPU 前应先通过 gpu_query 查询可用型号，不能凭空猜测。",
                false,
                List.of("gpu_model", "gpuType", "gpu_type")),
            ToolParameterSpec.stringParam("migConfig",
                "MIG 配置，可选。只有 gpu_query 返回明确 MIG 配置或用户明确指定时才填写。",
                false,
                List.of("mig", "mig_config")),
            ToolParameterSpec.stringParam("gpuSpec",
                "GPU 规格 key，来自 gpu_query 的组织级 GPU map，例如 A100 或 A100#all-2g.10gb；优先用它解析 gpuModel/migConfig。",
                false,
                List.of("gpu_spec", "gpuKey", "gpu_key"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String name = params.get("name") != null ? params.get("name").toString().trim() : "";
        String image = params.get("image") != null ? params.get("image").toString().trim() : "";

        if (name.isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: name（实例名称）", "MISSING_NAME",
                List.of("请提供实例名称，例如: my-ubuntu"));
        }
        if (image.isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: image（镜像名称）", "MISSING_IMAGE",
                List.of("请提供镜像名称，例如: ubuntu:22.04"));
        }

        double cpu = getDoubleParam(params, "cpuLimits", 2);
        double mem = getDoubleParam(params, "memLimits", 8);
        double gpu = getDoubleParam(params, "gpuPercentLimits", 0);
        double gpuMem = getDoubleParam(params, "gpuMemLimits", 0);
        int replicas = getIntParam(params, "replicas", 1);
        int bw = getIntParam(params, "bandwidth", 5);
        boolean ssh = getBoolParam(params, "enableWebSsh", true);
        boolean autoScale = getBoolParam(params, "autoScaleSwitch", false);

        log.info("[deploy_create_instance] 创建实例 name={}, image={}, cpu={}, mem={}", name, image, cpu, mem);

        try {
            String orgId = resolveOrganizationId(params);
            if (gpu > 0) {
                GpuSelectionResolver.GpuResolution gpuResolution = gpuSelectionResolver.resolve(orgId, params);
                if (!gpuResolution.success()) {
                    return AtlasToolResult.fail(gpuResolution.message(), gpuResolution.errorCode(), gpuResolution.suggestions());
                }
                params.put("gpuModel", gpuResolution.gpuModel());
                if (hasText(gpuResolution.migConfig())) {
                    params.put("migConfig", gpuResolution.migConfig());
                }
                // 半卡场景如果用户没给显存，就按组织 GPU map 中的单卡/MIG 显存保守回填。
                // 整卡和 MIG 场景的 gpuMemLimits 会在 buildCreateBody 中置 0，对齐成熟前端。
                if (gpu > 0 && gpu < 1 && gpuMem <= 0 && gpuResolution.memoryMiB() > 0) {
                    gpuMem = Math.floor(gpu * gpuResolution.memoryMiB() / 1024.0);
                }
            }
            String path = "/api/" + orgId + "/deployment";
            Map<String, Object> body = buildCreateBody(params, name, image, cpu, mem, gpu, gpuMem, replicas, bw, ssh, autoScale);
            Map<String, Object> response = httpClient.post(path, body);
            Object data = extractData(response);

            String summary = "实例 '" + name + "' 创建任务已提交 (镜像: " + image + ", CPU: " + cpu + "核, 内存: " + mem + "GB)";
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[deploy_create_instance] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("实例创建失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildCreateBody(
        Map<String, Object> params,
        String name,
        String image,
        double cpu,
        double mem,
        double gpu,
        double gpuMem,
        int replicas,
        int bandwidth,
        boolean enableWebSsh,
        boolean autoScaleSwitch
    ) {
        Map<String, Object> body = filterNullParams(params);
        body.put("name", name);
        body.put("image", image);

        // kube-manager 的 DeploymentDTO 使用后端单位：CPU=毫核，内存=MiB。
        // Agent 对用户暴露人类友好的单位：CPU=核，内存=GB；这里对齐成熟前端 formatApplication。
        int cpuMilli = (int) Math.round(cpu * 1000);
        int memMiB = (int) Math.round(mem * 1024);
        body.put("cpuLimits", cpuMilli);
        body.put("memLimits", memMiB);
        body.put("cpuRequests", cpuMilli);
        body.put("memRequests", memMiB);

        // 前端语义：gpuPercentLimits 表示卡数/比例，后端语义：百分比。
        // 例如 1 表示整卡 -> 100；0.5 表示半卡 -> 50；大于 1 时只能取整数卡。
        double normalizedGpu = gpu > 1 ? Math.floor(gpu) : gpu;
        int gpuPercent = (int) Math.round(normalizedGpu * 100);
        int gpuMemMiB = gpuPercent >= 100 ? 0 : (int) Math.round(gpuMem * 1024);
        body.put("gpuPercentLimits", gpuPercent);
        body.put("gpuMemLimits", gpuMemMiB);
        body.put("replicas", replicas);
        body.put("bandwidth", bandwidth);
        if (bandwidth > 0) {
            body.put("ingressBandwidth", bandwidth + "M");
            body.put("egressBandwidth", bandwidth + "M");
        }
        body.put("enableWebSsh", enableWebSsh);
        body.put("enableSecondNetwork", true);
        body.put("autoScaleSwitch", autoScaleSwitch);
        if (!autoScaleSwitch) {
            body.put("autoScaleConfig", null);
        }
        return body;
    }

    private Map<String, Object> filterNullParams(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            // organizationId/orgId/token/userId/conversationId 属于服务端可信上下文，
            // 只能用于执行链路内部判权，不能作为 DeploymentDTO body 透传给 kube-manager。
            if (value != null && !isProtectedContextKey(key)) {
                body.put(key, value);
            }
        });
        return body;
    }

    private boolean isProtectedContextKey(String key) {
        if (key == null) return false;
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return Set.of("token", "authtoken", "orgid", "organizationid", "userid", "conversationid", "sessionid")
            .contains(normalized);
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString().trim()); }
            catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultVal) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            try { return Double.parseDouble(v.toString().trim()); }
            catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    private boolean getBoolParam(Map<String, Object> params, String key, boolean defaultVal) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return "true".equalsIgnoreCase(v.toString().trim());
        return defaultVal;
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }
}
