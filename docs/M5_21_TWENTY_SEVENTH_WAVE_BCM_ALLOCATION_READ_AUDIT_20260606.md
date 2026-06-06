# M5.21-27 BCM 用户与节点分配敏感只读 Tool 审计

> 结论: 本批仅接入当前组织 BCM 用户、Slurm 节点分配和 BareMetal 节点分配 GET 读取能力；创建集群、切换 SSH/Sudo、站点管理员跨组织接口继续 HOLD。

## 成熟项目证据

- 后端: `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\bcm\controller\BCMController.java`
- 前端:
  - `F:\gitProject\vue-kube-manager\src\api\slurm.js`
  - `F:\gitProject\vue-kube-manager\src\api\bare-metal.js`
- mature org-scoped GET:
  - `GET /api/{organizationId}/bcm/users`
  - `GET /api/{organizationId}/bcm/all-slurm-nodes`
  - `GET /api/{organizationId}/bcm/all-bare-metal-nodes`
- 同域高风险操作:
  - `POST /api/{organizationId}/bcm/slurm-cluster`
  - `POST /api/{organizationId}/bcm/bare-metal`
  - `POST /api/{organizationId}/bcm/ssh`
  - `POST /api/{organizationId}/bcm/sudo`
  - `/api/bcm/*` 站点管理员跨组织接口

## 专家会诊结论

- 后端/API 专家: 这三类数据是创建 Slurm/BareMetal 前的选项数据，可帮助 Agent 做资源盘点和部署建议。
- 安全/RBAC 专家: 用户列表和节点分配会暴露组织用户边界、算力资产和资源分配关系，按 `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true` 处理。
- 测试架构专家: 不开放 page/limit/keyword，也不透传 `organizationId/orgId`、`assignedUserIds`、`loginNode/workNode`、`sudo` 等写操作字段；全部使用 mock HTTP client 验证。
- 外部参考:
  - OpenAI Agents SDK Human-in-the-loop
  - Microsoft Agent Framework Function Tools with Approval
  - OWASP LLM06 Excessive Agency

## 本批交付

- `BcmUserListTool`
- `BcmSlurmNodeAllocationListTool`
- `BcmBareMetalNodeAllocationListTool`
- `bcm_user_list`、`bcm_slurm_node_allocation_list`、`bcm_bare_metal_node_allocation_list` 意图
- `BcmAllocationReadToolHttpContractTest`
- `M511AtlasToolHttpContractTest` 新增 BCM allocation `SENSITIVE_READ` endpoint 精确白名单

## HOLD 清单

- 创建 Slurm 集群或 BareMetal 实例
- 切换 BCM SSH/Sudo 配置
- 站点管理员跨组织 `/api/bcm/*` 查询和创建接口
- Slurm/BareMetal 删除、节点重新分配和批量删除

## 验证计划

- 定向测试: `mvn -q "-Dtest=BcmAllocationReadToolHttpContractTest,HpcEnvironmentModuleToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest" test`
- 关键回归: 覆盖 ReAct、HITL fail-closed、安全执行、M5.21 HTTP 契约与 ToolRegistry。
- 静态检查: `git diff --check`；扫描新增 Tool 不包含 `post/put/patch/delete`。
