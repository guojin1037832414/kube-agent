# M5.21-26 HPC 环境与 Lmod module 敏感只读 Tool 审计

> 结论: 本批仅接入 HPC Miniconda 环境列表与 Lmod module 列表 GET 读取能力；创建/删除环境、安装包、安装或删除 module 继续 HOLD。

## 成熟项目证据

- 后端: `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\hpc\controller\HpcEnvironmentModuleController.java`
- 成熟 GET:
  - `GET /api/{organizationId}/hpc-env/environments/{clusterId}`
  - `GET /api/{organizationId}/hpc-env/modules?clusterId=...`
- 高风险写操作:
  - `POST /api/{organizationId}/hpc-env/environments/{clusterId}`
  - `DELETE /api/{organizationId}/hpc-env/environments/{clusterId}/{envName}`
  - `POST /api/{organizationId}/hpc-env/environments/{clusterId}/{envName}/packages`
  - `POST /api/{organizationId}/hpc-env/modules`
  - `DELETE /api/{organizationId}/hpc-env/modules/{moduleName}/{version}`

## 专家会诊结论

- 后端/API 专家: 环境与 module 查询是作业提交前的重要准备数据，但环境名、module 名称和版本会暴露集群软件栈，应区别于普通 partition/sbatch 参数读取。
- 安全/RBAC 专家: 采用最小权限和人工确认模式，标记为 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`；clusterId 只能来自可信上下文/成熟接口返回的数字 ID。
- 测试架构专家: 不访问真实 8100；使用 mock HTTP client 锁定 method/path/query，并测试非法 clusterId 在 HTTP 前 fail-closed。
- 外部参考:
  - OpenAI Agents SDK Human-in-the-loop
  - Microsoft Agent Framework Function Tools with Approval
  - OWASP LLM06 Excessive Agency

## 本批交付

- `HpcEnvironmentListTool`
- `HpcModuleListTool`
- `hpc_environment_list` 与 `hpc_module_list` 意图
- `HpcEnvironmentModuleToolHttpContractTest`
- `M511AtlasToolHttpContractTest` 新增 HPC environment/module `SENSITIVE_READ` endpoint 精确白名单

## HOLD 清单

- 创建或删除 Miniconda 环境
- 在环境中安装 package
- 安装或删除 Lmod module
- controller 中的 `GET /api/{organizationId}/hpc-env/test` 属测试/探测入口，不接入 Agent Tool

## 验证计划

- 定向测试: `mvn -q "-Dtest=HpcEnvironmentModuleToolHttpContractTest,HpcJobPreparationToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest" test`
- 关键回归: 覆盖 ReAct、HITL fail-closed、安全执行、M5.21 相关 HTTP 契约与 ToolRegistry。
- 静态检查: `git diff --check`；扫描新增 Tool 不包含 `post/put/patch/delete`。
