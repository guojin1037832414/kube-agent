# ONNX Runtime Java API 在 Spring Boot 中的集成调研报告

> **调研范围**: ONNX Runtime Java SDK、sentence-transformers/all-MiniLM-L6-v2 ONNX 加载、Tokenizer 实现、批量推理与线程安全、模型管理策略
> **目标项目**: kube-agent Atlas v3.1（Spring Boot 3.4.4 + Spring AI 1.1.6 + ONNX Runtime 1.17.3）
> **调研时间**: 2026-05-14

---

## 一、调研参考来源

| 来源 | URL |
|------|-----|
| ONNX Runtime Java API 官方仓库 | https://github.com/microsoft/onnxruntime/tree/main/java |
| ONNX Runtime Java 官方 Javadoc | https://onnxruntime.ai/docs/api/java/index.html |
| Spring AI TransformersEmbeddingModel 源码 | https://github.com/spring-projects/spring-ai/blob/main/models/spring-ai-transformers/ |
| Spring AI ResourceCacheService 源码 | 同上目录下 ResourceCacheService.java |
| GitHub Issue #4329 (线程安全 NativeObject) | https://github.com/microsoft/onnxruntime/pull/4329 |
| GitHub Issue #7178 (Batch Inference) | https://github.com/microsoft/onnxruntime/issues/7178 |
| Spring AI Issue #1130 (ragged array) | https://github.com/spring-projects/spring-ai/issues/1130 |
| Spring AI Issue #571 (Supplied array is ragged) | https://github.com/spring-projects/spring-ai/issues/571 |
| LangChain4j Issue #1579 (classloader问题) | https://github.com/langchain4j/langchain4j/issues/1579 |
| LangChain4j Issue #4134 (No tokenizers version found) | https://github.com/langchain4j/langchain4j/issues/4134 |

---

## 二、核心发现

### 2.1 ONNX Runtime Java SDK 使用方式

**关键类：**
- `OrtEnvironment`: **单例、线程安全**，通过 `OrtEnvironment.getEnvironment()` 获取，整个 JVM 生命周期内通常只创建一个
- `OrtSession`: 封装 ONNX 模型，支持**多线程并发推理**（底层 C++ runtime 线程安全），但 `OrtSession` 实例本身是引用计数管理的
- `OnnxTensor`: 输入张量包装，需通过 `try-with-resources` 或显式关闭释放 native 内存
- `OrtSession.Result`: 推理结果，同样 `AutoCloseable`

**线程安全策略（来自 PR #4329）**: 从 ONNX Runtime Java 1.7+ 开始，`OrtSession` 已内置引用计数（Reference Counting）机制：
```
- close() 不立即释放 native handle，而是等待最后一次 run() 完成
- 使用 CAS (AtomicInteger) 而非 synchronized，性能更优
- 参考自 TensorFlow JNI 实现
```

**结论**: `OrtSession` 可以安全地在多线程环境中共用一个实例，无需池化。

### 2.2 all-MiniLM-L6-v2 ONNX 模型加载

Spring AI `TransformersEmbeddingModel` 的实现方式是最佳实践参考：
1. 模型通过 `OrtEnvironment.createSession(byte[], SessionOptions)` 从 byte[] 加载
2. 收集模型的 `inputNames` 和 `outputNames` 用于运行时校验
3. all-MiniLM-L6-v2 的输出名是 `last_hidden_state`，维度为 `[batch, seq_len, 384]`

**模型转换命令**（Python端预处理）：
```bash
pip install optimum[onnxruntime]
optimum-cli export onnx --model sentence-transformers/all-MiniLM-L6-v2 ./onnx-output/
# 输出: model.onnx + tokenizer.json + vocab.txt + config.json
```

### 2.3 Tokenizer 在 Java 侧的实现

**技术选项对比：**

| 方案 | 库 | 优点 | 缺点 | 适用性 |
|------|----|------|------|--------|
| **A. DJL HuggingFace Tokenizer** | `ai.djl.huggingface:tokenizers` | 官方维护，与 Python tokenizers 库行为完全一致 | 有 native 依赖（libtokenizers.so/dylib），某些平台加载有问题 | Spring AI 采用，但偶有平台兼容 issue |
| **B. ONNX tokenizer 内嵌方案** | 用 ONNX 模型自带 tokenizer ops | 零额外依赖 | 需要特殊导出，非标准流程 | 不推荐 |
| **C. 自研简易 Tokenizer** | 基于 tokenizer.json 手动加载 vocab | 完全可控 | 复杂度高 | 不推荐 |

**Atlas v3.1 推荐方案：DJL HuggingFace Tokenizer**
- P0 阶段采用 DJL HuggingFace Tokenizer，与 Spring AI 一致
- 降级方案: 若 DJL native 库加载失败，回退到纯规则匹配（L2 only）
- **必开 padding=true + truncation=true**，否则不同长度文本会导致 ragged array 异常

### 2.4 批量推理与线程安全

- `OrtSession` 实例 = 单例（线程安全，并发 run() 不冲突）
- 单次请求走单条推理（延迟 < 10ms，响应快）
- 批量预计算 intent examples 时走 batch（启动时预加载到内存）
- tokenizer batchEncode 建议加锁保护（DJL 线程安全性未完全确认）

### 2.5 模型自动下载 vs 本地路径加载

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | `file:${user.home}/.atlas/models/all-MiniLM/` | 本地磁盘优先 |
| 2 | `classpath:/models/all-MiniLM/` | fat-jar 内置（开发测试）|
| 3 | HuggingFace Hub 下载 | 首次启动自动下载 |

### 2.6 模型文件（~90MB）管理策略

**结论: 放外部目录，不打包进 jar**
- 外部目录: `${user.home}/.atlas/models/` 或 `/opt/atlas/models/`
- Docker/K8s 通过 Volume/PVC 挂载
- CI/CD 不打包模型，构建阶段通过脚本预下载

---

## 三、代码结构建议

```
com.atlas.intent.embedding
├── EmbeddingService.java          # 主服务
├── EmbeddingConfig.java           # 配置类
├── OnnxSessionHolder.java         # OrtSession 单例
├── OnnxEnvironmentHolder.java     # OrtEnvironment 单例
├── ModelDownloader.java           # HuggingFace 下载
└── cos
    └── CosineSimilarity.java
```

### 核心代码骨架

#### OnnxEnvironmentHolder.java
```java
@Component
public class OnnxEnvironmentHolder {
    private static final OrtEnvironment ENV = OrtEnvironment.getEnvironment();
    public static OrtEnvironment get() { return ENV; }
}
```

#### OnnxSessionHolder.java
```java
@Component
@Slf4j
public class OnnxSessionHolder implements AutoCloseable {
    private final OrtSession session;
    public OnnxSessionHolder(Path modelPath) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = OnnxEnvironmentHolder.get().createSession(modelPath.toFile(), options);
    }
    public OrtSession getSession() { return session; }
    public void close() throws OrtException { if (session != null) session.close(); }
}
```

#### EmbeddingService.java (核心流程)
```java
@Service
public class EmbeddingService {
    private final HuggingFaceTokenizer tokenizer;
    private final OnnxSessionHolder sessionHolder;
    private final Lock tokenizerLock = new ReentrantLock();

    public float[] encode(String text) {
        return batchEncode(List.of(text))[0];
    }

    public float[][] batchEncode(List<String> texts) {
        // 1. Tokenizer batchEncode (加锁)
        // 2. OnnxTensor.createTensor (input_ids, attention_mask, token_type_ids)
        // 3. session.run(inputs) -> Result -> last_hidden_state
        // 4. meanPooling -> L2 normalize
    }

    private float[] meanPooling(float[][] tokenEmbeddings, long[] attentionMask) {
        // sum(token * mask) / sum(mask), then L2 normalize
    }
}
```

### pom.xml 补充依赖
```xml
<dependency>
    <groupId>ai.djl.huggingface</groupId>
    <artifactId>tokenizers</artifactId>
    <version>0.30.0</version>
</dependency>
```

---

## 四、风险清单与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| DJL tokenizer native 库加载失败 | L1 意图系统不可用 | 启动时捕获异常，降级到 L2 规则匹配 |
| 模型文件下载超时/失败 | 首次启动卡住 | 120s 超时，失败不阻塞启动，标记 embedding 不可用 |
| ONNX Runtime 内存泄漏 | OOM | 所有 OnnxTensor/Result 用 try-with-resources |
| 多线程并发 run() 竞态 | JVM 崩溃 | 确保使用 ONNX Runtime 1.17.3+（已修复 close() 竞态）|
| 不同长度文本 ragged array | 推理异常 | tokenizer 必开 `padding: true, truncation: true` |

---

## 五、与 Spring AI TransformersEmbeddingModel 的差异

本项目不直接复用 Spring AI TransformersEmbeddingModel 的原因：
1. Spring AI 1.1.x transformers starter 命名已变更，引入会增加依赖复杂度
2. 本方案更轻量: 不依赖 DJL NDManager，纯 Java array 操作即可
3. 意图系统的 embedding 需求更简单，不需要 Document metadata mode 等通用功能
4. 自定义 tokenizer lock 策略，避免 Spring AI Issue #3847 的 parallelStream ClassCastException

---

## 六、总结

ONNX Runtime Java API 在 Spring Boot 中的集成是**成熟且线程安全**的。核心要点：

1. **OrtEnvironment + OrtSession = 单例共享**，无需池化
2. **Tokenizer 用 DJL HuggingFaceTokenizer**，行为与 Python 一致，但需开启 padding
3. **模型放外部目录**（`~/.atlas/models/`），不打包进 jar，支持自动下载 fallback
4. **所有 native 资源 try-with-resources**，防止内存泄漏
5. **tokenizer batchEncode 加锁保护**，避免并发兼容性问题

---
*报告生成: Atlas v3.1 技术调研*
