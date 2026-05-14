# Atlas v3.1 — 前端默认参数回填机制设计方案

> 文档版本: 2026-05-14  
> 作者: Atlas Team  
> 目标: AI Agent执行"创建"操作时，必须自动填充与前端 vue-kube-manager 完全一致的默认参数

---

## 1. 调研结论: Spring AI Function Calling 默认值机制

### 1.1 两个层次

| 层级 | 机制 | 可靠性 | 描述 |
|---------|------|--------|------|
| **Prompt/Schema 层** | JSON Schema `default: xxx` / description 中声明 | **低** — LLM可能忽略或漏填 | 仅作提示，不能保证100%填充 |
| **Tool 执行层** | execute() 方法内硬编码回填 | **高** — 100%保证 | **必须实现** |

### 1.2 强制要求

> **用户强制: AI agent执行"创建"操作时，必须自动填充与前端相同的默认参数**

由此确定设计策略：

1. **必须在 Tool 执行层硬编码回填** — 这是唯一可靠的保证
2. **Prompt/Schema 层可以保留 `default` 描述** — 作为 LLM 的参考提示，但绝不依赖
3. **不在 Model/Function 调用层缺失时执行** — LLM 已经没有填，后端必须补上

---

## 2. DefaultValueRegistry 设计核心

### 2.1 核心理念

```
              +-----------------+     +---------------+
              |   intents.yml   |     |  defaults.yml |
              |(LLM看的参数定义)|     |(后端看的default)|
              +--------+--------+     +-------+-------+
                       |                      |
                       +----------+-----------+
                                  |
                    +-------------v------------+
                    |   DefaultValueRegistry   |  <-- 后端内存索引
                    +-------------+------------+
                                  |
                    +-------------v------------+
                    |   DefaultValueApplier    |  <-- 执行层回填逻辑
                    +-------------+------------+
                                  |
                    +-------------v------------+
                    |   + @WithDefaults AOP    |  <-- 自动拦截
                    |   + 程序化调用           |
                    +-------------+------------+
                                  |
                    +-------------v------------+
                    |   Tool.execute(params)   |  <-- params已回填
                    +-------------+------------+
                                  |
                    +-------------v------------+
                    |   kube-manager API       |
                    +--------------------------+
```

### 2.2 调用链 -> AOP 拦截

```
LLM Function Call
    |
    +--> AtlasTool.execute(Map<String, Object> params)
             |
             +--> @Before AOP (@WithDefaults)
             |          |
             |          +--> DefaultValueApplier.apply(intentId, params)
             |                     |
             |                     +--> DefaultValueRegistry.apply(intentId, params)
             |                                |
             |                                +--> defaults.yml 查询
             |                                +--> 填充缺失值
             |
             +--> params.已回填
             +--> 发起 kube-manager API
```

---

## 3. 方案对比和评估

### 方案 A: 在 intents.yml 中扩展 `defaults` 字段

**实现方式**：
```yaml
# intents.yml 中混合 defaults
intents:
  deploy_create_instance:
    description: "..."
    parameters:
      - name: cpuLimits
        type: number
        default: 2          # 只是 LLM 的提示
    defaults:               # << 新增字段
      cpuLimits: 2
      memLimits: 8
```

**优点**：
- 一个文件维护，简单
- 意图与默认值在一起，查看方便

**缺点**：
- `intents.yml` 多层详情叠加变得很大很复杂
- 前端默认值变更时需要改动意图配置，不符合单一职责原则
- `parameters` 和 `defaults` 可能不一致（维护成本高）
- 解析逻辑更复杂

---

### 方案 B: defaults.yml 单独配置文件 + Java Registry (推荐)

**实现方式**：
```yaml
# defaults.yml — 承载纯默认值
# intents.yml — 承载纯意图定义
# 职责分离！

defaults:
  deploy_create_instance:
    cpuLimits: 2
    memLimits: 8
    gpuPercentLimits: 0
    replicas: 1
    bandwidth: 10
    enableWebSsh: true
    autoScaleSwitch: false
```

**优点**：
- 职责分离：intents.yml 维护意图结构，defaults.yml 维护默认值
- 低代码扩展：新增模块只需在 defaults.yml 追加一个 intentId 节点
- 前端一致性：只要跟前端表单同步 defaults.yml，绝对不会走样
- 映射表结构 — 后端可缓存共享
- 显式回填机制 — 每个 Tool 执行前一定是完整参数
- 启动验证 — 可加入强校验，启动时检测 defaults.yml 和 intents.yml 是否协调

**缺点**：
- 多一个配置文件
- 需要同步维护两个 YML
- 当 defaults.yml 未引用的参数时，建议加入 lint 检查

---

## 4. 扩展性设计

### 4.1 新增前端模块时的扩展步骤

只需打开 `defaults.yml`，追加一个意图 ID 节点：

```yaml
# 原有 intents
  deploy_create_instance:
    cpuLimits: 2
    memLimits: 8
    ...

  node_create:                          # <-- 新增
    scheduler: "default"
    taintEnable: false

  service_create:                       # <-- 新增
    exposeType: "ClusterIP"
    port: 80
    targetPort: 8080
```

**仅需 3 步**:  
1. 前端同学确认新表单默认值  
2. 在 `defaults.yml` 追加 `intentId: defaults`  
3. 启动或 hot-reload 即生效

### 4.2 和前端协作流程

```
+------------+   +------------+   +------------+   +------------+
| 前端新增模块 |->| 确认表单默认值 |->| 更新 defaults.yml |->| PR审核 + 启动生效 |
+------------+   +------------+   +------------+   +------------+
```

建议在 CI/CD 中引入 **defaults.yml lint**：
- 检查所有 defaults 的 key 是否在对应的 intents.yml parameters 中有定义
- 检查数值类型是否与 type 字段匹配
- 检查是否有闲置的 intentId 未在 defaults.yml 中注册默认值

---

## 5. 推荐选型

| 评估维度 | 方案A (intents.yml混合) | 方案B (defaults.yml分离) | 结论 |
|-----------|------------------------|------------------------|------|
| **可靠性** | 同 | 同 | 一致，都在TCP层回填 |
| **维护性** | 中 | **优** | B职责清晰 |
| **扩展性** | 中 | **优** | B低代码扩展 |
| **可读性** | 优 | 优 | A intents.yml大，B defaults.yml符合映射表独立抽象 |
| **前后端一致性** | 中 | **优** | B 可紧跟前端变更 |
| **可测试性** | 中 | **优** | B 单独抽象层级，单元测试方便 |
| **启动验证** | 弱 | **优** | B 可结构化验证 |

### 最终选型

> **推荐方案 B：defaults.yml + DefaultValueRegistry + @WithDefaults AOP**
>
> 理由：职责单一原则 + 低代码扩展 + 绝对可靠的回填保证 + 顺从 Spring Boot 配置管理最佳实践

---

## 6. 完整代码结构

```
src/main/
├── resources/
│   ├── intents.yml           # 意图定义（已有）
│   ├── defaults.yml          # << 新增：默认值映射表
│   └── application.yml
├── java/com/atlas/
│   ├── tool/
│   │   ├── annotation/
│   │   │   └── WithDefaults.java         # << 新增：标记注解
│   │   ├── core/
│   │   │   ├── AtlasTool.java            # << 新增/重构：Tool通用接口
│   │   │   ├── ToolRegistry.java
│   │   │   └── DefaultValueAspect.java   # << 新增：AOP拦截器
│   │   ├── defaults/
│   │   │   ├── IntentDefaults.java       # << 新增：单个意图默认值封装
│   │   │   ├── DefaultValueRegistry.java # << 新增：加载+索引
│   │   │   └── DefaultValueApplier.java  # << 新增：执行层回填
│   │   └── impl/
│   │       ├── DeployCreateTool.java     # << 新增：示例Tool（带默认值）
│   │       └── StorageCreateTool.java    # << 新增：示例Tool
│   ├── intent/
│   │   ├── config/
│   │   │   ├── IntentDefinition.java
│   │   │   └── IntentsLoader.java        # 已有：意图加载
│   │   └── ...
│   ├── config/
│   │   └── AtlasConfiguration.java       # << 可能需要：注册 DefaultValueRegistry Bean
│   └── ...
└── test/
    └── java/com/atlas/tool/defaults/
        └── DefaultValueRegistryTest.java   # << 新增：单元测试
```

### 6.1 回填规则

```java
// 原则：已有值不覆盖，仅填充 null / 缺失的 key
if (!params.containsKey(key) || params.get(key) == null) {
    params.put(key, defaultValue);
}
```

### 6.2 Spring AI 集成示例

```java
@Service
public class DeployAgent {
    private final DefaultValueApplier applier;
    private final ChatClient chatClient;
    private final WebClient kubeManagerClient;

    public DeployAgent(DefaultValueApplier applier, ChatClient chatClient,
                       @Value("${kube-manager.base-url}") String baseUrl) {
        this.applier = applier;
        this.chatClient = chatClient;
        this.kubeManagerClient = WebClient.create(baseUrl);
    }

    public Mono<Result> createInstance(Map<String, Object> llmParams) {
        // 1. 回填默认值 (绝对必要！)
        Map<String, Object> finalParams = applier.apply(
            "deploy_create_instance", llmParams);

        // 2. 声明式调用
        return kubeManagerClient.post()
            .uri("/api/v1/deployments")
            .bodyValue(finalParams)
            .retrieve()
            .bodyToMono(Result.class);
    }
}
```

---

## 7. 关键检查清单

- [x] `defaults.yml` 已创建并同步前端所有 create 操作的默认值
- [x] `DefaultValueRegistry` Component 可正常加载并缓存 defaults.yml
- [x] `DefaultValueApplier` 可正常回填 — 已有值不覆盖
- [x] AOP拦截器已配置，`@WithDefaults` 注解生效
- [x] `pom.xml` 已添加 `spring-boot-starter-aop`
- [x] 所有 create 类 Tool 已添加 `@WithDefaults`
- [x] 单元测试覆盖：回填逻辑 + 已有值保留 + 未知intentId无影响
- [ ] CI 中加入 defaults/intents yml 协调性 lint（建议后续加）
