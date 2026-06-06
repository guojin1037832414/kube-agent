# M5.21 第六批 Slurm/NIM 创建 Tool 审计

> 日期：2026-06-05
> 范围：Slurm 分布式集群创建、NIM 服务创建
> 约束：只做源码对照和 mock 契约测试，不调用线上 kube-manager 创建接口。

## 专家会诊结论

本轮继续采用高风险 Tool 的最小权限和真实动作可审计原则。凡是创建类 Tool，都不能把 LLM 参数整包透传给 kube-manager；必须对齐成熟后端 DTO，并过滤服务端上下文和审批字段。

## 接口矩阵

| Tool | 成熟项目依据 | 本轮处理 | 安全策略 |
| --- | --- | --- | --- |
| `distributed_create` | `BCMController#createSlurmCluster(@RequestBody SlurmNodeParamDTO)` | `POST /api/{orgId}/bcm/slurm-cluster`，body 改为 Slurm DTO 白名单 | CREATE + AUTHENTICATED + HITL |
| `nim_create` | 成熟前端 `views/nim/index.vue` 先查 repository/tag 和 NIM template，最终调用 deployment 创建 | 不再调用历史 `/api/{orgId}/pod`，改为 fail-closed placeholder | PLACEHOLDER + AUTHENTICATED + HITL |

## 关键安全收口

- `distributed_create` 不再透传 `organizationId/orgId/token/sessionId/conversationId/approved`。
- Slurm 后端 DTO 字段名是 `userId`，但 Tool schema 使用 `assignedUserIds`，避免把受保护上下文字段名暴露给 LLM。执行层在构造后端 body 时再映射为 `userId`。
- `nim_create` 不再把 NIM 服务错误地当成裸 Pod 创建。成熟前端证明 NIM 需要 repository/tag、模板合并、GPU map 和 deployment 默认值编排，后续应以编排链路接入。

## 验证

- `mvn -q "-Dtest=HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M4Px4ToolParameterAliasContractTest" test` 通过。
- 本轮没有访问真实 8100 Slurm/NIM 创建接口。

## 后续验收项

1. NIM 正式开放前，需要新增专用编排：查询 NIM repository/tag、选择 NIM template、合并模板参数、复用 `deploy_create_instance` 的 GPU 与资源安全治理。
2. Slurm 创建后续可补前端候选卡片，让用户在 HITL 中确认 `assignedUserIds/loginNode/workNode/queues`，避免自然语言误选节点或人员。
3. 继续审计 `ComposeDeployCreateTool`、`HelmRepoAddTool`、`HelmReleaseDeleteTool`，确认路径、body 和权限策略是否仍与成熟后端一致。
