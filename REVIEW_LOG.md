# kube-agent REVIEW_LOG

> 本文件记录阶段性开发闭环：问题背景、解决方案、测试结果、代码 Review、风险与后续计划。

## 2026-05-20 23:10 - ReAct Tool 参数契约第一批扩展 + URL 查询参数修复

### 背景
- 已完成 `ToolParameterSpec`、`ToolInputSchemaBuilder`、schema-first `ToolParameterNormalizer`、`ReActPromptBuilder/ToolRegistry` 工具目录参数契约接入。
- 本轮按“先实验再铺开”原则，不批量修改全部 Tool，只选择第一批 3 个诊断/查询 Tool 验证契约扩展路径：
  - `log_query`
  - `deployment_detail`
  - `node_detail`

### 变更内容
1. `LogQueryTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数：`podName`、`namespace`、`lines`。
   - aliases：兼容 `pod_name`、`pod`、`targetName`、`keyword`、`ns`、`tailLines` 等历史/LLM 输出字段。

2. `DeploymentDetailTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数保持为当前执行逻辑读取的 `name`，description 明确限定为 Deployment/实例名称。
   - aliases 支持 `deploymentName`、`instanceName`、`targetName` 等。
   - 修复旧代码将 `?name=...` 手拼到 path，导致 `KubeManagerHttpClient` URI 编码为 `%253F` 的问题；改为统一放入 query map。

3. `NodeDetailTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数保持为当前执行逻辑读取的 `name`，description 明确限定为 Kubernetes Node 节点名称。
   - aliases 支持 `nodeName`、`hostName`、`targetName` 等。
   - 同步修复 `?name=...` 手拼 path 的潜在 `%253F` 编码问题。

4. 测试补充
   - `ToolParameterNormalizerTest`
     - 新增第一批 3 个 Tool 的 schema-first alias 归一化测试。
   - `ToolRegistryPromptContractTest`
     - 新增 prompt contract 测试，确保工具目录展示 canonical 参数，不展开 aliases。

### 测试结果
- 目标单测：
  - 命令：`mvn -Dtest=ToolParameterNormalizerTest,ToolRegistryPromptContractTest,ToolInputSchemaBuilderTest,AtlasToolCallbackTest test`
  - 结果：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

- 编译打包：
  - 命令：`mvn -DskipTests package`
  - 结果：BUILD SUCCESS。

- 服务健康检查：
  - 启动端口：8500。
  - 命令：`curl http://localhost:8500/actuator/health`
  - 结果：`{"status":"UP"}`。

- 真实 SSE E2E：
  1. 登录账号：`zhaotiandi / ninePwd!`，返回 `organizationId=100002`。
  2. 查询：`查询部署实例 aaaa 的详情`
     - SSE 返回 `event:done`。
     - 内容返回：`未查询到部署实例 aaaa 的相关详情信息。`
     - 服务日志确认调用：`[HTTP GET] /api/100002/deployment 参数={limit=100, name=aaaa, page=1}`。
     - 已确认 `%253F` 编码问题消失。
  3. 查询：`查看 pod nginx-not-exist-schema 最近 50 行日志`
     - SSE 返回 `event:done`。
     - 服务日志确认调用：`[HTTP GET] /api/log 参数={organizationId=100002, podName=nginx-not-exist-schema, keyword=nginx-not-exist-schema, lines=50, userId=zhaotiandi}`。

### 代码 Review
#### 优点
- 保持“小样本验证”策略，未盲目批量改 109 个 Tool。
- schema canonical 字段严格贴合当前 Tool 实际读取字段，避免 Prompt 契约与执行逻辑脱节。
- description 明确限定 `name` 的资源类型，降低 LLM 在 `name` 字段上的跨资源误填概率。
- 修复了真实 E2E 才暴露的 URL 查询参数拼接问题，提高 `deployment_detail` 与 `node_detail` 后端调用可靠性。

#### 风险
- `deployment_detail` 和 `node_detail` 仍共用 canonical `name`，长期看不如 `deploymentName/nodeName` 类型安全；当前为兼容旧执行逻辑的保守选择。
- `log_query` 的后端接口仍使用 `keyword` 语义，`podName` 与日志关键字存在一定耦合；后续如后端支持明确 podName 参数，可进一步收敛。
- 本轮 E2E 走的是 `CALL_TOOL -> tool_call` 简单路径，不是完整 `DELEGATE_REACT` 多步链路；参数契约对 ReAct Prompt 的收益已由 prompt contract 单测覆盖，但仍建议后续补专门 ReAct 多步 E2E。

### 后续建议
1. 继续按批次扩展 ToolParameterSpec：优先处理 detail/query/diagnose 类 Tool。
2. 后续独立重构 detail 类 Tool 的 canonical 字段：从 `name` 演进为资源类型明确的 `deploymentName/nodeName/podName`，并同步修改执行逻辑。
3. 增加 URL 参数构造专项扫描，查找所有 `path += "?"` 写法，统一改为 query map。
4. 补充完整 ReAct 多步链路 E2E，验证工具目录参数契约是否稳定提升 LLM 工具调用准确率。
