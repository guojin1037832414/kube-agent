# Atlas Kube-Agent 开发路线图

> **项目**: kube-agent — K8s 训练平台 AI Agent 入口  
> **目标**: 打造顶级 Agent 系统（K8s运维只是练兵场）  
> **当前基线**: M1.5 已完成（AtlasBrain 单次决策 + StateGraph + 6 Worker + HITL SSE）  
> **版本**: Atlas v3.1  
> **最后更新**: 2026-05-18

---

## 已完成（M0 — M1.5）

| 里程碑 | Commit 范围 | 交付 | 状态 |
|--------|-------------|------|------|
| M0 地基 | `a6f0203` ~ `a0df443` | v2.x 基线：23 DomainPlugin + SSE流式 + ChatMemory + 权限网关 + 环境配置分离 | ✅ 已归档 |
| M1.0 意图L1-L3 | `baca47d` ~ `05375a1` | L1规则短路 + L2 Embedding(ONNX) + L3 LLM分类 + 分数归一化仲裁 | ✅ |
| M1.1 StateGraph | `baca47d` ~ `edb29b2` | AtlasBrain手写决策器 + StateGraph supervisor节点 + 6 ReactAgent Worker子图 + 工具桥接 + Token透传 | ✅ |
| M1.5 HITL SSE | `61cab8f` | 高危操作命令式确认 + 幂等Token + Caffeine TTL + Graph resume + 前端ElMessageBox弹窗（代码完成，未联调） | ⚠️ 后端完成，前端等M3 |

---

## 进行中 & 未来

### 🔵 M2 — 查询全覆盖与质量加固（2-3周）

**交付物：**
1. 查询类 Tool（~45个）端到端测试覆盖
2. L1 Embedding 模型加载/降级路径测试
3. L2 精确匹配短路回归测试
4. L3 LLM 分类 prompt snapshot test
5. IntentArbiter 冲突边界 case 测试
6. AtlasBrain 解析测试（Mock ChatClient）
7. ToolRegistry 权限预检测试（匿名/普通/Admin三类）
8. 删除所有硬编码 `orgId="100001"`

**验收标准：**
- 单元测试 ≥ 35 个，`mvn test` 全绿
- 查询类 E2E ≥ 95%（所有 `*_query` / `*_detail` / `*_list`）
- DELETE/scale/stop/create 写操作**不测**（留到M3）

---

### 🟢 M3 — 写操作 + HITL 前端联调（2-3周）

**交付物：**
1. 前端 `useChat.ts` 补充 `/api/agent/hitl/confirm` 完整 API 调用链
2. 高危操作端到端 HITL 流浏览器测试：hitl_request → 弹窗 → confirm → resume → done
3. ThreadLocal Token 透传 → Graph State 显式传递（解决并发安全）
4. 写入类 Tool 冒烟测试（每个大类 ≥1 个 E2E）
5. 新建 `docs/api/HITL_API_CONTRACT.md` 记录 SSE event 格式 + threadId 生命周期

**验收标准：**
- 浏览器手动验证 HITL confirm + clarify 两条路径
- 写操作 E2E 冒烟 ≥ 80%
- Graph 中无 ThreadLocal hack 代码

**约束：**
> 测试时只验证查询类和 HITL 确认流程，**创建/修改/删除操作不做全量自动化测试**，留到 M4。

---

### 🟡 M4 — Plan-and-Execute + Reflection（3-4周）

**交付物：**
1. `PlanNode`：LLM 将请求拆解为 [step1, step2, ...] 任务列表
2. `ExecuteNode`：顺序执行各 step，上一步结果注入下一步
3. `ReflectNode`：每步后 LLM 判断 "成功/重试(上限3次)/重规划/完成"
4. AtlasBrain 从单次决策升级为"多轮 Plan-Execute-Reflect 循环（MAX=10轮）"

**验收标准：**
- 多步任务串联成功："部署服务并配置Ingress" → deploy + network
- Tool失败自动重试成功率 ≥ 70%
- 新增 `docs/adr/ADR-011-Plan-Execute-Reflection.md`

---

### 🔴 M5 — 长期 Memory + MCP + 可观测性（4-6周）

**交付物：**
1. Redis/Chroma 向量存储 + 对话摘要 + 跨会话检索注入 System Prompt
2. MCP 协议适配层：`McpServerAdapter` 将 BaseTool 暴露为 MCP Tool
3. Micrometer + Prometheus → `/actuator/metrics`
4. 链路追踪 traceId 贯穿全 Graph
5. LLM token 成本统计 + SSE 连接监控
6. Agent 安全层：输入/输出 Guardrails

**验收标准：**
- 外部 Agent（Claude Desktop）可发现并调用 109 个 Tool
- `/actuator/metrics` 可查看 LLM 延迟/Token消耗/Graph耗时
- 跨对话能引用历史偏好

---

## 架构演进路线图

```
当前(M1.5)                         目标(M5)
┌──────────────┐                  ┌──────────────────────┐
│ AtlasBrain   │ 单次decide      │ AtlasBrain           │
│   (单次决策)  │ ─────────────→  │  (Plan-Execute-Reflect循环)
└──────┬───────┘                  └──────────┬───────────┘
       │                                     │
┌──────▼───────┐                  ┌──────────▼───────────┐
│ AtlasGraph   │                  │  AtlasGraph          │
│ ┌──────────┐ │                  │  ┌──────────────┐   │
│ │supervisor│ │                  │  │supervisor    │   │
│ │  (Brain) │ │                  │  │  (Brain)     │   │
│ └────┬─────┘ │                  │  └──────┬───────┘   │
│      │      │                  │         │          │
│ 6 Worker    │     →→→→→→→    │    ┌────▼────┐    │
│  ReactAgent │     (M2-M4)     │    │PlanNode  │    │
│             │                  │    └────┬────┘    │
│             │                  │    ┌────▼────┐    │
│             │                  │    │Execute  │    │
│             │                  │    └────┬────┘    │
│             │                  │    ┌────▼────┐    │
│             │                  │    │Reflect  │    │
│             │                  │    └────┬────┘    │
│             │                  │         │ loop    │
│             │                  │    ┌────▼────┐    │
│             │                  │    │ Memory  │ Redis/Chroma
│             │                  │    └─────────┘    │
└─────────────┘                  └───────────────────┘
```

---

## 前端覆盖策略

**不**做60+物理按钮。改为"三级智能快捷面板"：

1. **语义搜索直达**（搜索框自动匹配 intents）
2. **高频快捷栏**（top 8 动态推荐）
3. **分类折叠面板**（6 大 Agent 分类，动态渲染）

实现要点：从 `intents.yml` 自动生成 `command-registry.json` → 前端动态渲染 → 按钮背后走现有聊天接口，复用 L1-L3 路由 + HITL 确认。

---

## 约束铁律

1. ✅ **Milestone 与代码同步**: 每次 commit message 前缀 `feat(Mx):`，完成 Milestone 时更新本文件
2. ✅ **测试范围分层**: M2只查查询类，M3加入写操作+HITL，M4才做Plan-Execute全量验证
3. ✅ **ADR 先行**: M4/M5 新架构必须先写 ADR（Proposed → Accepted）再编码
4. ✅ **文档更新门控**: 新增/修改 Tool、API、事件类型 → 同步更新契约文档
5. ✅ **月度文档审计**: 每月第一周检查 `docs/` 与代码一致性

---

*本文件是项目唯一真相源（Single Source of Truth）。如有分叉，以此为准。*
