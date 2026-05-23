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



## 2026-05-21 20:35 - ToolParameterSpec 第三批：Pod/Deployment 查询主链路参数契约

### 背景
- 本轮继续推进 K8s 诊断/查询主链路参数契约建设，目标链路为：查 Pod → 查 Deployment/实例 → 查 Event → 汇总根因。
- 按专家会诊结论，禁止只声明 `ToolParameterSpec` 而不让 `doExecute` 使用参数，否则会形成“伪参数”并误导 ReAct/LLM。
- 源码复核确认当前真实已注册 Tool 为：
  - `pod_status` → `PodQueryTool`
  - `deployment_status` → `DeploymentQueryTool`
- `pod_query`、`deployment_query`、`event_query` 当前未定位为独立已注册 Tool，本轮不凭空新增伪 Tool。

### 变更内容
1. `PodQueryTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数：`namespace`、`podName`、`username`、`status`，全部保持可选，兼容零参数查看 Pod 列表。
   - aliases 支持 `ns`、`pod_name`、`targetName`、`userName`、`phase` 等历史/LLM 输出字段。
   - `doExecute` 从固定 `page/limit` 扩展为 `LinkedHashMap` query map，按需透传 `namespace/name/username/status`。
   - 继续通过 `resolveOrganizationId(params)` 解析组织 ID，未硬编码 orgId。

2. `DeploymentQueryTool`
   - 新增 `getParameterSpecs()`。
   - canonical 参数：`name`、`namespace`、`username`、`status`，全部保持可选。
   - description 明确平台术语：“实例”= Deployment，不是 Pod。
   - aliases 支持 `deploymentName`、`instanceName`、`deployName`、`ns`、`owner`、`instanceStatus` 等。
   - `doExecute` 使用 `LinkedHashMap` query map 透传筛选条件，禁止手拼 URL。

3. 测试补充
   - `ToolRegistryPromptContractTest`
     - 新增第三批 `pod_status` / `deployment_status` ReAct 工具目录参数契约测试。
     - 验证 prompt 展示 canonical 参数，不展开 alias。
   - `ToolParameterNormalizerTest`
     - 新增第三批 schema-first alias 归一化测试。
     - 验证 `pod_name/ns/userName/phase` 可归一到 Pod canonical 参数。
     - 验证 `deploymentName/ns/owner/instanceStatus` 可归一到 Deployment canonical 参数，且不会误归一为 `podName`。

### 重要修正
- 初次测试暴露 `pod_status` 的 description 中出现了 `pod_name` 字符串，导致 prompt contract 失败。
- 该问题说明 alias 即使只写在中文描述里，也会诱导 LLM 输出非 canonical 字段。
- 已修正为：alias 只保留在 `ToolParameterSpec.aliases` 元数据中，不出现在 ReAct prompt 描述文本中。

### 测试结果
- 定向测试：
  - 命令：`mvn -q -Dtest=ToolRegistryPromptContractTest,ToolParameterNormalizerTest test`
  - 结果：通过，`Failures: 0, Errors: 0`。
- 全量测试：
  - 命令：`mvn -q test`
  - 结果：BUILD SUCCESS。
- 打包验证：
  - 命令：`mvn -q -DskipTests package`
  - 结果：BUILD SUCCESS。

### 代码 Review
#### 优点
- 遵循“先实验再铺开”：先实现 `pod_status` 小样本并定向测试，再铺到真实存在的 `deployment_status`。
- Tool schema 与执行逻辑同步修改，避免“声明参数但不生效”的伪参数问题。
- URL query 使用 map 构造，避免手工拼接带来的编码和注入风险。
- orgId 继续从上下文/参数解析，未新增硬编码组织 ID。
- 明确平台术语：“实例”= Deployment，降低 Pod/Deployment 混淆风险。
- prompt contract 测试覆盖 alias 不外显，防止 ReAct 工具目录诱导 LLM 生成 alias。

#### 风险
- 后端 `/api/{orgId}/pod` 与 `/api/{orgId}/deployment` 对 `namespace/name/username/status` 的具体筛选支持度仍依赖 kube-manager 实现；本轮保证 query map 真实透传，但未新增真实后端联调 E2E。
- `deployment_status` 的 canonical `name` 仍存在资源类型歧义；当前为贴合现有执行逻辑的兼容选择，后续可在统一迁移时演进为更强类型的 `deploymentName`。
- `event_query` 当前没有已注册 Tool，诊断链路中的 Event 查询仍是缺口。

### 后续建议
1. 继续定位 kube-manager Event API 与前端事件入口，确认是否应新增 `event_query` Tool。
2. 为 Pod/Deployment 列表接口补真实 SSE E2E，验证后端筛选字段实际命中效果。
3. 后续批量扩展查询类 Tool 时，继续坚持：先确认接口支持，再 schema + `doExecute` 同步透传。
4. 可考虑抽取通用 `putIfPresent` 到 BaseTool，减少各 Tool 私有重复代码。


## 2026-05-21 21:30 - event_query 小样本落地：基于 kube-manager Pod warning 的异常事件摘要 Tool

### 背景
- 上一轮第三批 Tool 参数契约扩展后，诊断链路仍缺少 `event_query`。
- 用户明确要求：暂不在 kube-agent 直接引入 Kubernetes Java Client，优先基于 kube-manager 已有能力实现。
- 专家会诊结论：当前 kube-manager 暂无独立完整 Event API；可先基于 `GET /api/{orgId}/pod` 返回记录中的 `warning` 字段，实现 Pod Warning/异常事件摘要查询。
- 本轮遵循“不造伪参数”原则：只声明真实生效参数，不把 Kubernetes 原生 EventList 能力伪装到 Tool schema 中。

### 方案边界
- 新 Tool 名称：`event_query`。
- 能力定位：Pod Warning/异常事件摘要查询，不是完整 Kubernetes EventList。
- 后端调用：`GET /api/{orgId}/pod`。
- 后端透传参数：`namespace`、`username`、`status`。
- kube-agent 本地过滤参数：`podName`、`reason`、`keyword`。
- 明确不声明：`fieldSelector`、`labelSelector`、`since`、`type`、`involvedObjectKind` 等 kube-manager 当前不支持的 Kubernetes 原生 Event 参数。
- 返回结构：`dataKind=podWarningSummaries`、`podWarningSummaries`、`count`、`source`、`query`、`limitations`。

### 本轮代码变更
1. 新增 `src/main/java/com/atlas/tool/impl/EventQueryTool.java`
   - 注册 `@AtlasToolMapping(name = "event_query", agent = "diag", intentId = "event_query")`。
   - 通过 `resolveOrganizationId(params)` 获取 orgId，未硬编码组织 ID。
   - 使用 `LinkedHashMap` query map 构造后端查询参数，未手拼 URL query。
   - 基于 kube-manager Pod 列表 `warning/warnings/eventWarning/message` 字段生成 Warning 摘要。
   - 空 warning 不输出，避免把正常 Pod 包装成“无事件”。
   - 失败返回改为泛化提示，详细异常仅写入日志，避免直接暴露后端异常细节。

2. 更新 `src/main/resources/intents.yml`
   - 新增 `event_query` intent。
   - description 明确声明该能力“基于 kube-manager Pod 列表 warning 字段，不是完整 Kubernetes EventList”。
   - 参数列表只包含真实生效的 6 个字段。

3. 更新参数契约测试
   - `ToolParameterNormalizerTest`：覆盖 `event_query` 的 schema-first alias 归一化，并验证不产生 `fieldSelector` 伪参数。
   - `ToolRegistryPromptContractTest`：验证 ReAct prompt 暴露 canonical 参数，且不暴露不支持的 Kubernetes 原生 Event 参数。

4. 新增 `src/test/java/com/atlas/tool/impl/EventQueryToolTest.java`
   - 覆盖主流程：查询 Pod warning、透传 `namespace/status`、本地按 `reason` 过滤。
   - 覆盖参数契约：只暴露真实支持参数，不暴露 Kubernetes 原生 Event 伪参数。
   - 覆盖边界：非 List 响应返回空摘要、空白过滤参数安全处理。
   - 覆盖大小写不敏感的 `podName/keyword` 本地过滤。

### 测试结果
- 定向测试：
  - 命令：`mvn -Dtest=EventQueryToolTest,ToolParameterNormalizerTest,ToolRegistryPromptContractTest test`
  - 结果：`Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 全量测试：
  - 命令：`mvn test`
  - 结果：`Tests run: 134, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

### 代码 Review
#### 优点
- 严格遵守用户约束：未引入 Kubernetes Java Client，未新增 `pom.xml` 依赖。
- Tool schema 与执行逻辑一致：声明的 6 个参数均有真实透传或本地过滤行为。
- 返回结果显式包含 `limitations`，防止 LLM/ReAct 将本工具误解为完整 EventList。
- orgId 继续从上下文/参数解析，未出现硬编码组织 ID。
- URL query 使用 map 构造，避免手拼 query 的编码风险。
- 单测覆盖正常路径、参数契约、prompt contract、空响应、大小写过滤与伪参数防护。
- 独立 Reviewer 进行 fail-closed 审查，结论为无阻塞问题；根据建议补强了边界测试和错误信息泛化。

#### 风险
- 当前能力依赖 kube-manager Pod DTO 中的 `warning` 字段，信息粒度不等同于 Kubernetes 原生 Event。
- 本轮未做真实后端 SSE E2E；仅通过单测和 Spring 全量测试验证代码路径。
- `limit=100` 是当前小样本默认值，若集群 Pod 数较大，后续需要分页聚合或按 namespace/podName 更精确查询。
- `orgId` 参与 path 构造，当前依赖 `resolveOrganizationId` 的可信输出；后续可考虑在 BaseTool 层统一增加 orgId 格式校验。

### 后续建议
1. 在 kube-manager 暴露真实 Event API 后，可新增 `kubernetes_event_query` 或升级 `event_query`，但必须同步调整 schema 与 limitations。
2. 结合 ReAct 诊断链路，把 `pod_status -> deployment_status -> event_query -> log_query` 做成可观测多步 E2E。
3. 若真实后端支持 Pod 精确 name 查询，应将 `podName` 从本地过滤升级为后端透传参数。
4. 抽取 `putIfPresent` 等 query 构造小工具到 BaseTool，减少各 Tool 重复代码。

## 2026-05-21 22:50 - event_query 接入 ReAct Pod 多步诊断提示词链路

### 背景
- 上一轮已新增 `event_query`，但它只是独立 Tool；ReAct 多步诊断 Prompt 尚未明确要求在 Pod 故障排查中使用事件摘要。
- 现有 `AtlasBrain.shouldUseReAct()` 对 `Warning`、`FailedScheduling`、`调度失败` 等事件/调度类故障词召回不足，可能导致复杂诊断没有进入 ReAct 多步链路。
- 本轮遵循“小样本先验证”和 TDD：先写 Prompt/Brain 契约测试确认缺口，再做最小实现。

### 变更内容
1. `AtlasBrain`
   - 扩展 ReAct 静态守卫关键词：`warning`、`event`、`事件`、`异常事件`、`告警`、`调度失败`、`failedscheduling`、`unschedulable`、`failedmount`、`createcontainerconfigerror`、`createcontainererror`。
   - 目标是将 Pod Warning、调度失败、挂载失败、容器创建失败等诊断类问题召回到 `DELEGATE_REACT`。

2. `ReActPromptBuilder`
   - 新增“Pod 诊断工具调用规则”。
   - 规则要求默认先查 `pod_status` 获取基础状态。
   - 对 Pending、ImagePullBackOff、ErrImagePull、ContainerCreating、CreateContainerConfigError、CreateContainerError、FailedMount、Unschedulable、FailedScheduling 等控制面/调度/镜像/创建阶段问题，优先调用 `event_query`。
   - 对 CrashLoopBackOff、RestartCount>0、Running 但 Ready=false、Terminated Error、OOMKilled 等运行时问题，要求结合 `event_query` 与 `log_query`。
   - 明确 `event_query` 只是基于 kube-manager Pod warning 字段的异常事件摘要，不是完整 Kubernetes EventList；禁止构造 `fieldSelector/labelSelector/since/type/involvedObjectKind` 等不支持参数。
   - 要求最终诊断按“现象、证据、判断、建议”组织，避免单工具绝对结论。

3. 测试补充
   - 新增 `ReActPromptBuilderPodDiagnosticContractTest`：锁定 ReAct Prompt 中必须包含 `pod_status/event_query/log_query` 证据链、事件能力边界、不支持参数禁止语义、最终诊断结构。
   - 扩展 `AtlasBrainMockTest`：覆盖 Warning 事件、FailedScheduling、调度失败等输入必须进入 ReAct 守卫。

### 测试结果
- 红灯验证：新增测试最初失败，失败点为 `AtlasBrain` 未覆盖 Warning/FailedScheduling/调度失败，`ReActPromptBuilder` 未包含 Pod 诊断工具调用规则。
- 定向测试：
  - 命令：`mvn -Dtest=ReActPromptBuilderPodDiagnosticContractTest,AtlasBrainMockTest test`
  - 结果：`Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 宽测试：
  - 命令：`mvn -Dtest=ReActPromptBuilderPodDiagnosticContractTest,AtlasBrainMockTest,ToolRegistryPromptContractTest,EventQueryToolTest,ReActEngineParamMergeTest,ReActEnginePolicyTest test`
  - 结果：`Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 全量测试：
  - 命令：`mvn test`
  - 结果：`Tests run: 135, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- 安全扫描：新增 diff 未发现硬编码密钥、危险进程执行、eval/exec 等问题。
- 独立代码 Review：通过，无阻塞问题；建议后续补行为级 ReAct 多步测试。

### 代码 Review
#### 优点
- 变更范围非常小，只修改 Brain 召回关键词与 ReAct Prompt 策略，不改 ReActEngine 执行循环，降低回归风险。
- Prompt 明确声明 `event_query` 能力边界，避免把 Pod warning 摘要伪装成完整 Kubernetes Event API。
- TDD 顺序清晰：先红灯确认缺口，再最小实现，再定向/宽/全量测试。
- Final Answer 结构化要求有助于减少“只凭日志/只凭事件”的单证据误判。

#### 风险
- `warning/event/failed` 等关键词较宽，可能让部分简单查询进入 ReAct 链路，增加一次 LLM/工具编排成本；当前为了诊断命中率优先可以接受。
- 新增测试是 Prompt 契约测试，尚未验证真实 LLM 是否严格按 `pod_status -> event_query -> log_query` 顺序执行。
- `event_query` 仍受 kube-manager Pod warning 字段粒度限制，不等同于原生 Kubernetes EventList。

### 后续建议
1. 补充 mock LLM 或可控 ReAct loop 行为级测试，验证 Pending/FailedScheduling 优先调用 `event_query`。
2. 补真实 SSE E2E：构造/选择一个存在 warning 的 Pod，观察 ReAct 是否形成 `pod_status -> event_query -> final/log_query` 证据链。
3. 若后续发现简单事件查询链路过重，可将 Brain 关键词从宽泛词改为组合判定或交给 IntentArbiter/Embedding 做更细路由。

## 2026-05-23 12:50 - M5.8 业务 Tool 禁止 sysadmin fallback token 自动降级

### 背景
- 专家会诊后，本轮选择最高价值且最小扩散面的安全闭环：`KubeManagerHttpClient#get/post/delete` 业务请求入口。
- M5.7 已完成 `fallbackOrgId` 可信语义收口，但 HTTP 客户端仍存在一个更底层的风险：业务 Tool 在缺少用户 ThreadLocal Token 时可能通过 `resolveToken()` 透明降级为 sysadmin fallback token。
- 该行为对开发兼容友好，但对多租户/RBAC 是权限放大器：一旦 Graph/ReAct/异步链路漏传 token，业务请求可能不再以真实用户身份执行。

### 专家会诊结论
1. 安全与多租户视角：业务请求必须 fail-closed，系统级 fallback 只能作为显式白名单能力，不能出现在 Tool 默认路径。
2. 架构视角：不要大范围重构上层 Agent 编排，先在 HTTP 客户端出口做最小安全门，后续再抽象 SystemContextPolicy。
3. 测试视角：必须用 MockRestServiceServer 断言缺 Token 时不发出任何 fallback 登录/业务请求，避免只测异常文案。
4. 开源对标视角：LangChain/LangGraph、Dapr、K8s controller-runtime 的通用经验是默认用户上下文优先，特权上下文必须显式声明和可审计。

### 变更内容
1. `KubeManagerHttpClient`
   - `get/post/delete` 改为调用 `resolveUserTokenRequired(operation, path)`。
   - 新增 `resolveUserTokenRequired`：ThreadLocal 用户 Token 为空时抛出 `IllegalStateException`，拒绝 sysadmin fallback。
   - 保留 `resolveToken()`，但文档明确其只允许未来显式系统任务使用，禁止业务 Tool 默认路径调用。

2. `KubeManagerHttpClientTokenFallbackSecurityTest`
   - 新增 5 个测试覆盖 GET/POST/DELETE 缺用户 Token fail-closed。
   - 验证用户 Token 存在时 Authorization Header 使用真实用户 Token。
   - 验证系统任务 fallback 入口仍保留，避免误伤未来健康探测/后台同步场景。

3. 文档
   - `CHANGELOG.md` 新增 M5.8 条目。
   - `docs/M5_8_AUDIT_CHECKLIST_20260523.md` 新增阶段审计清单。

### 测试结果
- 定向测试：`mvn test -q -Dtest=KubeManagerHttpClientTokenFallbackSecurityTest` → ✅ 5 tests, 0 failures。
- 安全组合回归：`mvn test -q -Dtest=KubeManagerHttpClientResolveOrgIdSecurityTest,M57FallbackOrgIdSourceContractTest,BaseToolOrganizationIdGovernanceTest,KubeManagerHttpClientTokenFallbackSecurityTest` → ✅ 17 tests, 0 failures。
- 全量测试：`mvn test -q` → ✅ 182 tests, 0 failures, 0 errors, 0 skipped。
- 打包：`mvn -q -DskipTests package` → ✅ BUILD SUCCESS。
- `git diff --check`：✅ 通过。
- 新增 diff 行敏感信息/危险执行扫描：✅ 未发现硬编码密钥、PAT、危险进程执行、`eval/exec` 等模式。

### 代码 Review
#### 优点
- 改动点集中在 HTTP 出口，安全收益大、扩散风险小。
- 业务请求与系统任务 Token 语义被明确拆开，降低后续误用概率。
- 单测不仅验证异常，还验证不会触发 fallback 登录请求，覆盖了真实安全意图。
- 保持详细中文注释，方便后续维护者理解为什么不能自动降级。

#### 风险
- `resolveToken()` 仍然保留 fallback 能力，后续如果新增调用方必须强制 Review 和测试。
- 本轮未全量扫描是否存在绕过 `KubeManagerHttpClient#get/post/delete` 的独立 HTTP 出口。
- 本轮没有做真实 SSE E2E；但安全边界位于客户端 Token 解析层，Mock 测试已精确覆盖。

### 后续建议
1. 下一批做“HTTP 出口契约审计”：扫描所有 `RestClient/WebClient/RestTemplate` 直接调用点，确认业务请求都经过统一安全门。
2. 引入 `SystemContextPolicy` 或源码契约测试，让系统任务 fallback 必须显式白名单化。
3. 服务重启后补一次登录 + 只读查询 SSE 冒烟，确认业务链路正确携带用户 Token。

### 持续学习总结
- 多租户 Agent 系统里，“开发兼容 fallback”很容易变成“生产权限放大器”。
- 安全治理应优先卡在最底层出口，先让默认路径安全，再逐步给系统任务开显式白名单。
- 对安全分支的测试不能只断言抛异常，还要断言危险副作用没有发生。


## 2026-05-23 16:31 - M5.9 HTTP 出口与 fallback token 源码契约治理

### 背景
- M5.8 已将 `KubeManagerHttpClient#get/post/delete` 收口为必须使用用户 ThreadLocal Token，缺失用户上下文时 fail-closed。
- 本轮继续推进时，哥哥明确要求：避免影响 kube-manager 的数据；所有删除和修改类不需要真实测试，只需要跑通逻辑。
- 因此 M5.9 选择低副作用、高收益的小样本落点：新增源码级契约测试，防止未来业务代码绕过统一 HTTP 出口或重新把 sysadmin fallback token 接回业务默认路径。

### 专家 Review 会诊结论
- 快速专家 Review 会诊结果：PASS with Notes。
- 结论：当前源码契约测试方向正确，不访问真实 kube-manager，不会影响 kube-manager 数据，可以合入。
- 专家建议补强：HTTP 出口扫描模式应覆盖 `HttpURLConnection/openConnection/HttpClient.newHttpClient` 等直接 HTTP 路径，并区分 kube-manager 出口与外部下载出口。
- 已按建议补强，并将 `ModelDownloader` 显式归类为“外部 Embedding 模型下载出口，不访问 kube-manager 数据面”。

### 变更内容
1. 新增 `M59HttpSecurityBoundaryContractTest`
   - 扫描 `src/main/java` 生产源码。
   - 白名单外禁止直接创建/注入 HTTP 客户端，避免业务 Tool 绕过 `KubeManagerHttpClient`。
   - 直接 HTTP 出口模式覆盖：`RestClient`、`RestTemplate`、`WebClient`、`HttpURLConnection`、`HttpClient`、`openConnection`、`OkHttpClient`、`Feign`、`Apache HttpClient` 等。
   - 白名单明确限定：
     - `KubeManagerHttpClient`：统一 kube-manager 数据面 HTTP 出口；
     - `AuthController`：登录代理入口；
     - `ModelDownloader`：外部 Embedding 模型下载，不访问 kube-manager 数据面。
2. 锁定 M5.8 token fallback 边界
   - `KubeManagerHttpClient#get/post/delete` 必须调用 `resolveUserTokenRequired`。
   - 业务方法不得调用允许 sysadmin fallback 的 `resolveToken()`。
   - 生产源码中 `resolveToken()` 调用点数量被锁定为仅方法声明本身。
3. 文档同步
   - `CHANGELOG.md` 新增 M5.9 记录。
   - 新增 `docs/M5_9_AUDIT_CHECKLIST_20260523.md`。

### 验证结果
- 定向逻辑验证：`mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest` → 通过。
- 安全组合回归：`mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` → 通过。
- 打包：`mvn -q -DskipTests package` → BUILD SUCCESS。
- 格式检查：`git diff --check` → 通过。
- Diff 敏感信息/危险执行扫描：通过，未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。
- 数据影响：未启动服务，未调用真实 kube-manager API，未执行真实删除/修改操作。

### 代码 Review
#### 优点
- 完全符合“避免影响 kube-manager 数据”的要求，只做源码扫描和单元逻辑验证。
- 把 M5.8 的安全修复升级为 CI 可持续防回归契约，降低未来新增 Tool 绕过统一出口的风险。
- 显式区分 kube-manager 数据面出口、登录代理出口和外部模型下载出口，避免误把所有 HTTP 行为混为一类。
- 失败信息会输出具体文件与行号，便于后续新增代码时快速定位违规点。

#### 风险
- 当前是源码字符串级扫描，不是 AST/ArchUnit 级强约束；如果未来代码通过非常规封装或反射绕过，可能需要更强架构测试。
- `AuthController` 当前为文件级白名单，未来如果在该类中加入非登录代理的数据面访问，契约可能无法细分识别。
- HTTP 客户端生态较多，后续新增 Feign/Retrofit/第三方 SDK 时，需要同步扩展契约模式或白名单说明。

### 后续建议
1. 后续可引入 ArchUnit，将包依赖、类依赖、方法调用约束升级为结构化架构测试。
2. 后续所有新增外部 HTTP 出口必须明确分类：kube-manager 数据面、认证代理、外部资源下载或第三方服务，并写入契约白名单说明。
3. 继续保持“专家会诊 → 小样本 → 逻辑验证 → Review → 文档 → 双远端同步”的闭环。

## 2026-05-23 17:36 - M5.10 ArchUnit 架构级安全边界契约治理

### 背景
- M5.9 已通过源码字符串契约测试锁定 HTTP 出口与 fallback token 方法体语义。
- 继续推进时，目标是把“源码扫描”进一步升级为“架构级依赖边界测试”，但仍必须避免影响 kube-manager 数据。
- 本轮选择小样本落地 ArchUnit，只做静态字节码/依赖分析，不启动服务、不访问真实 kube-manager、不执行真实删除/修改。

### 专家会诊与开源调研结论
- Java 架构专家建议：M5.10 适合最小引入 ArchUnit；ArchUnit 负责结构级、依赖级规则，M5.9 源码契约继续负责方法体语义。
- 安全专家复核：PASS；要求 ArchUnit 测试不得使用 `@SpringBootTest`，不得注入 Bean，不得调用真实 HTTP 方法。
- 开源调研：TNG/ArchUnit 是 Java architecture test library，用 plain Java unit testing 检查架构和编码规则，适合作为 CI 中的架构边界防回归机制。

### 变更内容
1. `pom.xml`
   - 新增 test scope 依赖：`com.tngtech.archunit:archunit-junit5:1.3.0`。
   - 该依赖仅用于测试，不进入生产运行时。
2. 新增 `M510ArchitectureBoundaryTest`
   - 使用 `@AnalyzeClasses(packages = "com.atlas", importOptions = DoNotIncludeTests.class)`。
   - 不使用 `@SpringBootTest`，不启动 Spring 容器。
   - 规则一：白名单外生产代码不得直接依赖底层 HTTP 客户端。
   - 规则二：`com.atlas.tool..` 不得依赖底层 HTTP 客户端。
   - 规则三：`com.atlas.controller..` 不得直接依赖 `com.atlas.tool.impl..`。
3. 底层 HTTP 客户端覆盖范围
   - `RestClient`
   - `RestTemplate`
   - `WebClient`
   - `java.net.*`
   - `OkHttp`
   - `Feign/OpenFeign`
   - Apache HttpClient 4/5

### 验证结果
- 定向验证：`mvn test -q -Dtest=M510ArchitectureBoundaryTest` → 通过。
- 安全组合回归：`mvn test -q -Dtest=M510ArchitectureBoundaryTest,M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` → 通过。
- 打包：`mvn -q -DskipTests package` → BUILD SUCCESS。
- 格式检查：`git diff --check` → 通过。
- Diff 敏感信息/危险执行扫描：通过，未发现新增密钥、PAT、危险进程执行、`eval/exec` 等模式。
- 数据影响：未启动服务，未访问真实 kube-manager API，未执行真实删除/修改操作。

### 代码 Review
#### 优点
- 将 M5.9 的源码级 HTTP 出口治理升级为更稳定的 ArchUnit 架构依赖治理。
- 保持最小落地，只加三条高价值规则，避免一次性大范围重构历史代码。
- 测试不启动 Spring，不访问真实服务，完全符合“避免影响 kube-manager 数据”的要求。
- 明确保留 M5.9 源码契约，避免用 ArchUnit 强行替代其不擅长的方法体语义检查。

#### 风险
- 当前规则仍是第一批最小架构边界，尚未覆盖 service/orchestrator/react/config 等完整层级。
- `com.atlas.http..` 作为包级白名单粒度较粗，未来如该包内出现非受控 HTTP 出口，需要继续细化。
- ArchUnit 规则会增加 CI 对依赖边界的敏感度，未来新增合法例外时必须写清白名单原因。

### 后续建议
1. M5.11 可继续推进“Tool 注解 method 与实际 HTTP 调用一致性契约”，专门防止 Agent 以为是 GET 但代码实际 POST/DELETE 的语义错位。
2. 后续可逐步增加 ArchUnit layer rules：controller → orchestrator/react → tool/core → http，保护整体依赖方向。
3. 保持 M5.7-M5.10 安全治理链共同运行，形成多层防线。
