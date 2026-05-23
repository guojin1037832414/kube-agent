# kube-agent M5.13 HITL fail-closed 审计清单

> 生成时间: 2026-05-23 23:45 CST  
> 审计人: Hermes  
> 审计范围: M5.13 后端执行层 HITL 强拦截 + 前端确认流 fail-closed 同步治理  
> 审计原则: 不对真实 kube-manager 执行删除/修改/创建类破坏性测试，只做源码契约、编译构建、静态扫描与独立 Review。

## 一、工程目录审计

### 1. 后端 `src/main/java/com/atlas/hitl/`
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `HitlConfirmation.java` | M5.13 | ✅ PASS | 服务端可信确认 marker，仅 confirm token 校验成功后生成。 |
| `HitlGuard.java` | M5.13 | ✅ PASS | 统一执行层 fail-closed 守卫，基于 ToolMetadata 判断高风险。 |

### 2. 后端执行入口
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `AtlasGraphConfig.java` | M5.13 | ✅ PASS | supervisorGraph/tool_call 接入 HitlGuard；confirm resume 复用注入决策。 |
| `ReActEngine.java` | M5.13 | ✅ PASS | ReAct 直接 execute 前接入 HitlGuard；高风险无 state 直接拒绝。 |
| `AtlasOrchestrator.java` | M5.13 | ✅ PASS | legacy fallback execute 前接入 HitlGuard；普通新会话清空 marker。 |
| `graph/bridge/AtlasToolCallback.java` | M5.13 | ✅ PASS | Spring AI callback execute 前接入 HitlGuard。 |
| `tool/core/AtlasToolCallback.java` | M5.13 | ✅ PASS | core callback execute 前接入 HitlGuard。 |
| `ToolRegistry.java` | M5.13 | ✅ PASS | 提供元数据查询能力，供 guard 判断风险。 |

### 3. HITL 控制与状态恢复
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `HITLController.java` | M5.13 | ✅ PASS | 注入 supervisorGraph；confirm 注入 marker；clarify 清空 marker。 |
| `AtlasOrchestratorJsonTest.java` | M5.13 | ✅ PASS | 适配新增 HitlGuard 依赖。 |

### 4. 后端测试
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `M513HitlFailClosedContractTest.java` | M5.13 | ✅ PASS | 源码契约锁定多入口 guard、确认 marker、普通/clarify 清理与确认后复用决策。 |

### 5. 前端 kube-agent-vue
| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/composables/useChat.ts` | M5.13 | ✅ PASS | confirm/clarify SSE 解析增强。 |
| `src/views/ChatView.vue` | M5.13 | ✅ PASS | 缺 threadId/confirmToken fail-closed。 |
| `src/components/ChatBubble.vue` | M5.13 | ✅ PASS | 风险文案改为“执行前确认”。 |
| `scripts/m513-hitl-contract-test.cjs` | M5.13 | ✅ PASS | 前端确认流源码契约测试。 |

## 二、功能验证

| 功能 | 验证方式 | 结果 |
|------|----------|------|
| 高风险 Tool 未确认拦截 | 后端源码契约 + 编译 | ✅ PASS |
| 已确认目标 Tool 放行链路 | 源码契约 + 独立 Review | ✅ PASS |
| 多 execute 入口防绕过 | 源码契约 + 独立 Review | ✅ PASS |
| clarify/普通新会话清空旧 marker | 源码契约 + 独立 Review | ✅ PASS |
| 前端确认流缺字段 fail-closed | 前端契约脚本 | ✅ PASS |
| 前端构建 | `npm run build` | ✅ PASS |
| 敏感信息扫描 | added-lines scan | ✅ PASS，未新增凭据 |

## 三、质量门禁

| 门禁 | 命令/方式 | 结果 |
|------|-----------|------|
| 后端定向测试 | `mvn -q -Dtest=M513HitlFailClosedContractTest test` | ✅ PASS |
| 后端编译 | `mvn -q -DskipTests compile` | ✅ PASS |
| 前端契约 | `node scripts/m513-hitl-contract-test.cjs` | ✅ PASS |
| 前端构建 | `npm run build` | ✅ PASS |
| 后端 diff check | `git diff --check` | ✅ PASS |
| 前端 diff check | `git diff --check` | ✅ PASS |
| 独立 Review | 3 轮 delegate_task | ✅ 第 3 轮 PASS |

## 四、缺口分析

| 缺口 | 优先级 | 影响 | 建议 |
|------|--------|------|------|
| 缺运行时 mock 集成测试 | 🟡 MED | 当前主要依赖源码契约，运行态覆盖不足 | 后续补 `HITL_CONFIRM -> confirm -> tool_call` mock 测试。 |
| 两个同名 `AtlasToolCallback` | 🟡 MED | 后续维护容易误改 | 合并或重命名，保留单一 callback 实现。 |
| HitlConfirmation 未强校验 threadId | 🟢 LOW | 当前 marker 来源受控，但边界可更严 | 后续把 threadId 纳入 guard verify 参数。 |
| 剩余 Tool 元数据未全量迁移 | 🟡 MED | metadata 缺失会 fail-closed，安全但可能影响可用性 | 继续分批迁移 HTTP/风险注解。 |

## 五、审计结论

### ✅ PASS
M5.13 已完成前后端同步交付：后端执行层 fail-closed HITL 强拦截已接入所有已知直接执行入口；前端确认流缺字段 fail-closed；定向测试、构建、静态扫描与三轮独立 Review 均通过。

### ⚠️ 遗留项
不执行真实删除/修改/创建类 E2E；这是用户明确要求下的安全测试策略。后续如需更强保证，建议使用 mock kube-manager 或测试沙箱环境。

## 六、后续建议

1. 下一阶段补 HITL 运行时 mock 集成测试。
2. 合并/重命名两个 `AtlasToolCallback` 类。
3. 将 threadId 纳入 `HitlConfirmation` 校验。
4. 继续推进剩余 Tool 元数据迁移与风险覆盖率统计。
