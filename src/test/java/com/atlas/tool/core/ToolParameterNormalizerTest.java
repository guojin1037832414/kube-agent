package com.atlas.tool.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolParameterNormalizer 参数归一化契约测试。
 *
 * <p>这些测试用于锁定 LLM 参数别名兼容规则，尤其防止 {@code name} 这类高歧义字段
 * 被全局误映射到错误资源类型。后续引入 Tool Schema 时，也必须保持这些兼容性契约。</p>
 */
class ToolParameterNormalizerTest {

    private final ToolParameterNormalizer normalizer = new ToolParameterNormalizer();

    @Test
    void normalize_shouldMapPodAliasesForPodTools() {
        Map<String, Object> result = normalizer.normalize("diagnose_pod", Map.of(
            "pod_name", "nginx-1",
            "name_space", "default"
        ));

        assertEquals("nginx-1", result.get("podName"));
        assertEquals("default", result.get("namespace"));
        assertEquals("nginx-1", result.get("pod_name"), "原始 alias 字段应保留，便于审计和兼容");
    }

    @Test
    void normalize_shouldMapNameByToolType() {
        Map<String, Object> podResult = normalizer.normalize("diagnose_pod", Map.of("name", "nginx-1"));
        Map<String, Object> nodeResult = normalizer.normalize("node_query", Map.of("name", "node-a"));
        Map<String, Object> deploymentResult = normalizer.normalize("deployment_query", Map.of("name", "deploy-a"));

        assertEquals("nginx-1", podResult.get("podName"));
        assertFalse(podResult.containsKey("nodeName"));

        assertEquals("node-a", nodeResult.get("nodeName"));
        assertFalse(nodeResult.containsKey("podName"));

        assertEquals("deploy-a", deploymentResult.get("deploymentName"));
        assertFalse(deploymentResult.containsKey("podName"));
    }

    @Test
    void normalize_shouldNotMapNameForUnknownTool() {
        Map<String, Object> result = normalizer.normalize("unknown_tool", Map.of("name", "ambiguous-name"));

        assertEquals("ambiguous-name", result.get("name"));
        assertFalse(result.containsKey("podName"));
        assertFalse(result.containsKey("nodeName"));
        assertFalse(result.containsKey("deploymentName"));
    }

    @Test
    void normalize_shouldNotOverrideCanonicalValue() {
        Map<String, Object> result = normalizer.normalize("diagnose_pod", Map.of(
            "podName", "canonical-pod",
            "pod_name", "alias-pod",
            "name", "name-pod"
        ));

        assertEquals("canonical-pod", result.get("podName"));
    }

    @Test
    void normalize_shouldPreserveFalsyAndUnknownValues() {
        Map<String, Object> params = new HashMap<>();
        params.put("enabled", false);
        params.put("replicas", 0);
        params.put("emptyText", "");
        params.put("vendor_custom_flag", "on");

        Map<String, Object> result = normalizer.normalize("diagnose_pod", params);

        assertEquals(false, result.get("enabled"));
        assertEquals(0, result.get("replicas"));
        assertEquals("", result.get("emptyText"));
        assertEquals("on", result.get("vendor_custom_flag"));
    }

    @Test
    void normalize_shouldReturnNewMapWithoutMutatingInput() {
        Map<String, Object> input = new HashMap<>();
        input.put("pod_name", "nginx-1");

        Map<String, Object> result = normalizer.normalize("diagnose_pod", input);

        assertNotSame(input, result);
        assertFalse(input.containsKey("podName"), "归一化器不应修改调用方传入的原始 Map");
        assertEquals("nginx-1", result.get("podName"));
    }
}
