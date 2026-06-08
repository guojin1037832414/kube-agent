# Atlas Kube-Agent 文档索引

> **项目**: kube-agent — K8s 训练平台 AI Agent 入口  
> **版本**: Atlas v3.1  
> **最后更新**: 2026-05-19  
> **维护者**: Hermes Agent (文档治理审查)

---

## 索引结构

| 分类 | 标记 | 说明 |
|------|------|------|
| ✅ 有效 | 无标记 | 与当前代码实时同步，可直接参考 |
| 📦 已归档 | `ARCHIVED_` 前缀 | 保留决策历史，不再维护，存于 `docs/archive/` |
| ⚠️ 已弃用 | `[DEPRECATED]` 前缀 | 内容过时但仍保留在原地，供历史对比参考 |

---

## 一、项目级核心文档（根目录）

| 文件 | 状态 | 说明 | 时效性 |
|------|------|------|--------|
| `README.md` | ✅ 有效 | 项目门面：架构全景图、快速开始、当前里程碑状态 | 与 M2 同步 |
| `ROADMAP.md` | ✅ 有效 | **唯一真相源**：M0-M5 里程碑、验收标准、约束铁律 | 与 M2 同步 |
| `CHANGELOG.md` | ✅ 有效 | 按里程碑分组的变更日志 | 与 M2 同步 |
| `TOOL_DEV_SPEC.md` | ✅ 有效 | DomainTool 开发规范（BaseTool + 注解 + defaults.yml） | 与 v3.1.0 同步 |
| `ARCHITECTURE_AUDIT_20260518.md` | ✅ 有效 | 一次性架构审计与行业调研报告 | 2026-05-18 生成，长期参考 |
| `docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md` | ✅ 有效 | 顶级 Agent 总体架构、技术点与学习地图 | M5.21 起持续维护 |
| `DOCUMENTATION_GOVERNANCE_REPORT.md` | ✅ 有效 | 文档治理方案与里程碑重对齐建议 | 2026-05-18 生成，长期参考 |
| `pom.xml` | ✅ 有效 | Maven 构建配置 | 与代码实时同步 |

---

## 二、架构决策记录 (ADR)

> 目录: `docs/adr/`  
> 规则：不可逆架构决策，状态 = Proposed / Accepted / Deprecated / Superseded

| 文件 | 状态 | 说明 | 时效性 |
|------|------|------|--------|
| `ADR-008-SPRING_AI_ALIBABA.md` | ✅ Accepted | 采纳 Spring AI Alibaba 作为核心 Agent 框架 | 已修正实施偏差，与代码同步 |
| `ADR-009-StateGraph-Migration-Completed.md` | ✅ Accepted | StateGraph + ReactAgent 6 Worker 迁移完成 | 与代码同步 |
| `ADR-010-AtlasBrain-Handwritten-Decision-Engine.md` | ✅ Accepted | 手写 AtlasBrain 替代 ReactAgent Supervisor | 与代码同步 |

**注**: ADR-001 至 ADR-007 收录于 `docs/v3.1/ARCHITECTURE_DECISIONS.md`。

---

## 三、v3.1 技术设计文档

> 目录: `docs/v3.1/`

### 3.1 有效文档

| 文件 | 状态 | 说明 | 时效性 |
|------|------|------|--------|
| `PROJECT_ATLAS_V3.md` | ✅ 有效 | v3.1 总纲：架构全景、技术栈、状态看板（已更新为 M1.5 完成） | 与 M1.5 同步 |
| `ARCHITECTURE_DECISIONS.md` | ✅ 有效 | ADR 索引页（ADR-001 至 ADR-008） | 已更新实施修正 |
| `DEVELOPMENT_GUIDE.md` | ✅ 有效 | 开发指南：环境、编译、规范、Milestone 路线 | 已更新为 M0-M5 |
| `TOOL_ARCHITECTURE_DESIGN.md` | ✅ 有效 | BaseTool 体系设计：抽象基类、响应标准化 | 与 v3.1.0 同步 |
| `TOOL_REGISTRY_DESIGN.md` | ✅ 有效 | ToolRegistry 启动扫描、权限预检、System Prompt 构建 | 与 P1.4 同步 |
| `DEFAULT_VALUE_DESIGN.md` | ✅ 有效 | defaults.yml + `@WithDefaults` 默认值回填设计 | 与 v3.1.0 同步 |
| `INTENT_SCORE_UNIFICATION_DESIGN.md` | ✅ 有效 | L1-L4 分数归一化 + IntentArbiter 冲突仲裁规则链 | 与代码同步 |
| `AtlasToolMapping-Permission-Design.md` | ✅ 有效 | `@AtlasToolMapping` + `@ToolPermission` 注解设计 | 与 P1.4 同步 |
| `INTENT_SCORE_CODE_PACKAGE.java` | ✅ 有效 | 意图分数归一化核心 Java 代码包（参考实现） | 与代码同步 |

### 3.2 Brain 子目录

| 文件 | 状态 | 说明 | 时效性 |
|------|------|------|--------|
| `brain/[DEPRECATED]_ATLASBRAIN_ENCODE_PLAN.md` | ⚠️ 已弃用 | 原 AtlasBrain Phase 1/2 编码方案 | 实际实现已偏离计划 |
| `brain/[DEPRECATED]_STATEGRAPH_REACTAGENT_INTEGRATION_REPORT.md` | ⚠️ 已弃用 | 原 Layer 3 StateGraph 集成调研报告 | 实际集成方式已调整 |

### 3.3 Security 子目录

| 文件 | 状态 | 说明 | 时效性 |
|------|------|------|--------|
| `security/P1.4_PERMISSION_SECURITY_REVIEW.md` | ✅ 有效 | P1.4 权限安全设计 Review | 与 P1.4 同步 |
| `security/SESSION_AUTH_SECURITY_DESIGN.md` | ✅ 有效 | Session + Auth Token 安全设计 | 与 P1.4 同步 |

### 3.4 已弃用文档（仍保留在 `docs/v3.1/`）

| 文件 | 状态 | 说明 | 弃用原因 |
|------|------|------|----------|
| `[DEPRECATED]_TOOL_GAP_MATRIX.md` | ⚠️ 已弃用 | 33 Tool → 60+ 功能点缺口矩阵 | 实际已有 109 Tool，缺口分析已失效 |
| `[DEPRECATED]_P2_BRAIN_AUDIT_CHECKLIST.md` | ⚠️ 已弃用 | Phase 2 AtlasBrain 集成审计清单 | Phase 2 已完成，历史参考 |
| `[DEPRECATED]_AUDIT_CHECKLIST_20260515.md` | ⚠️ 已弃用 | 109 Tool 批次审计（Batch 1-9） | 审计已完成，历史参考 |
| `[DEPRECATED]_FRONTEND_API_INVENTORY.md` | ⚠️ 已弃用 | 前端真实 API 盘点报告 | 基于旧版 API 映射，部分已变化 |

---

## 四、归档文档

> 目录: `docs/archive/`  
> 说明：不再维护，仅保留决策历史。文件命名规范：`ARCHIVED_YYYYMMDD_名称.md`

| 文件 | 原位置 | 归档原因 |
|------|--------|----------|
| `ARCHIVED_20260518_API_Mapping_Design.md` | docs/v3.1/ | API 映射已实现为 Tool-HTTP 桥接层 |
| `ARCHIVED_20260518_L3_LLM_Intent_Architecture.md` | docs/v3.1/ | L3 LLM 分类逻辑已产品化 |
| `ARCHIVED_20260518_ONNX_Runtime_Integration.md` | docs/ | ONNX 集成已在 L1 Embedding 中落地 |
| `ARCHIVED_20260518_OpenSource_Research.md` | docs/v3.1/ | 开源调研报告，实现阶段已结束 |
| `ARCHIVED_20260518_P2_Architecture_Refactor.md` | docs/v3.1/ | P2 架构规划已完成，代码已落地 |
| `ARCHIVED_20260518_Query_Agent_Function_Calling.md` | docs/v3.1/ | Query 类 Tool 已全部实现 |
| `ARCHIVED_20260519_P2_ARCHITECTURE_SPRING_AI_ALIBABA.md` | docs/v3.1/p2/ | 基于假设性 API 的架构方案，实际采用手写 AtlasBrain |
| `ARCHIVED_20260519_M1.5_PLAN.md` | docs/v3.1/milestones/ | M1.5 后端已完成，计划中的待办全部过时 |
| `ARCHIVED_20260519_P1.4_Architecture_Review.md` | docs/p1.4/ | P1.4 权限感知阶段已结束 |

---

## 五、开发审计日志

| 文件 | 状态 | 说明 | 时效性 |
|------|------|------|--------|
| `docs/REVIEW_LOG.md` | ✅ 有效 | 开发审计日志（Review #1 ~ #23+） | 持续追加，最新 M2.5 双推 |

---

## 六、文档时效性速查

### 当前里程碑对照表

| 里程碑 | 代码状态 | 文档同步状态 |
|--------|----------|-------------|
| M0 地基 | ✅ 已归档 | CHANGELOG.md 已记录 |
| M1 智能引擎 | ✅ 已完成 | PROJECT_ATLAS_V3.md, ADR-009, ADR-010 同步 |
| M1.5 HITL SSE | ⚠️ 后端完成，前端未联调 | M1.5_PLAN 已归档；ROADMAP.md 标注待 M3 |
| **M2 查询全覆盖** | **🔵 进行中** | **REVIEW_LOG #23 最新；测试文档待 M2 完成后补充** |
| M3 写操作+HITL联调 | ⏳ 未开始 | ROADMAP.md 已规划 |
| M4 Plan-Execute | ⏳ 未开始 | ROADMAP.md 已规划 |
| M5 Memory+MCP | ⏳ 未开始 | ROADMAP.md 已规划 |

### 防脱节机制

1. **Commit Message 前缀**: `feat(Mx):` / `fix(Mx):` / `docs(Mx):`
2. **文档更新门控**: 修改 Tool/API/Config/Prompt → 同步更新契约文档
3. **月度文档审计**: 每月第一周检查 `docs/` 与代码一致性
4. **Milestone 完成时**: 同步更新 ROADMAP.md + CHANGELOG.md + PROJECT_ATLAS_V3.md

---

## 七、文件导航

```
kube-agent/
├── README.md                           ← 先从这里开始
├── ROADMAP.md                          ← 唯一真相源（里程碑）
├── CHANGELOG.md                        ← 变更日志
├── TOOL_DEV_SPEC.md                    ← 开发规范
├── ARCHITECTURE_AUDIT_20260518.md      ← 架构审计报告
├── DOCUMENTATION_GOVERNANCE_REPORT.md  ← 文档治理方案
├── docs/
│   ├── INDEX.md                        ← 本文档（你在看的）
│   ├── REVIEW_LOG.md                   ← 开发审计日志（持续更新）
│   ├── adr/                            ← 架构决策记录
│   │   ├── ADR-008-SPRING_AI_ALIBABA.md
│   │   ├── ADR-009-StateGraph-Migration-Completed.md
│   │   └── ADR-010-AtlasBrain-Handwritten-Decision-Engine.md
│   ├── v3.1/                           ← 有效技术设计文档
│   │   ├── PROJECT_ATLAS_V3.md
│   │   ├── ARCHITECTURE_DECISIONS.md
│   │   ├── DEVELOPMENT_GUIDE.md
│   │   ├── TOOL_ARCHITECTURE_DESIGN.md
│   │   ├── TOOL_REGISTRY_DESIGN.md
│   │   ├── DEFAULT_VALUE_DESIGN.md
│   │   ├── INTENT_SCORE_UNIFICATION_DESIGN.md
│   │   ├── AtlasToolMapping-Permission-Design.md
│   │   ├── INTENT_SCORE_CODE_PACKAGE.java
│   │   ├── security/
│   │   │   ├── P1.4_PERMISSION_SECURITY_REVIEW.md
│   │   │   └── SESSION_AUTH_SECURITY_DESIGN.md
│   │   └── brain/                      ← 【已弃用调研文档】
│   │       ├── [DEPRECATED]_ATLASBRAIN_ENCODE_PLAN.md
│   │       └── [DEPRECATED]_STATEGRAPH_REACTAGENT_INTEGRATION_REPORT.md
│   └── archive/                        ← 【归档文档，不再维护】
│       ├── ARCHIVED_20260518_*.md  (6 份)
│       └── ARCHIVED_20260519_*.md  (3 份)
```

---

> *本索引由 Hermes Agent 于 2026-05-19 自动生成。  
> 如发现文档与代码不一致，请优先以 `ROADMAP.md` 和 `CHANGELOG.md` 为准。*
