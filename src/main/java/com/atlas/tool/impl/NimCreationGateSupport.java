package com.atlas.tool.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NIM 创建前门禁草案。
 *
 * <p>本类不做授权、不写审计、不调用 kube-manager。它只把 NIM 预检阶段已经掌握的事实
 * 转成结构化阻断原因和 HITL 确认卡片草案，让 Agent 能清楚解释“为什么现在还不能创建”。
 * 真正开放 {@code nim_create} 前，仍必须由后端可信执行链生成 {@code HitlConfirmation}
 * 并通过 {@code SafeToolExecutor + HitlGuard} 执行写操作。</p>
 */
final class NimCreationGateSupport {

    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_NOT_REQUIRED = "NOT_REQUIRED";

    private NimCreationGateSupport() {
    }

    static Map<String, Object> buildCreationGate(Map<String, Object> params,
                                                 String image,
                                                 Map<String, Object> selectedTemplate,
                                                 Map<String, Object> deploymentBodyPreview) {
        Map<String, Object> bodyDraft = objectMap(deploymentBodyPreview.get("bodyDraft"));
        Map<String, Object> gpuResolution = objectMap(deploymentBodyPreview.get("gpuResolution"));

        List<Map<String, Object>> blockers = new ArrayList<>();
        addStandingHoldBlockers(blockers);
        addDynamicBlockers(blockers, deploymentBodyPreview, bodyDraft, gpuResolution);

        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("gateState", "CLOSED");
        gate.put("allowedToCreateNow", false);
        gate.put("sideEffect", "NONE");
        gate.put("blockedBy", blockers);
        gate.put("ignoredCallerClaims", detectIgnoredCallerClaims(params));
        gate.put("requiredTrustedChecks", requiredTrustedChecks());
        gate.put("hitlCardDraft", buildHitlCardDraft(image, selectedTemplate, bodyDraft, gpuResolution));
        gate.put("futureWritePath", futureWritePath());
        gate.put("nextBestActions", nextBestActions(deploymentBodyPreview, bodyDraft, gpuResolution));
        return gate;
    }

    private static void addStandingHoldBlockers(List<Map<String, Object>> blockers) {
        blockers.add(blocker(
            "NIM_CREATE_TOOL_HOLD",
            "nim_create 当前仍是 PLACEHOLDER，未开放真实写操作编排。",
            "agent-safety"
        ));
        blockers.add(blocker(
            "NVAIE_LICENSE_NOT_VERIFIED",
            "尚未在 Agent 后端执行链中完成 NVAIE license 可信校验。",
            "backend-policy"
        ));
        blockers.add(blocker(
            "CALLER_ORG_POLICY_NOT_VERIFIED",
            "尚未在 Agent 执行链中可信确认调用者不是 SYS_ADMIN 且当前组织不是系统组织。",
            "rbac"
        ));
        blockers.add(blocker(
            "HITL_CONFIRMATION_NOT_ISSUED",
            "尚未生成服务端 HitlConfirmation marker；LLM/参数里的确认字段一律不可信。",
            "hitl"
        ));
        blockers.add(blocker(
            "AUDIT_AND_STATUS_FLOW_NOT_READY",
            "NIM 创建审计日志、创建后 Deployment/Service/NIM readiness 轮询尚未完成。",
            "observability"
        ));
    }

    private static void addDynamicBlockers(List<Map<String, Object>> blockers,
                                           Map<String, Object> deploymentBodyPreview,
                                           Map<String, Object> bodyDraft,
                                           Map<String, Object> gpuResolution) {
        if (!Boolean.TRUE.equals(deploymentBodyPreview.get("bodyComplete"))) {
            blockers.add(blocker(
                "DEPLOYMENT_BODY_PREVIEW_INCOMPLETE",
                "当前 DeploymentDTO 草案仍不完整，不能进入创建。",
                "dto-preview"
            ));
        }
        if (!Boolean.FALSE.equals(deploymentBodyPreview.get("safeToPost"))) {
            blockers.add(blocker(
                "PREVIEW_SAFE_FLAG_INVALID",
                "deploymentBodyPreview.safeToPost 必须保持 false，预览体不能直接 POST。",
                "dto-preview"
            ));
        }
        if (!hasText(bodyDraft.get("displayName"))) {
            blockers.add(blocker(
                "DISPLAY_NAME_REQUIRED",
                "缺少用户确认的 NIM 服务展示名称 displayName。",
                "user-input"
            ));
        }

        String gpuStatus = text(gpuResolution.get("status"));
        if (!List.of(STATUS_RESOLVED, STATUS_NOT_REQUIRED).contains(gpuStatus)) {
            blockers.add(blocker(
                "GPU_MAP_UNRESOLVED",
                "GPU 模板需要先通过已审计 GPU map 解析 gpuModel/migConfig。",
                "resource-resolution"
            ));
        }
    }

    private static List<Map<String, Object>> detectIgnoredCallerClaims(Map<String, Object> params) {
        List<String> riskyKeys = List.of(
            "approved",
            "confirmed",
            "hitlConfirmed",
            "safeToPost",
            "licenseValid",
            "isLicenseValid",
            "nvaieLicenseValid",
            "isSysOrg",
            "sysAdmin",
            "role",
            "userRole"
        );
        List<Map<String, Object>> ignored = new ArrayList<>();
        for (String key : riskyKeys) {
            if (params.containsKey(key)) {
                Map<String, Object> claim = new LinkedHashMap<>();
                claim.put("key", key);
                claim.put("ignored", true);
                claim.put("reason", "该字段来自 Tool 入参，不能作为 license、RBAC、HITL 或创建授权依据。");
                ignored.add(claim);
            }
        }
        return ignored;
    }

    private static List<String> requiredTrustedChecks() {
        return List.of(
            "后端可信读取并校验 NVAIE license 未过期",
            "后端可信确认当前组织不是系统组织，调用者不是 SYS_ADMIN 绕行创建",
            "通过已审计 GPU map 解析 gpuSpec，并确认组织资源/配额/费用影响",
            "生成 NIM 创建 HITL 卡片，人工确认 displayName、image、templateId、GPU、网络、费用/配额",
            "HITL confirm 接口生成服务端 HitlConfirmation marker，执行层只信任该 marker",
            "写入审计日志后，由已审计 nim_create 编排调用 POST /api/{orgId}/deployment",
            "创建后只轮询 Deployment/Service/NIM readiness，不生成、不保存、不展示真实 API Key"
        );
    }

    private static Map<String, Object> buildHitlCardDraft(String image,
                                                          Map<String, Object> selectedTemplate,
                                                          Map<String, Object> bodyDraft,
                                                          Map<String, Object> gpuResolution) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", "NIM_CREATE_CONFIRMATION_DRAFT");
        card.put("targetTool", "nim_create");
        card.put("targetIntent", "nim_create");
        card.put("operationType", "CREATE");
        card.put("requiresServerMarker", true);
        card.put("summary", "确认后才允许进入 NIM Deployment 创建；当前仅为卡片草案，不会执行。");
        card.put("fields", List.of(
            field("displayName", "服务展示名称", bodyDraft.get("displayName"), true, "user-confirmed"),
            field("image", "NIM 镜像", firstNonBlank(bodyDraft.get("image"), image), true, "repository-tag-template"),
            field("templateId", "模板 ID", firstNonBlank(bodyDraft.get("templateId"), selectedTemplate.get("id")), true, "template"),
            field("gpuSpec", "GPU 规格", bodyDraft.get("gpuSpec"), false, "template-gpu"),
            field("gpuResolution", "GPU 解析状态", gpuResolution.get("status"), true, "audited-gpu-map"),
            field("cpuLimits", "CPU 上限(m)", bodyDraft.get("cpuLimits"), true, "dto-preview"),
            field("memLimits", "内存上限(MiB)", bodyDraft.get("memLimits"), true, "dto-preview"),
            field("gpuPercentLimits", "GPU 百分比", bodyDraft.get("gpuPercentLimits"), false, "dto-preview"),
            field("gpuMemLimits", "GPU 显存(MiB)", bodyDraft.get("gpuMemLimits"), false, "dto-preview"),
            field("network", "网络/二层网络", networkSummary(bodyDraft), true, "dto-preview")
        ));
        card.put("editableFields", List.of(
            "displayName",
            "gpuSpec",
            "bandwidth",
            "autoScaleConfig",
            "networkExpose",
            "expectedCostAcknowledgement"
        ));
        card.put("confirmationChecklist", List.of(
            "我确认这是正确的 NIM image 和 templateType=NIM 模板",
            "我确认 GPU/CPU/内存/网络配置和组织配额、费用影响",
            "我确认不会让 Agent 生成、保存或展示真实 NGC/NIM API Key",
            "我确认创建后只进行 readiness 检查和状态回读"
        ));
        card.put("warnings", List.of(
            "当前卡片只是预览草案，不会创建服务。",
            "服务端确认 marker 必须由 HITLController 生成，不能来自 LLM 参数。",
            "预览体 safeToPost=false，禁止直接透传给 POST /deployment。"
        ));
        return card;
    }

    private static Map<String, Object> futureWritePath() {
        Map<String, Object> path = new LinkedHashMap<>();
        path.put("futureTool", "nim_create");
        path.put("currentStatus", "PLACEHOLDER_HOLD");
        path.put("backendEndpoint", "POST /api/{orgId}/deployment");
        path.put("directUseOfPreviewAllowed", false);
        path.put("fallbackTool", "deploy_create_instance");
        path.put("fallbackAllowedFromPreflight", false);
        return path;
    }

    private static List<String> nextBestActions(Map<String, Object> deploymentBodyPreview,
                                                Map<String, Object> bodyDraft,
                                                Map<String, Object> gpuResolution) {
        List<String> actions = new ArrayList<>();
        if (!hasText(bodyDraft.get("displayName"))) {
            actions.add("先让用户确认 NIM 服务展示名称 displayName。");
        }
        String gpuStatus = text(gpuResolution.get("status"));
        if (!List.of(STATUS_RESOLVED, STATUS_NOT_REQUIRED).contains(gpuStatus)) {
            actions.add("通过未来受控编排读取已审计 GPU map，解析 gpuSpec 到 gpuModel/migConfig。");
        }
        if (!Boolean.TRUE.equals(deploymentBodyPreview.get("bodyComplete"))) {
            actions.add("补齐 DeploymentDTO 草案后再次生成 HITL 卡片草案。");
        }
        actions.add("补 license、SYS_ADMIN/system org、审计日志和 readiness 轮询测试后，再考虑开放 nim_create。");
        return actions;
    }

    private static Map<String, Object> blocker(String code, String message, String source) {
        Map<String, Object> blocker = new LinkedHashMap<>();
        blocker.put("code", code);
        blocker.put("message", message);
        blocker.put("source", source);
        return blocker;
    }

    private static Map<String, Object> field(String key, String label, Object value, boolean required, String source) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", key);
        field.put("label", label);
        field.put("value", value == null ? "" : value);
        field.put("required", required);
        field.put("source", source);
        return field;
    }

    private static String networkSummary(Map<String, Object> bodyDraft) {
        String ingress = text(bodyDraft.get("ingressBandwidth"));
        String egress = text(bodyDraft.get("egressBandwidth"));
        String secondNetwork = Boolean.TRUE.equals(bodyDraft.get("enableSecondNetwork")) ? "enabled" : "disabled";
        if (!hasText(ingress) && !hasText(egress)) {
            return "secondNetwork=" + secondNetwork;
        }
        return "ingress=" + ingress + ", egress=" + egress + ", secondNetwork=" + secondNetwork;
    }

    private static Object firstNonBlank(Object primary, Object fallback) {
        return hasText(primary) ? primary : (fallback == null ? "" : fallback);
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }
}
