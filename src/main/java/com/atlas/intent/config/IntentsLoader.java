package com.atlas.intent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;

/**
 * {@code intents.yml} 配置加载器。
 *
 * <p>应用启动时一次性解析 {@code /intents.yml}，构建意图定义到内存索引。
 * 失败则启动失败，确保运行时配置永远可用。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Component
public class IntentsLoader {

    private static final Logger log = LoggerFactory.getLogger(IntentsLoader.class);
    private static final String CONFIG_FILE = "intents.yml";

    private final Map<String, IntentDefinition> intentMap = new LinkedHashMap<>();
    private final Map<String, List<IntentDefinition>> agentMap = new LinkedHashMap<>();
    private final List<String> allIntentIds = new ArrayList<>();

    @PostConstruct
    public void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in == null) throw new IllegalStateException("找不到: " + CONFIG_FILE);

            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> intentsRaw = (Map<String, Map<String, Object>>) root.get("intents");
            if (intentsRaw == null) throw new IllegalStateException("intents.yml 格式错误");

            for (Map.Entry<String, Map<String, Object>> entry : intentsRaw.entrySet()) {
                String id = entry.getKey();
                Map<String, Object> attrs = entry.getValue();

                IntentDefinition def = new IntentDefinition(
                    id,
                    toString(attrs.get("description")),
                    toString(attrs.get("agent")),
                    toString(attrs.get("level")),
                    toStrList(attrs.get("keywords")),
                    toStrList(attrs.get("patterns")),
                    toStrList(attrs.get("examples"))
                );

                intentMap.put(id, def);
                agentMap.computeIfAbsent(def.agent(), k -> new ArrayList<>()).add(def);
                allIntentIds.add(id);
            }
            log.info("[IntentsLoader] 加载 {} 个意图, {} 个Agent", intentMap.size(), agentMap.keySet());
        } catch (Exception e) {
            throw new IllegalStateException("intents.yml 加载失败", e);
        }
    }

    public IntentDefinition getIntent(String intentId) { return intentMap.get(intentId); }
    public Collection<IntentDefinition> getAllIntents() { return intentMap.values(); }
    public List<IntentDefinition> getIntentsByAgent(String agent) { return agentMap.getOrDefault(agent, List.of()); }
    public List<String> getAllIntentIds() { return allIntentIds; }

    @SuppressWarnings("unchecked")
    private List<String> toStrList(Object obj) {
        if (obj == null) return List.of();
        return ((List<Object>) obj).stream().map(Object::toString).toList();
    }
    private String toString(Object obj) { return obj == null ? "" : obj.toString(); }
}
