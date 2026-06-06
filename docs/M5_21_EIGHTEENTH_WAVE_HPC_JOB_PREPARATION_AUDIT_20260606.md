# M5.21-18 HPC 作业准备数据 Tool 审计

## 背景

本批继续补齐 mature kube-manager 的 HPC/Slurm 作业控制面。目标不是直接提交作业，而是让 AI
助手在提交前能读取可用集群分区和 sbatch 参数模板，从而完成作业资源分析、参数解释和提交前
校验准备。

## 专家会诊结论

- 后端专家：`HpcJobController` 暴露了集群、分区、sbatch 参数、作业提交、作业删除、作业详情、
  重提作业等接口。本批只接入 `GET /partition/{clusterId}` 和 `GET /sbatch_parameter/{category}`。
- 前端专家：成熟前端已有 Slurm/BCM 入口，作业提交前需要先选择集群、分区、节点和参数；这些只读
  准备数据适合先接入 agent。
- 安全专家：参考 OWASP Agentic AI 的最小权限/工具分层原则，以及 OpenAI/Microsoft 对高影响
  工具调用的 HITL 建议，HPC 作业提交、删除、重提会触发调度系统变更和远程命令，必须保留到
  高风险 HITL 批次。
- 测试专家：本批只用 mock HTTP contract，不访问真实 8100，不执行 Slurm 远程命令。

## 本批交付

- 新增 `HpcPartitionListTool`：查询指定 HPC/Slurm 集群的分区列表。
- 新增 `HpcSbatchParameterListTool`：查询指定 sbatch 参数分类。
- 新增 `HpcJobQuerySupport`：统一 clusterId 与 category 路径片段校验，category 会做 path segment 编码。
- 新增 `HpcJobPreparationToolHttpContractTest`：锁定路径、可信 org、category 编码与非法路径 fail-closed。
- 扩展 `M511AtlasToolHttpContractTest`：纳入 HPC 准备数据 READ endpoint 精确白名单。
- 扩展 `intents.yml`：新增 `hpc_partition_list`、`hpc_sbatch_parameter_list` 意图。

## 安全边界

- 仅接入只读准备数据，不接入作业提交、删除、重提。
- `clusterId` 只允许正整数，不能包含路径或脚本文本。
- `category` 只允许字母、数字、空格、下划线、点和短横线，并进行 URL path segment 编码。
- `{orgId}` 只来自可信 `UserPermissionContext`，忽略伪造的 `organizationId/orgId`。
- 测试不访问真实 8100，不触发调度系统。

## HOLD 范围

- 暂缓 `GET /api/{orgId}/hpc-job/jobs` 和 `GET /api/{orgId}/hpc-job/jobs/{jobId}`：成熟服务层会尝试实时刷新作业状态，
  读接口可能触发远程命令和状态更新，需单独评估是否标记为敏感 READ + HITL。
- 暂缓 `POST /submit-job-file`、`POST /submit-job-script`、`POST /submit-job-command`、`DELETE /jobs/{jobId}`、
  `POST /resubmit-job`：这些属于真实调度变更，后续必须作为高风险 Tool 接入。
