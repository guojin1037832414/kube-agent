# Atlas v3.1 开源LLM Agent架构评估报告

> **版本**: 1.0.0  
> **日期**: 2026-05-14  
> **调研范围**: LangChain/LangGraph、LlamaIndex、Rasa、Spring AI/MCP、LangChain4j、Spring AI Alibaba、Semantic Kernel Java  
> **核心结论**: Spring AI Alibaba 提供了最贴近Atlas v3.1技术栈的多Agent编排方案，LangChain4j 的 `@Tool` 注解 + 工具搜索策略值得迁移学习

---

## 一、LangChain/LangGraph — Router Chain / Supervisor Pattern

### 1.1 核心发现

**LangChain `RouterRunnable`** 实现了一个基于映射表（`Map<String, Runnable>`）的路由器，根据输入中的 `key` 字段选择对应的执行单元:

```python
class RouterRunnable(RunnableSerializable[RouterInput, Output]):
    runnables: Mapping[str, Runnable[Any, Output]]

    def invoke(self, input: RouterInput, config: RunnableConfig | None = None, **kwargs: Any) -> Output:
        key = input["key"]
        runnable = self.runnables[key]
        return runnable.invoke(actual_input, config)
```

**LangGraph Multi-Agent Supervisor Pattern** 提供了三种典型模式:
1. **Supervisor**: 一个中心LLM充当调度器，根据任务描述将工作分发给子Agent
2. **Handoffs**: Agent通过Function Calling主动将控制权移交给其他Agent
3. **Swarm**: 去中心化的Agent群体，每个Agent都可能根据条件切换上下文

### 1.2 可迁移到Java/Spring AI的思想

| 思想 | 迁移难度 | 说明 |
|------|---------|------|
| **RouterRunnable映射表** | ⭐ 低 | 直接对应Java的 `Map<String, Agent>` + `get(key).execute()` |
| **Supervisor + 子Agent** | ⭐⭐ 中 | 可用Spring `@Qualifier` 注入Agent列表 + LLM选择 |
| **Handoffs（主动移交）** | ⭐⭐ 中 | Agent内部暴露 `transfer_to_XXX` 工具，Spring AI Alibaba已有实现 |
| **Swarm去中心化** | ⭐⭐⭐ 高 | 需要对话状态共享机制，当前Atlas v3.1尚不需要 |

**最佳实践参考**: `langchain-ai/langgraph` 的 Multi-Agent Supervisors
- 来源: https://github.com/langchain-ai/langgraph/tree/main/docs/docs/concepts/multi_agent.md
- 官方教程: `https://langchain-ai.github.io/langgraph/concepts/multi_agent/`

---

## 二、LlamaIndex — Agent Router / Tool Retriever 模式

### 2.1 核心发现

LlamaIndex的 Agent Router 模式核心思想:**Agent不是拥有工具，而是动态搜索和选择工具**。

**关键架构**:
- `RouterAgent` 根据query语义，从工具注册表中检索最相关的子集
- 每个 `Tool` 带有描述性Embedding，LLM根据语义匹配度动态选择
- 工具的 `SearchBehavior` 决定是始终可见还是按需检索

**Tool Retriever的工作流程**:
1. 用户的query进入 RouterAgent
2. RouterAgent 通过Embedding匹配或LLM判断 → 选择1个或多个Agent/Tool子集
3. 将选择的Tool交给执行Agent进行调用

### 2.2 可迁移到Java的思想

| 思想 | 迁移方式 | 启发 |
|------|---------|------|
| **动态工具检索** | `ToolSearchStrategy` 接口 + Embedding相似度匹配 | Atlas的L1 Embedding预筛可直接复用为工具检索器 |
| **Agent即Tool** | 每个专业Agent注册为一个"复合Tool" | QueryAgent可被DiagAgent作为"先查后诊"的工具调用 |
| **语义工具发现** | Tool描述文本向量化存储 | 来源: `https://docs.llamaindex.ai/en/stable/module_guides/deploying/agents/` |

---

## 三、Rasa — DIETClassifier + TEDPolicy 联合训练思路

### 3.1 核心发现

Rasa的对话系统架构是所有调研项目中**最接近Atlas v3.1当前L1-L4分层**的实现:

**NLU层（对应Atlas意图分类）**:
- `DIETClassifier` (Dual Intent and Entity Transformer): 单一Transformer同时做意图分类和实体提取
- 架构: Transformer Encoder → `__CLS__` token映射到意图label的向量空间 → 负采样/交叉熵优化
- 支持**多任务联合训练**: intent + entity + masked LM三个任务同时优化

```
Transformer → __CLS__ token → 向量空间 → Dot Product Loss (最近邻匹配)
          → Sequence output → CRF → 实体标签序列
```

**Policy层（对应Atlas Agent路由决策）**:
- `TEDPolicy` (Transformer Embedding Dialogue Policy): 将对话历史（包括意图、实体、slot、动作）编码为Transformer嵌入
- 预测下一个动作（相当于Agent选择）

### 3.2 可借鉴的理念（非照搬，因Rasa是Python且需训练）

| 理念 | 借鉴方式 | 来源 |
|------|---------|------|
| **联合训练思想** | Atlas L1-L4可视为一个"预训练-微调"流水线而非独立模块 | https://github.com/RasaHQ/rasa/blob/main/rasa/nlu/classifiers/diet_classifier.py |
| **向量空间意图匹配** | DIET用目标label的Embedding做最近邻匹配，Atlas L1的Embedding余弦值可借鉴此思想 | arXiv:2004.09936 |
| **对话状态追踪** | Rasa Tracker保存完整对话状态（intent + slot + action），Atlas应维护对话上下文状态机而非仅单次query | https://github.com/RasaHQ/rasa/blob/main/docs/docs/architecture.mdx |
| **负采样策略** | DIET训练时采样20个负例意图，提升区分度 | 见`NUM_NEG: 20`配置 |

> **关键结论**: Rasa证明了"意图分类+对话状态+动作预测"的联合建模优于独立模块。Atlas v3.1目前将L1-L4视为串行守卫器，未来可考虑将L1/L2结果作为**上下文特征**注入到L3 LLM中，而非简单短路。

---

## 四、Spring AI MCP — 多Agent分发机制

### 4.1 核心发现

**MCP (Model Context Protocol) Java SDK 架构**:
```
┌─────────────────────────────────────────────────────┐
│  Client/Server Layer                                │
│  • McpClient / McpServer                            │
├─────────────────────────────────────────────────────┤
│  Session Layer                                      │
│  • McpSession (connection state)                    │
├─────────────────────────────────────────────────────┤
│  Transport Layer                                    │
│  • McpTransport (JSON-RPC over STDIO/HTTP/SSE)      │
└─────────────────────────────────────────────────────┘
```

**Spring AI MCP 关键组件**:
- `@McpTool` 注解: 将Java方法暴露为MCP Tool（json schema 自动生成）
- `@McpResource`: URIs for data access
- `@McpPrompt`: Template for prompts
- `spring-ai-starter-mcp-client`: MCP客户端Starter
- `spring-ai-starter-mcp-server`: MCP服务端Starter

### 4.2 Multi-Agent分发实现思路

| 组件 | Atlas应用方式 | MCP机制 |
|------|-------------|---------|
| **每个Agent作为MCP Server** | QueryAgent/DiagAgent各自启动MCP Server | 通过SSE/HTTP暴露tools |
| **编排器作为MCP Client** | AtlasOrchestrator连接各Agent的MCP Server | 统一工具发现与调用 |
| **共享Tool Registry** | kube-manager API通过 `@McpTool` 注册 | Client发现后传递给LLM |

> ⚠️ **重要**: Spring AI MCP 1.x 是**工具发现/调用**协议，不是**Agent路由**协议。多Agent路由需要自己实现。MCP能让Atlas的Tool被Claude/Cursor/Copilot等MCP客户端复用，但Agent间协作仍需上层机制。

---

## 五、LangChain4j — AiServices、Agent编排与@Tool

### 5.1 核心发现

**LangChain4j 的 `@Tool` 注解** 直接实现了"声明式工具注册":

```java
@Retention(RUNTIME) @Target(METHOD)
public @interface Tool {
    String name() default "";
    String[] value() default "";  // description
    SearchBehavior searchBehavior() default SearchBehavior.SEARCHABLE;  // 是否可被检索
}
```

**AiServices声明式编程**:
```java
// 定义接口
interface Assistant {
    @SystemMessage("You are a helpful assistant")
    String chat(@UserMessage String message);
}

// 创建代理实现
Assistant assistant = AiServices.create(Assistant.class, chatModel);
String answer = assistant.chat("Hello");
```

**关键特性支持Spring Boot自动配置**: Quarkus、Spring Boot、Micronaut、Helidon全部支持自动bean注入。

**Tool搜索策略**（1.12.0+新增）:
- `@Tool(searchBehavior = SearchBehavior.SEARCHABLE)` → 仅当tool search strategy匹配才暴露
- `@Tool(searchBehavior = SearchBehavior.ALWAYS_AVAILABLE)` → 始终暴露给LLM
- `ToolProvider` → 自定义工具发现逻辑

### 5.2 可迁移思想

| 思想 | Atlas实现方式 | 来源 |
|------|-------------|------|
| **声明式@Tool注册** | 类似Atlas的 `@ToolDefinition` 或 `@McpTool` | https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/agent/tool/Tool.java |
| **Tool描述文本向量化** | 将每个Tool的description存入Embedding索引 | https://docs.langchain4j.dev/tutorials/tools |
| **动态工具选择** | L1先检索相关Tool子集，再注入LLM上下文 | `searchBehavior()` 设计 |
| **AI Service代理模式** | Atlas的每个Agent可声明为接口，Spring生成代理 | https://docs.langchain4j.dev/tutorials/ai-services |

---

## 六、Spring AI Alibaba — 最接近Atlas的Agent框架

### 6.1 核心发现（本次调研最关键结果）

**Spring AI Alibaba** 是阿里巴巴基于Spring AI开源的Java Agent框架，提供了**与Atlas v3.1需求高度匹配**的多Agent编排能力:

**内置Agent模式**:
- `SequentialAgent` → 顺序执行
- `ParallelAgent` → 并行执行
- `RoutingAgent` → 路由选择（**直接对应Atlas需求**）
- `LoopAgent` → 循环执行

**Multi-Agent Handoffs模式**:
```java
// 实际源码（来自GitHub仓库）
graph.addConditionalEdges(START, new RouteInitialAction(), Map.of(
    SALES_AGENT, SALES_AGENT,
    SUPPORT_AGENT, SUPPORT_AGENT));

graph.addConditionalEdges(SALES_AGENT,
    new RouteAfterSalesAction(),
    Map.of(SUPPORT_AGENT, SUPPORT_AGENT, "__end__", END));

# Sales Agent内部持有 transfer_to_support Tool
# Support Agent内部持有 transfer_to_sales Tool
```

**Graph-based Workflow**:
- `StateGraph`: 定义Agent节点和条件边
- `ReactAgent`: ReAct模式的Agent节点
- `CompiledGraph`: 编译后的可执行图

**Context Engineering（Atlas需要的能力，SAA已内置）**:
- 上下文压缩 / 编辑
- 工具调用次数限制 / 重试
- Human-in-the-loop
- 规划 / 动态工具选择

**A2A (Agent-to-Agent) 支持**:
- Nacos集成 / 分布式Agent协调
- Agent可跨服务通信

### 6.2 关键源码来源

| 组件 | URL |
|------|-----|
| MultiAgent Handoffs Config | https://github.com/alibaba/spring-ai-alibaba/blob/main/examples/multiagent-patterns/handoffs-multiagent/src/main/java/com/alibaba/cloud/ai/examples/multiagents/handoffs/MultiAgentHandoffsConfig.java |
| Agent Framework Docs | https://java2ai.com/docs/quick-start/ |
| Multi-Agent Patterns | https://github.com/alibaba/spring-ai-alibaba/tree/main/examples/multiagent-patterns |
| Graph Core API | https://java2ai.com/docs/frameworks/graph-core/quick-start/ |
| Context Engineering | https://java2ai.com/docs/frameworks/agent-framework/tutorials/hooks |

---

## 七、其他Java生态框架调研

### 7.1 Semantic Kernel Java

微软的Semantic Kernel提供**Planner**概念（LLM自动生成任务计划），但：
- 社区活跃度高，但Java版本功能滞后于C#/Python
- 当前主要定位是Plugin编排，非多Agent协作
- 来源: https://github.com/microsoft/semantic-kernel-java

### 7.2 微软AutoGen

AutoGen（现更名为AutoGen Studio）提供多Agent对话框架，但:
- 主要面向Python
- Java支持不成熟
- 来源: https://github.com/microsoft/autogen

---

## 八、横向对比矩阵

| 框架 | 多Agent编排 | Java支持 | Spring集成 | 工具注册 | Agent协作 | MCP支持 |
|------|-----------|---------|-----------|---------|----------|---------|
| **LangGraph** | ✅ Supervisor/Handoffs/Swarm | ❌ Python原生 | ❌ 无 | 动态工具 | ✅ 完整 | ❌ 无原生 |
| **LlamaIndex** | ✅ RouterAgent/ToolRetriever | ⚠️ 实验性 | ❌ 无 | Embedding检索 | ⚠️ 有限 | ❌ |
| **Rasa** | ✅ Tracker+Policy | ⚠️ Python原生 | ❌ | NLU Pipeline | ✅ 对话级 | ❌ |
| **Spring AI MCP** | ⚠️ 非原生，需自建 | ✅ 官方 | ✅ Spring AI | `@McpTool` | ⚠️ 协议级 | ✅ 原生支持 |
| **LangChain4j** | ⚠️ AiService代理层 | ✅ Java原生 | ✅ Spring Boot | `@Tool`注解 | ⚠️ 需自建 | ⚠️ 有限 |
| **Spring AI Alibaba** | ✅ RoutingAgent/Handoffs/Graph | ✅ Java原生 | ✅ Spring Boot | `ReactAgent` builder | ✅ A2A+Nacos | ✅ 原生支持 |

> **结论**: Spring AI Alibaba 在"多Agent编排 + Java原生 + Spring集成"三个维度上全面领先，是Atlas v3.1技术路线最强的参照框架。

---

## 九、对Atlas v3.1的6-Agent架构具体建议

### 建议总览

| 优先级 | 建议 | 来源框架 |
|-------|------|---------|
| P0 | 引入 `StateGraph` 思想设计Agent编排图 | Spring AI Alibaba |
| P0 | Agent注册采用 "配置+发现" 双模式 | LangChain4j `@Tool` + Spring Boot |
| P1 | 实现 `transfer_to_XXX` 工具的Agent间协作 | Spring AI Alibaba Handoffs |
| P1 | Tool复用通过 "Plugin共享层" + MCP Server暴露 | Spring AI MCP + Semantic Kernel Plugin |
| P2 | L1-L4结果作为上下文特征注入L3（类似Rasa Tracker） | Rasa DIETClassifier |
| P2 | Tool描述向量化支持动态检索 | LlamaIndex Tool Retriever |

### 9.1 Agent注册机制（建议采用"Spring Bean + YAML配置"双模式）

**模式A: Spring Bean自动发现（来自LangChain4j + Spring AI Alibaba）**

```java
// 来源: https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/ai-services.md
//      https://github.com/alibaba/spring-ai-alibaba/blob/main/examples/...

@Component
public class QueryAgent implements AtlasAgent {
    @Override
    public String getAgentName() { return "query"; }
    
    @Override
    public List<AtlasTool> getTools() { /* ... */ }
    
    @Override
    public AgentResult execute(IntentResult intent, Map<String, Object> params) { /* ... */ }
}
```

**AtlasOrchestrator自动收集所有Agent**:
```java
@Component
public class AtlasOrchestrator {
    private final Map<String, AtlasAgent> agents;
    
    // Spring自动注入所有AtlasAgent实现
    public AtlasOrchestrator(List<AtlasAgent> agentList) {
        this.agents = agentList.stream()
            .collect(Collectors.toMap(
                AtlasAgent::getAgentName, 
                Function.identity()
            ));
    }
}
```

**模式B: YAML覆盖配置（保留当前intents.yml的映射）**

```yaml
# agents.yml — 新增配置，覆盖默认Bean映射
agents:
  query:
    class: "com.atlas.agent.query.QueryAgent"
    priority: 1
    fallback: "diag"  # Query未命中时fallback到Diag
    tools: ["node_query", "gpu_query", "image_query"]
  
  diag:
    class: "com.atlas.agent.diag.DiagAgent"
    inherits: "query"  # Diag可复用Query的Tool
    tools: ["diagnose_pod", "log_query"]
```

### 9.2 Tool复用机制（建议采用"三级复用"）

**三级复用架构**:
```
Level 1: SharedTools（所有Agent共享）
  └─ common: cluster_overview, greeting, help
  
Level 2: Parent-Child继承（Diag继承Query的Tool）
  └─ query: node_query, gpu_query, image_query
  └─ diag inherits query + own tools

Level 3: Cross-Agent调用（通过Transfer Tool）
  └─ QueryAgent 发现需要诊断 → transfer_to_diag Tool
```

**Tool注册来源参考**:
1. **LangChain4j `@Tool` 模式**: https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/agent/tool/Tool.java
2. **Spring AI MCP `@McpTool` 模式**: `spring-ai-mcp-annotations` 模块

**Atlas实现建议**:
```java
// 共用接口
public interface AtlasAgent {
    String getAgentName();
    // Tool集合 = 自有Tool + 继承Tool
    default Set<String> getEffectiveTools() { ... }
    // 检查是否有某个Tool的权限
    default boolean hasToolPermission(String toolName) { ... }
}

// DiagAgent复用Query的Tool
@Component
public class DiagAgent implements AtlasAgent {
    @Autowired private QueryAgent queryAgent;  // 或直接注入共享Tool集合
    
    @Override
    public Set<String> getEffectiveTools() {
        return Stream.concat(
            queryAgent.getEffectiveTools().stream(),  // 继承
            Stream.of("diagnose_pod", "log_query")    // 自有
        ).collect(Collectors.toSet());
    }
}
```

### 9.3 Agent间协作（建议引入"Transfer Tool + 路由图"双模式）

**模式一: Transfer Tool 主动移交（来自Spring AI Alibaba Handoffs）**

```java
// 来源: https://github.com/alibaba/spring-ai-alibaba/blob/main/examples/multiagent-patterns/handoffs-multiagent/...

// Sales Agent 持有 transfer_to_support Tool
// Support Agent 持有 transfer_to_sales Tool

// Atlas中: QueryAgent 持有 transfer_to_diag Tool
@Component
public class TransferToDiagTool {
    
    @AtlasTool(name = "transfer_to_diag", 
               description = "当用户查询的节点/服务存在异常需要诊断时，转交给诊断Agent")
    public String execute(TransferRequest request) {
        // 修改当前StateGraph的activeAgent = "diag"
        // 将当前对话上下文传递给DiagAgent
        return "已转交诊断Agent处理...";
    }
}
```

**模式二: StateGraph 条件路由（推荐引入Spring AI Alibaba思想）**

```java
// 来源: Spring AI Alibaba StateGraph实现
// https://github.com/alibaba/spring-ai-alibaba/blob/main/spring-ai-alibaba-graph-core/...

@Component
public class AtlasStateGraph {
    
    @Autowired private Map<String, AtlasAgent> agents;
    
    public void buildGraph() {
        StateGraph graph = new StateGraph("atlas_orchestrator");
        
        // 注册6个Agent为节点
        for (AtlasAgent agent : agents.values()) {
            graph.addNode(agent.getAgentName(), agent.asNode());
        }
        
        // 条件路由: 从START根据Intent路由到对应Agent
        graph.addConditionalEdges(START, new IntentRouterAction(), 
            IntentsLoader.getIntentToAgentMap());
        
        // 循环: Agent执行后，根据是否transfer决定next node
        for (AtlasAgent agent : agents.values()) {
            graph.addConditionalEdges(
                agent.getAgentName(),
                new TransferDecisionAction(agent),
                // 如果transfer_to_XXX，则路由到XXX；否则END
                agent.getTransferTargets()
            );
        }
        
        this.compiledGraph = graph.compile();
    }
}
```

### 9.4 L1-L4意图系统增强建议（借鉴Rasa DIET的思想）

**现状问题**: L1 Embedding → L2 Rule → L3 LLM → L4 Fuzzy 是完全串行短路，每一层的结果是独立的，没有上下文累积。

**建议改进**（借鉴Rasa Tracker思想）:
```
Atlas对话状态（AtlasTracker）:
├─ conversation_id
├─ messages[]                  # 历史消息
├─ current_intent              # 当前意图
├─ l1_embedding_result         # L1结果（作为特征）
├─ l2_rule_result              # L2结果（作为特征）
├─ slot_values                 # 对话中抽取的参数
├─ active_agent                # 当前活跃Agent
└─ handoff_history[]           # Agent移交历史
```

**L3 LLM 输入增强**:
```xml
<!-- prompt/l3-classifier.ftl -->
当前用户query: ${query}
L1 Embedding 预筛结果: ${l1_result} (confidence: ${l1_score})
L2 规则匹配结果: ${l2_result} (match_type: ${l2_type})
对话上下文Agent: ${active_agent}
已确认slot: ${confirmed_slots}

请根据以上信息，选择最合适的意图...
```

**来源**: 
- Rasa Tracker: https://github.com/RasaHQ/rasa/blob/main/docs/docs/architecture.mdx
- DIET联合训练思想: arXiv:2004.09936

### 9.5 Tool注册与发现的向量化（借鉴LlamaIndex + LangChain4j）

**建议**: Atlas的Tool注册不采用"全量注入LLM上下文"，而是先通过Embedding检索最相关的Tool集合:

```java
@Component
public class AtlasToolRegistry {
    
    private final EmbeddingService embeddingService;
    
    // 所有Tool的向量化描述索引
    private final Map<ToolDescriptor, float[]> toolEmbeddings;
    
    public List<AtlasTool> retrieveRelevantTools(String query, int topK) {
        float[] queryEmbedding = embeddingService.embed(query);
        return toolEmbeddings.entrySet().stream()
            .map(e -> Pair.of(e.getKey(), cosine(queryEmbedding, e.getValue())))
            .sorted(Comparator.comparingDouble(Pair::getValue).reversed())
            .limit(topK)
            .map(Pair::getKey)
            .map(ToolDescriptor::getTool)
            .collect(Collectors.toList());
    }
}
```

**来源**:
- LlamaIndex Tool Retriever: https://docs.llamaindex.ai/en/stable/module_guides/deploying/agents/
- LangChain4j `SearchBehavior.SEARCHABLE`: https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/agent/tool/Tool.java

---

## 十、总结与技术路线建议

### 10.1 对Atlas v3.1的核心建议

| # | 建议 | 优先级 | 实施阶段 | 来源 |
|---|------|-------|---------|------|
| 1 | 参考Spring AI Alibaba的 `StateGraph` 和 `ReactAgent` 重构AtlasOrchestrator | P0 | P2 | https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-graph-core |
| 2 | Agent注册采用 "Spring Bean + YAML配置" 双模式 | P0 | P0 | LangChain4j AiServices + Spring AI Alibaba Agent |
| 3 | 引入 `AtlasTracker` 统一维护对话状态（类似Rasa Tracker） | P1 | P1 | https://github.com/RasaHQ/rasa/docs/docs/architecture.mdx |
| 4 | 通过 `transfer_to_XXX` 工具实现Agent间主动移交（Handoffs） | P1 | P2 | https://github.com/alibaba/spring-ai-alibaba/blob/main/examples/multiagent-patterns/handoffs-multiagent/ |
| 5 | Tool复用采用 "三级复用"（Shared/继承/Cross-Agent调用） | P1 | P1 | 借鉴Semantic Kernel Plugin系统 |
| 6 | Tool描述向量化，支持动态检索（减少LLM context） | P2 | P3 | LlamaIndex Tool Retriever |
| 7 | 逐步引入 `@McpTool` 标注，使Atlas Tool可被外部MCP客户端复用 | P2 | P3 | https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html |
| 8 | 引入对话上下文注入L3 LLM提示词（L1/L2作为特征） | P2 | P1 | Rasa DIET联合训练思想, arXiv:2004.09936 |

### 10.2 最终推荐的技术路线

```
Atlas v3.1 推荐演进路线:

P0 (当前): L1-L4基础分层 + Spring Bean Agent注册
          └─ 参考: LangChain4j AiServices + Spring Boot自动配置

P1 (下一阶段): 意图上下文注入 + 三级Tool复用 + AtlasTracker状态机
          └─ 参考: Rasa Tracker, Semantic Kernel Native Functions

P2 (远期): StateGraph编排 + Handoffs Agent协作 + ReAct引擎
          └─ 参考: Spring AI Alibaba Graph + ReactAgent

P3 (未来): MCP Server化 + Tool向量化检索 + A2A分布式协作
          └─ 参考: Spring AI MCP + LlamaIndex Tool Retriever + SAA A2A
```

> 🎯 **核心一句话**: 以Spring AI为基础、Spring AI Alibaba的Graph+Agent模式为参考，融合LangChain4j的工具注册思想和Rasa的状态追踪理念，构建Java原生、Spring集成的多Agent编排系统。

---

**报告完成时间**: 2026-05-14
**数据最后更新**: 实际从GitHub仓库拉取（见各章节来源链接）
