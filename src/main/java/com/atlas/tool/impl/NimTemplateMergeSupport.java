package com.atlas.tool.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NIM 模板合并与 DeploymentDTO 草案预览辅助。
 *
 * <p>本类刻意只生成 {@code safeToPost=false} 的离线草案，不调用 kube-manager 写接口。
 * 它把 mature 前端 {@code mergeTemplate + formatApplication} 的确定性单位换算沉淀为可测试代码，
 * 方便未来正式 {@code nim_create} 在 HITL、license、系统组织限制和审计全部补齐后复用。</p>
 */
final class NimTemplateMergeSupport {

    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_PENDING_GPU_MAP = "PENDING_GPU_MAP";
    private static final String STATUS_NOT_REQUIRED = "NOT_REQUIRED";
    private static final String STATUS_NOT_FOUND = "GPU_SPEC_NOT_FOUND";
    private static final String STATUS_INVALID = "GPU_MAP_ENTRY_INVALID";

    private NimTemplateMergeSupport() {
    }

    static Map<String, Object> buildDeploymentBodyPreview(Map<String, Object> params,
                                                          String image,
                                                          Map<String, Object> selectedTemplate) {
        // 公开 Tool 入参来自 LLM/用户，不能信任其中的 gpuMap。未来如需解析 GPU，
        // 必须由受控编排先调用已审计的 GPU 读取能力，再走四参数重载显式传入。
        return buildDeploymentBodyPreview(params, image, selectedTemplate, Map.of());
    }

    static Map<String, Object> buildDeploymentBodyPreview(Map<String, Object> params,
                                                          String image,
                                                          Map<String, Object> selectedTemplate,
                                                          Map<String, Object> gpuMap) {
        Map<String, Object> mergedDraft = mergeTemplateForPreview(params, image, selectedTemplate);
        FormatResult formatResult = formatApplicationForPreview(mergedDraft, gpuMap);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("safeToPost", false);
        preview.put("previewOnly", true);
        preview.put("bodyComplete", formatResult.complete());
        preview.put("bodyDraft", formatResult.body());
        preview.put("uiMergedDraft", mergedDraft);
        preview.put("gpuResolution", formatResult.gpuResolution());
        preview.put("protectedFields", List.of("name", "displayName", "image"));
        preview.put("agentSafetyDeviation", List.of(
            "mature 前端只显式保护 displayName；Agent 预览额外保护 name/image，避免模板覆盖用户确认的服务名或已选镜像",
            "本预览不会生成随机服务名，不会替用户决定费用、GPU、网络暴露或自动扩缩容"
        ));
        preview.put("requiredBeforeCreate", buildRequiredBeforeCreate(formatResult));
        return preview;
    }

    private static Map<String, Object> mergeTemplateForPreview(Map<String, Object> params,
                                                               String image,
                                                               Map<String, Object> selectedTemplate) {
        Map<String, Object> temp = newTemp(params, image);
        Map<String, Object> template = normalizeTemplateForUi(selectedTemplate);

        String protectedName = text(temp.get("name"));
        String protectedDisplayName = text(temp.get("displayName"));
        String protectedImage = text(temp.get("image"));

        // 对齐前端 Object.assign(temp, template)，再恢复 Agent 必须由用户/HITL 确认的字段。
        temp.putAll(template);
        temp.put("name", protectedName);
        temp.put("displayName", protectedDisplayName);
        temp.put("image", protectedImage);
        return temp;
    }

    private static Map<String, Object> newTemp(Map<String, Object> params, String image) {
        String serviceName = text(params.get("serviceName"));

        Map<String, Object> temp = new LinkedHashMap<>();
        temp.put("uid", "");
        temp.put("name", serviceName);
        temp.put("namespace", "");
        temp.put("displayName", serviceName);
        temp.put("image", image);
        temp.put("templateId", "");
        temp.put("gpuSpec", "");
        temp.put("cpuLimits", 2);
        temp.put("cpuRequests", 2);
        temp.put("memLimits", 8);
        temp.put("memRequests", 8);
        temp.put("gpuPercentLimits", 0);
        temp.put("gpuMemLimits", 0);
        temp.put("replicas", 1);
        temp.put("acceptQueue", false);
        temp.put("enableWebSsh", true);
        temp.put("webSshPassword", "");
        temp.put("mainEntrance", "");
        temp.put("webPort", 0);
        temp.put("tcpPort", 0);
        temp.put("exposeType", "");
        temp.put("commands", "");
        temp.put("env", "");
        temp.put("runAsRoot", false);
        temp.put("bandwidth", 0);
        temp.put("ingressBandwidth", "");
        temp.put("egressBandwidth", "");
        temp.put("model", "");
        return temp;
    }

    private static Map<String, Object> normalizeTemplateForUi(Map<String, Object> selectedTemplate) {
        Map<String, Object> template = copyMap(selectedTemplate);
        putDivided(template, "cpuLimits", 1000.0, 3);
        putDivided(template, "memLimits", 1024.0, 3);
        putDivided(template, "gpuPercentLimits", 100.0, 2);
        putDivided(template, "gpuMemLimits", 1024.0, 3);

        String gpuModel = text(template.get("gpuModel"));
        String migConfig = text(template.get("migConfig"));
        if (hasText(gpuModel)) {
            template.put("gpuSpec", hasText(migConfig) ? gpuModel + "#" + migConfig : gpuModel);
            template.remove("gpuModel");
        }
        if (template.containsKey("id") && !hasText(template.get("templateId"))) {
            template.put("templateId", template.get("id"));
        }
        if (template.containsKey("gpuPercentLimits")) {
            template.put("gpuPercentLimits", numberOrDefault(template.get("gpuPercentLimits"), 0));
        }
        return template;
    }

    private static FormatResult formatApplicationForPreview(Map<String, Object> mergedDraft,
                                                            Map<String, Object> gpuMap) {
        Map<String, Object> body = copyMap(mergedDraft);
        int cpuMilli = (int) Math.round(numberOrDefault(mergedDraft.get("cpuLimits"), 2) * 1000);
        int memMiB = (int) Math.round(numberOrDefault(mergedDraft.get("memLimits"), 8) * 1024);
        body.put("cpuLimits", cpuMilli);
        body.put("memLimits", memMiB);
        body.put("cpuRequests", cpuMilli);
        body.put("memRequests", memMiB);

        double gpuInput = numberOrDefault(mergedDraft.get("gpuPercentLimits"), 0);
        double normalizedGpu = gpuInput > 1 ? Math.floor(gpuInput) : gpuInput;
        int gpuPercent = (int) Math.round(normalizedGpu * 100);
        int gpuMemMiB = gpuPercent >= 100
            ? 0
            : (int) Math.round(numberOrDefault(mergedDraft.get("gpuMemLimits"), 0) * 1024);
        body.put("gpuPercentLimits", gpuPercent);
        body.put("gpuMemLimits", gpuMemMiB);

        String gpuSpec = firstText(mergedDraft.get("gpuSpec"), mergedDraft.get("gpuModel"));
        if (hasText(gpuSpec)) {
            body.put("gpuSpec", gpuSpec);
        }
        GpuPreviewResolution gpuResolution = resolveGpuForPreview(gpuSpec, gpuMap);
        if (STATUS_RESOLVED.equals(gpuResolution.status())) {
            body.put("gpuModel", gpuResolution.gpuModel());
            body.put("migConfig", gpuResolution.migConfig());
        } else if (!hasText(gpuSpec)) {
            body.put("gpuModel", "");
            body.put("migConfig", "");
            body.put("gpuPercentLimits", 0);
            body.put("gpuMemLimits", 0);
        } else {
            body.remove("gpuModel");
            body.remove("migConfig");
        }

        int bandwidth = positiveIntOrZero(mergedDraft.get("bandwidth"));
        if (bandwidth > 0) {
            body.put("ingressBandwidth", bandwidth + "M");
            body.put("egressBandwidth", bandwidth + "M");
        }

        boolean autoScaleSwitch = booleanValue(mergedDraft.get("autoScaleSwitch"));
        Object autoScaleConfig = body.get("autoScaleConfig");
        if (autoScaleSwitch) {
            Map<String, Object> config = autoScaleConfig instanceof Map<?, ?> map ? copyMap(map) : new LinkedHashMap<>();
            if (!hasText(gpuSpec)) {
                config.put("targetGpuAverageUtilization", 0);
                config.put("targetGpuMemAverageUtilization", 0);
            }
            body.put("autoScaleConfig", config);
        } else {
            body.put("autoScaleConfig", null);
        }
        body.put("enableSecondNetwork", true);

        boolean gpuComplete = STATUS_RESOLVED.equals(gpuResolution.status())
            || STATUS_NOT_REQUIRED.equals(gpuResolution.status());
        boolean identityComplete = hasText(body.get("displayName")) && hasText(body.get("image"));
        boolean complete = gpuComplete && identityComplete;
        return new FormatResult(body, gpuResolution.toMap(), complete);
    }

    private static GpuPreviewResolution resolveGpuForPreview(String gpuSpec, Map<String, Object> gpuMap) {
        if (!hasText(gpuSpec)) {
            return new GpuPreviewResolution(STATUS_NOT_REQUIRED, "", "", "", List.of());
        }
        if (gpuMap == null || gpuMap.isEmpty()) {
            return new GpuPreviewResolution(STATUS_PENDING_GPU_MAP, gpuSpec, "", "",
                List.of("需要通过已审计 GPU map 确认 gpuSpec 对应的 gpuModel/migConfig 后才能创建"));
        }

        Object rawEntry = gpuMap.get(gpuSpec);
        if (!(rawEntry instanceof Map<?, ?>)) {
            rawEntry = findGpuEntryBySpec(gpuMap, gpuSpec);
        }
        if (!(rawEntry instanceof Map<?, ?> entry)) {
            return new GpuPreviewResolution(STATUS_NOT_FOUND, gpuSpec, "", "",
                List.of("gpuMap 中未找到模板要求的 gpuSpec: " + gpuSpec));
        }

        String gpuModel = firstText(entry.get("gpuModel"), gpuSpec.split("#", 2)[0]);
        String migConfig = text(entry.get("migConfig"));
        if (!hasText(gpuModel)) {
            return new GpuPreviewResolution(STATUS_INVALID, gpuSpec, "", "",
                List.of("gpuMap 条目缺少 gpuModel，不能生成可创建的 DeploymentDTO"));
        }
        return new GpuPreviewResolution(STATUS_RESOLVED, gpuSpec, gpuModel, migConfig, List.of());
    }

    private static Object findGpuEntryBySpec(Map<String, Object> gpuMap, String gpuSpec) {
        for (Object value : gpuMap.values()) {
            if (value instanceof Map<?, ?> entry && gpuSpec.equals(text(entry.get("spec")))) {
                return entry;
            }
        }
        return null;
    }

    private static List<String> buildRequiredBeforeCreate(FormatResult formatResult) {
        List<String> required = new ArrayList<>();
        required.add("人工确认服务 name/displayName、image、templateId、GPU 规格、网络暴露、费用/配额影响");
        required.add("后端可信校验 NVAIE license 有效性，并拒绝系统组织/SYS_ADMIN 直接创建");
        required.add("生成 HITL 确认卡片并写入审计日志，再由受控写 Tool 调用 POST /api/{orgId}/deployment");
        if (!hasText(formatResult.body().get("displayName"))) {
            required.add("补齐并人工确认 NIM 服务展示名称 displayName");
        }
        String gpuStatus = text(formatResult.gpuResolution().get("status"));
        if (!List.of(STATUS_RESOLVED, STATUS_NOT_REQUIRED).contains(gpuStatus)) {
            required.add("补齐 GPU map 解析，当前 bodyDraft 不是完整可提交 DeploymentDTO");
        }
        return required;
    }

    private static void putDivided(Map<String, Object> map, String key, double divisor, int scale) {
        if (!map.containsKey(key) || map.get(key) == null) {
            return;
        }
        double value = numberOrDefault(map.get(key), 0) / divisor;
        map.put(key, round(value, scale));
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private static int positiveIntOrZero(Object value) {
        if (value instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        if (value != null) {
            try {
                return Math.max(0, Integer.parseInt(value.toString().trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }

    private static double numberOrDefault(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String firstText(Object primary, Object fallback) {
        String primaryText = text(primary);
        return hasText(primaryText) ? primaryText : text(fallback);
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private record FormatResult(Map<String, Object> body, Map<String, Object> gpuResolution, boolean complete) {
    }

    private record GpuPreviewResolution(String status,
                                        String gpuSpec,
                                        String gpuModel,
                                        String migConfig,
                                        List<String> suggestions) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", status);
            map.put("gpuSpec", gpuSpec);
            map.put("gpuModel", gpuModel);
            map.put("migConfig", migConfig);
            map.put("suggestions", suggestions);
            return map;
        }
    }
}
