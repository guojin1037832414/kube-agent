package com.atlas.tool.defaults;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认值注册中心 — 加载 defaults.yml 并提供查询。
 *
 * <p>启动时一次性加载，存储到内存索引，
 * 运行时通过 {@link #getDefaults} / {@link #apply} 使用。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Component
public class DefaultValueRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultValueRegistry.class);
    private static final String CONFIG_FILE = "defaults.yml";

    private final Map<String, IntentDefaults> registry = new LinkedHashMap<>();

    @PostConstruct
    public void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                log.warn("[DefaultValueRegistry] 找不到: {}", CONFIG_FILE);
                return;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> defaultsRaw =
                (Map<String, Map<String, Object>>) root.get("defaults");

            if (defaultsRaw == null) {
                log.warn("[DefaultValueRegistry] defaults.yml 缺少 defaults 节点");
                return;
            }

            for (Map.Entry<String, Map<String, Object>> entry : defaultsRaw.entrySet()) {
                String intentId = entry.getKey();
                Map<String, Object> params = entry.getValue();

                registry.put(intentId, new IntentDefaults(intentId, params));
                log.debug("[DefaultValueRegistry] 注册 {} 默认值: {} 个参数", intentId, params.size());
            }
            log.info("[DefaultValueRegistry] 加载 {} 个意图的默认值", registry.size());
        } catch (Exception e) {
            log.error("[DefaultValueRegistry] 加载失败", e);
        }
    }

    /**
     * 按 intentId 获取默认值定义。
     */
    public IntentDefaults getDefaults(String intentId) {
        return registry.get(intentId);
    }

    /**
     * 判断指定 intentId 是否注册了默认值。
     */
    public boolean hasDefaults(String intentId) {
        return registry.containsKey(intentId);
    }

    /**
     * 对一个已有参数 Map 进行默认值回填。
     *
     * <p>原则：已有值不覆盖，仅对 null / 缺失的 key 填充。</p>
     *
     * @param intentId  意图 ID
     * @param params    当前参数（可变 Map，被直接修改）
     * @return 填充后的 Map
     */
    public Map<String, Object> apply(String intentId, Map<String, Object> params) {
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        IntentDefaults defaults = registry.get(intentId);
        if (defaults == null || defaults.isEmpty()) {
            return params;
        }

        int filled = 0;
        for (Map.Entry<String, Object> entry : defaults.parameters().entrySet()) {
            String key = entry.getKey();
            Object defaultValue = entry.getValue();

            if (!params.containsKey(key) || params.get(key) == null) {
                params.put(key, defaultValue);
                filled++;
                log.debug("[DefaultValueRegistry] 回填 {}.{} = {}", intentId, key, defaultValue);
            }
        }

        if (filled > 0) {
            log.info("[DefaultValueRegistry] {} 回填 {} 个默认参数", intentId, filled);
        }
        return params;
    }

    /**
     * 返回所有已注册的 intentId。
     */
    public List<String> getAllIntentIds() {
        return List.copyOf(registry.keySet());
    }
}
