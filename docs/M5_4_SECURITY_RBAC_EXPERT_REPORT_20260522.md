# M5.4 安全/RBAC/隐私专家报告 — 固定分页候选风险分层与开放建议

> 项目：`/home/guojin/kube-agent`
> 角色：安全 / RBAC / 隐私专家
> 输入依据：`docs/M5_4_FIXED_PAGINATION_AUDIT_SEED_20260522.md`、M5.1~M5.3 变更记录、当前 Tool 代码抽样复核
> 结论日期：2026-05-22

> **主流程采纳说明（M5.4 最终决策）**：本报告是安全/RBAC 单路专家建议，报告中“dashboard/count 可有限开放”的意见未被直接采纳为本轮实现。由于后端/API 语义、安全/RBAC、测试架构三路专家未对 Dashboard count 参数开放达成一致，M5.4 按 fail-safe 策略执行：**不修改生产代码，仅新增 Dashboard 与 SysInfoMap 的 HOLD 保护测试**，防止后续批量脚本误开放 page/limit/keyword。

## 1. 总结性结论

**M5.4 下一最小安全专项建议选择：`dashboard/count` 组，但只开放受限 `page/limit-only`，且需排除/谨慎处理 `dashboard_easy_flow`。**

候选分组风险从低到高建议排序：

1. **低~中风险：`dashboard/count` 3 个 orgScoped dashboard 接口**
   - `dashboard_deployment_count`、`dashboard_image_count` 可作为最小开放候选。
   - `dashboard_easy_flow` 名称虽在 dashboard 组，但语义是“流程列表”，不是纯 count；建议先按中风险 HOLD 或仅在响应字段确认后纳入同批。
2. **中风险：`public/no-org SysInfoMap` 1 个**
   - 公开系统配置 map，不应机械参数化；建议继续固定查询或 no-param contract，不开放 `page/limit/keyword`。
3. **中~高风险：`special-field/detail/option` 10 个**
   - 混合 detail、option、路径、用户详情、GPU map、节点指标、诊断等；不能按列表批量开放。
4. **高风险：`tenant-list-like` 11 个**
   - 涉及用户、节点、namespace、镜像、集群、服务、DevOps 等组织内资源枚举；必须先做 RBAC/租户隔离/字段脱敏专项。
5. **最高风险：`existing-hold-sensitive` 10 个**
   - 已被 M5.1~M5.3 判定 HOLD 的账务、审批、RBAC、GLOBAL/GPU/系统模型；必须继续测试锁住。

**核心边界：M5.4 不应把 `PUBLIC/no-org/GLOBAL/跨组织/详情/用户/RBAC/账务/GPU/系统模型` 机械套用 `page/limit/keyword`。**
`keyword` 在公共或敏感域会把“展示/摘要”升级成“搜索/枚举/探测”，仍应默认禁止。

---

## 2. 分组风险分层

### 2.1 `dashboard/count`：最低可治理风险，但不是无风险

候选：

- `DashboardDeploymentCountTool` → `/api/{orgId}/dashboard/deployment/count`
- `DashboardImageCountTool` → `/api/{orgId}/dashboard/image/count`
- `DashboardEasyFlowTool` → `/api/{orgId}/dashboard/easy-flow`

当前共同特征：

- 路径带 `{orgId}`，属于 orgScoped。
- 当前注解仍为 `@ToolPermission(PUBLIC)`，这是贯穿 M5 的权限债务。
- 当前执行固定透传 `page=1&limit=100`。
- 当前无参数契约 `HasSpecs=False`。

安全判断：

- `deployment/count`、`image/count` 从命名看偏聚合统计，**比普通资源列表、用户列表、账务/RBAC 列表风险更低**。
- 但 count/summary 接口仍可能暴露：
  - 某组织是否存在资源；
  - 部署/镜像数量、活跃度、增长变化；
  - 资源规模侧信道；
  - orgId 枚举带来的跨租户探测。
- `easy-flow` 风险高于两个 count：名称指向流程列表/便捷流程，可能包含流程名称、创建者、状态、模板、任务链等业务信息；在响应字段未确认前不能等同 count。

**建议：M5.4 如要选最小开放面，应只推进 dashboard count/easy-flow 专项中的“安全子集”：**

- 首选开放：`DashboardDeploymentCountTool`、`DashboardImageCountTool`
- 条件开放：`DashboardEasyFlowTool` 必须先确认响应字段仅为低敏聚合/导航项；否则 HOLD。

开放方式：

- 只允许 `page/limit-only`，禁止 `keyword/name/search/kw`。
- `limit` 最大值不超过 100，建议沿用 M5.3 `buildPageLimitOnlyQuery(params, 100)`。
- 禁止调用方覆盖 `orgId/organizationId` 旁路参数；orgId 必须来自当前会话/上下文解析，而不是 PUBLIC 用户自然语言指定。
- 在 RBAC 权限收敛前，不要把该组从“参数治理”扩大为“匿名可跨组织 dashboard”。

### 2.2 `public/no-org SysInfoMap`：公开端点但不适合公开参数化

候选：

- `SysInfoMapTool` → `/api/public/sys-info/all/map`

安全判断：

- 路径是 `/api/public/...` 且 no-org，表面看是公开系统信息。
- 但 `all/map` 语义表示一次性返回系统信息配置映射，可能包含：功能开关、产品配置、默认资源、系统枚举、环境能力、前端渲染配置、租户注册策略等。
- 对 map 类接口开放 `page/limit` 本身语义不自然；开放 `keyword` 则可能变成配置键名探测。

**建议：继续 HOLD 参数化，不开放 `page/limit/keyword`。**

可接受状态：

- 保持无参数契约，固定调用后端默认行为。
- 或建立 `no-param public config contract` 测试，明确锁住不暴露任何查询参数。
- 若后续要公开参数，只能在后端提供白名单字段/公开 schema 后，按白名单 key 枚举，而不是通用 keyword 搜索。

### 2.3 `special-field/detail/option`：异构高不确定性，不能批量开放

候选包括：裸金属模板、诊断、存储 option、卷路径、GPU map、GPU/节点 metrics、Helm release history、MPI job detail、用户详情。

主要风险：

- **detail 类**：往往需要精确资源标识，分页参数可能掩盖“详情接口实际按列表返回”的后端行为，导致枚举详情。
- **option/path 类**：可能暴露存储拓扑、卷路径、后端挂载点、可选资源池。
- **metrics 类**：GPU/节点指标具有资源侧信道属性，可推断租户负载、节点压力、GPU 稀缺程度。
- **user_detail**：用户隐私/RBAC 边界，必须 HOLD。
- **gpu_map_detail**：GLOBAL/no-org GPU map，风险接近 M5.3 `/api/gpu`，应继续 HOLD。

**建议：整组不作为 M5.4 最小开放对象。** 后续应拆成更小专项：

- option 白名单专项；
- detail 精确 ID/RBAC 专项；
- metrics 侧信道专项；
- 用户隐私专项；
- GPU/global 专项。

### 2.4 `tenant-list-like`：组织内资源枚举面，高风险

候选包括集群、DaemonSet、DevOps、镜像、namespace、节点、服务、用户管理/用户查询等。

主要风险：

- 组织内资源列表即使 orgScoped，也可能因 `orgId` 可控或解析默认值导致跨租户访问。
- `page/limit` 扩大枚举能力；`keyword` 扩大精确探测能力。
- 用户、节点、namespace、镜像仓库、集群信息均可用于攻击面测绘。

**建议：继续 HOLD，不在 M5.4 开放。**

开放前置条件：

- 证明后端按当前登录用户/角色强制过滤；
- 证明 `resolveOrganizationId(params)` 不接受用户自由覆盖跨租户 orgId；
- 明确字段脱敏；
- 明确 keyword 搜索字段；
- 建立跨租户负向测试。

### 2.5 `existing-hold-sensitive`：最高风险，必须测试锁住

候选包括：

- `GpuGlobalListTool`、`SysModelListTool`
- `OrderListTool`、`QuotaReceiveListTool`
- `LdapConfigListTool`、`OrganizationListTool`、`PermissionMenuListTool`
- `RegisterAuditListTool`、`RoleAssignableListTool`、`RoleEditableListTool`

这些对象已经在 M5.1~M5.3 明确 HOLD。M5.4 不应重新打开。

**建议：继续由 `SensitiveListToolHoldContractTest` 锁住，必要时新增 M5.4 用例覆盖 `page/limit/keyword` 均不得暴露。**

---

## 3. Dashboard count / easy-flow 专项判断

### 3.1 是否存在枚举风险？存在，但可控

即使 count 接口不是明细列表，`page/limit` 仍可能让调用者遍历统计条目，例如按类型、按状态、按命名空间、按时间窗口的计数桶。风险低于明细列表，但不是零。

控制建议：

- 只开放 `page/limit`，不开放 `keyword`。
- `limit <= 100`。
- 禁止搜索别名：`keyword/name/search/kw`。
- 禁止透传用户提供的 `orgId/organizationId`。

### 3.2 是否存在跨租户风险？存在，取决于 orgId 解析与后端鉴权

当前路径为 `/api/{orgId}/...`，Tool 内调用 `resolveOrganizationId(params)`。如果 PUBLIC 用户或 LLM 参数能影响 orgId，则可能构成跨组织探测。

控制建议：

- 参数契约中不得声明 orgId。
- 执行层 query map 不得包含 orgId/organizationId。
- 需要测试覆盖：传入 `orgId=其他租户` 时，不进入 query 参数，且路径 orgId 来源必须是受信上下文。
- 在权限收敛前，至少保持“默认组织”行为不扩大；但最终应改为 AUTHENTICATED/org-scoped policy。

### 3.3 是否存在资源侧信道风险？存在

Dashboard 统计可能暴露组织资源规模、镜像数量、部署数量、活跃度。攻击者可通过多次查询观察资源变化。

控制建议：

- 不开放 keyword，避免精确探测某镜像/部署是否存在。
- 不开放任意时间窗口/过滤器。
- 如后端支持时间/状态筛选，不得在本阶段透传。
- 后续可考虑速率限制、审计日志、输出聚合粒度限制。

### 3.4 `dashboard_easy_flow` 特别意见

`easy-flow` 不是明确 count。若响应是流程列表，可能包含名称、状态、创建人、任务链、资源引用等。它的风险应按“tenant-list-like”而不是“count”处理。

建议：

- **默认 HOLD**，直到确认响应仅是低敏 dashboard 导航/摘要。
- 若确认为列表，不能在 M5.4 与 count 同批开放。
- 若确认为低敏摘要，可使用同样的 `page/limit-only`，但仍禁止 keyword。

---

## 4. HOLD / 开放建议清单

| 分组 | 建议 | 说明 |
|---|---|---|
| `dashboard/count` | **有限开放候选** | 最低风险；优先两个 count；`easy-flow` 条件开放/默认 HOLD |
| `public/no-org SysInfoMap` | **HOLD 参数化** | public config map 不适合 page/limit/keyword；尤其禁止 keyword |
| `special-field/detail/option` | **HOLD** | 异构字段、详情、路径、用户、GPU、metrics 风险混杂 |
| `tenant-list-like` | **HOLD** | 组织内资源/用户/节点/namespace 枚举与侧信道风险高 |
| `existing-hold-sensitive` | **继续 HOLD 并测试锁住** | M5.1~M5.3 已判定：账务/RBAC/GLOBAL/GPU/系统模型不得开放 |

M5.4 推荐执行路线：

1. 新增 `DashboardPageLimitOnlyContractTest`。
2. 仅对 `DashboardDeploymentCountTool`、`DashboardImageCountTool` 接入 `pageLimitOnlyParameterSpecs()` 与 `buildPageLimitOnlyQuery(params, 100)`。
3. `DashboardEasyFlowTool` 先加入 HOLD 测试；若后端/API 专家确认响应为低敏摘要，再转入 page/limit-only。
4. 新增/扩展 HOLD 测试覆盖：`SysInfoMapTool`、`DashboardEasyFlowTool`、`GpuMapDetailTool`、`UserDetailTool`、`NodeAllocationTool`、`FileVolumePathTool` 等高风险代表。

---

## 5. 建议测试边界

### 5.1 Dashboard page/limit-only 正向契约

覆盖对象：

- `DashboardDeploymentCountTool`
- `DashboardImageCountTool`

断言：

- 只暴露 `page`、`limit`。
- 不暴露 `keyword`。
- aliases 仅限分页语义：`pageNo/page_no/current`、`pageSize/page_size/size`。
- 调用参数 `page=2, limit=25` 时真实透传。

### 5.2 Dashboard 旁路/搜索负向契约

传入：

- `keyword`
- `name`
- `search`
- `kw`
- `orgId`
- `organizationId`
- 任意业务 filter，如 `status/type/timeRange`（如果自然语言参数抽取可能产生）

断言：

- HTTP query 只包含 `page/limit`。
- 不透传搜索字段、org 字段、业务 filter。

### 5.3 Dashboard 分页校验

断言：

- `page <= 0` 拒绝。
- `limit <= 0` 拒绝。
- `limit > 100` 拒绝。
- 小数、非数字、空白字符串返回结构化错误。
- 参数错误时不发起 HTTP 调用。

### 5.4 Dashboard HOLD 测试

若 `DashboardEasyFlowTool` 未确认低敏，应断言：

- 不暴露 `page`。
- 不暴露 `limit`。
- 不暴露 `keyword`。

### 5.5 SysInfoMap no-param/HOLD 测试

断言：

- `SysInfoMapTool` 不暴露 `page/limit/keyword`。
- 传入 `keyword/name/search/kw/page/limit` 不应改变后端 query 行为。
- 如保留固定 `page=1&limit=100` 的实现，至少不要新增参数契约；更理想是 no-param 调用由后端默认返回公开 map。

### 5.6 existing-hold-sensitive 回归

继续保留并扩展 `SensitiveListToolHoldContractTest`：

- `GpuGlobalListTool`
- `SysModelListTool`
- `OrderListTool`
- `QuotaReceiveListTool`
- `LdapConfigListTool`
- `OrganizationListTool`
- `PermissionMenuListTool`
- `RegisterAuditListTool`
- `RoleAssignableListTool`
- `RoleEditableListTool`

断言它们不得暴露 `page/limit/keyword`。

---

## 6. 最终专家建议

**M5.4 最低风险组：`dashboard/count`，但应拆分为“两个 count 可有限开放 + easy-flow 默认 HOLD”。**

可开放：

- `DashboardDeploymentCountTool`
- `DashboardImageCountTool`

开放限制：

- `page/limit-only`
- `limit <= 100`
- 禁止 `keyword/name/search/kw`
- 禁止 orgId query 旁路
- 不新增任意 filter
- 后续 RBAC 专项将 PUBLIC 收敛到 AUTHENTICATED/org-scoped

应 HOLD：

- `DashboardEasyFlowTool`，直到确认响应低敏
- `SysInfoMapTool`，不公开参数化
- `special-field/detail/option` 整组
- `tenant-list-like` 整组
- `existing-hold-sensitive` 整组

一句话结论：**M5.4 可以小步开放 dashboard 聚合 count 的受限分页，但不能把 PUBLIC/no-org/GLOBAL/详情/用户/RBAC/账务/GPU/系统模型机械开放为 page/limit/keyword 列表能力。**
