package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 创建标准实例(Deployment) Tool — 含默认参数回填。
 *
 * <p>调用 kube-manager API: POST /api/instance/create</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 *
 * <p><b>默认参数(匹配前端表单)</b>:</p>
 * <ul>
 *   <li>cpuLimits = 2</li>
 *   <li>memLimits = 8</li>
 *   <li>gpuPercentLimits = 0</li>
 *   <li>replicas = 1</li>
 *   <li>bandwidth = 10</li>
 *   <li>enableWebSsh = true</li>
 *   <li>autoScaleSwitch = false</li>
 * </ul>
 */
@Component
@AtlasToolMapping(
    name = "deploy_create_instance",
    agent = "deploy",
    intentId = "deploy_create_instance",
    description = "创建标准实例(Deployment)，含前端表单默认参数"
)
// P1 创建部署会修改集群状态，但不限定管理员；要求调用方已登录，后端继续按用户真实 Token 做细粒度鉴权。
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class DeployCreateTool extends BaseTool {

    public DeployCreateTool() {
        super("deploy_create_instance", "创建标准实例(Deployment)，含前端表单默认参数");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "image");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("cpuLimits", Integer.class),
            Map.entry("memLimits", Integer.class),
            Map.entry("gpuPercentLimits", Integer.class),
            Map.entry("replicas", Integer.class),
            Map.entry("bandwidth", Integer.class),
            Map.entry("enableWebSsh", Boolean.class),
            Map.entry("autoScaleSwitch", Boolean.class)
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

        // P1阶段: Mock创建（后续替换为真实HTTP调用）
        int cpu = getIntParam(params, "cpuLimits", 2);
        int mem = getIntParam(params, "memLimits", 8);
        int gpu = getIntParam(params, "gpuPercentLimits", 0);
        int replicas = getIntParam(params, "replicas", 1);
        int bw = getIntParam(params, "bandwidth", 10);
        boolean ssh = getBoolParam(params, "enableWebSsh", true);

        log.info("[deploy_create_instance] 创建实例 name={}, image={}, cpu={}, mem={}", name, image, cpu, mem);

        Map<String, Object> data = Map.of(
            "success", true,
            "createdName", name,
            "image", image,
            "config", Map.of(
                "cpuLimits", cpu,
                "memLimits", mem,
                "gpuPercentLimits", gpu,
                "replicas", replicas,
                "bandwidth", bw,
                "enableWebSsh", ssh
            ),
            "action", "deploy_create_instance",
            "status", "Created",
            "message", "创建任务已提交, 请稍候确认状态"
        );

        String summary = "实例 '" + name + "' 创建任务已提交 (镜像: " + image + ", CPU: " + cpu + "核, 内存: " + mem + "GB)";
        return AtlasToolResult.ok(summary, data);
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

    private boolean getBoolParam(Map<String, Object> params, String key, boolean defaultVal) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return "true".equalsIgnoreCase(v.toString().trim());
        return defaultVal;
    }
}
