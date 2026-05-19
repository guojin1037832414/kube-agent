# kube-agent Tool 缺口矩阵表 v3.1

> 分析日期: 2026-05-15  
> 分析范围: vue-kube-manager 前端 9 大模块 × 60+ 功能点  vs  kube-agent 当前 33 个 Tool  
> 分析原则: **仅关注查询类(Q)和创建类(C)按钮；删除/更新/扩缩容/重启类(◇)明确标记为暂缓**

---

## 一、当前 33 个 Tool 速查（已注册）

| # | Tool 名称 | Agent | 操作类型 | REST API |
|---|-----------|-------|----------|----------|
| 1 | node_query | query | Q | GET /api/{orgId}/node |
| 2 | node_detail | query | Q | GET /api/{orgId}/node/{name} |
| 3 | node_metrics | query | Q | GET /api/{orgId}/node (同1) |
| 4 | gpu_query | query | Q | GET /api/{orgId}/node/all/gpu-map |
| 5 | gpu_metrics | query | Q | GET /api/{orgId}/node/all/gpu-map (同4) |
| 6 | image_query | query | Q | GET /api/{orgId}/image |
| 7 | cluster_overview | query | Q | GET /api/{orgId}/dashboard/resources |
| 8 | cluster_query | query | Q | GET /api/{orgId}/hpc-job/cluster |
| 9 | resource_monitor | query | Q | GET /api/{orgId}/resource |
| 10 | deployment_status | query | Q | GET /api/{orgId}/deployment |
| 11 | pod_status | query | Q | GET /api/{orgId}/pod |
| 12 | namespace_status | query | Q | GET /api/namespace |
| 13 | service_status | query | Q | GET /api/{orgId}/dashboard/resources (近似) |
| 14 | daemonset_status | query | Q | GET /api/{orgId}/dashboard/deployment (近似) |
| 15 | devops_pipeline | query | Q | GET /api/{orgId}/dashboard/deployment (近似) |
| 16 | network_query | network | Q | GET /api/{orgId}/dashboard/deployment (近似) |
| 17 | ingress_query | network | Q | GET /api/{orgId}/dashboard/deployment (近似) |
| 18 | diagnose_pod | diag | Q | GET /api/{orgId}/pod |
| 19 | log_query | diag | Q | GET /api/log |
| 20 | role_query | rbac | Q | GET /api/{orgId}/role |
| 21 | user_query | rbac | Q | GET /api/{orgId}/user |
| 22 | user_management | rbac | Q | GET /api/{orgId}/user (同21) |
| 23 | storage_status | storage | Q | GET /api/storage/pageList / /api/pvc/pageList |
| 24 | deploy_create_instance | deploy | C | POST /api/{orgId}/deployment |
| 25 | nim_create | deploy | C | POST /api/{orgId}/pod |
| 26 | distributed_create | deploy | C | POST /api/{orgId}/bcm/slurm-cluster |
| 27 | storage_create | storage | C | POST /api/{orgId}/file/storage |
| 28 | user_create | rbac | C | POST /api/{orgId}/user |
| 29 | deploy_scale | deploy | ◇U | TODO PATCH (未接入) |
| 30 | deploy_delete | deploy | ◇D | POST /api/{orgId}/deployment/{name}/delete |
| 31 | deploy_restart | deploy | ◇U | POST /api/{orgId}/deployment/{name}/restart |
| 32 | storage_delete | storage | ◇D | POST /api/{orgId}/file/storage/{name}/delete |
| 33 | user_delete | rbac | ◇D | DELETE /api/{orgId}/user/{id} |

> **Agent 覆盖现状**: query(16), diag(2), deploy(6), rbac(4), storage(4), network(2) = 33 Tool
> **操作类型分布**: 查询类(23), 创建类(5), 删除/更新/重启(5)

---

## 二、缺口矩阵：9 大前端模块 × Tool 覆盖缺口

### 2.1 资源监控模块

| 前端按钮/功能 | 预期 REST API | 类型 | 所属 Agent | 现有 Tool | 缺口 |
|--------------|--------------|------|-----------|----------|------|
| 查看节点资源监控 | GET /api/{orgId}/node | Q | query | node_query ✅ | ✅ 已覆盖 |
| 查看节点指标 | GET /api/{orgId}/node | Q | query | node_metrics ✅ | ✅ 已覆盖 |
| 查看 Pod 监控 | GET /api/{orgId}/pod | Q | query | pod_status ✅ | ✅ 已覆盖 |
| 查看 Deployment监控 | GET /api/{orgId}/deployment | Q | query | deployment_status ✅ | ✅ 已覆盖 |
| 查看全量资源监控看板 | GET /api/{orgId}/resource | Q | query | resource_monitor ✅ | ✅ 已覆盖 |
| ⭐ **查看 Events 事件列表** | GET /api/{orgId}/events?type=warning | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看告警规则列表** | GET /api/{orgId}/alert/rules | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查询实时资源使用率趋势图** | GET /api/{orgId}/metrics/trend?metric=cpu&range=1h | Q | query | ❌ 缺失 | 🔴 **缺** |

**结论**: 5/8 覆盖，**缺 3 个查询 Tool** (events_query, alert_rule_query, metrics_trend_query)

---

### 2.2 运营看板模块

| 前端按钮/功能 | 预期 REST API | 类型 | 所属 Agent | 现有 Tool | 缺口 |
|--------------|--------------|------|-----------|----------|------|
| 查看集群资源概览 | GET /api/{orgId}/dashboard/resources | Q | query | cluster_overview ✅ | ✅ 已覆盖 |
| 查看应用/服务列表 | GET /api/{orgId}/dashboard/deployment | Q | query | service_status ✅ | ✅ 已覆盖(近似) |
| ⭐ **查看任务运行统计** | GET /api/{orgId}/dashboard/tasks/stats | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **生成运营报表（周/月）** | GET /api/{orgId}/report/weekly | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看资源配额使用情况** | GET /api/{orgId}/quota/usage | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看集群 TopN 资源消耗者** | GET /api/{orgId}/dashboard/top?n=10 | Q | query | ❌ 缺失 | 🔴 **缺** |

**结论**: 2/6 覆盖，**缺 4 个查询 Tool**

---

### 2.3 标准实例模块（Deployment管理）

| 前端按钮/功能 | 预期 REST API | 类型 | 所属 Agent | 现有 Tool | 缺口 |
|--------------|--------------|------|-----------|----------|------|
| 查看实例列表 | GET /api/{orgId}/deployment | Q | query | deployment_status ✅ | ✅ 已覆盖 |
| 查看实例详情 | GET /api/{orgId}/deployment/{name} | Q | query | ❌ 缺失(详情) | 🔴 **缺** |
| ⭐ **创建标准实例** | POST /api/{orgId}/deployment | C | deploy | deploy_create_instance ✅ | ✅ 已覆盖 |
| 扩缩容 | PUT /api/{orgId}/deployment/{name}/scale | ◇U | deploy | deploy_scale ✅(暂缓) | ◇ |
| 删除实例 | POST /api/{orgId}/deployment/{name}/delete | ◇D | deploy | deploy_delete ✅(暂缓) | ◇ |
| 重启实例 | POST /api/{orgId}/deployment/{name}/restart | ◇U | deploy | deploy_restart ✅(暂缓) | ◇ |
| ⭐ **查看实例日志** | GET /api/{orgId}/log?podName=xxx | Q | diag | log_query ✅ | ✅ 已覆盖 |
| ⭐ **回滚到指定版本** | ❌ 无明确API | ◇U | deploy | ❌ 缺失 | ◇ |
| ⭐ **查看实例事件/Event** | GET /api/{orgId}/events?resource=xxx | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看实例 Yaml** | GET /api/{orgId}/deployment/{name}/yaml | Q | query | ❌ 缺失 | 🔴 **缺** |

**结论**: 4/10 覆盖(查询+创建)，**缺 4 个 Tool**（2查询+2更新，2更新暂缓）

---

### 2.4 NIM 服务模块

| 前端按钮/功能 | 预期 REST API | 类型 | 所属 Agent | 现有 Tool | 缺口 |
|--------------|--------------|------|-----------|----------|------|
| ⭐ **查看 NIM 服务列表** | GET /api/{orgId}/nim/list | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **创建 NIM 服务** | POST /api/{orgId}/pod (复用) | C | deploy | nim_create ✅ | ✅ 已覆盖 |
| 删除 NIM 服务 | DELETE /api/{orgId}/pod/{name} | ◇D | deploy | ❌ 缺失 | ◇ |
| 更新 NIM 服务配置 | PATCH /api/{orgId}/pod/{name} | ◇U | deploy | ❌ 缺失 | ◇ |
| ⭐ **查看 NIM 模型详情** | GET /api/{orgId}/nim/{name} | Q | query | ❌ 缺失 | 🔴 **缺** |

**结论**: 1/5 覆盖（仅有创建），**缺 3 个 Tool**（2查询+1更新/删除，更新删除暂缓）

---

### 2.5 镜像资源模块

| 前端按钮/功能 | 预期 REST API | 类型 | 所属 Agent | 现有 Tool | 缺口 |
|--------------|--------------|------|-----------|----------|------|
| ⭐ **查看镜像列表** | GET /api/{orgId}/image | Q | query | image_query ✅ | ✅ 已覆盖 |
| ⭐ **搜索镜像** | GET /api/{orgId}/image?keyword=xxx | Q | query | image_query ✅ | ✅ 已覆盖(参数兼容) |
| ⭐ **查看镜像详情** | GET /api/{orgId}/image/{id} | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查询镜像 Tags/版本** | GET /api/{orgId}/image/{id}/tags | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **拉取/导入镜像** | POST /api/{orgId}/image/pull | C | deploy | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看镜像仓库列表** | GET /api/{orgId}/registry/list | Q | query | ❌ 缺失 | 🔴 **缺** |

**结论**: 2/6 覆盖，**缺 4 个 Tool**

---

### 2.6 分布式计算模块

| 前端按钮/功能 | 预期 REST API | 类型 | 所属 Agent | 现有 Tool | 缺口 |
|--------------|--------------|------|-----------|----------|------|
| ⭐ **查看算力集群列表** | GET /api/{orgId}/hpc-job/cluster | Q | query | cluster_query ✅ | ✅ 已覆盖 |
| ⭐ **创建算力集群** | POST /api/{orgId}/hpc-job/cluster (即SLURM) | C | deploy | distributed_create ✅ | ✅ 已覆盖 |
| ⭐ **查看分布式任务列表** | GET /api/{orgId}/hpc-job/jobs | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **提交计算任务（到已有集群）** | POST /api/{orgId}/hpc-job/submit | C | deploy | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看任务运行状态/输出** | GET /api/{orgId}/hpc-job/job/{id} | Q | query | ❌ 缺失 | 🔴 **缺** |
| 删除算力集群 | DELETE /api/{orgId}/hpc-job/cluster/{id} | ◇D | deploy | ❌ 缺失 | ◇ |
| ⭐ **查看节点资源池/GPU池** | GET /api/{orgId}/hpc-job/resources | Q | query | ❌ 缺失 | 🔴 **缺** |

**结论**: 2/7 覆盖，**缺 5 个 Tool**（4查询+1创建+1删除，删除暂缓）

---

### 2.7 存储管理模块

| 前端按钮/功能 | 预期 REST API | 类型 | 所属 Agent | 现有 Tool | 缺口 |
|--------------|--------------|------|-----------|----------|------|
| ⭐ **查看存储卷列表** | GET /api/storage/pageList / /api/{orgId}/file/storage | Q | storage | storage_status ✅ | ✅ 已覆盖 |
| ⭐ **创建存储卷(PVC)** | POST /api/{orgId}/file/storage | C | storage | storage_create ✅ | ✅ 已覆盖 |
| ⭐ **查看存储类/StorageClass** | GET /api/{orgId}/storage/classes | Q | storage | ❌ 缺失 | 🔴 **缺** |
| 删除存储卷 | POST /api/{orgId}/file/storage/{name}/delete | ◇D | storage | storage_delete ✅(暂缓) | ◇ |
| ⭐ **查看存储快照列表** | GET /api/{orgId}/snapshot/list | Q | storage | ❌ 缺失 | 🔴 **缺** |
| ⭐ **创建存储快照** | POST /api/{orgId}/snapshot/create | C | storage | ❌ 缺失 | 🔴 **缺** |
| ⭐ **文件管理器(浏览目录)** | GET /api/{orgId}/file/list?path=xxx | Q | storage | ❌ 缺失 | 🔴 **缺** |
| ⭐ **上传文件** | POST /api/{orgId}/file/upload | C | storage | ❌ 缺失 | 🔴 **缺** |
| ⭐ **下载/预测览文件** | GET /api/{orgId}/file/download?path=xxx | Q | storage | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看 PV 列表** | GET /api/pv/pageList | Q | storage | ❌ 缺失 | 🔴 **缺** |

**结论**: 2/10 覆盖，**缺 8 个 Tool**（7查询+1创建，实际缺7个因为1个已覆盖）

---

### 2.8 GPU 信息模块

| 前端按钮/功能 | 预期 REST API | 类型 | 所属 Agent | 现有 Tool | 缺口 |
|--------------|--------------|------|-----------|----------|------|
| ⭐ **查看 GPU 节点映射** | GET /api/{orgId}/node/all/gpu-map | Q | query | gpu_query ✅ / gpu_metrics ✅ | ✅ 已覆盖(重复) |
| ⭐ **查看 GPU 使用详情** | GET /api/{orgId}/gpu/info | Q | query | ❌ 缺失(用gpu_query近似) | 🟡 弱覆盖 |
| ⭐ **查看 GPU 任务分配** | GET /api/{orgId}/gpu/allocations | Q | query | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看 GPU 显存趋势** | GET /api/{orgId}/gpu/metrics/trend | Q | query | ❌ 缺失 | 🔴 **缺** |

**结论**: 1(近似)/4 覆盖，**缺 3 个 Tool**

---

### 2.9 账户管理模块（RBAC）

| 前端按钮/功能 | 预期 REST API | 类型 | 所属 Agent | 现有 Tool | 缺口 |
|--------------|--------------|------|-----------|----------|------|
| ⭐ **查看用户列表** | GET /api/{orgId}/user | Q | rbac | user_query ✅ | ✅ 已覆盖 |
| ⭐ **创建用户** | POST /api/{orgId}/user | C | rbac | user_create ✅ | ✅ 已覆盖 |
| ⭐ **查看角色列表** | GET /api/{orgId}/role | Q | rbac | role_query ✅ | ✅ 已覆盖 |
| ⭐ **查看用户管理列表(管理视角)** | GET /api/{orgId}/user | Q | rbac | user_management ✅ | ✅ 已覆盖(同user_query重复) |
| 删除用户 | DELETE /api/{orgId}/user/{id} | ◇D | rbac | user_delete ✅(暂缓) | ◇ |
| ⭐ **查看用户详情/权限详情** | GET /api/{orgId}/user/{id} | Q | rbac | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看组织架构/部门列表** | GET /api/{orgId}/org/departments | Q | rbac | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看操作审计日志** | GET /api/{orgId}/audit/logs | Q | rbac | ❌ 缺失 | 🔴 **缺** |
| ⭐ **查看 API Token 列表** | GET /api/{orgId}/tokens | Q | rbac | ❌ 缺失 | 🔴 **缺** |
| ⭐ **创建 API Token** | POST /api/{orgId}/tokens | C | rbac | ❌ 缺失 | 🔴 **缺** |

**结论**: 4/10 覆盖，**缺 6 个 Tool**（4查询+2创建，实际缺6）

---

## 三、缺口汇总

### 3.1 按模块统计

| 前端模块 | 已有 Q/C Tool | 缺失 Q/C Tool | 暂缓 D/U Tool | 模块覆盖度 |
|---------|--------------|---------------|---------------|-----------|
| 资源监控 | 5 | 3 (Q) | 0 | 5/8 = 62.5% |
| 运营看板 | 2 | 4 (Q) | 0 | 2/6 = 33.3% |
| 标准实例 | 3 | 2 (Q) | 3 (U/D/R) | 3/8 = 37.5% |
| NIM服务 | 1 | 2 (Q) | 1 (D/U) | 1/5 = 20% |
| 镜像资源 | 2 | 4 (Q+C) | 0 | 2/6 = 33.3% |
| 分布式计算 | 2 | 4 (Q+C) | 1 (D) | 2/7 = 28.6% |
| 存储管理 | 2 | 7 (Q+C) | 1 (D) | 2/10 = 20% |
| GPU信息 | 1 | 3 (Q) | 0 | 1/4 = 25% |
| 账户管理 | 4 | 5 (Q+C) | 1 (D) | 4/10 = 40% |
| **合计** | **22** | **34** | **7** | **22+/60+ = ~37%** |

### 3.2 优先级分级建议

#### 🔴 P0 — 核心缺失 (优先补齐)

| # | Tool 名 | Agent | 类型 | 对应前端模块 | 对应 REST API |
|---|---------|-------|------|-------------|--------------|
| 1 | deployment_detail | query | Q | 标准实例 | GET /api/{orgId}/deployment/{name} |
| 2 | events_query | query | Q | 资源监控/标准实例 | GET /api/{orgId}/events |
| 3 | image_detail | query | Q | 镜像资源 | GET /api/{orgId}/image/{id} |
| 4 | image_tags_query | query | Q | 镜像资源 | GET /api/{orgId}/image/{id}/tags |
| 5 | hpc_job_list | query | Q | 分布式计算 | GET /api/{orgId}/hpc-job/jobs |
| 6 | hpc_job_detail | query | Q | 分布式计算 | GET /api/{orgId}/hpc-job/job/{id} |
| 7 | storage_class_query | storage | Q | 存储管理 | GET /api/{orgId}/storage/classes |
| 8 | pvc_snapshot_query | storage | Q | 存储管理 | GET /api/{orgId}/snapshot/list |
| 9 | pvc_snapshot_create | storage | C | 存储管理 | POST /api/{orgId}/snapshot/create |
| 10 | user_detail | rbac | Q | 账户管理 | GET /api/{orgId}/user/{id} |
| 11 | department_query | rbac | Q | 账户管理 | GET /api/{orgId}/org/departments |

#### 🟠 P1 — 重要缺失 (次优补齐)

| # | Tool 名 | Agent | 类型 | 对应前端模块 | 对应 REST API |
|---|---------|-------|------|-------------|--------------|
| 12 | metrics_trend_query | query | Q | 资源监控 | GET /api/{orgId}/metrics/trend |
| 13 | alert_rule_query | query | Q | 资源监控 | GET /api/{orgId}/alert/rules |
| 14 | task_stats_query | query | Q | 运营看板 | GET /api/{orgId}/dashboard/tasks/stats |
| 15 | report_query | query | Q | 运营看板 | GET /api/{orgId}/report/weekly |
| 16 | quota_usage_query | query | Q | 运营看板 | GET /api/{orgId}/quota/usage |
| 17 | top_resource_query | query | Q | 运营看板 | GET /api/{orgId}/dashboard/top |
| 18 | nim_list | query | Q | NIM服务 | GET /api/{orgId}/nim/list |
| 19 | nim_detail | query | Q | NIM服务 | GET /api/{orgId}/nim/{name} |
| 20 | pv_list | storage | Q | 存储管理 | GET /api/pv/pageList |
| 21 | file_browse | storage | Q | 存储管理 | GET /api/{orgId}/file/list |
| 22 | file_upload | storage | C | 存储管理 | POST /api/{orgId}/file/upload |
| 23 | file_download | storage | Q | 存储管理 | GET /api/{orgId}/file/download |
| 24 | gpu_allocation_query | query | Q | GPU信息 | GET /api/{orgId}/gpu/allocations |
| 25 | gpu_trend_query | query | Q | GPU信息 | GET /api/{orgId}/gpu/metrics/trend |
| 26 | audit_log_query | rbac | Q | 账户管理 | GET /api/{orgId}/audit/logs |
| 27 | api_token_query | rbac | Q | 账户管理 | GET /api/{orgId}/tokens |
| 28 | api_token_create | rbac | C | 账户管理 | POST /api/{orgId}/tokens |
| 29 | image_pull | deploy | C | 镜像资源 | POST /api/{orgId}/image/pull |
| 30 | registry_list | query | Q | 镜像资源 | GET /api/{orgId}/registry/list |
| 31 | hpc_job_submit | deploy | C | 分布式计算 | POST /api/{orgId}/hpc-job/submit |
| 32 | hpc_resource_pool | query | Q | 分布式计算 | GET /api/{orgId}/hpc-job/resources |
| 33 | image_registry_auth | rbac | C | 镜像资源 | POST /api/{orgId}/registry/auth |
| 34 | deployment_yaml_query | query | Q | 标准实例 | GET /api/{orgId}/deployment/{name}/yaml |

> **总计**: P0=11个 + P1=23个 = **34 个缺失 Tool** (Q/C类)
> 当前已有 Q/C Tool = 22 个，目标 = 56 个，还差 34 个

---

## 四、Agent 模块间 Tool 分布建议（优化后）

| Agent | 当前 Tool 数 | 建议补充 Tool 数 | 建议补充内容 |
|-------|-------------|-----------------|-------------|
| query | 16 | +14 | events, metrics_trend, alert, top, task_stats, report, quota, nim_list/detail, gpu_allocation/trend, registry_list, image_detail/tags, hpc_job_list/detail/resource_pool, deployment_detail/yaml |
| deploy | 6 | +3 | image_pull, hpc_job_submit, (保留现有) |
| diag | 2 | 0 | 当前够用(仅日志+诊断) |
| rbac | 4 | +6 | user_detail, department, audit_log, api_token_query/create, image_registry_auth |
| storage | 4 | +9 | storage_class, pv_list, snapshot_query/create, file_browse/upload/download |
| network | 2 | 0 | 当前网络查询够用(基础) |

---

## 五、关键发现与建议

### 5.1 已有 Tool 质量问题
1. ** gpu_query 与 gpu_metrics 完全重复** — 都调用 `/api/{orgId}/node/all/gpu-map`，建议合并
2. **user_query 与 user_management 完全重复** — 都调用 `/api/{orgId}/user`，建议合并
3. **IngressQueryTool / NetworkQueryTool / DaemonSetQueryTool / DevOpsQueryTool / ServiceQueryTool** — 都是近似/兜底实现，调用 `/api/{orgId}/dashboard/deployment` 或 `/api/{orgId}/dashboard/resources`，**未来需要接入真实 API**

### 5.2 高价值优先开发
- **P0 级**（11个Tool）建议 **2-3 周内补齐**，覆盖核心用户高频操作
- **P1 级**（23个Tool）建议 **P0完成后4-6周补齐**

### 5.3 安全注意事项
- 新增 Tool 如果是查询类 → 标注 `@ToolPermission(PUBLIC)`
- 新增 Tool 如果是创建类 → 标注 `@ToolPermission(AUTHENTICATED)`
- 不在本次开发范围的 删除/更新/扩缩容 Tool 保持 `@ToolPermission(ADMIN_ONLY)`

---

> 文档生成时间: 2026-05-15  
> 基于 kube-agent v3.1.0-P1.4 代码结构  
> 数据来源: 33个Tool源码 + intents.yml + API_MAPPING_DESIGN_REPORT.md + PROJECT_ATLAS_V3.md
