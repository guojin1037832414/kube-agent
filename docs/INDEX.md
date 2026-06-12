# kube-agent 当前文档索引

> 最后更新：2026-06-12
> 原则：当前 docs 树只保留主线入口、长期学习资料、ADR、技术栈和仍有直接维护价值的设计文档。历史波次报告已从当前目录清理，恢复可走 Git 历史和 `codex-memory`。

## 先读这些

| 顺序 | 文档 | 作用 |
|---:|---|---|
| 1 | `codex-memory/kube-agent/current/RECOVERY_STATUS.md` | 新会话恢复入口，记录最新完成切片、当前目标和下一步。 |
| 2 | `README.md` | 项目当前状态、架构边界、运行与验证入口。 |
| 3 | `ROADMAP.md` | 当前开发计划和 Phase 1 / Phase 2 边界。 |
| 4 | `docs/PROJECT_MISSION_AND_MEMORY.md` | 用户终极目标、长期记忆和阶段叙事。 |
| 5 | `docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md` | 顶级 Agent 架构、技术点和学习总结。 |
| 6 | `docs/DOCUMENTATION_GOVERNANCE.md` | 文档清理、保留、删除和恢复规则。 |

## 当前保留文档

| 类别 | 文档 |
|---|---|
| 项目入口 | `README.md`, `ROADMAP.md`, `CHANGELOG.md`, `AGENTS.md` |
| 学习与记忆 | `docs/PROJECT_MISSION_AND_MEMORY.md`, `docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md` |
| 文档治理 | `docs/INDEX.md`, `docs/DOCUMENTATION_GOVERNANCE.md` |
| Tool 开发 | `TOOL_DEV_SPEC.md` |
| Auth / Session | `docs/auth-session-api-design.md` |
| ADR | `docs/adr/ADR-008-SPRING_AI_ALIBABA.md`, `docs/adr/ADR-009-StateGraph-Migration-Completed.md`, `docs/adr/ADR-010-AtlasBrain-Handwritten-Decision-Engine.md` |
| 技术栈 | `docs/tech-stack/BACKEND_JAVA_TECH_STACK_AUDIT_20260609.md`, `docs/tech-stack/BACKEND_ADVANCED_TECH_STACK_ROADMAP_20260608.md` |

## 已清理内容

这次刷新删除了当前 docs 树中的旧 M4/M5 波次报告、旧 v3.1 设计目录、重复 review log、旧会话快照、根目录旧架构审计报告和旧文档治理报告。删除原因不是“历史没有价值”，而是它们已经不适合作为当前项目入口：

- 状态大多停留在早期 M2 / M4 / M5.20 / M5.21。
- NIM / HPC / Slurm / BCM 的大量材料现在属于二期暂停范围。
- 几百个波次文件会淹没当前 Phase 1 顶级 Agent Core 的学习路径。
- Git 历史和 `codex-memory` 已承担恢复与追溯职责。

如需回看旧报告，优先使用：

```powershell
git log -- docs
git show <commit>:docs/<old-file>
```

## 二期暂停范围

NIM / HPC / Slurm / BCM 当前不作为 Phase 1 开发入口。相关历史证据可以从 Git 历史或 `codex-memory` 恢复，但不得因为旧文档存在就重新打开 runtime authority。

## 恢复镜像

`codex-memory/kube-agent/current` 是工作区内恢复记忆目录，优先级高于历史外部路径 `H:\codex重要文件`。每次有意义切片完成后，应同步：

- `RECOVERY_STATUS.md`
- `docs_AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md`
- `docs_PROJECT_MISSION_AND_MEMORY.md`
- `docs_INDEX.md`
- `CHANGELOG.md`

必要时也可以镜像 `README.md`、`ROADMAP.md` 和 `docs_DOCUMENTATION_GOVERNANCE.md`，让新会话更快恢复。
