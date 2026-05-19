# Atlas v3.1 架构决策记录 (ADR)

> Architecture Decision Records for Atlas v3.1

---

## ADR-001: 推倒重来而非逐步重构

**状态**: 已决策 ✅  
**日期**: 2026-05-14  
**决策**: 废弃v2全部代码，基于v2的经验从零搭建v3.1骨架  
**理由**: 
- v2的AtlasOrchestrator和三层路由过于耦合，难以支持6-Agent拆分
- 意图系统需要从"串行守卫模式"升级为"L1-L4四级分层"
- 本地Embedding需要深度集成，而非后期补丁式添加
- 项目尚未上线(仅测试开发)，重构风险可控

---

## ADR-002: 本地Embedding选择 all-MiniLM

**状态**: 已决策 ✅  
**日期**: 2026-05-14  
**决策**: 使用 sentence-transformers/all-MiniLM-L6-v2 + ONNX Runtime Java  
**对比**: 
| 方案 | 模型大小 | 内存 | 速度 | 质量 | 离线 |
|------|---------|------|------|------|------|
| all-MiniLM | ~100MB | 150MB | <10ms | 适合意图匹配 | ✅ |
| all-MiniLM v2 | ~130MB | 200MB | <10ms | 略好 | ✅ |
| OpenAI Embedding | 0 | 0 | 500ms | 最好 | ❌ |

**结论**: all-MiniLM 性价比最高，完全满足意图分类的语义匹配需求。

---

## ADR-003: 任务分级 L1-L4 定义

**状态**: 已决策 ✅  
**日期**: 2026-05-14  

| 层级 | 名称 | 触发条件 | 延迟 | Token |
|------|------|---------|------|-------|
| L1 | Embedding预筛 | 所有query首先经过 | <10ms | 0 |
| L2 | 规则精确匹配 | Embedding score ≥ 0.85 或关键词score=100 | <1ms | 0 |
| L3 | LLM语义分类 | L1/L2未命中时触发 | 200-500ms | ~500 |
| L4 | 模糊兜底 | LLM失败/超时/不可用时 | <1ms | 0 |

**关键**: L1不是替代L2，而是增强——先用Embedding做语义扩展，再用规则做精确匹配。

---

## ADR-004: Agent 拆分粒度

**状态**: 已决策 ✅  
**日期**: 2026-05-14  
**决策**: 6个Agent（Query/Diag/Deploy/RBAC/Storage/Network）  
**排除方案**: 
- 4个Agent：太粗，Deploy+Network+Storage合并后职责不清
- 8个Agent：太细，运维成本高于收益

**原则**: 按前端模块职责边界拆分，每个Agent对应2-3个前端模块。

---

## ADR-005: HITL 方案选择 C

**状态**: 已决策 ✅  
**日期**: 2026-05-14  
**决策**: C方案（两者结合，动态分级）  

```
风险等级    确认方式                    示例
─────────────────────────────────────────────────────────
P0-高危     命令式确认                  "请输入'确认删除production'"
P1-中危     前端弹窗 + 详情预览          [是 / 否 / 查看影响范围]
P2-低危     前端弹窗                    [是 / 否]
P3-查询     免确认，直出结果              立即返回
```

---

## ADR-006: Spring AI 版本与协议

**状态**: 已决策 ✅  
**日期**: 2026-05-14  
**决策**: Spring AI 1.1.6 + spring-ai-openai-starter  
**理由**: 
- 公司new-api代理是OpenAI兼容协议，不是Anthropic原生
- Spring AI 1.1.x 相比 1.0.0-M6 有大量稳定性改进
- 结构化输出 (Structured Output) 支持更好

---

## ADR-007: 开发方法论

**状态**: 已决策 ✅  
**日期**: 2026-05-14  
**决策**: 专家会诊 → 最优方案 → 编码 → Review → 测试 → 记录 → GitLab  
**角色分工**: 
- Hermes = 项目经理/架构师（方案制定、任务拆解、代码审计）
- Claude Code = 编码实现（文件操作、具体代码编写）
- 多专家 = 技术调研（并行调研+讨论论证）

---

## ADR-008: 引入 Spring AI Alibaba 作为核心 Agent 框架

**状态**: 已决策 ✅  
**日期**: 2026-05-14  
**决策**: 采用 Spring AI Alibaba v1.1.2.2 作为 Atlas v3.1 核心 Agent 框架  
**理由**: 
- 专家会诊（第2轮）深度调研确认：Spring AI Alibaba 已完整实现 P2 阶段规划的所有能力
- 框架提供：ReactAgent（多步推理）、StateGraph（图编排）、HITL（人工确认）、Checkpoint（状态持久化）
- 社区认可度高（9,596 Stars），阿里巴巴生产验证，持续活跃开发
- 基于 Spring AI 构建，与现有技术栈完全一致，迁移成本可控
- 支持 OpenAI 兼容协议（公司代理可用）
- 相比手写所有组件，开发周期从 4-6 周缩短至 2 周

**实施修正（2026-05-18）**:
- 实际未使用 `ReactAgent.builder()` 等假设性 API（框架文档与源码存在偏差）
- 最终采用 **手写 AtlasBrain + StateGraph `node_async`/`addConditionalEdges`** 实现决策循环
- 框架的 `StateGraph` / `CompiledGraph.stream()` / `MemorySaver` 被实际使用并验证有效
- 原 `P2_ARCHITECTURE_SPRING_AI_ALIBABA.md` 已归档，保留 ADR-008 记录决策历史

**废弃的旧方案**: 手写的 ReActEngine、AgentGraph、AgentState Record、Checkpoint 实现
**保留的组件**: BaseTool 体系、IntentRouter L1-L4、ToolRegistry 权限感知、SSE 流式
**实际使用**: StateGraph API、CompiledGraph.stream()、MemorySaver、OverAllState

**详细文档**: [ADR-008-SPRING_AI_ALIBABA.md](ADR-008-SPRING_AI_ALIBABA.md)  
**原架构方案**: [docs/archive/ARCHIVED_20260519_P2_ARCHITECTURE_SPRING_AI_ALIBABA.md](../../archive/ARCHIVED_20260519_P2_ARCHITECTURE_SPRING_AI_ALIBABA.md) (已归档)

---

*后续ADR将持续追加...*
