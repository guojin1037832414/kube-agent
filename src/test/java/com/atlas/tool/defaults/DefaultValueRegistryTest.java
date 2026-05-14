package com.atlas.tool.defaults;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * DefaultValueRegistry 单元测试。
 */
class DefaultValueRegistryTest {

    @Test
    void testApply_FillMissingOnly() {
        DefaultValueRegistry registry = new DefaultValueRegistry();
        // manually populate for unit test
        try {
            java.lang.reflect.Field f = DefaultValueRegistry.class.getDeclaredField("registry");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, IntentDefaults> map = (java.util.Map<String, IntentDefaults>) f.get(registry);
            map.put("deploy_create_instance", new IntentDefaults("deploy_create_instance",
                java.util.Map.of(
                    "cpuLimits", 2,
                    "memLimits", 8,
                    "replicas", 1,
                    "enableWebSsh", true,
                    "autoScaleSwitch", false
                )));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("name", "my-app");
        params.put("image", "nginx:latest");
        params.put("cpuLimits", 4); // 用户明确指定了 4核，不应被覆盖

        Map<String, Object> result = registry.apply("deploy_create_instance", params);

        assertEquals("my-app", result.get("name"));
        assertEquals(4, result.get("cpuLimits"));       // 用户值保留
        assertEquals(8, result.get("memLimits"));        // 默认值回填
        assertEquals(1, result.get("replicas"));         // 默认值回填
        assertEquals(true, result.get("enableWebSsh"));  // 默认值回填
        assertEquals(false, result.get("autoScaleSwitch")); // 默认值回填
    }

    @Test
    void testApply_NoDefaultsFound() {
        DefaultValueRegistry registry = new DefaultValueRegistry();
        Map<String, Object> params = Map.of("key", "val");
        Map<String, Object> result = registry.apply("unknown_intent", new HashMap<>(params));
        assertEquals("val", result.get("key"));
        assertEquals(1, result.size());
    }
}
