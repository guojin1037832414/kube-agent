# Atlas v3.1 开发指南

## 快速开始

### 1. 环境要求
- Java 17+ (推荐 GraalVM)
- Maven 3.8+
- WSL (Windows Subsystem for Linux)

### 2. 首次启动

```bash
# 进入项目目录
cd ~/kube-agent

# 编译
mvn clean compile

# 启动 (开发模式)
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.ai.openai.api-key=YOUR_KEY"

# 或使用打包方式 (推荐，避免WSL僵尸进程)
mvn clean package -DskipTests
java -jar target/kube-agent-3.1.0-SNAPSHOT.jar \
  --spring.ai.openai.api-key=YOUR_KEY
```

### 3. 本地Embedding模型首次下载

首次启动时会自动从 HuggingFace 下载模型到 `~/.atlas/models/all-MiniLM/`：
```
~/.atlas/models/all-MiniLM/
├── model.onnx           # ONNX模型文件 (~90MB)
└── tokenizer.json       # 分词器配置
```

如果网络受限，可手动下载后放入该目录。

### 4. API密钥配置

```bash
# 方式1: 环境变量 (推荐)
export ATLAS_LLM_API_KEY="sk-REPLACE_WITH_YOUR_KEY"

# 方式2: 启动参数
--spring.ai.openai.api-key=sk-...

# 方式3: ~/.hermes/secure_config.yml (如果你用Hermes管理)
```

### 5. 开发规范

- 所有新增类必须有 **中文注释**
- 提交前必须跑通 `mvn test`
- 修改记录到 `docs/REVIEW_LOG.md`
- 遵循 **专家会诊 → 编码 → Review → 测试 → 记录 → GitLab+GitHub 双推** 流程
- 文档更新门控：新增/修改 Tool、API、Config、Prompt → 必须同步更新契约文档

---

## 模块开发顺序 (Milestone)

```
M0: 地基 — v2.x 基线、23 Plugin、ChatMemory、SSE 流式
M1: 智能引擎 — L1-L4 意图路由、AtlasBrain 决策、StateGraph 编排、6 Worker、109 Tool
  M1.5: HITL SSE 后端闭环（前端弹窗代码完成，待 M3 联调）
M2: 查询全覆盖与质量加固 — 35+ 单元测试、Query E2E ≥95%、硬编码清理
M3: 写操作 + HITL 前端联调 — ThreadLocal→State 重构、浏览器验证 HITL
M4: Plan-and-Execute + Reflection — 多步任务拆解、自我修正循环
M5: 长期 Memory + MCP + 可观测性 — Redis/Chroma、Micrometer、Guardrails
```

> 详细路线图见项目根目录 `ROADMAP.md`

---

## 测试账号

- sysadmin / SuperAdmin@2035
- zhaotiandi / ninePwd!

---

## 常见问题

### WSL僵尸进程
- 永远不要用 `mvn spring-boot:run` 跑长服务
- 永远用 `java -jar` 方式启动
- 检查端口: `netstat -ano | grep 8300` (Windows)
- 杀进程: `taskkill /PID <pid> /F` (Windows)

### kube-manager连接
- WSL Mirrored模式: `localhost:8100` 直连Windows主机
- 检查状态: `curl http://localhost:8100/api/login -X POST ...`
- CLOSE_WAIT风暴 = 后端线程池耗尽，不是网络问题

---

## NIM 写链路安全门学习笔记

`nim_create` 当前仍然保持 `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`，不得在审计波次中直接访问真实 kube-manager `8100` 或执行 `POST /api/{orgId}/deployment`。

未来真正开放 NIM 创建前，至少需要连续满足这些服务端可信关卡：

- trusted policy provider 读取真实 license、角色和组织事实，且不能相信 Tool 入参里的自报权限。
- creation gate 进入 `READY_FOR_SERVER_CONFIRMED_WRITE`，并明确禁止 preflight preview 或 fallback Tool 直通写入。
- HITLController 注入 target 精确为 `nim_create` 的服务端确认，调用方参数里的 `confirmed=true` 不可信。
- audit context 先被 durable audit writer 持久化，并返回 `DURABLE_RECORDED + DURABLE_AUDIT_LOG` receipt。
- mature `kube-manager` 的 `sys_log` 只能作为 durable audit storage 候选证据；它是通用系统日志，不能直接替代 NIM 专用 pre-write audit receipt。
- 未来 NIM audit writer 必须从可信服务端 principal 获取 username/orgId/userId，只写脱敏 params/body 摘要，并区分 pre-write intent 与 post-write result。
- 当前 `NimCreateDurableAuditWriterPlanSupport` 只生成两阶段 writer plan 和 record templates，仍然 `IMPLEMENTATION_HOLD`；计划层不能替代 `DURABLE_RECORDED` receipt。
- 真实 dedicated writer 必须先通过 storage availability gate，再持久化 pre-write intent，POST 结束后持久化 post-write result，两条记录都确认 durable 后才允许签发 receipt。
- 当前 `NimCreateDurableAuditStorageAvailabilityGateSupport` 只生成未来 probe plan，仍然 `storageProbeExecuted=false`、`storageAvailable=false`；真实可用性探测必须在 dedicated writer 服务端边界内完成。
- 当前 `NimCreateDedicatedDurableAuditWriterBoundarySupport` 只生成未来 `NimDurableAuditWriter` 的边界计划和测试替身契约，仍然 `IMPLEMENTATION_HOLD`；test double 只能验证顺序、digest/identity binding 和 fail-closed blocker，不能声称 `storageAvailable=true`、`preWritePersisted=true`、`postWritePersisted=true` 或 `DURABLE_RECORDED`。
- 当前 `NimCreateDurableAuditWriterInterfaceSpecSupport` 只定义未来 `NimDurableAuditWriter` 的 request/response/method/failure/test-double 规格，仍然 `IMPLEMENTATION_HOLD`；接口规格不能替代真实 Java 接口、真实存储 probe、durable ack 或 release credential。
- 当前 `NimCreateDurableAuditReceiptSchemaSupport` 只定义未来 `StorageAvailabilityProbeReceipt`、`PreWriteDurableAck`、`PostWriteDurableAck` 和 `DurableAuditReceipt` 的 schema，仍然 `IMPLEMENTATION_HOLD`；schema 不是 ack instance，也不是 `DURABLE_RECORDED` release credential。即使调用方传入空的 typed ack/receipt 对象，也必须按 forged success claim 拒绝。
- 当前 `NimCreateDurableAuditReceiptValidationGateSupport` 只定义未来 `NimDurableAuditReceiptValidator` 的 validation gate 规则，仍然 `IMPLEMENTATION_HOLD`；validation plan 不是 validation pass，不能让 `releaseEligible` 或 `writeExecutionAllowed` 变为 true。
- write body rebuilder 只能从已审计 NIM 状态重建白名单 DeploymentDTO，不得复用 preview body 引用。
- POST request spec adapter 只能从 body rebuild report 编译 `POST /api/{orgId}/deployment` 规格，且 `sideEffect=NONE`、无调用方 header、无 Authorization、无真实 NGC/NIM API Key。
- write execution handoff 必须在真实 durable writer 前绑定 request spec digest、body digest、durable audit receipt、服务端派生幂等键和写后 readiness handoff；它仍然不是 HTTP 执行器。
- durable write executor 当前只有合同壳，合法 handoff 也只能进入 `IMPLEMENTATION_HOLD`，不得产生 `writeExecuted=true` 或部署 ID。
- 状态机现在必须看到 durable write executor report；缺失时返回 `DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY`，看到当前 shell report 时仍返回 `DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD`。
- 当前版本任何 `writeExecuted=true`、`deploymentId`、`deploymentUid`、`writeResult` 或 `postWriteReadinessTriggered=true` 都是未审计成功声明，不能成为放行条件。
- readiness executor 必须只读轮询并返回 READY，不能在 readiness 阶段调用 chat/embedding 写接口。
- 最后还需要代码级 release switch 显式打开，才能考虑接入真实 durable write executor。

这条链路的教学重点是：顶级 Agent 不靠“相信中间对象已经安全”来放行，而是把每一段证据都变成可测试、可复算、可审计的契约。

### M5.21-58 validation result / release decision migration note

- `NimCreateDurableAuditValidationResultMigrationSupport` only defines a future migration plan for `NimDurableAuditReceiptValidationResult` and `NimDurableAuditReleaseDecision`; it does not create real DTOs, Beans, validators, storage writes, or release credentials.
- Learning distinction: schema describes the expected evidence shape; validation gate describes how evidence must be checked; validation result is a future server-issued fact that the checks passed; release decision is a future server-issued permission to let the write executor proceed.
- Current migration plan remains `IMPLEMENTATION_HOLD`; `validationStatus=NOT_RUN_UNTIL_REAL_RECEIPT`, `releaseEligible=false`, `releaseDecisionAccepted=false`, `releaseCredentialIssued=false`, and `writeExecutionAllowed=false`.
- Caller supplied `validationResult`, `releaseDecision`, or legacy `auditReceipt.releaseEligible=true` is treated as a forged release claim, even when the supplied object is empty.
- Future real write release must bind the M5.21-57 validation plan digest, receipt schema digest, source audit event digest, typed evidence digests, trusted principal, and code release switch.
- This wave keeps `nim_create` at `httpMethod=NONE + PLACEHOLDER + requiresConfirmation=true`; no real `8100`, no `POST /api/{orgId}/deployment`, no Elasticsearch, no `ISysLogService`, and no `sys_log` write.
