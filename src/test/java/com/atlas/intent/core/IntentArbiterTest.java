package com.atlas.intent.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntentArbiter 纯单元测试 — 验证多层意图冲突仲裁规则链。
 *
 * <p>仲裁规则链（优先级由高到低）：</p>
 * <ol>
 *   <li>同 intentId 合并，取 max score + 3% crossBoost</li>
 *   <li>同层决胜：同 matchedLevel 取高者</li>
 *   <li>L2 Exact 护城河：L2 exact ≥ 0.95 时，除非对方也是 L2 且 ≥ 0.93，否则 L2 胜</li>
 *   <li>极高语义压倒：对方 L2 ≥ 0.95，但己方 L1/L3 ≥ 0.96 且 p0/p1 高优意图，己方胜</li>
 *   <li>意图优先级兜底：高优意图（数值小）允许落后 ≤ 0.05</li>
 *   <li>显著差距：Δ ≥ 0.15，高分直接胜出</li>
 *   <li>模糊区 fallback：层级优先级 [L2, L3, L1, L4]</li>
 * </ol>
 *
 * @version 3.1.0-M2
 */
class IntentArbiterTest {

    // ═══════════════════════════════════════════════════════════
    // 边界条件
    // ═══════════════════════════════════════════════════════════

    @Test
    void testNullInput_returnsNull() {
        assertNull(IntentArbiter.arbitrate(null), "null输入应返回null");
    }

    @Test
    void testEmptyList_returnsNull() {
        assertNull(IntentArbiter.arbitrate(List.of()), "空列表应返回null");
    }

    @Test
    void testSingleResult_returnsIt() {
        IntentResult r = new IntentResult("node_query", "查询节点", 0.85, "L1", "query", "p0", "节点状态");
        IntentResult result = IntentArbiter.arbitrate(List.of(r));
        assertNotNull(result);
        assertEquals("node_query", result.intentId());
        assertEquals(0.85, result.confidence(), 0.001);
    }

    @Test
    void testBelowThreshold_filtered() {
        IntentResult low = new IntentResult("unknown", "未知意图", 0.50, "L4", "query", "p3", "随便说点什么");
        assertNull(IntentArbiter.arbitrate(List.of(low)), "低于0.70阈值的结果应被过滤");
    }

    // ═══════════════════════════════════════════════════════════
    // Rule A: 同层决胜（同 matchedLevel 取高分者）
    // ═══════════════════════════════════════════════════════════

    @Test
    void testSameLayer_higherScoreWins() {
        IntentResult a = new IntentResult("node_query", "节点查询", 0.95, "L2", "query", "p0", "查询节点状态");
        IntentResult b = new IntentResult("pod_status", "Pod状态", 0.90, "L2", "query", "p0", "查看pod状态");
        IntentResult winner = IntentArbiter.arbitrate(List.of(a, b));
        assertEquals("node_query", winner.intentId(), "同层L2，分数高的胜出");
    }

    @Test
    void testSameLayer_L3higherScoreWins() {
        IntentResult a = new IntentResult("deploy_create_instance", "创建实例", 0.88, "L3", "deploy", "p1", "创建服务");
        IntentResult b = new IntentResult("deploy_scale", "扩缩容", 0.82, "L3", "deploy", "p2", "调整副本数");
        IntentResult winner = IntentArbiter.arbitrate(List.of(a, b));
        assertEquals("deploy_create_instance", winner.intentId(), "同层L3，分数高的胜出");
    }

    // ═══════════════════════════════════════════════════════════
    // Rule B: L2 护城河（L2 ≥ 0.95 且对方不满足条件 → L2 胜）
    // ═══════════════════════════════════════════════════════════

    @Test
    void testL2Moat_L2HighBeatsL1() {
        IntentResult l2 = new IntentResult("node_query", "节点查询", 0.96, "L2", "query", "p0", "查询所有节点");
        IntentResult l1 = new IntentResult("pod_list", "Pod列表", 0.94, "L1", "query", "p3", "查看pod列表");
        IntentResult winner = IntentArbiter.arbitrate(List.of(l2, l1));
        assertEquals("node_query", winner.intentId(), "L2护城河：L2≥0.95，对方非L2且<0.93，L2胜");
        assertEquals("L2", winner.matchedLevel());
    }

    @Test
    void testL2Moat_L2HighBeatsL3() {
        IntentResult l2 = new IntentResult("pod_status", "Pod状态", 0.97, "L2", "query", "p0", "查询pod运行状态");
        IntentResult l3 = new IntentResult("pod_logs", "Pod日志", 0.92, "L3", "query", "p2", "查看pod日志");
        IntentResult winner = IntentArbiter.arbitrate(List.of(l2, l3));
        assertEquals("pod_status", winner.intentId(), "L2护城河：L2≥0.95，对方是L3，L2胜");
    }

    @Test
    void testL2Moat_bothL2_sameLevelWinsByScore() {
        IntentResult l2a = new IntentResult("node_query", "节点查询", 0.96, "L2", "query", "p2", "查询节点状态");
        IntentResult l2b = new IntentResult("node_list", "节点列表", 0.94, "L2", "query", "p0", "查看节点列表");
        IntentResult winner = IntentArbiter.arbitrate(List.of(l2a, l2b));
        assertEquals("node_query", winner.intentId(), "双方都是L2且≥0.93，按同层决胜取分高者");
    }

    // ═══════════════════════════════════════════════════════════
    // Rule C: 极高语义压倒（高优意图 p0/p1 ≥ 0.96 可压倒 L2）
    // ═══════════════════════════════════════════════════════════

    @Test
    void testSemanticOverrule_p0HighIntentBeatsL2() {
        IntentResult l2 = new IntentResult("pod_list", "Pod列表", 0.96, "L2", "query", "p3", "查看所有pod");
        IntentResult l1 = new IntentResult("deploy_delete", "删除部署", 0.97, "L1", "deploy", "p0", "删除服务实例");
        IntentResult winner = IntentArbiter.arbitrate(List.of(l1, l2));
        assertEquals("deploy_delete", winner.intentId(), "p0高优意图≥0.96，可压倒L2护城河");
    }

    @Test
    void testSemanticOverrule_p1HighIntentBeatsL2() {
        IntentResult l2 = new IntentResult("storage_list", "存储列表", 0.96, "L2", "query", "p3", "查看存储卷");
        IntentResult l3 = new IntentResult("storage_create", "创建存储", 0.97, "L3", "storage", "p1", "创建新存储");
        IntentResult winner = IntentArbiter.arbitrate(List.of(l3, l2));
        assertEquals("storage_create", winner.intentId(), "p1高优意图≥0.96，可压倒L2护城河");
    }

    // ═══════════════════════════════════════════════════════════
    // Rule D: 意图优先级兜底（高优意图落后≤0.05仍可胜）
    // ═══════════════════════════════════════════════════════════

    @Test
    void testPriorityFallback_p0CanLoseSlightly() {
        IntentResult p0 = new IntentResult("deploy_delete", "删除部署", 0.91, "L1", "deploy", "p0", "删除实例");
        IntentResult p2 = new IntentResult("deploy_scale", "扩缩容", 0.90, "L3", "deploy", "p2", "调整副本");
        IntentResult winner = IntentArbiter.arbitrate(List.of(p0, p2));
        assertEquals("deploy_delete", winner.intentId(), "p0高优意图分数略高于低优L3，意图优先级兜底胜出");
    }

    @Test
    void testPriorityFallback_p0LosesTooMuch() {
        IntentResult p0 = new IntentResult("deploy_delete", "删除部署", 0.80, "L1", "deploy", "p0", "删除实例");
        IntentResult p2 = new IntentResult("deploy_scale", "扩缩容", 0.90, "L3", "deploy", "p2", "调整副本");
        IntentResult winner = IntentArbiter.arbitrate(List.of(p0, p2));
        assertEquals("deploy_scale", winner.intentId(), "p0高优意图落后0.10（>0.05），优先级兜底不生效");
    }

    // ═══════════════════════════════════════════════════════════
    // Rule E: 显著差距（Δ ≥ 0.15，高分者直接胜出）
    // ═══════════════════════════════════════════════════════════

    @Test
    void testSignificantGap_wins() {
        IntentResult a = new IntentResult("node_query", "查询节点", 0.92, "L1", "query", "p0", "节点");
        IntentResult b = new IntentResult("pod_status", "Pod状态", 0.70, "L3", "query", "p0", "pod状态");
        IntentResult winner = IntentArbiter.arbitrate(List.of(a, b));
        assertEquals("node_query", winner.intentId(), "差距0.22≥0.15，显著差距规则生效");
    }

    @Test
    void testNoSignificantGap_fallsThrough() {
        IntentResult l3 = new IntentResult("node_query", "节点查询", 0.80, "L3", "query", "p0", "节点");
        IntentResult l1 = new IntentResult("pod_status", "Pod状态", 0.75, "L1", "query", "p0", "pod");
        IntentResult winner = IntentArbiter.arbitrate(List.of(l3, l1));
        assertEquals("node_query", winner.intentId(), "差距0.05<0.15，模糊区 fallback → L3 > L1 层级优先级");
    }

    // ═══════════════════════════════════════════════════════════
    // Rule F: 模糊区 fallback — 层级优先级 [L2, L3, L1, L4]
    // ═══════════════════════════════════════════════════════════

    @Test
    void testLayerPriority_L3overL1() {
        IntentResult l3 = new IntentResult("node_query", "节点查询", 0.75, "L3", "query", "p0", "节点");
        IntentResult l1 = new IntentResult("pod_status", "Pod状态", 0.75, "L1", "query", "p0", "pod");
        IntentResult winner = IntentArbiter.arbitrate(List.of(l3, l1));
        assertEquals("node_query", winner.intentId(), "同分 → L3 > L1 层级优先级");
    }

    @Test
    void testLayerPriority_L2overL3() {
        IntentResult l2 = new IntentResult("node_query", "节点查询", 0.75, "L2", "query", "p0", "节点");
        IntentResult l3 = new IntentResult("pod_status", "Pod状态", 0.78, "L3", "query", "p0", "pod");
        IntentResult winner = IntentArbiter.arbitrate(List.of(l2, l3));
        assertEquals("node_query", winner.intentId(), "L2层级最高，即使在其他规则都不触发时胜出");
    }

    @Test
    void testLayerPriority_L1overL4() {
        IntentResult l1 = new IntentResult("node_query", "节点查询", 0.72, "L1", "query", "p0", "节点");
        IntentResult l4 = new IntentResult("pod_status", "Pod状态", 0.73, "L4", "query", "p0", "pod");
        IntentResult winner = IntentArbiter.arbitrate(List.of(l1, l4));
        assertEquals("node_query", winner.intentId(), "L1 > L4 层级优先级");
    }

    // ═══════════════════════════════════════════════════════════
    // 同 intentId 合并
    // ═══════════════════════════════════════════════════════════

    @Test
    void testMergeSameIntentId_takeMaxPlusCrossBoost() {
        IntentResult r1 = new IntentResult("node_query", "查询节点", 0.80, "L1", "query", "p0", "节点");
        IntentResult r2 = new IntentResult("node_query", "查询节点", 0.75, "L3", "query", "p0", "节点");
        IntentResult merged = IntentArbiter.arbitrate(List.of(r1, r2));
        assertEquals("node_query", merged.intentId());
        // max(0.80, 0.75) + 0.03 = 0.83
        assertEquals(0.83, merged.confidence(), 0.001, "同intentId合并应取最高分+crossBoost");
        // 层级取更高者 L1 > L3（按 LAYER_PRIORITY，L3 > L1）→ merged时取pickHigherLayer
        assertEquals("L3", merged.matchedLevel(), "同intentId合并取更高层级");
    }

    @Test
    void testMergeSameIntentId_crossBoostCappedAt1() {
        IntentResult r1 = new IntentResult("node_query", "查询节点", 0.99, "L1", "query", "p0", "节点");
        IntentResult r2 = new IntentResult("node_query", "查询节点", 0.98, "L3", "query", "p0", "节点");
        IntentResult merged = IntentArbiter.arbitrate(List.of(r1, r2));
        assertEquals(1.0, merged.confidence(), 0.001, "crossBoost不能超过1.0上限");
    }
}
