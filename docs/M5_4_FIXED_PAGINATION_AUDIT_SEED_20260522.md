# M5.4 固定分页候选复扫种子清单 — 20260522

> 自动复扫候选数：35

## special-field/detail/option（10）

| File | Tool | Permission | OrgScoped | HasSpecs | API Path | Description |
|---|---|---|---|---|---|---|
| `BareMetalTemplateTool.java` | `bare_metal_template` | PUBLIC | False | False | `/api/bare-metal-config-template` | 查询裸金属配置模板列表 |
| `DiagnosePodTool.java` | `diagnose_pod` | PUBLIC | True | True | `/api/` | 诊断Pod/服务故障 |
| `FileStorageOptionTool.java` | `file_storage_option` | PUBLIC | True | False | `/api/{orgId}/file/storage/option` | 查询存储选项配置 |
| `FileVolumePathTool.java` | `file_volume_path` | PUBLIC | True | False | `/api/{orgId}/file/volume-path` | 查询存储卷路径列表 |
| `GpuMapDetailTool.java` | `gpu_map_detail` | PUBLIC | False | False | `/api/gpu/all/gpu-map` | 查询GPU映射配置详情 |
| `GpuMetricsTool.java` | `gpu_metrics` | PUBLIC | True | False | `/api/` | 查询GPU配置映射 |
| `HelmReleaseHistoryTool.java` | `helm_release_history` | PUBLIC | True | False | `/api/{orgId}/helm/releases` | 查询Helm Release历史记录 |
| `MpiJobDetailTool.java` | `mpi_job_detail` | PUBLIC | True | False | `/api/{orgId}/mpi-job` | 查询MPI分布式计算任务详情 |
| `NodeMetricsTool.java` | `node_metrics` | PUBLIC | True | False | `/api/` | 查询节点列表及资源使用率 |
| `UserDetailTool.java` | `user_detail` | PUBLIC | True | False | `/api/{orgId}/user` | 查询用户详情 |

## tenant-list-like（11）

| File | Tool | Permission | OrgScoped | HasSpecs | API Path | Description |
|---|---|---|---|---|---|---|
| `ClusterQueryTool.java` | `cluster_query` | PUBLIC | True | False | `/api/` | 查询集群列表 |
| `DaemonSetQueryTool.java` | `daemonset_status` | PUBLIC | True | False | `/api/` | 查询DaemonSet状态 |
| `DevOpsQueryTool.java` | `devops_pipeline` | PUBLIC | True | False | `/api/` | 查询DevOps工作负载 |
| `ImageQueryTool.java` | `image_query` | PUBLIC | True | False | `/api/` | 查询镜像资源列表 |
| `ImageRepositoryTool.java` | `image_repository` | PUBLIC | True | False | `/api/{orgId}/image/repository` | 查询镜像仓库列表 |
| `NamespaceQueryTool.java` | `namespace_status` | PUBLIC | False | False | `/api/namespace` | 查询Namespace列表 |
| `NodeAllocationTool.java` | `node_allocation` | PUBLIC | True | False | `/api/{orgId}/node/organization/allocation` | 查询节点分配情况 |
| `NodeQueryTool.java` | `node_query` | PUBLIC | True | False | `/api/` | 查询 Kubernetes 集群所有节点的状态、资源使用情况 |
| `ServiceQueryTool.java` | `service_status` | PUBLIC | True | False | `/api/` | 查询资源看板 |
| `UserManagementTool.java` | `user_management` | PUBLIC | True | False | `/api/` | 查询用户管理列表 |
| `UserQueryTool.java` | `user_query` | PUBLIC | True | False | `/api/` | 查询用户列表 |

## dashboard/count（3）

| File | Tool | Permission | OrgScoped | HasSpecs | API Path | Description |
|---|---|---|---|---|---|---|
| `DashboardDeploymentCountTool.java` | `dashboard_deployment_count` | PUBLIC | True | False | `/api/{orgId}/dashboard/deployment/count` | 查询Dashboard部署统计信息 |
| `DashboardEasyFlowTool.java` | `dashboard_easy_flow` | PUBLIC | True | False | `/api/{orgId}/dashboard/easy-flow` | 查询Dashboard流程列表 |
| `DashboardImageCountTool.java` | `dashboard_image_count` | PUBLIC | True | False | `/api/{orgId}/dashboard/image/count` | 查询Dashboard镜像统计信息 |

## existing-hold-sensitive（10）

| File | Tool | Permission | OrgScoped | HasSpecs | API Path | Description |
|---|---|---|---|---|---|---|
| `GpuGlobalListTool.java` | `gpu_global_list` | PUBLIC | False | False | `/api/gpu` | 查询全局GPU信息列表 |
| `LdapConfigListTool.java` | `ldap_config_list` | PUBLIC | True | False | `/api/{orgId}/ldap` | 查询LDAP配置列表 |
| `OrderListTool.java` | `order_list` | PUBLIC | True | False | `/api/` | 查询订单列表 |
| `OrganizationListTool.java` | `organization_list` | PUBLIC | False | False | `/api/organization` | 查询组织列表 |
| `PermissionMenuListTool.java` | `permission_menu_list` | PUBLIC | True | False | `/api/{orgId}/permission/menu` | 查询权限菜单列表 |
| `QuotaReceiveListTool.java` | `quota_receive_list` | PUBLIC | True | False | `/api/` | 查询配额审批列表 |
| `RegisterAuditListTool.java` | `register_audit_list` | PUBLIC | False | False | `/api/register/organization` | 查询组织注册审核列表 |
| `RoleAssignableListTool.java` | `role_assignable` | PUBLIC | True | False | `/api/{orgId}/role/assignable` | 查询可分配角色列表 |
| `RoleEditableListTool.java` | `role_editable` | PUBLIC | True | False | `/api/{orgId}/role/editable` | 查询可编辑角色列表 |
| `SysModelListTool.java` | `sys_model_list` | PUBLIC | False | False | `/api/model` | 查询全局模型列表 |

## public/no-org（1）

| File | Tool | Permission | OrgScoped | HasSpecs | API Path | Description |
|---|---|---|---|---|---|---|
| `SysInfoMapTool.java` | `sys_info_map` | PUBLIC | False | False | `/api/public/sys-info/all/map` | 查询系统信息配置 |
