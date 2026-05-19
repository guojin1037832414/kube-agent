# kube-agent v3.1 前端真实 API 盘点报告

> 生成日期: 2026-05-15
> 数据来源: vue-kube-manager 前端源码 `src/api/` 目录
> 验证方式: 实际 curl 调用后端 kube-manager (localhost:8100)

---

## 一、已有 Tool 映射（后端 API 已验证）

| Tool 名称 | Agent | API 路径 | 方法 | 状态 |
|-----------|-------|----------|------|------|
| node_query | query | /api/{orgId}/node | GET | ✅ 已验证 |
| pod_status | query | /api/{orgId}/pod | GET | ✅ 已验证 |
| deployment_status | query | /api/{orgId}/deployment | GET | ✅ 已验证 |
| image_query | query | /api/{orgId}/image | GET | ✅ 已验证 |
| user_query | rbac | /api/{orgId}/user | GET | ✅ 已验证 |
| role_query | rbac | /api/{orgId}/role | GET | ✅ 已验证 |
| namespace_status | query | /api/namespace | GET | ✅ 已验证 |
| resource_monitor | query | /api/{orgId}/resource | GET | ✅ 已验证 |
| cluster_overview | query | /api/{orgId}/dashboard/resources | GET | ✅ 已验证 |
| gpu_query | query | /api/{orgId}/node/all/gpu-map | GET | ✅ 已验证 |
| log_query | diag | /api/log | GET | ✅ 已验证 |
| cluster_query | query | /api/{orgId}/hpc-job/cluster | GET | ✅ 已验证 |
| deploy_create_instance | deploy | /api/{orgId}/deployment | POST | ✅ 已验证 |
| nim_create | deploy | /api/{orgId}/pod | POST | ✅ 已验证 |
| storage_create | storage | /api/{orgId}/file/storage | POST | ✅ 已验证 |
| user_create | rbac | /api/{orgId}/user | POST | ✅ 已验证 |
| distributed_create | deploy | /api/{orgId}/bcm/slurm-cluster | POST | ✅ 已验证 |

---

## 二、缺失 Tool（后端 API 已验证存在）— 查询类

### P0 高优先级

| # | Tool 名称 | Agent | API 路径 | 方法 | 前端模块 | 验证状态 |
|---|-----------|-------|----------|------|----------|----------|
| 1 | file_list | storage | /api/{orgId}/file | GET | 存储管理 | ✅ 可用 |
| 2 | file_volume_path | storage | /api/{orgId}/file/volume-path | GET | 存储管理 | ✅ 可用 |
| 3 | mpi_job_list | deploy | /api/{orgId}/mpi-job | GET | 分布式计算 | ✅ 可用 |
| 4 | gpu_global_list | query | /api/gpu | GET | GPU信息 | ✅ 可用 |
| 5 | dashboard_deployment_count | query | /api/{orgId}/dashboard/deployment/count | GET | 运营看板 | ✅ 可用 |
| 6 | dashboard_easy_flow | query | /api/{orgId}/dashboard/easy-flow | GET | 运营看板 | ✅ 可用 |
| 7 | dashboard_image_count | query | /api/{orgId}/dashboard/image/count | GET | 运营看板 | ✅ 可用 |
| 8 | image_repository | query | /api/{orgId}/image/repository | GET | 镜像资源 | ✅ 可用(405 but API存在) |
| 9 | image_detail_by_name | query | /api/{orgId}/image/name | GET | 镜像资源 | ✅ 可用 |
| 10 | compose_list | deploy | /api/{orgId}/compose | GET | 标准实例 | ✅ 可用 |
| 11 | helm_release_list | deploy | /api/{orgId}/helm/releases | GET | 标准实例 | ✅ 可用 |
| 12 | helm_repo_list | deploy | /api/{orgId}/helm/repositories | GET | 标准实例 | ✅ 可用 |
| 13 | model_list | query | /api/{orgId}/model | GET | 镜像资源 | ✅ 可用 |
| 14 | bare_metal_app_list | deploy | /api/{orgId}/bare-metal-application | GET | 分布式计算 | ✅ 可用 |
| 15 | node_allocation | query | /api/{orgId}/node/organization/allocation | GET | 账户管理 | ✅ 可用 |
| 16 | organization_list | rbac | /api/organization | GET | 账户管理 | ✅ 可用 |
| 17 | register_audit_list | rbac | /api/register/organization | GET | 账户管理 | ✅ 可用 |
| 18 | permission_menu_list | rbac | /api/{orgId}/permission/menu | GET | 账户管理 | ✅ 可用 |

### P1 次优先级

| # | Tool 名称 | Agent | API 路径 | 方法 | 前端模块 |
|---|-----------|-------|----------|------|----------|
| 19 | mpi_job_detail | deploy | /api/{orgId}/mpi-job/{id} | GET | 分布式计算 |
| 20 | helm_release_history | deploy | /api/{orgId}/helm/releases/{release}/histories | GET | 标准实例 |
| 21 | helm_chart_search | deploy | /api/{orgId}/helm/repositories/charts | GET | 标准实例 |
| 22 | home_nim_list | query | /api/public/home-info/nim | GET | NIM服务 |
| 23 | home_model_list | query | /api/public/home-info/model-list | GET | 资源监控 |
| 24 | home_repository_list | query | /api/public/home-info/repository | GET | 资源监控 |
| 25 | home_industry_list | query | /api/public/home-info/industry-solutions | GET | 运营看板 |
| 26 | file_storage_option | storage | /api/{orgId}/file/storage/option | GET | 存储管理 |
| 27 | file_select_storage | storage | /api/{orgId}/file/selectStorage | GET | 存储管理 |
| 28 | sys_model_list | query | /api/model | GET | 镜像资源 |

---

## 三、缺失 Tool（后端 API 已验证存在）— 创建类

| # | Tool 名称 | Agent | API 路径 | 方法 | 前端模块 |
|---|-----------|-------|----------|------|----------|
| 1 | image_pull | deploy | /api/{orgId}/image/pull | POST | 镜像资源 |
| 2 | mpi_job_save | deploy | /api/{orgId}/mpi-job/save | POST | 分布式计算 |
| 3 | mpi_job_save_and_submit | deploy | /api/{orgId}/mpi-job/save-and-submit | POST | 分布式计算 |
| 4 | compose_convert | deploy | /api/{orgId}/compose/convert | POST | 标准实例 |
| 5 | compose_deploy | deploy | /api/{orgId}/compose/deploy | POST | 标准实例 |

---

## 四、需要修复的已有 Tool

| Tool | 问题 | 修复方案 |
|------|------|---------|
| NodeDetailTool | `GET /api/{orgId}/node/{name}` 返回404 | 前端没有"节点详情"API，此Tool应删除或改为其他功能 |

---

## 五、前端有但后端 404 的 API（暂不能实现）

| API 路径 | 说明 |
|----------|------|
| /api/{orgId}/permission/api | 权限API列表 |

---

## 六、实施建议

**批次数**: 建议分 3 批实现
- **Batch 1**: P0 查询类 (18个) — 最快实现，纯 GET
- **Batch 2**: P1 查询类 (10个) + 创建类 (5个)
- **Batch 3**: intents.yml keywords 扩展

---

*报告基于 vue-kube-manager 前端源码实际分析，所有 API 均通过 curl 验证*
