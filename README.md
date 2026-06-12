# Atlas Kube-Agent

`kube-agent` 是构建在 `kube-manager` / `vue-kube-manager` 能力之上的 Kubernetes Agent 后端，也是一个长期 Agent 开发学习项目。目标不是“能用的生产级 Agent”，而是一期开出一个顶级 Agent Core：安全边界清晰、证据链完整、可观测、可评测、可恢复，并且每一批代码和文档都能帮助学习 Agent 工程。

当前后端主线在 `F:\gitProject\kube-agent`，前端临时工作台在 `F:\gitProject\kube-agent-vue`，正式 Vue 集成仍以 `vue-kube-manager` 的能力和风格为目标。

## 当前状态

| 范围 | 状态 | 说明 |
|---|---|---|
| Phase 1 顶级 Agent Core | 进行中 | 不降低目标，优先完成安全、编排、Tool 治理、Memory/RAG、Eval、可观测、多 Agent 审查和教学注释。 |
| NIM / HPC / Slurm / BCM | 二期暂停 | 历史 Tool/测试/证据代码仍可存在，但一期不导出 MCP、不新增 runtime authority，高风险写默认被 durable audit fail-closed 阻断。 |
| 中文代码注释 | 分批推进 | 已完成安全入口、Tool/MCP/kube-manager 执行边界、Orchestrator / Graph / ReAct / Plan 编排链路、Memory / RAG / Eval / Observability / Audit 证据链，以及 Batch 5 的 DTO / store / config、support contract、query/path/body helper、分析目录 query helper；下一步继续支撑类收尾与 Orchestrator hardening。 |
| 文档治理 | 当前刷新 | 主线文档精简到入口、架构学习、使命记忆、ADR、技术栈和恢复记忆；历史波次报告从当前 docs 树移除。 |

## 核心架构

```text
用户 / 前端
  -> Spring Security + AgentPrincipal + kube-manager session bridge
  -> AtlasOrchestrator / StateGraph / AtlasBrain / ReAct / Plan
  -> SafeToolExecutor
  -> ToolRegistry / Tool metadata / HITL / protected parameter filter
  -> KubeManagerHttpClient -> kube-manager 8100

旁路证据面:
  MCP Manifest(read-only catalogue)
  Observability / Audit / Eval / Memory-RAG / Top-tier read models
  codex-memory recovery mirror
```

重要边界：

- LLM、Plan、前端传入的参数只能是候选业务输入，不能成为 token、orgId、userId、HITL、审计、release 或写入权限事实。
- `SafeToolExecutor` 是 Graph / ReAct / ToolCallback / Plan 路径进入真实 `BaseTool.execute` 的统一执行边界。
- MCP 当前只导出 admin-only 只读 Manifest / governance，不开放 `tools/call` 运行时执行权；NIM/HPC/Slurm/BCM 这类二期域不会进入一期 MCP 导出清单。
- kube-manager 是真实外部网络出口，默认连接 `http://localhost:8100`；业务 Tool 请求必须使用当前用户 Token，不能透明降级为 sysadmin。
- 高风险写操作默认要求 ready durable audit prewrite；HITL 确认本身不等于可执行。
- Memory 当前是按用户保存的 caller-submitted bounded summary，会做基础脱敏和截断，但不是可信 RAG prompt authority。
- Replay / Eval / Audit / Memory-RAG 证据链已补中文教学边界：它们是 admin-only 或用户隔离的只读/脱敏证据，不重新执行 Tool，不调用 MCP/LLM/kube-manager，不授予 release authority。
- 支撑层 DTO / Store / Config 也承载安全事实：`ApiResponse.success` 不是权限事实，`LoginRequest.organizationId` 只是 kube-manager 登录候选参数，`sessionId` 不是 JWT，`conversationId` 不是授权凭证，异步线程只能传播服务端可信 token/orgId 快照。
- 支撑契约层不是授权层：`ToolParameterSpec`、`SafeToolExecutionResult`、`SafeToolExecutionSource`、`AgentTraceContext`、`IntentDefinition`、`IntentResult`、默认值补参和 Tool 异常只描述 schema、结果、来源、trace、路由、草稿补参或错误信号，不能替代 Tool 权限、HITL、audit、release、MCP runtime、kube-manager 写入或部署授权。
- query/path/body helper 是 kube-manager HTTP 前最后一层轻量收敛：下载任务、课件、模板、TensorBoard、文件/存储和用户高风险变更参数必须拒绝路径/query/body 注入；资源 ID 只是定位符，充值 body 只能保留白名单字段，不能透传 token/orgId/HITL/audit/release/writeAllowed。
- 分析/目录类 query helper 只做 GET 筛选白名单：虚拟机、repository catalog、sale 产品/报价、行业应用和成本账单查询不能把 LLM/前端字段整体透传给 kube-manager；目录筛选不等于 NIM 部署，金额预估不等于下单/支付，API 历史不等于重放请求。
- Eval / Replay 已有 admin-only 确定性读模型和部分运行入口；CI blocking、LLM eval、Memory/RAG retrieval eval runtime 仍必须经过 reviewed trace evidence 和单独 release 审查。
- 写重试、RAG prompt 注入、A2A runtime handoff、MCP runtime、NIM/HPC/Slurm/BCM 都必须经过证据门和单独 release 审查。

## 技术栈

| 层 | 当前选择 |
|---|---|
| Java | 17 |
| 后端框架 | Spring Boot 3.5.14 |
| Agent / LLM | Spring AI 1.1.7，OpenAI-compatible model adapter |
| 图编排 | Spring AI Alibaba `spring-ai-alibaba-agent-framework` / `graph-core` 1.1.2.2 |
| HTTP 韧性 | Resilience4j 2.3.0 + Micrometer |
| Embedding 基础 | ONNX Runtime 1.17.3 + DJL HuggingFace tokenizer |
| API 文档 | Knife4j OpenAPI 3 Jakarta starter |
| 测试治理 | JUnit, Spring Boot Test, ArchUnit, source-contract tests, Maven validate |

最新技术不会盲目升级到主干。Java 21/25、Spring Boot 4、Spring AI 2、完整 MCP runtime、A2A handoff、GraphRAG、reranker、vector store、OTel GenAI 等都必须先进入官方来源审查、兼容矩阵、证据就绪、Vue 可视化和 release gate。

## 运行与验证

```powershell
# 编译校验
mvn -q "-DskipTests" validate

# 后端默认端口 8300；如需临时改端口，可追加 --server.port=8500
mvn spring-boot:run

# 健康检查
Invoke-RestMethod http://localhost:8300/api/agent/health
```

常用外部依赖：

- kube-agent 后端默认端口：`8300`
- kube-manager 测试入口：`http://localhost:8100`
- 前端临时工作台：`F:\gitProject\kube-agent-vue`

## 文档入口

| 文档 | 用途 |
|---|---|
| `开发路线图.md` | 当前开发计划和顺序表。 |
| `docs/文档索引.md` | 当前仍保留的文档索引。 |
| `docs/项目使命与当前记忆.md` | 用户目标、阶段记忆、恢复叙事。 |
| `docs/顶级Agent架构与技术学习地图.md` | 总体架构、技术点和学习笔记。 |
| `docs/learning/以kube-agent为例的顶级Agent开发学习指南.md` | 以当前项目为案例的系统学习文档。 |
| `docs/文档治理规则.md` | 文档保留、删除、归档和恢复规则。 |
| `Tool开发规范.md` | Tool 开发规范。 |
| `docs/adr/` | 架构决策记录。 |
| `docs/tech-stack/` | 后端技术栈审计与先进技术路线。 |
| `codex-memory/kube-agent/current/当前恢复状态.md` | 新会话恢复优先入口。 |

## 恢复规则

每个有意义的后端切片完成后都要更新 `codex-memory/kube-agent/current`，并提交推送。历史大文档已从当前 docs 树清理；如需回看，优先看 Git 历史和 `codex-memory` 镜像，不把当前阅读入口重新堆满。
