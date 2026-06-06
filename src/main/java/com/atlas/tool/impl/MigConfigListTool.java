package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.exception.AtlasToolValidationException;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询指定 GPU 的 MIG 配置清单 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "mig_config_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/mig/{gpuId}</p>
 */
@Component
@AtlasToolMapping(
    name = "mig_config_list",
    agent = "query",
    intentId = "mig_config_list",
    description = "查询指定 GPU 的 MIG 配置清单",
    httpMethod = "GET",
    apiEndpoints = {"/api/mig/{gpuId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class MigConfigListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public MigConfigListTool(KubeManagerHttpClient httpClient) {
        super("mig_config_list", "查询指定 GPU 的 MIG 配置清单");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("gpuId");
    }

    /**
     * MIG 成熟接口按 gpuId 定位，不是可翻页的全局列表。
     *
     * <p>这里刻意只暴露 {@code gpuId}，避免 Agent 把它误用成 page/limit/keyword 枚举入口。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(new ToolParameterSpec(
            "gpuId",
            "integer",
            "GPU 规格 ID，必须来自 gpu_detail_list 返回的数字 ID。",
            true,
            List.of("id", "gpu_id", "gpuDetailId", "gpu_detail_id")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String gpuId = positiveGpuId(params);
            String path = "/api/mig/" + gpuId;

            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = extractData(response);
            return AtlasToolResult.ok("查询指定 GPU 的 MIG 配置清单完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[mig_config_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询指定 GPU 的 MIG 配置清单失败: " + e.getMessage());
        }
    }

    private String positiveGpuId(Map<String, Object> params) {
        Object raw = params.get("gpuId");
        if (raw == null || raw.toString().isBlank()) {
            throw new AtlasToolValidationException(
                "缺少必填参数: gpuId",
                "MISSING_GPU_ID",
                List.of("请先通过 gpu_detail_list 查询 GPU 规格，再使用返回的数字 ID 查询 MIG 配置")
            );
        }
        String value = raw.toString().trim();
        if (!value.matches("[1-9]\\d*")) {
            throw new AtlasToolValidationException(
                "gpuId 仅支持正整数: " + value,
                "INVALID_GPU_ID",
                List.of("gpuId 会进入 URL path，必须使用成熟后端返回的数字 ID，不能包含路径、脚本或查询字符串")
            );
        }
        return value;
    }
}
