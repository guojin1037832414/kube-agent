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
 * 升级 Helm Release Tool。
 *
 * <p>成熟接口为 {@code PUT /api/{orgId}/helm/release/{release}/upgrade?chart=xxx}，
 * body 为 UpgradeBodyDTO。升级会修改线上应用，必须保留人工确认。</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_release_upgrade",
    agent = "deploy",
    intentId = "helm_release_upgrade",
    description = "升级 Helm Release，会修改集群中的应用",
    httpMethod = "PUT",
    apiEndpoints = {"/api/{orgId}/helm/release/{release}/upgrade"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class HelmReleaseUpgradeTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmReleaseUpgradeTool(KubeManagerHttpClient httpClient) {
        super("helm_release_upgrade", "升级 Helm Release");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("release", "chart");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("release", "要升级的 Helm Release 名称。", true, List.of("releaseName", "name")),
            ToolParameterSpec.stringParam("chart", "升级使用的 Chart 名称或 URL。", true, List.of("chartName", "chartUrl")),
            ToolParameterSpec.stringParam("version", "目标 Chart 版本。", false, List.of("chartVersion")),
            ToolParameterSpec.stringParam("values", "升级使用的 values.yaml 文件或 URL。", false, List.of("valuesYaml")),
            new ToolParameterSpec("set", "array", "Helm --set 参数列表。", false, List.of("setValues")),
            new ToolParameterSpec("force", "boolean", "是否强制更新资源。", false, List.of()),
            new ToolParameterSpec("wait", "boolean", "是否等待资源就绪。", false, List.of()),
            new ToolParameterSpec("dry_run", "boolean", "是否仅模拟升级，不实际修改资源。", false, List.of("dryRun"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String release = requiredString(params, "release");
        String chart = requiredString(params, "chart");
        if (release.isBlank() || chart.isBlank()) {
            return AtlasToolResult.fail("缺少升级 Helm Release 所需参数: release/chart",
                "MISSING_HELM_UPGRADE_PARAMS",
                List.of("请提供 release 和 chart，并在确认弹窗中核对目标 Release"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.put(
                "/api/" + orgId + "/helm/release/" + release + "/upgrade",
                Map.of("chart", chart),
                HelmReleaseBodyBuilder.upgradeBody(params)
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("Helm Release 升级请求已发送: " + release + " <- " + chart, data);
        } catch (Exception e) {
            log.error("[helm_release_upgrade] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("升级 Helm Release 失败: " + e.getMessage());
        }
    }

    private String requiredString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
