# ADR-009: StateGraph + ReactAgent 迁移完成

> **状态**: Accepted  
> **日期**: 2026-05-16  
> **提出者**: Atlas Team  
> **相关**: ADR-008（选用 Spring AI Alibaba StateGraph）、MIGRATION_StateGraph_ReactAgent.md（已归档）

---

## 背景

在 ADR-008 中，我们决定启动从手动 if-else 路由到 Spring AI Alibaba StateGraph 的迁移。迁移方案记录在 `MIGRATION_StateGraph_ReactAgent.md`（约 943 行），涵盖：

- StateGraph 节点设计（supervisor → 条件边 → Worker Agent）
- ReactAgent 编排在 Spring 容器中的 Bean 化
- ToolCallback 桥接（Spring AI `@Tool` → StateGraph `ToolCallback`）
- Token 透传在异步线程中的生命周期管理

---

## 决策

迁移已在以下 commit 中**完整执行**：

| Commit | 说明 |
|--------|------|
| `baca47d` | Phase 1: supervisorGraph + StateGraph 条件路由集成 |
| `a0df443` | Review #20 + 条件路由调试 |
| `05375a1` | Phase 2-A: Tool 执行下沉到 Graph 节点，StateGraph 各负其责 |
| `4efb8e8` | Phase 2-A Fix: Graph 异步线程 Token 透传修复 |
| `edb29b2` | Phase 2-B: supervisorGraph delegate 接入 ReactAgent 子图分发 |

架构已完整实现并编译通过。

---

## 架构快照

```
START → supervisor_agent(AtlasBrain) → [conditional] → query/deploy/rbac/diag/storage/network_agent
                                                          ↓
                                              [any_agent] → merge_result → emit_sse → END
```

- `supervisor` 节点：由 `AtlasBrain.cogitate()` 产出 `BrainDecision`，从中提取 `target` 作为 routing key
- 6 个 Worker Agent：每个都是独立的 `ReactAgent`，绑定对应 Agent 的 Tool subset
- `merge_result` 节点：合并 Worker 执行结果
- `emit_sse` 节点：`StreamingEmitter` 向客户端推送 SSE 事件

---

## 影响

- 所有请求默认走 `supervisorGraph`，`chatStream()` 接口已切换
- 原手动 if-else 路由代码（`AtlasOrchestrator` 旧版）已删除
- `MIGRATION_StateGraph_ReactAgent.md` 方案已完成使命，已归档至 `docs/archive/`

---

## 替代方案

- ~~保持手动 if-else 路由~~（已废弃，无法承载 109 个 Tool 的复杂度）
- ~~LangChain4j~~（在 ADR-008 中评估，Spring AI Alibaba 作为 Spring 生态原生方案胜出）

---

## 相关文件

- `src/main/java/com/atlas/graph/config/AtlasGraphConfig.java`（306 行）
- `src/main/java/com/atlas/orchestrator/AtlasOrchestrator.java`（supervisorGraph 调用）
- `docs/archive/ARCHIVED_20260518_StateGraph_ReactAgent_Migration.md`（原方案归档）
