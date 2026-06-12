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
 * <p>中文说明：IntentsLoader 是意图目录的启动期只读加载器，把 classpath 中的
 * {@code intents.yml} 解析成按 intentId 和 agent 分组的内存索引。IntentRouter、RuleMatcher、
 * EmbeddingMatcher 和 L3IntentClassifier 都从这里读取同一份目录，避免各层维护不同口径。</p>
 *
 * <p>安全边界：intents.yml 是路由目录，不是 Tool 权限表、不是 MCP manifest、
 * 不是 kube-manager API 白名单，也不是 Phase 2 域能力开关。配置里的 agent、level、
 * keywords、patterns、examples 只能影响候选路由；不能注册新 Tool、打开 runtime handoff、
 * 绑定 token/orgId、放行写操作或绕过 SafeToolExecutor。</p>
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

                // 中文说明：这里保存的是路由目录事实，不是执行能力注册；
                // 真正可执行 Tool 仍由 ToolRegistry/AtlasToolMapping/SafeToolExecutor 决定。
                intentMap.put(id, def);
                agentMap.computeIfAbsent(def.agent(), k -> new ArrayList<>()).add(def);
                allIntentIds.add(id);
            }
            log.info("[IntentsLoader] 加载 {} 个意图, {} 个Agent", intentMap.size(), agentMap.keySet());
        } catch (Exception e) {
            throw new IllegalStateException("intents.yml 加载失败", e);
        }
    }

    /**
     * 按 intentId 读取只读目录定义。
     *
     * <p>安全边界：返回非空只说明目录中存在该意图，不代表当前用户可执行对应 Tool。</p>
     */
    public IntentDefinition getIntent(String intentId) { return intentMap.get(intentId); }

    /**
     * 返回所有意图定义，供多层 matcher 构建候选池。
     */
    public Collection<IntentDefinition> getAllIntents() { return intentMap.values(); }

    /**
     * 按 agent 分组读取目录定义。
     *
     * <p>中文说明：agent 分组用于路由和提示，不是运行时 A2A handoff 或专业 Agent 授权。</p>
     */
    public List<IntentDefinition> getIntentsByAgent(String agent) { return agentMap.getOrDefault(agent, List.of()); }

    /**
     * 返回所有 intentId，主要供校验、测试和 L3 白名单使用。
     */
    public List<String> getAllIntentIds() { return allIntentIds; }

    @SuppressWarnings("unchecked")
    private List<String> toStrList(Object obj) {
        if (obj == null) return List.of();
        return ((List<Object>) obj).stream().map(Object::toString).toList();
    }
    private String toString(Object obj) { return obj == null ? "" : obj.toString(); }
}
