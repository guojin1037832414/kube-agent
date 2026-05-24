# Atlas Kube-Agent

> K8s 训练平台的 AI 自然语言代理入口 — 打造顶级 Agent 系统，K8s 运维只是练兵场。

```
用户输入 → L1/L2/L3 意图路由 → AtlasBrain 决策 → StateGraph 编排
                                                    ↓
                    ┌──────┬──────┬──────┬──────┬────┴─────┬──────┐
                    │query │deploy│ diag │ rbac │ storage  │network│
                    │Agent │Agent │Agent │Agent │  Agent   │ Agent │
                    └──┬───┴──┬───┴──┬───┴──┬───┴────┬─────┴──┬───┘
                       │      │      │      │        │        │
                    ┌──┴──────┴──────┴──────┴────────┴────────┴──┐
                    │          109 个 DomainTool (单例/有状态)      │
                    └───────────────────────────────────────────────┘
                                       ↓
                                     HITL（高危操作人工确认）
                                       ↓
                                   SSE 流式输出
```

## 技术栈

| 层 | 技术 |
|---|---|
| 框架 | Spring Boot 3.4.4 + Spring AI 1.1.6 + Spring AI Alibaba 1.1.2.2 |
| JDK | 17 (GraalVM) |
| LLM | moonshotai/kimi-k2.6 (OpenAI-compatible proxy) |
| Embedding | all-MiniLM-L6-v2 (ONNX Runtime，本地 CPU) |
| 编排 | Spring AI Alibaba StateGraph + ReactAgent |
| 前端 | Vue 3.5.13 + Vite 6.3.5 + TypeScript + Pinia |
| 端口 | agent 8300 / manager 8100 / 前端 dev 3000 |

## 快速开始

```bash
# 后端编译
mvn clean package -DskipTests

# 后端启动（需指定 LLM API key）
java -jar target/kube-agent-3.1.0-SNAPSHOT.jar \
  --spring.ai.openai.api-key=YOUR_KEY_HERE

# 前端（另开终端）
cd /mnt/f/gitProject/kube-agent-vue
npm run dev
```

健康检查：`GET http://localhost:8300/api/agent/health`

## 当前状态

| 里程碑 | 完成度 | 说明 |
|--------|--------|------|
| M0 基线 | ✅ 已归档 | v2.x 基线 + 23 Plugin + ChatMemory |
| M1 智能引擎 | ✅ 已完成 | L1-L3 意图 + AtlasBrain + StateGraph + 6 Worker + 109 Tool |
| M1.5 HITL SSE | ⚠️ 后端完成 | confirm/resume/checkpoint 全通，前端代码写好未浏览器联调 |
| **M2 查询全覆盖** | **🔵 即将启动** | 35+ 单元测试 + 查询类 E2E ≥ 95% |
| M3 写操作+HITL联调 | ⏳ | HITL confirm 浏览器验证 + ThreadLocal→State重构 |
| M4 Plan-Execute | 🟡 最小安全闭环 | PLAN → plan_node → PlanEngine → execute_node(fail-closed)，tool_call 已复用 SafeToolExecutor |
| M5 Memory+MCP | ✅ 最小闭环 | MCP 安全 Manifest + 最近摘要 Memory + Micrometer/Actuator 指标 |

> **测试约束**: M2 只验证查询类 Tool，创建/修改/删除/高危操作留到 M3/M4。

## 架构关键决策

- **ADR-008**: 选用 Spring AI Alibaba StateGraph（已采纳）
- **ADR-009**: StateGraph + ReactAgent 迁移完成（即将新建）
- **ADR-010**: AtlasBrain 手写决策器替代 ReactAgent Supervisor（即将新建）

完整 ADR 列表见 `docs/adr/`。

## 测试

```bash
# 全量测试
mvn test
```

## 文档导航

| 文件 | 说明 |
|------|------|
| `ROADMAP.md` | **开发路线图**（唯一真相源） |
| `CHANGELOG.md` | 变更日志 |
| `TOOL_DEV_SPEC.md` | DomainTool 开发规范 |
| `ARCHITECTURE_AUDIT_20260518.md` | 架构审计 & 行业调研报告 |
| `docs/adr/` | 架构决策记录 (ADR) |
| `docs/v3.1/` | v3.1 技术设计文档 |

## 安全 & 合规

- 后端权限：`@Isolation(SYS_ADMIN_ONLY)` + `@ToolPermission` + ThreadLocal Token 透传
- 高危操作：命令式 HITL 确认（需输入"确认执行"）
- Tool 执行：Graph tool_call 统一经过 SafeToolExecutor，执行前过滤受保护参数并调用 HitlGuard，execute_node 当前默认 fail-closed
- 幂等性：confirmToken + Caffeine TTL 5min + 审计日志

## 仓库

- GitLab: `http://cloud.zentek.com.cn:8686/jguo/kube-agent-client.git`
- GitHub: `https://github.com/guojin1037832414/kube-agent.git`

---

*最后更新: 2026-05-25 | Atlas v3.1 / M4-PX.3*
