# Atlas Kube-Agent 变更日志

> 按 Milestone 分组，记录所有已实现功能和重大变更。  
> 格式：基于 [Keep a Changelog](https://keepachangelog.com/)。  
> 里程碑前缀规范：`feat(Mx):`、`fix(Mx):`、`docs(Mx):`

---

## [M1] — 智能引擎与意图全链路

**周期**: 2026-05-14 ~ 2026-05-18  
**交付**: L1-L3 意图路由 + AtlasBrain 决策 + StateGraph 编排 + HITL SSE 后端闭环

### Added
- **L1 规则意图路由** — 关键词精确匹配，零 token 快速短路（`RuleMatcher`）
- **L2 Embedding 语义预筛** — all-MiniLM-L6-v2 ONNX Runtime 本地部署，约 150MB 内存（`EmbeddingService` + `IntentRouter`）
- **L3 LLM 意图分类** — ChatClient 结构化输出分类，兜底降级（`L3IntentClassifier`）
- ** AtlasBrain 认知决策中枢** — `BrainDecision` record 含 5 个 `actionType`：`CALL_TOOL`、`DELEGATE_AGENT`、`DIRECT_ANSWER`、`ASK_CLARIFY`、`HITL_CONFIRM`
- **StateGraph 编排引擎** — supervisor 节点 → 条件边 → 6 个专业 ReactAgent Worker（query/deploy/diag/rbac/storage/network）
- **109 个 DomainTool** — 覆盖前端 9 大模块全部按钮映射（查询类 100%，写操作类骨架）
- **HITL SSE 流式确认** — `HITLController.confirmAndResume()` + `resumeGraph()` + checkpoint 恢复，Caffeine TTL 5min + 幂等 Token
- **TimedDecisionCache** — TTL + 最大容量 + 幂等性 + 审计日志
- **ThreadLocal Token 透传** — `AuthTokenFilter` → `AsyncContextHolder` → 全链路权限感知
- **前端 SSE 多事件** — `thinking`/`tool_start`/`tool_done`/`content`/`error`/`done` + HITL `hitl_request`/`clarify` 事件

### Changed
- API 路径 `/api/v1` → `/api/agent`，对齐前端 proxy
- 后端端口 8500 → 8300
- `AtlasOrchestrator`: 删除旧 AgentBase 依赖，直接走 `ToolRegistry`

### Deprecated
- `com.kube.agent.atlas` 包名 v2.0 代码（保留 `archive/v2-m1` 分支备查）

### Security
- `@ToolPermission` + `@Isolation(SYS_ADMIN_ONLY)` + `PermissionTokenFilter` 三层权限拦截
- HITL 命令式确认（需输入"确认执行"），防止误触高危操作

---

## [M0] — Atlas v2.x 基线（归档）

**周期**: 2026-05-12 ~ 2026-05-14  
**交付**: 23 个 DomainPlugin + SSE 流式 + ChatMemory 持久化 + 权限网关

### Added
- `AtlasOrchestrator` 统一编排器（v2.0 Architecture）
- `DomainRouter` 领域路由 + 23 DomainPlugin
- `PermissionGateway` 权限网关 + `@FunctionRole`
- `MessageWindowChatMemory` Phase 4 持久化
- 环境配置分离：`application.yml` + `application-dev.yml`
- SSE 流式：Flux<ServerSentEvent> + 前端三态渲染
- MCP Server 初版：`AtlasMcpToolProvider`

---

## 未来变更（待填入）

- `[M2]` — 查询全覆盖与质量加固（待启动）
- `[M3]` — 写操作 + HITL 前端联调（待启动）
- `[M4]` — Plan-and-Execute + Reflection（待启动）
- `[M5]` — 长期 Memory + MCP + 可观测性（待启动）

---

## 参考

- 完整路线图见 `ROADMAP.md`
- 架构审计报告见 `ARCHITECTURE_AUDIT_20260518.md`
- 文档治理方案见 `DOCUMENTATION_GOVERNANCE_REPORT.md`
