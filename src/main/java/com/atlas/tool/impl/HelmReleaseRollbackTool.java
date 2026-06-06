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
 * 回滚 Helm Release Tool。
 *
 * <p>成熟接口为 {@code PUT /api/{orgId}/helm/release/{release}/rollback/{version}}。
 * 回滚可能删除并重建资源，因此需要明确 release 与 revision 后再执行。</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_release_rollback",
    agent = "deploy",
    intentId = "helm_release_rollback",
    description = "回滚 Helm Release，会修改集群中的应用",
    httpMethod = "PUT",
    apiEndpoints = {"/api/{orgId}/helm/release/{release}/rollback/{version}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class HelmReleaseRollbackTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmReleaseRollbackTool(KubeManagerHttpClient httpClient) {
        super("helm_release_rollback", "回滚 Helm Release");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("release", "version");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("release", "要回滚的 Helm Release 名称。", true, List.of("releaseName", "name")),
            ToolParameterSpec.stringParam("version", "要回滚到的 revision 版本号。", true, List.of("revision")),
            new ToolParameterSpec("wait", "boolean", "是否等待资源就绪。", false, List.of()),
            new ToolParameterSpec("force", "boolean", "是否强制回滚，可能删除并重建资源。", false, List.of()),
            new ToolParameterSpec("cleanup_on_fail", "boolean", "回滚失败时是否清理本次创建的资源。", false, List.of("cleanupOnFail")),
            new ToolParameterSpec("history_max", "integer", "每个 release 保留的最大历史版本数。", false, List.of("historyMax")),
            new ToolParameterSpec("dry_run", "boolean", "是否仅模拟回滚，不实际修改资源。", false, List.of("dryRun"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String release = requiredString(params, "release");
        String version = requiredString(params, "version");
        if (release.isBlank() || version.isBlank()) {
            return AtlasToolResult.fail("缺少回滚 Helm Release 所需参数: release/version",
                "MISSING_HELM_ROLLBACK_PARAMS",
                List.of("请提供 release 和要回滚到的 revision 版本号"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.put(
                "/api/" + orgId + "/helm/release/" + release + "/rollback/" + version,
                HelmReleaseBodyBuilder.rollbackBody(params)
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("Helm Release 回滚请求已发送: " + release + " -> revision " + version, data);
        } catch (Exception e) {
            log.error("[helm_release_rollback] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("回滚 Helm Release 失败: " + e.getMessage());
        }
    }

    private String requiredString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
