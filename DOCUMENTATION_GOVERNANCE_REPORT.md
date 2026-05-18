# Atlas Kube-Agent 文档治理与里程碑重对齐方案

> **生成日期**: 2026-05-18  
> **角色**: 文档治理与项目路线规划师  
> **范围**: 全项目 30+ 份 Markdown 文档、Git 41 commits、167 个 Java 文件 / 6,962 LOC 工具层  
> **前提**: 代码已大幅超前于文档描述，必须承认现实并重新对齐。

---

## 一、行业文档治理最佳实践（1-2段）

顶级开源项目（LangGraph 32K★、CrewAI 51K★、Pydantic AI、Langfuse、k8sgpt）普遍采用 **“Living Documentation + ADR + Single Source of Truth”** 的三层治理模型。核心原则是：

1. **文档即代码，与代码绑定在同一 PR 中更新**。Langfuse 的 `AGENTS.md` 明文规定"Maintenance Contract"——任何架构、依赖边界或发布流程的变更必须在同一 PR 中更新对应的 Agent 指南；Pydantic AI 要求"Write the failing test first, then fix the bug"，并把功能变更与文档更新视为同一交付物。违规的下场就是当前项目的状态：31 个 md 文件横跨三个废弃版本，新成员找不到有效信息。

2. **ADR（Architecture Decision Record）只记录不可逆的架构决策，而非设计过程草稿**。k8sgpt、dapr、azure-sdk-for-java 均采用 `docs/adr/ADR-NNN-title.md` 的扁平编号格式，状态只能是 `Proposed / Accepted / Deprecated / Superseded`。当前项目把调研报告（QUERY_AGENT_FUNCTION_CALLING_DESIGN.md，39KB）、审计清单（P1_AUDIT_CHECKLIST.md）、Review 流水账（REVIEW_LOG.md，74KB）都混在 `docs/v3.1/` 中，导致“真相源”被噪音淹没。正确做法是：设计调研一旦转化为代码，调研报告应归档到 `archive/` 或被 ADR 替代；持续更新的 Review 日志应拆入 Git commit message 或 CI 报告，而非长期占据文档目录。

---

## 二、当前文档问题清单（逐条，标注严重程度）

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| 1 | **根目录无 README.md** | 🔴 极高 | 项目根目录没有 README，任何外部开发者/Reviewer 无法在最短时间内理解项目是什么、如何编译、如何运行。这是开源项目的“门面缺失”。 |
| 2 | **docs/v3.1/PROJECT_ATLAS_V3.md 严重过时** | 🔴 极高 | 状态仍写着"🚧 开发中（P0 阶段）"，而代码已完成 M1.5（HITL SSE 闭环）。版本号、架构图、技术栈描述与实际脱节，是误导性最高的文件。 |
| 3 | **无 PROJECT_STATUS / CHANGELOG / ROADMAP** | 🔴 极高 | Git 41 个 commit 没有任何版本化变更日志。无法从文档中追溯“哪个功能在哪个 commit 完成”。ARCHITECTURE_AUDIT_20260518.md 是临时审计报告，不应替代状态文档。 |
| 4 | **P1_AUDIT_CHECKLIST.md 已失效** | 🔴 高 | 5 月 14 日的审计清单描述的目录结构、文件状态（如 agent/ 目录已不存在）与当前代码完全不符。继续保留会造成维护者误判。 |
| 5 | **MIGRATION_StateGraph_ReactAgent.md 已执行完成但未标记** | 🔴 高 | 943 行的迁移方案文档中描述的架构（StateGraph + ReactAgent 6 Worker + Tool 桥接）已在 commit `edb29b2`/`baca47d` 中完整实现，但文档状态仍是“方案”，无 Superseded 标记。 |
| 6 | **REVIEW_LOG.md 73KB 流水账** | 🟡 中高 | docs/v3.1/REVIEW_LOG.md 包含从 Review #1 到 #20+ 的全部历史流水账，长度达 74KB。Review 记录应存在于 Git commit message、GitHub PR description 或 CI pipeline 报告中，不应长期占据正式文档目录。 |
| 7 | **文档目录层级混乱** | 🟡 中高 | 根目录散放 TASK.md（78 行，已完成）、TOOL_DEV_SPEC.md（405 行，仍有效）、MIGRATION_...md（已过时）；docs/ 下同时存在 `v3.1/`、`archive/`、`ReviewLogs/`、`p1.4/`、根级文件共 5 个不同命名规范的子目录，缺乏一致性。 |
| 8 | **测试覆盖率未在文档中记录** | 🟡 中 | 3 个单元测试 / 167 个 Java 文件，覆盖率极低。应在 README 或 DEVELOPMENT_GUIDE 中诚实标注当前测试状态并给出补全计划。 |
| 9 | **docs/archive/ 未真正归档** | 🟡 中 | 归档目录仍暴露在主文档路径中，且无 `ARCHIVED_` 前缀或日期戳。根据 Living Documentation 原则，过时文档应尽早移入外部存储或明确标记废弃日期。 |
| 10 | **HITL 联调状态未在文档中闭环** | 🟡 中 | 后端 HITL SSE 闭环已实现（M1.5 commit），但前端联调状态、API 契约细节（SSE event 格式、threadId 传递）未在任何文档中确立，新开发者无法接手。 |
| 11 | **无“文档更新流程”约定** | 🟡 中 | 没有 CONTRIBUTING.md 或等效文件规定"改代码必须同步改文档"，这是导致脱节的根本原因。 |
| 12 | **ONNX-Runtime 调研报告已产品化** | 🟢 低 | docs/ONNX-Runtime-Java-Integration-Report.md 描述的 ONNX 集成已在 L1 Embedding 中落地，但作为技术参考仍有价值，建议移至 `docs/research/` 并标注"已实现"。 |

---

## 三、文档清理方案（旧文件处理列表）

### 3.1 删除（7 个文件）

| 文件路径 | 删除理由 |
|----------|----------|
| `TASK.md` | AtlasBrain 编码任务已完成（commit `92042db` 对应代码已入主干），保留无任何参考价值。 |
| `MIGRATION_StateGraph_ReactAgent.md` | 迁移方案已在 `edb29b2`/`baca47d` 完整执行。核心结论应抽取为 ADR-009，全文删除。 |
| `docs/P1_AUDIT_CHECKLIST.md` | 5 月 14 日审计，目录结构、文件状态全部失效。 |
| `docs/archive/P2_AGENT_SPLIT_ARCHITECTURE_DEPRECATED_20260514.md` | 已明确标注 DEPRECATED，无需保留在主仓库中。 |
| `docs/archive/REACT_TASKCLASSIFIER_DESIGN.md` | 废弃设计，ReactAgent 已取代手写 ReAct 方案。 |
| `docs/ReviewLogs/REVIEW_LOG_0_Phase2B.md` | 流水账式 Review，信息已包含在 commit message 中。 |
| `docs/v3.1/REVIEW_LOG.md` | 73KB 流水账，应拆散到各 ADR/Appenx 或移入外部笔记。 |

### 3.2 归档到 `docs/archive/`（加 ARCHIVED_ 前缀）（6 个文件）

| 文件路径 | 归档理由 |
|----------|----------|
| `docs/ONNX-Runtime-Java-Integration-Report.md` → `docs/archive/ARCHIVED_20260518_ONNX_Runtime_Integration.md` | 技术调研参考，产品化后不再更新。 |
| `docs/v3.1/QUERY_AGENT_FUNCTION_CALLING_DESIGN.md` → `docs/archive/ARCHIVED_20260518_Query_Agent_Function_Calling.md` | 39KB 设计报告，query 类 Tool 已全部实现，仅保留历史参考。 |
| `docs/v3.1/L3_LLM_Intent_Classification_Architecture_Report.md` → `docs/archive/ARCHIVED_20260518_L3_LLM_Intent_Architecture.md` | L3 LLM 分类逻辑已产品化，报告无继续更新价值。 |
| `docs/v3.1/API_MAPPING_DESIGN_REPORT.md` → `docs/archive/ARCHIVED_20260518_API_Mapping_Design.md` | API 映射已实现为 Tool-HTTP 桥接层。 |
| `docs/v3.1/ATLAS_P2_ARCHITECTURE_REFACTOR_PLAN.md` → `docs/archive/ARCHIVED_20260518_P2_Architecture_Refactor.md` | P2 架构规划已完成，代码已落地。 |
| `docs/v3.1/ATLAS_V3_1_OPEN_SOURCE_RESEARCH_REPORT.md` → `docs/archive/ARCHIVED_20260518_OpenSource_Research.md` | 开源调研报告，实现阶段已结束。 |

### 3.3 重写/合并（3 个文件）

| 目标文件 | 操作 | 来源 |
|----------|------|------|
| `README.md`（新建） | **重写** | 合并 PROJECT_ATLAS_V3.md 的架构总览 + TOOL_DEV_SPEC.md 的速查表 + ARCHITECTURE_AUDIT_20260518.md 的当前状态，形成“Single Source of Truth”。 |
| `docs/v3.1/PROJECT_ATLAS_V3.md` | **重写** | 将状态从"🚧 P0 开发中"更新为"M1.5 已完成"，更新架构图（加入 AtlasBrain、6 Worker 子图、SSE 流式），删除过时技术栈说明。 |
| `docs/v3.1/DEVELOPMENT_GUIDE.md` | **扩展** | 当前仅 2,178 字节，需补充：编译命令、测试运行方式、新增 Tool 的标准步骤（合并 TOOL_DEV_SPEC.md 的精华）、文档更新流程约定。 |

### 3.4 保留并持续维护（9 个文件）

| 文件路径 | 维护策略 |
|----------|----------|
| `TOOL_DEV_SPEC.md` | 保留在根目录（高频查阅），每次新增 Tool 类型时同步更新。 |
| `docs/v3.1/ADR-008-SPRING_AI_ALIBABA.md` | 保留，状态改为 `Accepted`，如未来换框架则标注 `Superseded by ADR-NNN`。 |
| `docs/v3.1/ARCHITECTURE_DECISIONS.md` | 保留作为 ADR 索引页，新增 ADR-009（MIGRATION 方案 → 代码落地）、ADR-010（AtlasBrain 手写决策器替换 ReactAgent Supervisor）。 |
| `docs/v3.1/TOOL_ARCHITECTURE_DESIGN.md` | 保留，BaseTool 体系仍有效，随框架演进小幅更新。 |
| `docs/v3.1/TOOL_REGISTRY_DESIGN.md` | 保留，ToolRegistry 仍是核心，随权限/分组变化更新。 |
| `docs/v3.1/TOOL_GAP_MATRIX.md` | 保留，作为测试覆盖率的直观映照；每完成一个 Agent 的测试 batch，更新一次矩阵。 |
| `docs/v3.1/DEFAULT_VALUE_DESIGN.md` | 保留，`defaults.yml + @WithDefaults` 仍在使用。 |
| `docs/v3.1/AtlasToolMapping-Permission-Design.md` | 保留，权限注解体系仍在使用。 |
| `docs/v3.1/INTENT_SCORE_UNIFICATION_DESIGN.md` | 保留，L1-L4 分数归一化仲裁逻辑仍在运行。 |
| `ARCHITECTURE_AUDIT_20260518.md` | 保留在根目录，标注为"一次性审计报告"，不列入长期维护清单。 |

### 3.5 新建（4 个文件）

| 新建文件 | 目的 |
|----------|------|
| `CHANGELOG.md` | 按 Milestone 分组记录所有已实现功能和重大修复。 |
| `ROADMAP.md` | 未来 4-5 个 Milestone 的交付物和验收标准（详见第四节）。 |
| `docs/adr/ADR-009-StateGraph-Migration-Completed.md` | 将 MIGRATION_StateGraph_ReactAgent.md 的结论固化为 ADR。 |
| `docs/adr/ADR-010-AtlasBrain-Handwritten-Decision-Engine.md` | 记录为何弃用 ReactAgent Supervisor 而手写 AtlasBrain 决策循环。 |

---

## 四、新里程碑体系设计（承认代码超前，重新对齐）

### 4.1 已完成的里程碑：如何命名和记录

| 里程碑 | Commit 范围 | 实际完成内容 | 如何记录 |
|--------|-------------|--------------|----------|
| **M0 地基** | `546f896` ~ `a6f0203` | Atlas v2.0 基线、com.kube 包名重构、ReAct Prompt + SSE 流式 + ChatMemory 持久化、5 个 DomainTool | 在 CHANGELOG.md 中写一段摘要，标注日期范围。 |
| **M1 核心闭环** | `64de30f` ~ `61cab8f` | Phase 0-3 (109 Tool)、StateGraph + supervisorGraph、AtlasBrain 手写决策器、6 Worker ReactAgent 子图、L1-L4 意图全链路、HITL SSE 后端闭环 | CHANGELOG.md 中分 M1.0~M1.5 子条目记录，PROJECT_ATLAS_V3.md 架构图更新为"已完成"。 |

### 4.2 未来里程碑（4 个，严格按用户约束排序）

> **用户约束铁律**：先查询全覆盖 → 再写操作+HITL 联调 → 再高级能力（Reflection/Planning）

#### M2 — 查询全覆盖与质量加固（目标：2-3 周）

**核心交付物**：
1. 全部 Query 类 Tool（~45 个）的端到端自动化测试覆盖，含 Mock KubeManager 响应
2. L1 Embedding 模型正确加载/降级路径测试（ONNX Runtime 场景）
3. L2 精确匹配短路规则回归测试
4. L3 LLM 分类 prompt 的 snapshot test（防止 prompt 调优破坏已有分类）
5. 意图仲裁器（IntentArbiter）冲突规则链的边界 case 测试
6. AtlasBrain 决策解析测试（Mock ChatClient 返回固定 JSON，验证 ActionDispatcher 路由）
7. ToolRegistry 权限预检测试（匿名/普通用户/Admin 三类角色）

**验收标准**：
- 单元测试数量从 3 个提升到 **≥ 35 个**
- `mvn test` 在 CI（或本地脚本）中全绿
- 查询类意图（所有 `*_query` / `*_detail` / `*_list`）的 E2E 通过率 **≥ 95%**
- 删除或修复所有硬编码 `orgId="100001"`

#### M3 — 写操作+HITL 前端联调（目标：2-3 周）

**核心交付物**：
1. 前端 `useChat.ts` 中补充 `/api/agent/hitl/confirm` 与 `/api/agent/hitl/clarify` 的完整 API 调用链
2. 高危操作（DELETE/scale/stop/create 等 P0 级别）的端到端 HITL 流测试：
   - SSE event:`hitl_request` → 前端弹窗 → 用户 confirm → SSE 恢复流 → 完成
3. 写入类 Tool 的参数补全机制：前端弹轻量输入框 → 拼接为自然语言 → 走现有聊天接口
4. 部署/扩缩容/停止/删除/创建类 Tool 的冒烟测试（每个大类 ≥ 1 个 E2E）
5. ThreadLocal Token 透传替换为 Graph State 显式传递（解决并发安全）

**验收标准**：
- 前端 HITL confirm/clarify 两种路径均通过真实浏览器手动验证
- 写操作类意图 E2E 冒烟测试通过率 **≥ 80%**
- `AtlasGraphConfig` 中无 `UserPermissionContext.CURRENT_TOKEN.set()` 的 hack 代码
- `docs/v3.1/HITL_API_CONTRACT.md` 新建，记录 SSE event 格式、threadId 生命周期、确认/取消的 wire protocol

#### M4 — Plan-and-Execute + Reflection 自我修正（目标：3-4 周）

**核心交付物**：
1. `PlanNode`：LLM 将复杂请求拆解为 `[step1, step2, ...]` 任务列表（BrainDecision 新增 `PLAN` actionType）
2. `ExecuteNode`：按顺序执行每个 step，上一步结果注入下一步上下文
3. `ReflectNode`：每步执行后 LLM 判断"成功 / 重试 / 重规划 / 完成"
4. Tool 执行失败后自动 Reflection 闭环：错误信息 + 当前参数 + Tool schema → LLM 诊断 → 修正参数 → 重试（上限 3 次）
5. AtlasBrain 从"单次决策"升级为"多轮 Plan-Execute-Reflect"循环（MAX=10 轮）

**验收标准**：
- 能正确处理多步任务：如"部署一个服务并配置 Ingress"拆解为 deploy → network 两个步骤串联
- Tool 执行失败后（Mock 404/参数错误）自动重试成功率 **≥ 70%**
- 新增 `docs/v3.1/PLAN_EXECUTE_DESIGN.md` 记录设计，状态为 `Accepted`

#### M5 — 长期 Memory + MCP 适配 + 可观测性（目标：4-6 周）

**核心交付物**：
1. 长期 Memory：Redis/Chroma 向量存储 + 对话摘要生成 + 跨会话检索注入 System Prompt
2. MCP 协议适配层：`McpServerAdapter` 将 BaseTool 转换为 MCP Tool，对外暴露 109 个 Tool
3. 可观测性：Micrometer + Prometheus `/actuator/metrics`，链路追踪 traceId 贯穿全 Graph，LLM token 成本统计
4. Agent 安全层：高危操作 Guardrails（输入/输出校验），OWASP LLM Top 10 的基本防护

**验收标准**：
- MCP Server 可用 stdio/sse 模式独立启动，外部 Agent（如 Claude Desktop）可发现并调用 Tool
- `/actuator/metrics` 暴露 LLM 调用延迟、Token 消耗、Graph 执行耗时、SSE 连接数
- 可查询用户最近 10 次对话摘要，新对话能引用历史偏好

---

## 五、防脱节机制建议

### 5.1 文档更新门控（Documentation Gate）

**规则**：任何涉及以下内容的 PR/Commit，必须在同一次提交中包含文档更新：
- 新增/删除/重命名 Java 包或类
- 新增/修改 Tool（`@AtlasToolMapping` 变更、参数变化、权限变化）
- 修改 SSE event 类型、Graph 节点结构、API endpoint
- 修改 prompts（System Prompt、L3 classification prompt、BrainDecision prompt 模板）

**执行方式**：在 `ROADMAP.md` 或 `DEVELOPMENT_GUIDE.md` 中写明此规则；每次代码 Review 时检查 `docs/` 是否有配套变更。

### 5.2 README 作为活路由（Living Router）

仿照 Langfuse 的 `.agents/AGENTS.md` 模式：
- `README.md` 只放**最精简**的：项目一句话描述、编译运行命令、架构全景图、最新 Milestone 状态、文档入口链接。
- 所有详细内容按目录分片：`docs/architecture/`、`docs/adr/`、`docs/development/`、`docs/api/`。
- `README.md` 每月由维护者检查一次链接有效性。

### 5.3 ADR 生命周期管理

- 所有新的架构决策（如 M4 的 Plan-and-Execute）必须先写 ADR 草案（`docs/adr/ADR-NNN-xxx.md`，状态 `Proposed`），Review 通过后改为 `Accepted`。
- 被取代的 ADR 不删除，改为 `Superseded by ADR-NNN`，保留决策连续性。
- ADR 编号全局递增，不限于 v3.1 版本号，避免"v3.1/ADR-xxx" 这种与版本耦合的路径。

### 5.4 CHANGELOG 自动化约定

采用 [Keep a Changelog](https://keepachangelog.com/) 格式，按 Milestone 分区块：
```markdown
## [M2] — 查询全覆盖与质量加固 — 2026-06-XX
### Added
- Query 类 Tool 35 个单元测试
- L3 classification snapshot test
### Changed
- 硬编码 orgId 移除，改为 State 显式传递
### Fixed
- ThreadLocal Token 泄漏风险（重构为 State 传递）
```
- 每个 commit message 前缀规范：`feat(M2): ...`、`fix(M2): ...`、`docs(M1): ...`，便于脚本提取 CHANGELOG。
- 发布（或 Milestone 完成）时由维护者汇总编辑一次，不依赖全自动化。

### 5.5 月度文档审计（Monthly Doc Audit）

每月第一周执行一次快速审计：
1. 检查 `docs/` 中所有文件的 `最后修改日期` 与对应代码的 `最后修改日期` 是否相差超过 2 周
2. 检查 `PROJECT_ATLAS_V3.md` 的状态文字是否与最新 Milestone 一致
3. 检查 `archive/` 中是否有文件超过 3 个月未访问——考虑彻底删除
4. 检查结果写入 `docs/archive/doc_audit_YYYYMM.md`，作为可追溯记录

### 5.6 测试即文档（Test as Spec）

当前测试覆盖率（3/167 文件）是文档脱节的直接原因之一：测试本身就是最精确的“活文档”。建议从 M2 开始：
- 每个新增的单元测试用 Javadoc 写明"此测试验证的功能点"，替代部分设计文档
- 使用 `@DisplayName` 描述用户场景，如 `"给定匿名用户调用 admin_only Tool，应返回权限不足"`
- E2E 测试脚本可直接作为“API 使用手册”的前置材料

---

## 六、执行优先级与时间表

| 阶段 | 时间 | 行动 | 产出物 |
|------|------|------|--------|
| **P0 清理** | Day 1-2 | 执行第三节的删除/归档/重写清单，新建 README.md + CHANGELOG.md + ROADMAP.md | 干净文档目录 |
| **P1 补缺口** | Day 3-5 | 新建 ADR-009/010，重写 PROJECT_ATLAS_V3.md，新建 HITL_API_CONTRACT.md（如 M3 提前） | 真相源对齐 |
| **P2 测试** | Week 2-4 | M2 里程碑执行（查询全覆盖测试） | ≥35 单元测试 |
| **P3 联调** | Week 5-7 | M3 里程碑执行（写操作+HITL 前端联调） | 前端 HITL 闭环 |
| **P4 升级** | Week 8-12 | M4/M5 里程碑（Architecture 升级） | Plan-Execute + Reflection |

---

*本报告由 Hermes Agent 于 2026-05-18 生成，建议作为下一轮 Sprint Planning 的基准文档。*
