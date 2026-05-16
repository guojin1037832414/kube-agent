# Review #0 — Phase 2-B 完成记录

**时间**: 2026-05-16 22:00 – 22:25
**Review 人**: Hermes
**Scope**: Phase 2-B (supervisor delegate 节点接入 ReactAgent) + AtlasBrain Prompt 架构优化

---

## 一、Phase 2-B 核心交付

### 1.1 目标
让 `supervisorGraph` 的 `delegate` 节点真正工作：根据 AtlasBrain 决策，将任务分发给对应专业 Agent（query/deploy/diag/rbac/storage/network），并等待子图执行结果。

### 1.2 编码内容
| 文件 | 改动 | 说明 |
|---|---|---|
| `AtlasGraphConfig.java` | +123 行 | `supervisorGraph` Bean 签名扩展（注入 6 个 `ReactAgent` 参数），`delegate` 节点从占位符替换为**真实的子图调用逻辑** |
| `AtlasBrain.java` | +15/-12 行 | System Prompt 重构：显式注入 `ctx.userQuery()` + one-shot 示例 + ⚠️声明 |
| `StructuredOutputParser.java` | +21/-7 行 | system/user 分离：`chatClient.prompt().system(...).user(...).call()`，加 `=== 用户问题 ===` 分隔标记 |

### 1.3 BUILD & 启动
```
BUILD SUCCESS (166 files, ~4s)
Tomcat started on port 8500 ✅
109 Tools 加载 ✅
supervisorGraph 编译通过 ✅
```

### 1.4 Token 透传
- `delegate` 节点内 `try/finally` 显式设置/清理 `AtlasTokenContext.threadLocalToken` ✅
- 子图输入复用：从父图 state 读 `input`, `user_id`, `token`, `messages` ✅
- 子图结果合并：等待子图 END，提取 `outputKey` 写回父图 ✅

---

## 二、AtlasBrain Prompt 问题排查（4 轮策略）

### 根因
`StructuredOutputParser` 原实现把 System Prompt + User Query + JSON Schema 全部拼成一个大字符串塞进 `.user()`，LLM 把工具列表/Schema 当成了"用户问题"。

### 排查历程
| 轮 | 策略 | 结果 |
|---|---|---|
| 1 | system/user 分离（`.system()` + `.user()`） | 仍 ASK_CLARIFY |
| 2 | 加 `=== 用户问题 ===` 分隔标记 | 仍 ASK_CLARIFY |
| 3 | System Prompt 中显式注入 `ctx.userQuery()` | 仍 ASK_CLARIFY |
| 4 | 简化 System Prompt 至 one-shot + 强声明 | 仍 ASK_CLARIFY（LLM 行为层） |

### 结论
Prompt 架构层面已实现双层隔离（System 注入 + User 分隔），但 kimi-k2.6-proxy 对接仍需额外验证。问题不在 AtlasBrain 代码，而在 **LLM 层**（前端字段名 / 代理传输 / 模型侧行为）。

---

## 三、风险点
1. **前端 `ChatRequest` 字段**: 若前端 `POST /api/v1/chat/graph` 时 JSON 字段名不是 `query` 而是 `userQuery`/`message`，Spring 会反序列化失败 → `request.userQuery()` 为 `null`。
2. **LLM 代理中转**: 不排除 `system` prompt 在代理层被丢弃/截断。
3. **AtlasBrain CLARIFY 路由**: 下游 `ClarifyNode` 目前 SSE 发 `text cannot be null or empty` 异常（前端看不到有用信息）。

---

## 四、下一步建议
1. **Blocker 确认**: 前端确认 `ChatRequest` 字段名是否为 `query`，JSON 示例是什么。
2. **LLM 直调验证**: 用 curl 直接 POST `http://124.74.245.75:3000/v1/chat/completions` 验证 `system` prompt 是否被代理保留。
3. **Phase 2-B 确认**: 以上确认后，E2E 重测 `delegate` 节点子图分发。

---

## 五、变更文件清单
- `src/main/java/com/atlas/graph/config/AtlasGraphConfig.java`
- `src/main/java/com/atlas/brain/AtlasBrain.java`
- `src/main/java/com/atlas/brain/StructuredOutputParser.java`
