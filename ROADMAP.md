# Atlas Kube-Agent 开发路线图

> **项目**: kube-agent — K8s 训练平台 AI Agent 入口
> **目标**: 打造顶级 Agent 系统（K8s 运维只是第一个练兵场）
> **当前基线**: M5.20 安全底座已完成；M4-PX.2 Plan-and-Execute 最小 POC 已落地
> **当前 commit**: `bdf579e` 后继续推进 M4-PX.2，待提交
> **版本**: Atlas v3.1 / ReAct + PLAN + Tool 风险治理 + MCP 安全 Manifest 阶段
> **最后更新**: 2026-05-24

---

## 当前总进度

截至 2026-05-20，kube-agent 已完成从单步 Tool 调用到手写 ReAct 多步诊断链路的关键升级。ReAct MVP 已进入 live Graph 路径：

```text
AtlasBrain -> DELEGATE_REACT -> react_node -> ReActEngine.runWithEvents()
AtlasBrain -> PLAN -> plan_node -> PlanEngine.plan()
```

当前系统已具备：

- 109 个 Tool 自动注册与真实 kube-manager API 调用。
- L1/L2/L3/L4 意图分级路由与降级机制。
- AtlasBrain 决策、StateGraph 编排、SSE 流式返回。
- token + orgId 登录后缓存、ThreadLocal 双透传、异步上下文包装。
- 手写 ReAct 核心循环、事件化输出、目标资源未找到早停、Graph/Orchestrator 接入。
- M4-PX.2 Plan-and-Execute 最小 POC：PLAN actionType、plan_node、PlanEngine、单次 Reflection 自检。
- Tool Schema 基础设施：`ToolParameterSpec`、`ToolInputSchemaBuilder`、schema-first `ToolParameterNormalizer`、ReAct Prompt 参数契约展示。
- 首批参数契约扩展：`diagnose_pod`、`log_query`、`deployment_detail`、`node_detail`。

当前重点已从“ReAct 能否跑通”转向：

1. ReAct 是否稳定。
2. Tool 参数是否契约化。
3. Prompt 规模是否可控。
4. 查询/诊断链路是否能被稳定 E2E 验证。

粗略阶段完成度：

| 阶段 | 完成度 | 说明 |
|---|---:|---|
| M0 地基 | 100% | v2/v3 基线、SSE、Tool 层已归档 |
| M1 智能引擎 | 100% | L1-L4、AtlasBrain、StateGraph、基础 HITL 后端能力已完成 |
| M2 查询全覆盖与质量加固 | ~75% | 109 Tool 与 orgId 链路完成，测试/参数契约仍需继续补齐 |
| M3 写操作 + HITL 安全治理 | ~80% | HITLGuard fail-closed、Tool 风险元数据、高风险确认门已完成；前端完整交互仍可增强 |
| M4 ReAct / Plan-and-Execute | ~55% | ReAct MVP + 指标接入完成；M4-PX.2 已落地 PLAN/plan_node/PlanEngine 最小 POC，完整 execute/reflect 循环待后续 |
| M5 Memory / MCP / Observability | ✅ 最小闭环完成 | M5.18~M5.20 完成敏感 READ、高风险 mutation、MCP 安全 Manifest、最近 10 次摘要 Memory、Micrometer/Actuator 指标 |

---

## 已完成里程碑

| 里程碑 | Commit 范围 | 交付 | 状态 |
|--------|-------------|------|------|
| M0 地基 | `a6f0203` ~ `a0df443` | v2.x 基线：DomainPlugin、SSE、ChatMemory、权限网关、环境配置分离 | ✅ 已归档 |
| M1.0 意图 L1-L3/L4 | `baca47d` ~ `05375a1` | 规则短路、Embedding、LLM 分类、模糊兜底、分数仲裁 | ✅ 完成 |
| M1.1 StateGraph | `baca47d` ~ `edb29b2` | AtlasBrain 决策器、StateGraph supervisor、Tool 桥接、Token 透传 | ✅ 完成 |
| M1.5 HITL SSE 后端基础 | `61cab8f` | 高危操作确认、幂等 token、Caffeine TTL、Graph resume、SSE HITL 事件基础 | ✅ 后端基础完成，完整安全治理待 M3/M5 |
| M2 主体 Tool 覆盖 | 多批次提交 | 前端 9 大模块 109 个 Tool 注册，真实 API 覆盖，默认参数与 orgId 链路修复 | ✅ 主体完成，质量加固继续 |
| M3.2 ReAct MVP | `9cde237` ~ `e977b03` | ReActEngine、ReActMemory、ReActPromptBuilder、Graph 接入、SSE 事件化、E2E 稳定性修复 | ✅ MVP 完成 |
| M4.1 Tool Schema 基础 | `386ea9c` ~ `c296a3c` | ToolParameterSpec、inputSchema、schema-first normalizer、ReAct Prompt 工具参数契约、首批 4 个 Tool schema | 🟡 进行中 |
| M5.20 安全/Memory/MCP/Observability 最小闭环 | `bdf579e` | `SENSITIVE_READ`、高风险 mutation HITL、MCP 安全 Manifest、最近摘要 Memory、Micrometer/Actuator 指标 | ✅ 最小闭环完成 |
| M4-PX.2 Plan-and-Execute 最小 POC | `bdf579e` 后 | PLAN actionType、plan_node、PlanEngine、单次 Reflection 自检、安全契约测试 | ✅ 最小闭环完成，待提交 |

---

## 当前进行中：M4.1 Tool Schema 参数契约分批铺开

### 目标

让 ReAct / LLM 工具调用不再依赖模糊字段猜测，而是共享同一份 Tool 参数契约：

```text
ToolParameterSpec -> ToolInputSchemaBuilder -> AtlasToolCallback -> ToolParameterNormalizer -> ReActPromptBuilder
```

### 已完成

1. `diagnose_pod` 小样本完成。
2. ReAct Prompt 可展示轻量参数契约。
3. ToolDefinition inputSchema 接入真实参数 schema。
4. schema-first normalizer 以 Tool 自身 spec 为准。
5. 首批扩展完成：
   - `log_query`
   - `deployment_detail`
   - `node_detail`
6. URL query 拼接专项已开始：`deployment_detail`、`node_detail` 已修复，后续继续扫描剩余手拼 query。

### 当前风险

| 风险 | 说明 | 对策 |
|---|---|---|
| Tool Schema 覆盖率低 | 109 个 Tool 中只有少数具备 `getParameterSpecs()` | 每批 3~5 个，小样本验证后铺开 |
| Prompt 膨胀 | 所有 Tool 全量参数展示会增加 token | 后续按 agent/意图裁剪工具目录 |
| `name` 字段歧义 | deployment/node/detail 暂时仍用 canonical `name` | description 限定资源类型；后续专项重构为 `deploymentName/nodeName` |
| 后端 query 拼接遗留 | 手拼 `?name=` 可能产生 `%253F` 或 query injection | 专项清理所有 `path += "?"` |
| ReAct 成功路径 E2E 不足 | 当前更多验证 target-not-found 和单步 CALL_TOOL | 补真实存在资源的多步诊断 E2E |

---

## 后续路线

### P0 — Tool Schema 与 URL 安全加固

1. 继续清理所有 URL 手拼 query：禁止 `path += "?xxx="`。
2. 按批次补 `ToolParameterSpec`：优先 detail/query/diagnose 类。
3. 补对应单测：Prompt contract + Normalizer contract + ToolDefinition schema。
4. 每批做真实 SSE E2E，并从服务日志确认 path 与 query 参数分离。

### P1 — ReAct 多步成功路径验证

1. 设计真实存在资源的多步诊断 query。

### P1.5 — Plan-and-Execute 完整循环

1. ✅ M4-PX.2 最小 POC：`PLAN -> plan_node -> PlanEngine.plan()`，只规划不执行。
2. 后续新增 execute_node：统一接入 Tool 执行服务，执行前必须重新读取 ToolMetadata 并经过 HitlGuard。
3. 后续新增 reflection_node：每步后做成功/失败/重试/重规划判断，但高危重试必须重新 HITL。
4. 前端读取 `plan_steps` 做 Timeline/确认卡片，避免纯文本计划难以审计。


2. 验证 `DELEGATE_REACT -> think -> act -> observe -> final` 完整链路。
3. 增加 ReAct 预算控制、Observation 截断策略和 Prompt 长度预算。

### P2 — Agent 分治

1. 创建 `agent/core` 抽象。
2. 逐步拆分 Query/Diag/Deploy/RBAC/Storage/Network 六大专业 Agent。
3. AtlasBrain 瘦身为认知路由器，不再承载所有领域执行逻辑。

### P3 — HITL C 方案完整安全治理

1. 新建 `hitl/HITLGuard`、`RiskClassifier`、`ConfirmationService`。
2. 写操作统一进入风险分级。
3. 前端完成弹窗确认 + 命令式确认 + resume。
4. 写操作 E2E 只做安全冒烟，不做无保护全量执行。

### P4 — MCP / Memory / Observability

1. ✅ MCP 安全 Manifest 先行：只导出普通 READ 且不需要确认的 Tool；敏感 READ、写/删/ACTION、UNKNOWN 默认不开放。
2. ✅ 最近 10 次摘要 Memory：内存存储 + 自动脱敏 + 查询 API。
3. ✅ Micrometer + Actuator 最小指标：ReAct run、Tool call、HITL block 计数/计时。
4. ⏳ 后续增强：Redis/向量记忆、System Prompt 历史注入、完整 MCP stdio/sse Server、LLM token 成本、TraceId 全链路、SSE 连接监控。

---

## 约束铁律

1. **专家会诊前置**：架构/编码前先会诊，不能跳过。
2. **先实验再铺开**：每批小样本验证，再扩大范围。
3. **文档同步门控**：架构、Tool、事件、API 变化必须同步更新文档。
4. **测试闭环**：单测/编译/E2E/Review Log/双推缺一不可。
5. **危险操作安全优先**：DELETE/scale/stop/create 不做无保护真实自动化全量测试。
6. **重要文档双备份**：项目目录 + `/mnt/h/Hermes中重要文件/`。

---

*本文件是项目路线图唯一真相源。若其他文档与本文件冲突，以本文件和最新代码审计结果为准。*
