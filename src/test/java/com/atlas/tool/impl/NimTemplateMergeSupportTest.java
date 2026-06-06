package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.Map.entry;

/**
 * NIM 模板合并预览测试。
 *
 * <p>目标是锁定 mature 前端 mergeTemplate/formatApplication 的确定性换算规则，同时明确
 * Agent 侧只生成 safeToPost=false 草案，不能把预览体直接用于创建。</p>
 */
class NimTemplateMergeSupportTest {

    @Test
    void preview_shouldProtectUserConfirmedFieldsAndMarkGpuResolutionPendingWithoutGpuMap() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of("serviceName", "llama-service"),
            "nvcr.io/nim/llama:1.0.0",
            Map.ofEntries(
                entry("id", 42),
                entry("name", "template-name-should-not-win"),
                entry("displayName", "template-display-should-not-win"),
                entry("image", "template-image-should-not-win"),
                entry("cpuLimits", 4000),
                entry("memLimits", 16384),
                entry("gpuPercentLimits", 100),
                entry("gpuMemLimits", 8192),
                entry("gpuModel", "A100"),
                entry("migConfig", "all-2g.10gb"),
                entry("bandwidth", 20)
            )
        );

        assertEquals(false, preview.get("safeToPost"));
        assertEquals(false, preview.get("bodyComplete"));
        assertEquals(List.of("name", "displayName", "image"), preview.get("protectedFields"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) preview.get("bodyDraft");
        assertEquals("llama-service", body.get("name"));
        assertEquals("llama-service", body.get("displayName"));
        assertEquals("nvcr.io/nim/llama:1.0.0", body.get("image"));
        assertEquals(42, body.get("templateId"));
        assertEquals(4000, body.get("cpuLimits"));
        assertEquals(4000, body.get("cpuRequests"));
        assertEquals(16384, body.get("memLimits"));
        assertEquals(16384, body.get("memRequests"));
        assertEquals(100, body.get("gpuPercentLimits"));
        assertEquals(0, body.get("gpuMemLimits"));
        assertEquals("A100#all-2g.10gb", body.get("gpuSpec"));
        assertFalse(body.containsKey("gpuModel"));
        assertFalse(body.containsKey("migConfig"));
        assertEquals("20M", body.get("ingressBandwidth"));
        assertEquals("20M", body.get("egressBandwidth"));
        assertEquals(true, body.get("enableSecondNetwork"));

        @SuppressWarnings("unchecked")
        Map<String, Object> gpuResolution = (Map<String, Object>) preview.get("gpuResolution");
        assertEquals("PENDING_GPU_MAP", gpuResolution.get("status"));
    }

    @Test
    void preview_shouldResolveGpuMapAndNormalizeAutoscaleForGpuDeployment() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of("serviceName", "nim-mistral"),
            "nvcr.io/nim/mistral:2.0.0",
            Map.of(
                "id", 77,
                "cpuLimits", 2500,
                "memLimits", 12288,
                "gpuPercentLimits", 50,
                "gpuMemLimits", 10240,
                "gpuModel", "L40S",
                "autoScaleSwitch", true,
                "autoScaleConfig", Map.of(
                    "targetCpuAverageUtilization", 60,
                    "targetGpuAverageUtilization", 70,
                    "targetGpuMemAverageUtilization", 80
                )
            ),
            Map.of("L40S", Map.of(
                "gpuModel", "L40S",
                "migConfig", "",
                "memory", 49152
            ))
        );

        assertEquals(false, preview.get("safeToPost"));
        assertEquals(true, preview.get("bodyComplete"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) preview.get("bodyDraft");
        assertEquals(2500, body.get("cpuLimits"));
        assertEquals(12288, body.get("memLimits"));
        assertEquals(50, body.get("gpuPercentLimits"));
        assertEquals(10240, body.get("gpuMemLimits"));
        assertEquals("L40S", body.get("gpuModel"));
        assertEquals("", body.get("migConfig"));

        @SuppressWarnings("unchecked")
        Map<String, Object> autoScaleConfig = (Map<String, Object>) body.get("autoScaleConfig");
        assertEquals(70, autoScaleConfig.get("targetGpuAverageUtilization"));
        assertEquals(80, autoScaleConfig.get("targetGpuMemAverageUtilization"));

        @SuppressWarnings("unchecked")
        Map<String, Object> gpuResolution = (Map<String, Object>) preview.get("gpuResolution");
        assertEquals("RESOLVED", gpuResolution.get("status"));
        assertTrue(((List<?>) preview.get("requiredBeforeCreate")).stream()
            .anyMatch(item -> item.toString().contains("HITL")));
    }

    @Test
    void preview_shouldClearGpuFieldsForCpuOnlyTemplateAndKeepSafeToPostFalse() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of("serviceName", "nim-cpu"),
            "nvcr.io/nim/cpu:1.0",
            Map.of(
                "id", 88,
                "cpuLimits", 1000,
                "memLimits", 2048,
                "gpuPercentLimits", 0,
                "gpuMemLimits", 0,
                "autoScaleSwitch", true,
                "autoScaleConfig", Map.of(
                    "targetGpuAverageUtilization", 90,
                    "targetGpuMemAverageUtilization", 91
                )
            )
        );

        assertEquals(false, preview.get("safeToPost"));
        assertEquals(true, preview.get("bodyComplete"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) preview.get("bodyDraft");
        assertEquals("", body.get("gpuModel"));
        assertEquals("", body.get("migConfig"));
        assertEquals(0, body.get("gpuPercentLimits"));
        assertEquals(0, body.get("gpuMemLimits"));

        @SuppressWarnings("unchecked")
        Map<String, Object> autoScaleConfig = (Map<String, Object>) body.get("autoScaleConfig");
        assertEquals(0, autoScaleConfig.get("targetGpuAverageUtilization"));
        assertEquals(0, autoScaleConfig.get("targetGpuMemAverageUtilization"));
    }

    @Test
    void preview_shouldRequireConfirmedDisplayNameBeforeBodyIsComplete() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of(),
            "nvcr.io/nim/no-name:1.0",
            Map.of(
                "id", 99,
                "cpuLimits", 1000,
                "memLimits", 2048,
                "gpuPercentLimits", 0
            )
        );

        assertEquals(false, preview.get("safeToPost"));
        assertEquals(false, preview.get("bodyComplete"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) preview.get("bodyDraft");
        assertEquals("", body.get("displayName"));
        assertTrue(((List<?>) preview.get("requiredBeforeCreate")).stream()
            .anyMatch(item -> item.toString().contains("displayName")));
    }

    @Test
    void publicPreviewOverload_shouldIgnoreCallerSuppliedGpuMap() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of(
                "serviceName", "nim-forged-gpu",
                "gpuMap", Map.of("A100", Map.of("gpuModel", "A100", "migConfig", ""))
            ),
            "nvcr.io/nim/forged:1.0",
            Map.of(
                "id", 100,
                "cpuLimits", 2000,
                "memLimits", 4096,
                "gpuPercentLimits", 100,
                "gpuModel", "A100"
            )
        );

        assertEquals(false, preview.get("bodyComplete"));
        @SuppressWarnings("unchecked")
        Map<String, Object> gpuResolution = (Map<String, Object>) preview.get("gpuResolution");
        assertEquals("PENDING_GPU_MAP", gpuResolution.get("status"));
    }
}
