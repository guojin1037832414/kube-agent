# ADR-008: 采纳 Spring AI Alibaba 作为核心 Agent 框架

**状态**: 已决策 ✅  
**日期**: 2026-05-14  
**提出人**: Hermes (架构师)  
**审批人**: 用户 (哥哥)  

---

> 2026-06-12 现状说明：这是历史 ADR，记录“引入 Spring AI Alibaba / StateGraph”这一方向的决策。
> 当前实现并不是完全用框架 `ReactAgent` 替代所有手写逻辑：`supervisorGraph` / `atlasGraph`
> 仍使用 Spring AI Alibaba Graph 能力，但 kube-agent 也保留了手写 `AtlasBrain` 与手写
> `ReActEngine`，且所有 Tool 运行时必须经 `SafeToolExecutor`。因此本文中“完全替换手写 ReActEngine /
> HITL / Checkpoint”的表述应理解为当时的目标，不是当前代码事实；当前事实以 ADR-010、源码和测试为准。

## 背景

Atlas v3.1 P2 阶段原本规划自行实现：
- ReActEngine 状态机（手写 OBSERVE→THINK→ACT loop）
- AgentGraph 图编排（模拟 LangGraph StateGraph）
- AgentState Record + Checkpoint 状态管理
- HITL 人工确认状态机

在专家会诊（第2轮）过程中，通过深度调研发现 **Spring AI Alibaba**（alibaba/spring-ai-alibaba）已经完整实现了上述所有能力，且是生产级、工业级框架（9,596 Stars，持续活跃开发）。

---

## 决策

**采纳 Spring AI Alibaba 作为 Atlas v3.1 核心 Agent 框架。**

---

## 技术验证结果

| 验证项 | 结果 | 说明 |
|--------|------|------|
| 版本号 | v1.1.2.2 | 2026-03-10 发布，今日仍在更新代码 |
| Stars | 9,596 | 社区高度认可 |
| Spring AI 依赖 | 1.1.2 | 我们当前使用 1.1.6，需确认 BOM 兼容性 |
| Spring Boot 要求 | 3.5.8 | 我们当前使用 3.4.4，需评估升级影响 |
| Java 要求 | 17+ | ✅ 我们已满足（GraalVM 17） |
| 描述 | Agentic AI Framework for Java Developers | 完美匹配需求 |
| ReactAgent | ✅ 内建 | spring-ai-alibaba-agent-framework 模块 |
| Graph API | ✅ 内建 | spring-ai-alibaba-graph-core 模块 |
| Multi-Agent 编排 | ✅ Sequential/Parallel/Routing/Loop | 内置 4 种编排模式 |
| HITL | ✅ 内置 | 人工确认内建支持 |
| 状态持久化 | ✅ Checkpoint | 支持 MemorySaver / Redis / 文件 |
| Streaming SSE | ✅ 原生支持 | Graph 有 StreamingExample |
| MCP 支持 | ✅ v0.14.0 | 与 Atlas 未来 MCP Server 方向一致 |
| A2A (Agent-to-Agent) | 🧪 兼容矩阵 / 实验方向 | Spring AI Alibaba 生态存在分布式 Agent / Nacos 方向，但 kube-agent 当前尚未实现 Agent Card、JSON-RPC A2A adapter 或跨 Agent 互操作协议 |

> 2026-06-09 修订：原表格把 A2A 写成“已支持”过于乐观。准确状态是：框架生态有相关方向，kube-agent 目前仍以内部多专家角色、Graph 编排、`SafeToolExecutor`、trace 和 audit 为主线；A2A 进入兼容矩阵，不能替代当前安全执行边界。

### 核心模块结构

```
spring-ai-alibaba (根 POM)
├── spring-ai-alibaba-agent-framework    ← ReactAgent + 编排模式
├── spring-ai-alibaba-graph-core         ← Graph API + 状态管理
├── spring-ai-alibaba-graph-checkpoint   ← 持久化存储
└── examples/
    ├── chatbot                            ← 基础聊天示例
    ├── multiagent-patterns                ← 多 Agent 编排示例
    ├── documentation/graph/               ← Graph 入门教程
    │   ├── QuickStartExample.java
    │   ├── MemoryExample.java
    │   ├── PersistenceExample.java
    │   └── StreamingExample.java
    └── deepresearch                       ← 深度研究 Agent 示例
```

### 关键发现

1. **不必手写 ReActEngine**
   - Spring AI Alibaba 的 `ReactAgent` 类已完整实现 Thought→Action→Observation 循环
   - 支持 Tool 调用失败自动重试、循环检测防死循环

2. **不必手写 AgentGraph**
   - `StateGraph` API 与 LangGraph 概念一致（addNode/addEdge/compile/invoke）
   - 支持条件路由、嵌套图、并行执行
   - 有完整的 Java 文档和示例

3. **不必手写 HITL 状态机**
   - HITL 是框架内建能力，通过配置即可启用
   - 支持打断-恢复模式（前端保持 SSE 连接）

4. **不必手写状态持久化**
   - `MemorySaver`、`RedisSaver`、`FileSaver` 已提供
   - 支持 Checkpoint 回滚和恢复

---

## 与原有方案的对比

| 维度 | 原 P2 方案（手写） | Spring AI Alibaba（引入） |
|------|-------------------|--------------------------|
| ReActEngine | 手写状态机 ~300行 | ✅ ReactAgent 内建 |
| AgentGraph | 手写图编排 ~200行 | ✅ StateGraph API |
| AgentNode 接口 | 手写 ~50行 | ✅ NodeAction 接口 |
| AgentState Record | 手写 ~30行 | ✅ OverAllState |
| Checkpoint | 手写持久化 | ✅ MemorySaver/RedisSaver |
| HITL | 手写 PAUSE 状态 | ✅ 内建 Human-in-the-loop |
| 流式 SSE | 已有基础设施 | ✅ StreamingExample |
| Tool 循环 | 手写 while loop | ✅ 框架自动处理 |
| 多 Agent 编排 | 需手写 | ✅ Sequential/Parallel/Routing/Loop |
| Context 工程 | 无 | ✅ 动态 Tool 选择、上下文压缩 |
| 可观测性 | 无 | ✅ OpenTelemetry 集成 |
| 生产级稳定性 | 需长期打磨 | ✅ 阿里巴巴生产验证 |

---

## 风险与应对

| 风险 | 等级 | 应对策略 |
|------|------|---------|
| Spring Boot 3.4.4 → 3.5.8 升级 | 🟡 中 | 先验证兼容性，逐步升级 |
| Spring AI 1.1.6 → 1.1.2 降级？ | 🟡 中 | 确认 BOM 是否强制 1.1.2，尝试排除依赖强制 1.1.6 |
| BaseTool 体系桥接 | 🟡 中 | BaseTool 需包装为 ReactAgent 可用的 Tool（@Tool 注解已兼容） |
| 学习成本 | 🟡 中 | 通过 Examples + 官方文档学习，了解框架内部实现 |
| 框架绑定 | 🟡 中 | 接受。Spring AI Alibaba 基于 Spring AI，迁移成本可控 |
| 公司代理兼容性 | 🟢 低 | 框架支持 OpenAI 兼容协议，配置 Base URL 即可 |
| SSE 前端兼容性 | 🟢 低 | Graph 有原生 StreamingExample，适配成本低 |

---

## 实施路线（修订版 P2）

### Phase 1: 引入验证（1-2天）
- [ ] 添加 Maven 依赖（spring-ai-alibaba-agent-framework + spring-ai-alibaba-graph-core）
- [ ] 解决版本冲突（Spring AI 1.1.6 vs 1.1.2、Spring Boot 3.4.4 vs 3.5.8）
- [ ] 编写最小可运行 ReactAgent PoC（调用一个 BaseTool）
- [ ] 验证 OpenAI 代理连通性（http://124.74.245.75:3000）

### Phase 2: 基础迁移（2-3天）
- [ ] AtlasOrchestrator 接入 StateGraph
- [ ] 用 ReactAgent 替换手写的 ReActEngine
- [ ] IntentRouter L1-L4 接入 Graph Entry Node
- [ ] SSE 流式对接 Graph Streaming API

### Phase 3: Agent 完整化（3-4天）
- [ ] QueryAgent 基于 ReactAgent 实质化
- [ ] 6 个 Agent 接入 Graph 编排
- [ ] HITL 接入高危操作流程
- [ ] Checkpoint 持久化（Redis）

### Phase 4: 功能补齐（2-3天）
- [ ] Multi-Agent 编排示例（Sequential Routing）
- [ ] Context Engineering（动态 Tool 选择）
- [ ] 可观测性（OpenTelemetry）
- [ ] MCP Server 桥接

---

## 与旧 P2 方案的对比

**旧方案（LangGraph 模拟）** 已被完全替换为 **Spring AI Alibaba 原生框架**。

保留的旧组件：
- ✅ BaseTool 体系（23 个域 Operation）
- ✅ IntentRouter L1-L4（四级意图路由）
- ✅ ToolRegistry 权限感知
- ✅ SSE 前端流式（对接 Graph Streaming API）
- ✅ AuthToken 透传机制

废弃的旧组件：
- ❌ 手写的 ReActEngine（替换为框架 ReactAgent）
- ❌ 手写的 AgentGraph（替换为框架 StateGraph）
- ❌ 手写的 AgentState（替换为框架 OverAllState）
- ❌ 手写的 Checkpoint（替换为框架 Saver）
- ❌ MAPof() 硬编码（修复：Tool 由 LLM 自动选择+传参）

---

## 相关文档

- [ADR-008-SpringAIAlibaba选型决策.md](ADR-008-SpringAIAlibaba选型决策.md)
- [Spring AI Alibaba 官方文档](https://java2ai.com/docs/frameworks/agent-framework/quick-start/)
- [Spring AI Alibaba Graph 文档](https://java2ai.com/docs/frameworks/graph-core/quick-start/)
- 旧版 P2 架构方案当前已从主 docs 树清理；如需考古，请使用 Git 历史或 `codex-memory` 历史快照。

---

*决策记录于 2026-05-14，由 Hermes 起草，用户（哥哥）审批通过。*
