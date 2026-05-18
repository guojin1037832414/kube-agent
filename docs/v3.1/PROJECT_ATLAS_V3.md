# Atlas v3.1 — AI Agent for kube-manager

> **版本**: 3.1.0-SNAPSHOT
> **日期**: 2026-05-14
> **状态**: ✅ M1.5 已完成（AtlasBrain 单次决策 + StateGraph + 6 Worker + HITL SSE 后端）
> **当前里程碑**: M2 查询全覆盖（即将启动）
> **架构**: AtlasBrain + StateGraph + 6 ReactAgent Worker + L1-L4 意图路由 + HITL SSE
> **路线图**: 见 `ROADMAP.md`  

---

## 一、架构总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Atlas v3.1 架构全景                          │
├─────────────────────────────────────────────────────────────────────┤
│  [用户输入] → [L1 Embedding零token预筛] → [意图分类层]                │
│                                                   ↓                 │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    6 大专业 Agent 路由层                        │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │  │
│  │  │ Query   │ │ Diag    │ │ Deploy  │ │ RBAC    │ │ Storage │ │  │
│  │  │ Agent   │ │ Agent   │ │ Agent   │ │ Agent   │ │ Agent   │ │  │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ │  │
│  │  ┌─────────┐                                                  │  │
│  │  │ Network │                                                  │  │
│  │  │ Agent   │                                                  │  │
│  │  └─────────┘                                                  │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              ↓                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │              HITL 人机回环确认层 (C方案混合)                     │  │
│  │  • 高危操作 → 命令式确认 ("请输入'确认删除production'")          │  │
│  │  • 普通操作 → 前端弹窗确认                                      │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              ↓                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │              ReAct 多步推理引擎 + MCP Server 工具调用            │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              ↓                                      │
│  [SSE流式输出] ← [AtlasOrchestrator 统一编排] ← [kube-manager API]  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、核心组件

### 2.1 意图分类层 (Intent Classification)

| 层级 | 机制 | 延迟 | Token消耗 | 说明 |
|------|------|------|-----------|------|
| **L1** | 本地Embedding语义预筛 | <10ms | 0 | all-MiniLM ONNX Runtime, 100MB模型 |
| **L2** | 规则层精确匹配 (score=100) | <1ms | 0 | 关键词/正则 优先级评分 |
| **L3** | LLM语义分类 | 200-500ms | ~500 | 仅当L1/L2未命中时触发 |
| **L4** | 模糊规则兜底 | <1ms | 0 | LLM失败时fallback |

**降级策略**: L1 → L2 → L3 → L4，任何一级命中即返回，LLM仅在真正需要语义理解时调用。

### 2.2 六大专业Agent

| Agent | 职责 | 覆盖前端模块 |
|-------|------|-------------|
| **QueryAgent** | 资源查询、状态查看、信息检索 | 资源监控、运营看板、GPU信息、镜像资源 |
| **DiagAgent** | 故障诊断、日志分析、异常排查 | 资源监控(异常部分)、运营看板 |
| **DeployAgent** | 应用部署、服务创建、配置下发 | 标准实例、NIM服务、分布式计算 |
| **RBACAgent** | 权限管理、用户/角色操作 | 账户管理 |
| **StorageAgent** | 存储卷、PVC、快照管理 | 存储管理 |
| **NetworkAgent** | 网络配置、带宽策略、域名 | 标准实例(网络部分) |

### 2.3 本地Embedding模型

- **模型**: `sentence-transformers/all-MiniLM-L6-v2`
- **推理引擎**: ONNX Runtime Java API
- **内存占用**: ~150MB
- **模型文件**: `~/.atlas/models/all-MiniLM/` (首次启动自动下载)
- **用途**: 
  - 用户query与意图描述向量的余弦相似度匹配
  - 实现真正的"零token"预筛
  - 替代v2的纯关键词匹配，提升口语化query命中率

### 2.4 HITL (Human-in-the-Loop) — C方案

```
操作类型          确认方式                          示例
─────────────────────────────────────────────────────────────────
高危操作(P0)      命令式确认                         "请输入'确认删除namespace production'"
普通操作(P1-P2)   前端弹窗确认                        [是/否/查看详情]
查询操作(P3)      免确认，直接执行                     立即返回结果
```

---

## 三、技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.4.4 | 主体框架 |
| Spring AI | 1.1.6 | AI抽象层，OpenAI兼容协议 |
| Java | 17 (GraalVM) | JDK版本 |
| ONNX Runtime | 1.17.3 | 本地Embedding推理 |
| Knife4j | 4.5.0 | API文档 |
| LLM Provider | 公司new-api | `http://124.74.245.75:3000` |
| LLM Model | moonshotai/kimi-k2.6 | 主模型 |

---

## 四、实施路线图 (P0 → P4)

### 🔴 P0: 地基重建 (当前阶段)
- [ ] 新项目骨架搭建 (pom.xml + 包结构)
- [ ] 本地Embedding服务 (ONNX Runtime + all-MiniLM)
- [ ] L1/L2 意图预筛层 (Embedding + 规则混合)
- [ ] 基础SSE流式输出框架
- [ ] 与kube-manager基础连通性验证

### 🟠 P1: 任务分级 + Tool升级
- [ ] L1-L4分级策略完整实现
- [ ] 前端9大模块 Function Calling 全覆盖
- [ ] 权限感知 (SYS_ADMIN_ONLY 适配)
- [ ] 前端默认参数回填 (创建操作时)

### 🟡 P2: Agent拆分 + 推理引擎
- [ ] 6大Agent独立模块
- [ ] ReAct多步推理引擎
- [ ] MCP Server 集成
- [ ] AtlasOrchestrator 统一编排

### 🟢 P3: 安全治理 + HITL
- [ ] HITL C方案完整实现
- [ ] 高危命令式确认机制
- [ ] 操作审计日志
- [ ] Agent间安全边界

### 🔵 P4: 功能补齐 + 监控
- [ ] 剩余边缘功能覆盖
- [ ] 监控大盘 /status.html
- [ ] 性能指标收集
- [ ] GitLab CI/CD 集成

---

## 五、目录结构

```
kube-agent/
├── docs/v3.1/
│   ├── PROJECT_ATLAS_V3.md          # 本文档 (总纲)
│   ├── ARCHITECTURE_DECISIONS.md    # 架构决策记录 (ADR)
│   ├── DEVELOPMENT_GUIDE.md         # 开发指南
│   ├── API_SPEC.md                  # API接口规范
│   └── REVIEW_LOG.md                # 代码Review记录
│
├── src/main/java/com/atlas/
│   ├── KubeAgentApplication.java    # Spring Boot入口
│   │
│   ├── intent/                      # 意图系统 (L1-L4)
│   │   ├── embedding/               # 本地Embedding服务
│   │   ├── rule/                    # 规则匹配层
│   │   ├── llm/                     # LLM分类层
│   │   └── IntentRouter.java        # 意图路由器
│   │
│   ├── agent/                       # 6大专业Agent
│   │   ├── core/                    # Agent抽象基类
│   │   ├── query/                   # QueryAgent
│   │   ├── diag/                    # DiagAgent
│   │   ├── deploy/                  # DeployAgent
│   │   ├── rbac/                    # RBACAgent
│   │   ├── storage/                 # StorageAgent
│   │   └── network/                 # NetworkAgent
│   │
│   ├── orchestrator/                # 编排层
│   │   ├── AtlasOrchestrator.java   # 统一编排器
│   │   ├── TaskClassifier.java      # 任务分级器
│   │   └── StreamingEmitter.java    # SSE流式发射器
│   │
│   ├── hitl/                        # 人机回环
│   │   ├── HITLGuard.java           # HITL守卫
│   │   ├── ConfirmationService.java # 确认服务
│   │   └── RiskClassifier.java      # 风险分级器
│   │
│   ├── tool/                        # 工具层 (Function Calling)
│   │   ├── core/                    # 工具抽象/注册
│   │   ├── kube/                    # kube-manager API封装
│   │   └── mcp/                     # MCP Server集成
│   │
│   ├── react/                       # ReAct推理引擎
│   │   ├── ReActEngine.java
│   │   ├── ThoughtChain.java
│   │   └── ActionExecutor.java
│   │
│   ├── config/                      # 配置类
│   └── common/                      # 公共工具/常量
│
├── src/main/resources/
│   ├── application.yml              # 主配置
│   ├── intents.yml                  # 意图定义 + 口语化变体
│   ├── agents.yml                   # Agent配置
│   └── prompts/                     # LLM提示词模板
│
└── src/test/java/com/atlas/         # 测试代码
```

---

## 六、关键配置

### 6.1 application.yml 核心配置

```yaml
atlas:
  # 本地Embedding配置
  embedding:
    model-path: "${user.home}/.atlas/models/all-MiniLM"
    model-url: "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2"
    dimension: 384
    match-threshold: 0.75  # 余弦相似度阈值

  # 任务分级边界
  task-levels:
    l1-exact-match-score: 100
    l2-embedding-threshold: 0.85
    l3-llm-confidence: 0.70
    l4-fuzzy-fallback: true

  # HITL配置
  hitl:
    high-risk-commands: ["DELETE", "DROP", "REMOVE", "PURGE", "SCALE_DOWN"]
    confirmation-timeout-seconds: 60

  # Agent路由
  agent-routing:
    default: "query"
    overrides:
      - intent: "user_*"         → rbac
      - intent: "deploy_*"       → deploy
      - intent: "storage_*"      → storage
      - intent: "network_*"      → network
      - intent: "diagnose_*"     → diag

spring:
  ai:
    openai:
      base-url: "http://124.74.245.75:3000"
      api-key: "${ATLAS_LLM_API_KEY:}"  # 从环境变量注入
      chat:
        options:
          model: "moonshotai/kimi-k2.6"
```

---

## 七、开发规范

### 7.1 代码规范 (强制)
- ✅ 所有Java类必须有详细的中文注释
- ✅ 严格遵循阿里巴巴编码规范
- ✅ Entity类禁止使用 `@Data`，显式getter/setter
- ✅ SOLID原则

### 7.2 开发流程 (铁律)
```
专家会诊 → 最优方案 → 编码实现 → 代码Review → 测试验证 → 记录REVIEW_LOG → 提交GitLab
```

### 7.3 每轮修改闭环
1. 测试通过 (E2E/单元测试)
2. 代码Review (优缺点、风险分析)
3. 完善分析 (根因、方案)
4. 记录 REVIEW_LOG.md
5. 持续学习总结

---

## 八、状态看板

| 模块 | 状态 | 负责人 | 备注 |
|------|------|--------|------|
| 项目骨架 | 🚧 进行中 | Hermes | pom.xml ✅, 包结构 ✅ |
| 本地Embedding | ⏳ 待开始 | - | ONNX Runtime |
| 意图系统L1-L2 | ⏳ 待开始 | - | |
| 意图系统L3-L4 | ⏳ 待开始 | - | |
| QueryAgent | ⏳ 待开始 | - | |
| DiagAgent | ⏳ 待开始 | - | |
| DeployAgent | ⏳ 待开始 | - | |
| RBACAgent | ⏳ 待开始 | - | |
| StorageAgent | ⏳ 待开始 | - | |
| NetworkAgent | ⏳ 待开始 | - | |
| HITL | ⏳ 待开始 | - | |
| ReAct引擎 | ⏳ 待开始 | - | |
| MCP Server | ⏳ 待开始 | - | |
| 前端集成 | ⏳ 待开始 | - | |

---

> **Atlas v3.1 — 推倒重来，但不是从零开始。我们在v2的经验之上，构建一个真正可扩展、可治理、可进化的AI Agent平台。**
