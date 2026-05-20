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

## 2026-05-20 23:45 - 文档里程碑重对齐 + URL query 拼接专项清理

### 背景
- 用户要求“更新文档，然后继续推进”。
- 文档层面：`ROADMAP.md` 仍停留在 M1.5 旧基线，`CHANGELOG.md` 未记录 M3.2 ReAct 与 M4.1 Tool Schema，`docs/会话上下文快照_20260520.md` 仍把 ReAct 核心文件列为待创建。
- 代码层面：上一轮 E2E 已发现 `path += "?name="` 可能被 URI builder 编码为 `%253F`，扫描后剩余 4 个同类风险点。

### 专家会诊结论
1. 文档必须对齐到当前真实状态：M3.2 ReAct MVP 已落地，M4.1 Tool Schema 参数契约分批铺开中。
2. `CHANGELOG.md` 应补 `[M3.2]` 与 `[M4.1]`，不能继续写 M3/M4 待启动。
3. `docs/会话上下文快照_20260520.md` 应从 `fdd8c42` 更新到 `c296a3c`，并把“新建 ReActEngine”等待办改为已完成归档。
4. URL query 构造应统一改为 `httpClient.get(path, queryMap)`，禁止手拼 `?xxx=`。

### 本轮代码变更
修复 4 个剩余 URL query 拼接点：

| 文件 | 原字段 | 修复方式 |
|------|--------|----------|
| `HelmChartSearchTool.java` | `?keyword=` | `query.put("keyword", kwParam.toString())` |
| `HelmChartInfoTool.java` | `?chart=` | `query.put("chart", chartParam.toString())` |
| `ImageDetailByNameTool.java` | `?name=` | `query.put("name", nameParam.toString())` |
| `FileSelectStorageTool.java` | `?name=` | `query.put("name", nameParam.toString())` |

所有文件保留原有 `page=1`、`limit=100` 行为，并使用 `LinkedHashMap` 保持参数构造清晰可审计。

### 本轮文档变更
- 重写 `ROADMAP.md`：对齐当前基线为“M3.2 ReAct MVP 已打通；M4.1 Tool Schema 参数契约分批铺开中”。
- 重写 `CHANGELOG.md`：新增 M3.2 ReAct 与 M4.1 Tool Schema 章节。
- 更新 `docs/会话上下文快照_20260520.md`：从旧 `fdd8c42` 快照更新到当前 `c296a3c` 之后的真实状态。
- 追加当前 `REVIEW_LOG.md` 记录。

### 静态验证
- 命令：`grep -RIn 'path += "?' src/main/java/com/atlas/tool/impl || true`
- 结果：无输出。
- 命令：`grep -RIn '\?name=|\?chart=|\?keyword=' src/main/java/com/atlas/tool/impl || true`
- 结果：无输出。

### 代码 Review
#### 优点
- 修复范围小且明确，只处理 4 个已扫描出的风险点。
- 不改变业务参数语义，不改变必填参数校验，不改变分页默认行为。
- 统一消除 URL query injection 和 `%253F` 编码风险。
- 文档同步反映真实工程进度，避免 ROADMAP/CHANGELOG/会话快照继续误导后续开发。

#### 风险
- 本轮 URL 修复仍需编译、重启和真实 SSE E2E 验证。
- Helm 相关接口可能因后端 Helm 服务未连接返回业务失败；本轮验收重点是 path/query 分离，不以 Helm 业务数据是否存在为唯一标准。
- 里程碑全景图与 `PROJECT_ATLAS_V3.md` 仍建议后续继续做深度重对齐。

### 后续建议
1. 执行目标测试、package、重启服务、4 条 E2E 查询。
2. 继续第二批 `ToolParameterSpec`：优先对本轮 4 个 URL 修复 Tool 补参数契约。
3. 后续单独更新 `docs/v3.1/项目里程碑全景图_20260519.md` 与 `PROJECT_ATLAS_V3.md`，避免总览文档漂移。

### 执行结果更新（2026-05-21 00:00）
- 目标单测：`ToolParameterNormalizerTest,ToolRegistryPromptContractTest,ToolInputSchemaBuilderTest,AtlasToolCallbackTest` 全部通过，`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`。
- 打包：`mvn -DskipTests package`，BUILD SUCCESS。
- 服务重启：端口 8500，`/actuator/health={"status":"UP"}`。
- 真实 SSE E2E：4/4 返回 `event:done`。
  1. `查询名称为 test-storage 的存储详情` → 命中 `file_select_storage`，日志确认 `/api/100002/file/selectStorage 参数={page=1, limit=100, name=test-storage}`。
  2. `查询镜像 nginx:latest 的详情` → 命中 `image_detail_by_name`，日志确认 `/api/100002/image/name 参数={page=1, limit=100, name=nginx:latest}`。
  3. `查询 Helm Chart nginx 的详情` → 命中 `helm_chart_info`，日志确认 `/api/100002/helm/charts/single 参数={page=1, limit=100, chart=nginx}`。
  4. `搜索 Helm Chart 关键字 redis` → SSE `event:done`，Brain target=`helm_chart_search`；业务返回空结果属于可接受状态。

## 2026-05-21 01:30 - ToolParameterSpec 第二批：Storage/Image/Helm 查询类参数契约

### 背景
- 在完成 URL query 拼接专项清理后，继续按“先实验再铺开”推进 Tool 参数契约。
- 本批选择刚刚通过真实 SSE E2E 的 4 个 Tool，链路稳定、风险可控：
  - `file_select_storage`
  - `image_detail_by_name`
  - `helm_chart_info`
  - `helm_chart_search`

### 专家会诊结论
1. 不改执行字段名，canonical 必须贴合当前 Tool 实际读取逻辑。
2. `name` 字段高度歧义，必须通过 description 明确资源类型，不能全局猜测。
3. aliases 只用于 schema-first normalizer 兼容历史/LLM输出，不在 ReAct 工具目录展开，避免反向诱导 LLM 输出 alias。
4. Helm 语义必须区分 Chart / Release / Repository；keyword 只能表示模糊搜索词，不是精确名称。

### 变更内容
1. `FileSelectStorageTool`
   - 新增 `getParameterSpecs()`。
   - canonical: `name`（必填）。
   - description 明确：存储卷/PVC 名称，不是文件名、目录名、镜像名或 StorageClass。
   - aliases: `storageName`, `storage_name`, `storage`, `pvc`, `pvcName`, `volumeName`, `targetName` 等。

2. `ImageDetailByNameTool`
   - 新增 `getParameterSpecs()`。
   - canonical: `name`（可选，匹配当前执行逻辑）。
   - description 明确：容器镜像名称或镜像引用，如 `nginx:latest`、`library/nginx:1.25`。
   - aliases: `imageName`, `image_name`, `image`, `containerImage`, `imageRef`, `targetName` 等。

3. `HelmChartInfoTool`
   - 新增 `getParameterSpecs()`。
   - canonical: `chart`（必填）。
   - description 明确：Helm Chart 名称或标识，不是 Helm Release 名称，也不是 Dashboard 图表。
   - aliases: `chartName`, `chart_name`, `helmChart`, `helm_chart`。

4. `HelmChartSearchTool`
   - 新增 `getParameterSpecs()`。
   - canonical: `keyword`（可选）。
   - description 明确：Helm Chart 模糊搜索关键字，不是精确 Chart 名称、Release 名称或仓库名称。
   - aliases: `q`, `query`, `search`, `searchText`, `search_text`, `filter`。

5. 测试补充
   - `ToolParameterNormalizerTest`
     - 新增第二批 Storage/Image/Helm Tool schema-first alias 归一化测试。
     - 验证 `storageName -> name`、`imageName -> name`、`chartName -> chart`、`searchText -> keyword`。
     - 验证不会误归一到 `storageClass`、`podName`、`deploymentName`、`releaseName`、`name` 等错误字段。
   - `ToolRegistryPromptContractTest`
     - 新增第二批 Tool Prompt contract 测试。
     - 验证 ReAct 工具目录只展示 canonical 参数，不展开 `storage_name/image_name/chart_name/searchText` 等 alias。

### 测试结果
- 目标单测：
  - 命令：`mvn -Dtest=ToolParameterNormalizerTest,ToolRegistryPromptContractTest,ToolInputSchemaBuilderTest,AtlasToolCallbackTest test`
  - 结果：`Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 打包：
  - 命令：`mvn -DskipTests package`
  - 结果：BUILD SUCCESS。
- 服务：
  - 重启新 jar，端口 8500。
  - `/actuator/health={"status":"UP"}`。
- 真实 SSE E2E：
  1. `查询名称为 test-storage 的存储详情` → `event:done`，无 error；日志命中 `file_select_storage`，参数 `{page=1, limit=100, name=test-storage}`。
  2. `查询镜像 nginx:latest 的详情` → `event:done`，无 error；日志命中 `image_detail_by_name`，参数 `{page=1, limit=100, name=nginx:latest}`。
  3. `查询 Helm Chart nginx 的详情` → `event:done`，无 error；日志命中 `helm_chart_info`，参数 `{page=1, limit=100, chart=nginx}`。
  4. `搜索 Helm Chart 关键字 redis` → `event:done`，无 error；业务返回空结果属于可接受状态。

### 代码 Review
#### 优点
- 继续保持小批量推进，不盲目全量铺开 109 个 Tool。
- 只补参数契约，不改执行字段名，避免破坏当前稳定路径。
- 对高歧义 `name` 做了资源类型限定，降低 LLM 在 Storage/Image/Deployment/Pod 间误填概率。
- Normalizer 和 Prompt 两条线都有测试，兼顾执行兼容与 Prompt 约束。

#### 风险
- `file_select_storage`、`image_detail_by_name` 当前 canonical 仍是 `name`，长期看语义不如 `storageName/imageName` 清晰；后续如迁移需同步改执行逻辑、必填校验和测试。
- Helm Chart 搜索/详情返回空数据与后端仓库配置有关，不代表 Tool 失败；E2E 验收重点是路由、参数和 SSE 生命周期。
- 本批仍主要验证 `CALL_TOOL -> tool_call` 路径，完整 ReAct 多步链路还需专项 E2E。

### 后续建议
1. 第三批 ToolParameterSpec 继续选择已验证链路的查询/诊断 Tool，例如 `pod_status`、`deployment_query`、`event_query`。
2. 对 `name` canonical 的工具建立迁移计划，逐步转为 `imageName/storageName/deploymentName/nodeName` 等更语义化字段。
3. 增加 ReAct 多步成功路径 E2E，验证 Prompt 参数契约是否能让 LLM 在多轮 Action 中优先输出 canonical。

