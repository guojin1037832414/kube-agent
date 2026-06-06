package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;

import java.util.List;
import java.util.Map;

/**
 * GPU 选择解析器。
 *
 * <p>部署创建 Tool 本身是 CREATE/POST，高风险元数据必须保持清晰；GPU map 的 GET 读取属于
 * 参数澄清/解析辅助能力，因此从 {@link DeployCreateTool} 中拆出来，避免一个写操作 Tool
 * 在源码契约里同时呈现 GET+POST。解析器只读取组织级 GPU map，不访问全局 GPU 管理表。</p>
 */
class GpuSelectionResolver {

    private final KubeManagerHttpClient httpClient;

    GpuSelectionResolver(KubeManagerHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    GpuResolution resolve(String orgId, Map<String, Object> params) {
        String requestedSpec = trimmed(params.get("gpuSpec"));
        String requestedModel = trimmed(params.get("gpuModel"));
        String requestedMig = trimmed(params.get("migConfig"));

        if (!hasText(requestedSpec) && !hasText(requestedModel)) {
            return GpuResolution.fail("创建 GPU 实例时缺少 gpuSpec/gpuModel，已拒绝使用不明确的 GPU 配置",
                "MISSING_GPU_SPEC",
                List.of("请先通过 gpu_query 查询组织可用 GPU，再提供 gpuSpec 或 gpuModel"));
        }

        Map<String, Object> response = httpClient.get("/api/" + orgId + "/node/all/gpu-map");
        Object data = extractData(response);
        if (!(data instanceof Map<?, ?> gpuMap) || gpuMap.isEmpty()) {
            return GpuResolution.fail("组织 GPU map 为空，无法确认可用 GPU 配置",
                "GPU_MAP_EMPTY",
                List.of("请先确认当前组织已分配 GPU 节点，或改为创建 CPU 实例"));
        }

        return hasText(requestedSpec)
            ? resolveBySpec(gpuMap, requestedSpec)
            : resolveByModelAndMig(gpuMap, requestedModel, requestedMig);
    }

    private GpuResolution resolveBySpec(Map<?, ?> gpuMap, String requestedSpec) {
        Object direct = gpuMap.get(requestedSpec);
        if (direct instanceof Map<?, ?> directMap) {
            return fromGpuEntry(requestedSpec, directMap);
        }

        List<GpuResolution> matches = gpuMap.entrySet().stream()
            .filter(e -> e.getValue() instanceof Map<?, ?>)
            .map(e -> fromGpuEntry(String.valueOf(e.getKey()), (Map<?, ?>) e.getValue()))
            .filter(GpuResolution::success)
            .filter(r -> equalsIgnoreCase(requestedSpec, r.spec()))
            .toList();
        if (matches.size() == 1) {
            return matches.get(0);
        }
        return GpuResolution.fail("未在组织 GPU map 中找到 gpuSpec: " + requestedSpec,
            "GPU_SPEC_NOT_FOUND",
            List.of("请先调用 gpu_query，并使用返回 map 的 key 作为 gpuSpec"));
    }

    private GpuResolution resolveByModelAndMig(Map<?, ?> gpuMap, String requestedModel, String requestedMig) {
        List<GpuResolution> matches = gpuMap.entrySet().stream()
            .filter(e -> e.getValue() instanceof Map<?, ?>)
            .map(e -> fromGpuEntry(String.valueOf(e.getKey()), (Map<?, ?>) e.getValue()))
            .filter(GpuResolution::success)
            .filter(r -> equalsIgnoreCase(requestedModel, r.gpuModel()))
            .filter(r -> !hasText(requestedMig) || equalsIgnoreCase(requestedMig, r.migConfig()))
            .toList();

        if (matches.isEmpty()) {
            return GpuResolution.fail("未在组织 GPU map 中找到 gpuModel: " + requestedModel,
                "GPU_MODEL_NOT_FOUND",
                List.of("请先调用 gpu_query 确认当前组织可用 GPU 型号"));
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }

        List<GpuResolution> wholeCard = matches.stream()
            .filter(r -> !hasText(r.migConfig()))
            .toList();
        if (!hasText(requestedMig) && wholeCard.size() == 1) {
            return wholeCard.get(0);
        }
        return GpuResolution.fail("GPU 型号存在多个可选 MIG/整卡规格，已拒绝猜测: " + requestedModel,
            "AMBIGUOUS_GPU_SPEC",
            List.of("请从 gpu_query 返回结果中选择明确 gpuSpec，例如 A100#all-2g.10gb"));
    }

    private GpuResolution fromGpuEntry(String mapKey, Map<?, ?> entry) {
        String spec = firstText(entry.get("spec"), mapKey);
        String gpuModel = firstText(entry.get("gpuModel"), spec);
        String migConfig = trimmed(entry.get("migConfig"));
        int memoryMiB = getIntFromObject(entry.get("memory"), 0);
        if (!hasText(gpuModel)) {
            return GpuResolution.fail("GPU map 条目缺少 gpuModel: " + mapKey,
                "GPU_ENTRY_INVALID",
                List.of("请检查 kube-manager GPU 配置"));
        }
        return GpuResolution.ok(spec, gpuModel, migConfig, memoryMiB);
    }

    private String firstText(Object primary, String fallback) {
        String value = trimmed(primary);
        return hasText(value) ? value : fallback;
    }

    private String trimmed(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return hasText(a) && hasText(b) && a.equalsIgnoreCase(b);
    }

    private int getIntFromObject(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value != null) {
            try { return Integer.parseInt(value.toString().trim()); }
            catch (NumberFormatException ignored) { return defaultValue; }
        }
        return defaultValue;
    }

    private Object extractData(Map<String, Object> response) {
        Object result = response.get("result");
        if (result instanceof Map<?, ?> map && map.get("records") != null) {
            return map.get("records");
        }
        return result;
    }

    record GpuResolution(
        boolean success,
        String spec,
        String gpuModel,
        String migConfig,
        int memoryMiB,
        String errorCode,
        String message,
        List<String> suggestions
    ) {
        static GpuResolution ok(String spec, String gpuModel, String migConfig, int memoryMiB) {
            return new GpuResolution(true, spec, gpuModel, migConfig, memoryMiB, "", "", List.of());
        }

        static GpuResolution fail(String message, String errorCode, List<String> suggestions) {
            return new GpuResolution(false, "", "", "", 0, errorCode, message, suggestions);
        }
    }
}
