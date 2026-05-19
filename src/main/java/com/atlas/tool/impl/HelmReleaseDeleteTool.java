package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 卸载Helm Release Tool。
 * <p><b>⚠️ 危险操作</b>: P0级, 会卸载并删除Kubernetes集群中的 Helm 应用, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "helm_release_delete"}</p>
 * <p>Agent归属: deploy | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_release_delete",
    agent = "deploy",
    intentId = "helm_release_delete",
    description = "卸载Helm Release"
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class HelmReleaseDeleteTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmReleaseDeleteTool(KubeManagerHttpClient httpClient) {
        super("helm_release_delete", "卸载Helm Release");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("releaseName");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[helm_release_delete] 执行卸载Helm Release");
        String releaseName = params.get("releaseName") != null ? params.get("releaseName").toString() : "";
        if (releaseName.isBlank()) {
            return AtlasToolResult.fail("缺少必需的参数: releaseName（Release名称）");
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.delete(
                "/api/" + orgId + "/helm/releases/" + releaseName,
                Map.of()
            );
            Object data = extractData(response);
            String summary = "Helm Release已卸载: " + releaseName;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[helm_release_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Helm Release卸载失败: " + e.getMessage());
        }
    }
}
