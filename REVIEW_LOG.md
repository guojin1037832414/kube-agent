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
