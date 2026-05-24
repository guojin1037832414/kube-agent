# kube-agent M4-PX.3 审计清单

> 生成时间: 2026-05-25
> 审计人: Hermes
> 审计范围: M4-PX.3 SafeToolExecutor + execute_node fail-closed 最小安全闭环

## 一、工程目录审计

### 1. 生产源码
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/main/java/com/atlas/tool/execution/SafeToolExecutor.java` | M4-PX.3-A | ✅ PASS | 统一安全 Tool 执行器，集中 Tool 查找、权限校验、参数过滤、HITL、ThreadLocal 绑定/恢复、结果归一化。 |
| `src/main/java/com/atlas/tool/execution/SafeToolExecutionRequest.java` | M4-PX.3-A | ✅ PASS | 安全执行请求 record，承载可信上下文、业务参数、confirmation marker 与 source。 |
| `src/main/java/com/atlas/tool/execution/SafeToolExecutionResult.java` | M4-PX.3-A | ✅ PASS | 安全执行结果 record，保持 Graph `tool_call` 返回结构兼容。 |
| `src/main/java/com/atlas/tool/execution/SafeToolExecutionSource.java` | M4-PX.3-A | ✅ PASS | 执行来源枚举，仅用于审计和策略扩展，不作为绕过 HITL 的依据。 |
| `src/main/java/com/atlas/graph/config/AtlasGraphConfig.java` | M4-PX.3-A/B | ✅ PASS | `tool_call` 改为委托 `SafeToolExecutor`；新增 `execute_node`；PLAN 路由改为 `plan_node -> execute_node -> END`。 |

### 2. 测试代码
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/test/java/com/atlas/tool/execution/SafeToolExecutorTest.java` | M4-PX.3-C | ✅ PASS | 覆盖 READ 成功、伪造上下文字段过滤、高危无确认拦截、ThreadLocal 快照恢复。 |
| `src/test/java/com/atlas/contract/M42PlanExecuteSafetyContractTest.java` | M4-PX.3-C | ✅ PASS | 锁定 PLAN 路由进入 execute_node、execute_node 默认 fail-closed、不直接执行 Tool。 |
| `src/test/java/com/atlas/contract/M513HitlFailClosedContractTest.java` | M4-PX.3-C | ✅ PASS | 契约从 Graph 内联 HitlGuard 调整为 Graph 读取 confirmation、SafeToolExecutor 执行前调用 HitlGuard。 |

### 3. 文档
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `CHANGELOG.md` | M4-PX.3-D | ✅ PASS | 新增 M4-PX.3 变更、验证、安全边界与 Deferred。 |
| `ROADMAP.md` | M4-PX.3-D | ✅ PASS | 当前基线更新为 M4-PX.3，M4 完成度与 P1.5 路线同步。 |
| `README.md` | M4-PX.3-D | ✅ PASS | 当前状态与安全合规段同步 SafeToolExecutor / execute_node fail-closed。 |
| `docs/REVIEW_LOG.md` | M4-PX.3-D | ✅ PASS | 记录背景、专家 Review、测试、代码 Review、根因和后续建议。 |
| `docs/M4_PX_3_AUDIT_CHECKLIST_20260525.md` | M4-PX.3-D | ✅ PASS | 本审计清单。 |

## 二、功能验证
| 功能 | 测试方式 | 结果 |
|------|----------|------|
| Graph tool_call 复用 SafeToolExecutor | `M513HitlFailClosedContractTest` + 定向测试 | ✅ PASS |
| READ Tool 正常执行且返回 Graph 兼容结构 | `SafeToolExecutorTest` | ✅ PASS |
| LLM/Plan 伪造上下文字段过滤 | `SafeToolExecutorTest` | ✅ PASS |
| 高危 Tool 无服务端确认 fail-closed | `SafeToolExecutorTest` | ✅ PASS |
| ThreadLocal 执行后恢复快照 | `SafeToolExecutorTest` | ✅ PASS |
| execute_node 默认不执行 | `M42PlanExecuteSafetyContractTest` | ✅ PASS |
| PLAN 路由进入 execute_node | `M42PlanExecuteSafetyContractTest` | ✅ PASS |

## 三、测试与质量门禁
| 门禁 | 命令/方式 | 结果 |
|------|-----------|------|
| 定向测试 | `mvn -q -Dtest=SafeToolExecutorTest,M42PlanExecuteSafetyContractTest,M513HitlFailClosedContractTest,ActionTypeTest,AtlasBrainMockTest,SupervisorGraphReactRoutingTest test` | ✅ PASS |
| SafeToolExecutor 单测复测 | `mvn -q -Dtest=SafeToolExecutorTest test` | ✅ PASS |
| 全量测试 | `mvn -q test` | ✅ PASS（228 tests） |
| 空白检查 | `git diff --check` | ✅ PASS |
| 新增行敏感扫描 | Python added-lines scan | ✅ `ADDED_LINE_SECRET_SUSPECTS 0` |
| 三路 Review | delegate_task | ✅ 安全架构 PASS / 测试契约 PASS / 工程落地 PASS |

## 四、缺口分析
| 缺口 | 阶段 | 优先级 | 影响 | 建议 |
|------|------|--------|------|------|
| 全局 `tool.execute(...)` 入口扫描契约 | 后续 M4-PX.4/M5 | 🔴 HIGH | ReAct/ToolCallback 等历史入口可能与 SafeToolExecutor 语义分叉 | 新增源码级 contract，逐步收口所有真实执行入口。 |
| 高危 + 服务端可信确认 marker 成功路径测试 | 后续 M4-PX.4 | 🟡 MED | 当前重点覆盖无确认阻断，确认放行路径可进一步增强 | 补 mock confirmation 放行单测，不触碰真实 kube-manager。 |
| Tool 异常后 ThreadLocal 恢复测试 | 后续 M4-PX.4 | 🟡 MED | 异常路径污染线程池会影响后续请求 | 新增抛异常假 Tool，断言 finally 恢复旧 token/orgId。 |
| 嵌套参数递归脱敏/过滤 | 后续安全增强 | 🟡 MED | 当前保护字段过滤是浅层参数过滤 | 补递归 sanitization 工具与日志/HITL 展示一致性契约。 |
| execute_node READ-only 单步执行 | 后续 M4-PX.4 | 🟡 MED | 当前 execute_node 是安全占位，不执行真实计划步骤 | 仅在白名单 READ Tool、参数 schema、预算、审计、HITL resume 完成后开放。 |

## 五、审计结论

### ✅ PASS
- M4-PX.3-A SafeToolExecutor 抽取完成，既有 Graph `tool_call` 已复用统一安全执行层。
- M4-PX.3-B execute_node 已接入 Graph，但默认 fail-closed，不自动执行计划。
- M4-PX.3-C 定向测试、单测、全量测试、静态质量门禁均通过。
- M4-PX.3-D 文档、Review、审计清单已完成。

### ⚠️ 遗留项
- `execute_node` 尚未开放真实执行，后续必须按 READ-only 单步、安全白名单、参数 schema、审计与 HITL resume 分阶段推进。
- 历史执行入口仍需逐步统一收口，防止 SafeToolExecutor 与 ReAct/ToolCallback 行为分叉。

### ❌ 阻塞项
- 无。

## 六、下一步建议
1. M4-PX.4：新增全局执行入口源码契约，扫描并约束生产代码 `tool.execute(...)`。
2. M4-PX.4：补 SafeToolExecutor 异常路径与确认放行路径测试。
3. M4-PX.5：只开放 READ-only 单步 execute_node 实验执行，接入预算、审计、参数 schema 与 SSE timeline。
