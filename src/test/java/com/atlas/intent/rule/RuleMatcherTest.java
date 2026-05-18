package com.atlas.intent.rule;

import com.atlas.intent.config.IntentDefinition;
import com.atlas.intent.config.IntentsLoader;
import com.atlas.intent.core.IntentResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * RuleMatcher 集成测试 — 验证 L2 精确匹配和 L4 模糊匹配规则。
 *
 * <p>由于 RuleMatcher 依赖 {@link IntentsLoader} 获取意图定义，测试中通过
 * {@code @MockBean} stub 返回预定义的静态意图列表。</p>
 *
 * @version 3.1.0-M2
 */
@SpringBootTest
@ActiveProfiles("test")
class RuleMatcherTest {

    @Autowired
    private RuleMatcher ruleMatcher;

    @MockBean
    private IntentsLoader intentsLoader;

    /**
     * 构建 stub 意图列表
     */
    private void stubIntents() {
        List<IntentDefinition> defs = new ArrayList<>();

        // 节点查询意图（L2精确匹配关键词）
        defs.add(new IntentDefinition(
            "node_query",
            "查询节点状态和资源",
            "query",
            "p0",
            List.of("节点", "node"),
            List.of("查看.*节点.*状态", "查看.*node.*status"),
            List.of("node_status", "node_resources")
        ));

        // Pod 状态查询（L2精确匹配关键词）
        defs.add(new IntentDefinition(
            "pod_status",
            "查询Pod运行状态",
            "query",
            "p0",
            List.of("pod", "状态"),
            List.of("查看.*pod.*状态"),
            List.of("pod_status", "pod_health")
        ));

        // 删除部署（高危意图）
        defs.add(new IntentDefinition(
            "deploy_delete",
            "删除训练实例或部署",
            "deploy",
            "p0",
            List.of("删除", "训练实例"),
            List.of("删除.*实例", "删除.*部署", "delete.*"),
            List.of("delete_instance", "deploy_delete")
        ));

        // 创建存储
        defs.add(new IntentDefinition(
            "storage_create",
            "创建新的存储卷或数据集",
            "storage",
            "p1",
            List.of("创建", "存储", "数据集"),
            List.of(),
            List.of("create_storage", "storage_create")
        ));

        when(intentsLoader.getAllIntents()).thenReturn(defs);
    }

    // ═══════════════════════════════════════════════════════════
    // L2 精确匹配
    // ═══════════════════════════════════════════════════════════

    @Test
    void testExactMatch_keywordsAllMatch() {
        stubIntents();
        IntentResult result = ruleMatcher.exactMatch("查看所有节点状态");

        assertNotNull(result, "关键词全包含应命中");
        assertEquals("node_query", result.intentId());
        assertEquals(1.0, result.confidence(), 0.001, "L2精确匹配分数应为1.0");
        assertEquals("L2", result.matchedLevel());
    }

    @Test
    void testExactMatch_multipleKeywordsMatch() {
        stubIntents();
        IntentResult result = ruleMatcher.exactMatch("查看pod状态信息");

        assertNotNull(result, "pod+状态两个关键词都包含应命中");
        assertEquals("pod_status", result.intentId());
    }

    @Test
    void testExactMatch_regexMatch() {
        stubIntents();
        IntentResult result = ruleMatcher.exactMatch("查看节点运行状态");

        assertNotNull(result, "正则匹配应命中");
        assertEquals("node_query", result.intentId(), "正则 '查看.*节点.*状态' 应匹配");
    }

    @Test
    void testExactMatch_partialKeywords_noMatch() {
        stubIntents();
        IntentResult result = ruleMatcher.exactMatch("查看集群信息");

        assertNull(result, "关键词不全包含且未命中正则应返回null");
    }

    @Test
    void testExactMatch_singleKeywordIntent() {
        stubIntents();
        // "pod_status" 只需要 "pod" 和 "状态" 两个关键词
        IntentResult result = ruleMatcher.exactMatch("查看所有pod状态");

        assertNotNull(result, "pod+状态 两个关键词都包含应命中");
        assertEquals("pod_status", result.intentId());
    }

    @Test
    void testExactMatch_multipleKeywordsBothPresent() {
        stubIntents();
        // "node_query" 需同时包含 "节点" 和 "node"
        IntentResult result = ruleMatcher.exactMatch("查看节点和node的状态");

        assertNotNull(result, "同时包含 '节点' 和 'node' 应命中 node_query");
        assertEquals("node_query", result.intentId());
    }

    // ═══════════════════════════════════════════════════════════
    // L4 模糊匹配
    // ═══════════════════════════════════════════════════════════

    @Test
    void testFuzzyMatch_partialKeywords() {
        stubIntents();
        IntentResult result = ruleMatcher.fuzzyMatch("帮我删除某个部署");

        assertNotNull(result, "部分关键词（删除）应触发模糊匹配");
        assertTrue(result.confidence() < 1.0, "模糊匹配分数应 < 1.0");
        assertTrue(result.confidence() > 0, "模糊匹配应有正分数");
    }

    @Test
    void testFuzzyMatch_noKeywords_noMatch() {
        stubIntents();
        IntentResult result = ruleMatcher.fuzzyMatch("今天天气怎么样");

        assertNull(result, "无相关关键词应返回null");
    }

    @Test
    void testFuzzyMatch_returnsBestScore() {
        stubIntents();
        IntentResult result = ruleMatcher.fuzzyMatch("创建新存储卷");

        assertNotNull(result);
        assertEquals("storage_create", result.intentId(),
            "创建+存储两个关键词应命中 storage_create");
    }
}
