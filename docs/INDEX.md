# Atlas Kube-Agent 文档索引

> 最后更新：2026-06-12
> 当前主线：M5.85 中文教学注释分批治理 + Phase 1 顶级 Agent Core
> 文档原则：先分类、再归档；除非确认无恢复价值，否则不删除历史审计证据。

## 快速入口

| 入口 | 状态 | 用途 |
|---|---|---|
| `docs/PROJECT_MISSION_AND_MEMORY.md` | 当前维护 | 项目任务、里程碑、提交、验证、恢复记忆的主叙事。 |
| `docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md` | 当前维护 | 顶级 Agent 总体架构、技术点、学习路线和每个切片的教学总结。 |
| `codex-memory/kube-agent/current/RECOVERY_STATUS.md` | 当前维护 | 新会话恢复时优先读取的工作区内恢复状态。 |
| `CHANGELOG.md` | 当前维护 | 已交付功能按 M5.x 切片记录。 |
| `AGENTS.md` | 当前维护 | Codex 项目规则，包含中文注释和分批推进要求。 |
| `ROADMAP.md` | 历史参考 | 早期路线图，保留背景；当前状态以 PROJECT_MISSION / architecture learning / recovery memory 为准。 |
| `README.md` | 需后续刷新 | 项目门面仍有早期 M2/M5.20 文字，暂不作为当前进度真相源。 |

## 当前有效文档

| 分类 | 文件 | 说明 |
|---|---|---|
| 项目使命 | `docs/PROJECT_MISSION_AND_MEMORY.md` | 记录用户终极目标：一期做顶级 Agent；NIM/HPC/Slurm/BCM 暂停到二期。 |
| 架构学习 | `docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md` | 维护整体架构、技术点、学习笔记和安全边界。 |
| 技术栈审计 | `docs/tech-stack/BACKEND_JAVA_TECH_STACK_AUDIT_20260609.md` | Java/Spring 作为主线控制平面的审计与现代化判断。 |
| 技术栈路线 | `docs/tech-stack/BACKEND_ADVANCED_TECH_STACK_ROADMAP_20260608.md` | 最新技术引入路线，运行时权限仍需证据门。 |
| ADR | `docs/adr/ADR-009-StateGraph-Migration-Completed.md` | StateGraph 迁移完成决策。 |
| ADR | `docs/adr/ADR-010-AtlasBrain-Handwritten-Decision-Engine.md` | 手写 AtlasBrain 决策器决策。 |
| Auth 设计 | `docs/auth-session-api-design.md` | 登录、Session、身份桥接相关设计。 |
| Review 日志 | `docs/REVIEW_LOG.md` | 历史审查日志，后续可继续补充但不是唯一真相源。 |

## M5.37 以后当前主线读模型文档

这些文档仍属于 Phase 1 顶级 Agent Core 的当前学习/治理材料，主要覆盖 Eval、kube-manager HTTP outlet、MCP、Memory/RAG、最新技术引入、Vue 工作台契约。

| 范围 | 文件模式 | 状态 |
|---|---|---|
| Eval / Release Gate | `docs/M5_37_*.md` 到 `docs/M5_48_*.md` | 当前参考 |
| kube-manager HTTP outlet | `docs/M5_49_*.md` 到 `docs/M5_55_*.md` | 当前参考 |
| MCP 治理 | `docs/M5_56_*.md` | 当前参考 |
| Top-tier readiness | `docs/M5_57_*.md`、`docs/M5_63_*.md`、`docs/M5_64_*.md` | 当前参考 |
| Memory/RAG | `docs/M5_58_*.md` 到 `docs/M5_73_*.md` | 当前参考 |
| 官方版本/协议 watch | `docs/M5_74_*.md` 到 `docs/M5_78_*.md` | 当前参考 |
| Vue workbench 契约 | `docs/M5_79_*.md`、`docs/M5_83_*.md`、`docs/M5_84_*.md` | 当前参考 |
| 后端技术现代化 | `docs/M5_80_*.md` 到 `docs/M5_82_*.md` | 当前参考 |
| 多 Agent 审查 | `docs/M5_85_*.md` | 当前参考 |

## 历史审计证据

以下文档很多已经不代表当前开发顺序，但仍保留为安全审计、学习复盘和恢复证据。不要在没有引用扫描和用户确认时删除。

| 范围 | 文件模式 | 处理方式 |
|---|---|---|
| M4/M5 早期审计 | `docs/M4_*.md`、`docs/M5_4_*.md` 到 `docs/M5_20_*.md` | 历史参考，后续可逐步移入 `docs/archive/`。 |
| M5.21 工具对齐波次 | `docs/M5_21_*_AUDIT_20260605.md` 到 `docs/M5_21_*_AUDIT_20260608.md` | 历史审计证据；大量 NIM/HPC/Slurm/BCM 相关内容现为二期参考。 |
| M5.21 波次索引 | `docs/M5_21_WAVE_INDEX_20260606.md` | 读取 M5.21 波次历史时先看这里。 |
| 会话快照 | `docs/SESSION_PROGRESS_20260606_M521_29.md`、`docs/会话上下文快照_20260520.md` | 恢复历史上下文用，不作为当前实现计划。 |
| v3.1 早期设计 | `docs/v3.1/**` | 早期架构/工具/权限/开发指南，部分仍有学习价值，当前状态需与代码和 M5 文档交叉验证。 |

## 二期暂停范围

用户明确要求 HPC / Slurm / BCM / NIM 暂停到二期。相关文档和代码可作为历史证据保留，但 Phase 1 不继续新增运行时能力。

| 范围 | 代表文档 | 当前状态 |
|---|---|---|
| NIM create 链路 | `docs/M5_21_*NIM*.md` | 二期参考；`nim_create` 保持 HOLD，不打开真实写入。 |
| HPC / Slurm | `docs/M5_21_*HPC*.md`、`docs/M5_21_*SLURM*.md` | 二期参考；不作为当前主线。 |
| BCM | `docs/M5_21_*BCM*.md` | 二期参考；不作为当前主线。 |

## 恢复镜像说明

`codex-memory/kube-agent/current` 是工作区内恢复记忆目录，优先于历史外部路径 `H:\codex重要文件`。

| 类型 | 文件 |
|---|---|
| 当前恢复状态 | `codex-memory/kube-agent/current/RECOVERY_STATUS.md` |
| 长期架构镜像 | `codex-memory/kube-agent/current/docs_AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md` |
| 项目使命镜像 | `codex-memory/kube-agent/current/docs_PROJECT_MISSION_AND_MEMORY.md` |
| 文档索引镜像 | `codex-memory/kube-agent/current/docs_INDEX.md` |
| 历史 SHA 证据 | `codex-memory/kube-agent/current/*_WORKSPACE_RECOVERY_SHA256.json` |

## 清理策略

1. 不把“旧”直接等同于“没用”。审计波次、恢复快照和二期参考文档仍有学习价值。
2. 当前主线文档应持续更新，历史文档尽量只补索引和状态，不反复修改正文。
3. 大规模移动文档前必须先做引用扫描，避免破坏 `codex-memory`、README、CHANGELOG 或测试里的路径。
4. 二期暂停文档保留但降权，不再作为 Phase 1 开发计划来源。
5. 下一批文档治理可优先刷新 `README.md` 和 `ROADMAP.md` 的当前状态说明，再考虑建立 `docs/archive/` 并移动早期 M4/M5 审计文件。
