package com.atlas.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReActMemory 纯单元测试 — 验证记忆管理、重复检测、历史格式化等功能。
 *
 * <p>不依赖 Spring 上下文，仅用真实 ObjectMapper。</p>
 *
 * @version 3.1.0-M3.2
 */
class ReActMemoryTest {

    private ObjectMapper objectMapper;
    private ReActMemory memory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        memory = new ReActMemory(objectMapper);
    }

    // ═══════════════════════════════════════════════════════════
    // TC-MEM-01: 基本步骤添加与读取
    // ═══════════════════════════════════════════════════════════

    @Test
    void testAddStep_and_steps() {
        memory.addStep("我想查询节点", "node_query",
            Map.of("clusterId", "c1"),
            "{\"success\":true}", true, 120);

        List<ReActMemory.Step> steps = memory.steps();
        assertEquals(1, steps.size());

        ReActMemory.Step s = steps.get(0);
        assertEquals(1, s.step());
        assertEquals("我想查询节点", s.thought());
        assertEquals("node_query", s.toolName());
        assertTrue(s.success());
        assertEquals(120, s.executionTimeMs());
    }

    // ═══════════════════════════════════════════════════════════
    // TC-MEM-02: visitedActionKeys 收集与重复检测
    // ═══════════════════════════════════════════════════════════

    @Test
    void testVisitedActionKeys_and_isDuplicate() {
        memory.addStep("查询A", "tool_a", Map.of("x", 1), "ok", true, 50);
        memory.addStep("查询B", "tool_b", Map.of("y", 2), "ok", true, 60);

        Set<String> visited = memory.visitedActionKeys();
        assertEquals(2, visited.size());

        // Final Answer 步骤不应进入 visited
        memory.addStep("总结", null, null, "最终答案", true, 0);
        assertEquals(2, memory.visitedActionKeys().size());

        // 重复检测
        assertTrue(memory.isDuplicateAction("tool_a", Map.of("x", 1)),
            "相同 tool+params 应判定为重复");
        assertFalse(memory.isDuplicateAction("tool_a", Map.of("x", 2)),
            "不同 params 不应判定为重复");
        assertFalse(memory.isDuplicateAction("tool_c", Map.of("x", 1)),
            "不同 tool 不应判定为重复");
    }

    // ═══════════════════════════════════════════════════════════
    // TC-MEM-03: Observation 历史格式化（未截断）
    // ═══════════════════════════════════════════════════════════

    @Test
    void testToObservationHistory_noTruncation() {
        memory.addStep("第一步", "t1", Map.of(), "结果1", true, 100);
        memory.addStep("第二步", null, null, "最终结果", true, 0);

        String history = memory.toObservationHistory(10000);
        assertTrue(history.contains("=== 历史执行记录 ==="));
        assertTrue(history.contains("【第 1 轮】"));
        assertTrue(history.contains("【第 2 轮】"));
        assertTrue(history.contains("Thought: 第一步"));
        assertTrue(history.contains("Observation: 结果1"));
        assertTrue(history.contains("Observation: 最终结果"));
    }

    // ═══════════════════════════════════════════════════════════
    // TC-MEM-04: Observation 历史截断
    // ═══════════════════════════════════════════════════════════

    @Test
    void testToObservationHistory_truncation() {
        String longObs = "a".repeat(5000);
        memory.addStep("查询", "tool", Map.of(), longObs, true, 100);

        String history = memory.toObservationHistory(200);
        assertTrue(history.contains("... [历史记录过长，已截断"),
            "超长历史应包含截断标记");
        assertTrue(history.length() <= 200,
            "截断后长度不应超过 maxChars");
    }

    // ═══════════════════════════════════════════════════════════
    // TC-MEM-05: formatSummary 生成摘要
    // ═══════════════════════════════════════════════════════════

    @Test
    void testFormatSummary() {
        memory.addStep("诊断", "diagnose_pod",
            Map.of("podName", "nginx-1"),
            "Pod is running normally", true, 150);
        memory.addStep("回答", null, null, "一切正常", true, 0);

        String summary = memory.formatSummary();
        assertTrue(summary.contains("ReAct 执行摘要"));
        assertTrue(summary.contains("diagnose_pod"));
        assertTrue(summary.contains("一切正常"));
    }

    // ═══════════════════════════════════════════════════════════
    // TC-MEM-06: 步骤序号递增
    // ═══════════════════════════════════════════════════════════

    @Test
    void testStepNumberIncrement() {
        memory.addStep("t1", "tool", Map.of(), "o1", true, 10);
        memory.addStep("t2", "tool", Map.of(), "o2", true, 10);
        memory.addStep("t3", "tool", Map.of(), "o3", true, 10);

        List<ReActMemory.Step> steps = memory.steps();
        assertEquals(1, steps.get(0).step());
        assertEquals(2, steps.get(1).step());
        assertEquals(3, steps.get(2).step());
    }

    // ═══════════════════════════════════════════════════════════
    // TC-MEM-07: steps() 返回不可变视图
    // ═══════════════════════════════════════════════════════════

    @Test
    void testStepsImmutable() {
        memory.addStep("t1", "tool", Map.of(), "o1", true, 10);
        List<ReActMemory.Step> steps = memory.steps();
        assertThrows(UnsupportedOperationException.class, () -> steps.add(null),
            "steps() 应返回不可变列表");
    }

    // ═══════════════════════════════════════════════════════════
    // TC-MEM-08: visitedActionKeys() 返回不可变视图
    // ═══════════════════════════════════════════════════════════

    @Test
    void testVisitedKeysImmutable() {
        memory.addStep("t1", "tool", Map.of(), "o1", true, 10);
        Set<String> visited = memory.visitedActionKeys();
        assertThrows(UnsupportedOperationException.class, () -> visited.add("x"),
            "visitedActionKeys() 应返回不可变集合");
    }
}
