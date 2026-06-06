package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安装 Helm Release Tool。
 *
 * <p>成熟接口为 {@code POST /api/{orgId}/helm/releases/{release}?chart=xxx}。
 * chart 是 query 参数，安装选项是 InstallBodyDTO；本 Tool 只透传 DTO 白名单字段。</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_release_install",
    agent = "deploy",
    intentId = "helm_release_install",
    description = "安装 Helm Release，会在集群中创建应用",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/helm/releases/{release}"},
    operationType = AtlasToolMapping.OperationType.CREATE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class HelmReleaseInstallTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmReleaseInstallTool(KubeManagerHttpClient httpClient) {
        super("helm_release_install", "安装 Helm Release");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("release", "chart");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("release", "要安装的 Helm Release 名称。", true, List.of("releaseName", "name")),
            ToolParameterSpec.stringParam("chart", "Chart 名称、URL 或已上传的 chart.tgz 文件名。", true, List.of("chartName", "chartUrl")),
            ToolParameterSpec.stringParam("version", "Chart 版本约束；不传则由 Helm 使用默认版本。", false, List.of("chartVersion")),
            ToolParameterSpec.stringParam("values", "values.yaml 文件或 URL；复杂 values 建议先由 chart 详情确认。", false, List.of("valuesYaml")),
            new ToolParameterSpec("set", "array", "Helm --set 参数列表，例如 image.tag=1.0。", false, List.of("setValues")),
            new ToolParameterSpec("wait", "boolean", "是否等待资源就绪后再标记安装成功。", false, List.of()),
            new ToolParameterSpec("atomic", "boolean", "安装失败时是否自动清理本次创建的资源。", false, List.of()),
            new ToolParameterSpec("dry_run", "boolean", "是否仅模拟安装，不实际创建资源。", false, List.of("dryRun"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String release = requiredString(params, "release");
        String chart = requiredString(params, "chart");
        if (release.isBlank() || chart.isBlank()) {
            return AtlasToolResult.fail("缺少安装 Helm Release 所需参数: release/chart",
                "MISSING_HELM_INSTALL_PARAMS",
                List.of("请提供 release 和 chart，写操作执行前仍需用户确认"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/helm/releases/" + release,
                Map.of("chart", chart),
                HelmReleaseBodyBuilder.installBody(params)
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("Helm Release 安装请求已发送: " + release + " <- " + chart, data);
        } catch (Exception e) {
            log.error("[helm_release_install] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("安装 Helm Release 失败: " + e.getMessage());
        }
    }

    private String requiredString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
