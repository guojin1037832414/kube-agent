# M4-PX.2 Plan-and-Execute 最小 POC 审计清单

> 日期：2026-05-24
> 范围：`PLAN actionType`、`plan_node`、`PlanEngine`、Reflection 最小自检、契约测试、文档同步。

## 1. Deliverable Inventory

| 路径 | 类型 | 状态 | 说明 |
|------|------|------|------|
| `src/main/java/com/atlas/brain/BrainDecision.java` | 决策模型 | ✅ PASS | 新增 `ActionType.PLAN`。 |
| `src/main/java/com/atlas/brain/AtlasBrain.java` | 决策入口 | ✅ PASS | 新增 PLAN prompt、`shouldUsePlan`、HITL > PLAN > ReAct 守卫。 |
| `src/main/java/com/atlas/graph/config/AtlasGraphConfig.java` | Graph 编排 | ✅ PASS | 新增 `plan_node`、State key、条件边和 END 边。 |
| `src/main/java/com/atlas/plan/` | Plan POC | ✅ PASS | 新增 PlanEngine/DTO/状态/Reflection 结果，全部中文注释。 |
| `src/test/java/com/atlas/contract/M42PlanExecuteSafetyContractTest.java` | 安全契约 | ✅ PASS | 锁定 plan_node 只规划、不执行、不写 HITL marker。 |
| `src/test/java/com/atlas/brain/ActionTypeTest.java` | 枚举契约 | ✅ PASS | 锁定 PLAN 枚举和值反序列化。 |
| `src/test/java/com/atlas/brain/AtlasBrainMockTest.java` | Brain 守卫 | ✅ PASS | 覆盖 PLAN 命中、高危 PLAN 仍 HITL、ReAct 不破坏。 |
| `src/test/java/com/atlas/graph/config/SupervisorGraphReactRoutingTest.java` | 路由契约 | ✅ PASS | 锁定 `PLAN -> plan_node`。 |
| `CHANGELOG.md` | 文档 | ✅ PASS | 新增 M4-PX.2 变更记录。 |
| `ROADMAP.md` | 文档 | ✅ PASS | 更新 M4-PX 当前状态和后续路线。 |
| `README.md` | 文档 | ✅ PASS | 更新 M4/M5 当前状态。 |
| `docs/REVIEW_LOG.md` | Review | ✅ PASS | 追加本阶段 Review、测试、风险和后续建议。 |

## 2. Verification

| 项目 | 命令 | 结果 |
|------|------|------|
| 定向测试 | `mvn -q -Dtest=ActionTypeTest,AtlasBrainMockTest,SupervisorGraphReactRoutingTest,M42PlanExecuteSafetyContractTest,M513HitlFailClosedContractTest,ToolRegistryPromptContractTest,ReActEventRiskMetadataTest test` | ✅ PASS |
| 编译 | `mvn -q -DskipTests compile` | ✅ PASS |
| 空白检查 | `git diff --check` | ✅ PASS |
| 全量测试 | `mvn -q test` | ✅ PASS |

## 3. Safety Review

- ✅ `plan_node` 不调用 Tool。
- ✅ `plan_node` 不访问 kube-manager。
- ✅ `plan_node` 不写 `tool_result`。
- ✅ `plan_node` 不创建/写入 `hitl_confirmation`。
- ✅ `/plan 删除...` 仍强制进入 `HITL_CONFIRM`。
- ✅ Reflection 只做结构自检，不自动重试、不自动执行。

## 4. Deferred

1. execute_node 未落地。
2. reflection_node 未落地。
3. 前端 plan timeline 未落地。
4. LLM Planner / ToolRegistry 风险元数据驱动计划未落地。

## 5. Verdict

✅ **PASS**：M4-PX.2 最小 POC 满足“可路由、可计划、可审计、不可绕过 HITL”的阶段目标。
