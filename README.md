# Atlas Kube-Agent

`kube-agent` 是构建在 `kube-manager` / `vue-kube-manager` 能力之上的 Kubernetes Agent 后端，也是一个长期 Agent 开发学习项目。目标不是“能用的生产级 Agent”，而是一期开出一个顶级 Agent Core：安全边界清晰、证据链完整、可观测、可评测、可恢复，并且每一批代码和文档都能帮助学习 Agent 工程。

当前后端主线在 `F:\gitProject\kube-agent`，前端临时工作台在 `F:\gitProject\kube-agent-vue`，正式 Vue 集成仍以 `vue-kube-manager` 的能力和风格为目标。

## 当前状态

| 范围 | 状态 | 说明 |
|---|---|---|
| Phase 1 顶级 Agent Core | 进行中 | 不降低目标，优先完成安全、编排、Tool 治理、Memory/RAG、Eval、可观测、多 Agent 审查和教学注释。 |
| NIM / HPC / Slurm / BCM | 二期暂停 | 历史 Tool/测试/证据代码仍可存在，但一期不导出 MCP、不新增 runtime authority，高风险写默认被 durable audit fail-closed 阻断。 |
| 中文代码注释 | 分批推进 | 已完成安全入口、Tool/MCP/kube-manager 执行边界、Orchestrator / Graph / ReAct / Plan 编排链路、Memory / RAG / Eval / Observability / Audit 证据链，以及 Batch 5 的 DTO / store / config、support contract、query/path/body helper、分析目录 query helper；Eval reviewed trace fixture 已补 intake 合同、repo/classpath manifest、template/schema、catalog patch review readiness 和 candidate preview；M5.85-44 新增 reviewed fixture candidate workbench，M5.85-45 新增 reviewed fixture human review package，把自动候选草稿整理成人工 Git review 包，仍不接收 caller traceId、不创建 fixture、不写 catalog、不嵌入 raw replay/report/fixture rows。 |
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
- `SafeToolExecutor` 是 Graph / ReAct / ToolCallback / Plan / Orchestrator fallback 路径进入真实 `BaseTool.execute` 的统一执行边界；M5.85-40 已用入口矩阵契约锁定 Graph `tool_call`、Plan `execute_node`、ReAct Action、Graph Bridge ToolCallback、legacy core ToolCallback 和 Orchestrator fallback 都必须构造 `SafeToolExecutionRequest` 并标注 `SafeToolExecutionSource`。
- Graph `tool_call` 现在在创建 `SafeToolExecutionRequest` 前先做入口守卫：空目标、缺失可信 orgId、伪造控制字段都会 fail-closed，并通过 SSE 展示未执行原因；`SafeToolExecutor` 仍是最终边界。
- Graph `react_node` 现在会把服务端 `traceId` 传入 ReAct `initialParams`，并在缺失可信 orgId 时于调用 LLM / Tool / kube-manager 前 fail-closed；ReAct 每轮 `Action.params` 仍只是候选业务字段。
- Graph `execute_node` 现在会在单步 READ Plan 候选创建 `SafeToolExecutionRequest` 前确认可信 orgId；PlanStep 参数不能补租户上下文，缺失可信 orgId 会 fail-closed。
- 旧 `atlasGraph` 的 `merge_result` 会优先保留已有 `final_answer`、ReAct 结果和 fail-closed `answer`，确保用户能看到最终回答或安全停止原因；这只是展示合并，不代表 Tool 成功。
- 主 `supervisorGraph` 的 delegate 节点会把专业 Agent 输出或 delegate fail-closed 原因推成 SSE `content`；这只是展示投影，真实 Tool 调用仍必须通过 ToolCallback / SafeToolExecutor。
- 主 `supervisorGraph` 的 direct_answer、tool_call、delegate、ReAct state fallback 和 ReAct content 事件现在共享最终展示内容去重契约：同一段答案只推一次，空文本和 `{}` 占位不生成前端气泡；这仍只是 SSE 展示层，不代表 Tool/HITL/audit/release/write 成功。
- MCP 当前只导出 admin-only 只读 Manifest / governance，不开放 `tools/call` 运行时执行权；NIM/HPC/Slurm/BCM 这类二期域不会进入一期 MCP 导出清单。
- kube-manager 是真实外部网络出口，默认连接 `http://localhost:8100`；业务 Tool 请求必须使用当前用户 Token，不能透明降级为 sysadmin。
- kube-manager `8100` 真实只读联调已有 opt-in smoke：默认单测不会访问外部服务；可显式提供当前用户 token/orgId，或通过 username/password 登录型 smoke 在进程内获取临时 token 并反查可信 orgId 后，依次验证节点列表、节点剩余资源、Dashboard 部署/镜像/EasyFlow 统计和 EasyFlow 列表这 6 条 GET/READ/no-HITL 链路；M5.85-38 已把同一批只读链路升级验证到 `SafeToolExecutor` 真实 Agent 执行边界，并用代表性 `AtlasToolCallback -> SafeToolExecutor` 调用证明模型 ToolCallback 入口不会绕过受保护参数过滤、权限预检和审计；M5.85-40 将 opt-in smoke 读超时改为可配置、默认 30 秒；M5.85-46 已把真实 `SafeToolExecutor` READ smoke 留下的 redacted audit 继续接到 replay/eval/candidate discovery/reviewed fixture human review package，证明真实只读链路能产出可人工 review 的候选包，但仍不创建 fixture、不写 catalog、不启用 CI/release。
- 高风险写操作默认要求 ready durable audit prewrite；HITL 确认本身不等于可执行。
- Memory 当前是按用户保存的 caller-submitted bounded summary，会做基础脱敏和截断，但不是可信 RAG prompt authority。
- Replay / Eval / Audit / Memory-RAG 证据链已补中文教学边界：它们是 admin-only 或用户隔离的只读/脱敏证据，不重新执行 Tool，不调用 MCP/LLM/kube-manager，不授予 release authority。
- 支撑层 DTO / Store / Config 也承载安全事实：`ApiResponse.success` 不是权限事实，`LoginRequest.organizationId` 只是 kube-manager 登录候选参数，`sessionId` 不是 JWT，`conversationId` 不是授权凭证，异步线程只能传播服务端可信 token/orgId 快照。
- 支撑契约层不是授权层：`ToolParameterSpec`、`SafeToolExecutionResult`、`SafeToolExecutionSource`、`AgentTraceContext`、`IntentDefinition`、`IntentResult`、默认值补参和 Tool 异常只描述 schema、结果、来源、trace、路由、草稿补参或错误信号，不能替代 Tool 权限、HITL、audit、release、MCP runtime、kube-manager 写入或部署授权。
- query/path/body helper 是 kube-manager HTTP 前最后一层轻量收敛：下载任务、课件、模板、TensorBoard、文件/存储和用户高风险变更参数必须拒绝路径/query/body 注入；资源 ID 只是定位符，充值 body 只能保留白名单字段，不能透传 token/orgId/HITL/audit/release/writeAllowed。
- 分析/目录类 query helper 只做 GET 筛选白名单：虚拟机、repository catalog、sale 产品/报价、行业应用和成本账单查询不能把 LLM/前端字段整体透传给 kube-manager；目录筛选不等于 NIM 部署，金额预估不等于下单/支付，API 历史不等于重放请求。
- Eval / Replay 已有 admin-only 确定性读模型和部分运行入口；reviewed trace fixture 目前只开放 intake 合同、repo/classpath manifest 覆盖视图、作者 template/schema、workbench catalog patch review 中的 `reviewedFixtureReadiness` 摘要、M5.85-43 的手动 candidate preview、M5.85-44 的 `/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate-workbench` 自动候选预检，以及 M5.85-45 的 `/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-human-review-package` 人工 Git review 准备包；这些入口只组合 redacted audit discovery、candidate preview、人工必填字段、复核清单和质量门预期，`readyForFixtureCommit=false`，不接收 caller traceId、不接收上传、不写 catalog、不授予 CI/release 权力；CI blocking、LLM eval、Memory/RAG retrieval eval runtime 仍必须经过 reviewed trace evidence 和单独 release 审查。
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

# 可选：真实 kube-manager 8100 只读 smoke
# 默认测试会跳过；需要先启动 kube-manager，并提供当前用户 token/orgId。
# 当前 smoke 会验证节点列表、节点剩余资源、Dashboard 部署/镜像/EasyFlow 统计和 EasyFlow 列表 6 条 GET/READ/no-HITL 链路；
# 开启后还会跑 SafeToolExecutor 真实 Agent 执行链、代表性 ToolCallback -> SafeToolExecutor 链路，
# 并把真实 READ 审计事件接到 reviewed fixture human review package 的只读数据面。
mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" `
  "-Datlas.kube-manager.smoke.enabled=true" `
  "-Datlas.kube-manager.smoke.base-url=http://localhost:8100" `
  "-Datlas.kube-manager.smoke.token=<当前用户token>" `
  "-Datlas.kube-manager.smoke.org-id=<当前组织ID>" test

# 可选：登录型 smoke。密码只放在当前 shell 环境变量里，测试不会把密码/token 写入文件或 git。
$env:ATLAS_KUBE_MANAGER_SMOKE_USERNAME="<当前用户名>"
$env:ATLAS_KUBE_MANAGER_SMOKE_PASSWORD="<当前密码>"
mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" `
  "-Datlas.kube-manager.smoke.enabled=true" `
  "-Datlas.kube-manager.smoke.base-url=http://localhost:8100" test
Remove-Item Env:\ATLAS_KUBE_MANAGER_SMOKE_USERNAME,Env:\ATLAS_KUBE_MANAGER_SMOKE_PASSWORD -ErrorAction SilentlyContinue
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
