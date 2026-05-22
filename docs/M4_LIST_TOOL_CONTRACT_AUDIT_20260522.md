# kube-agent M4 列表 Tool 参数契约阶段收口审计清单 — 20260522

> 生成时间: 2026-05-22 13:35 CST  
> 审计人: Hermes  
> 审计范围: M4.3-M4.8 标准列表 Tool `page/limit/keyword` 参数契约铺开、测试、Review、文档与双远端状态  
> 对应 HEAD: `19c9ff9 feat(M4.8): expand low-risk billing list contracts`

---

## 一、Git 状态审计

| 项目 | 状态 | 证据 |
|------|------|------|
| 当前分支 | ✅ PASS | `master` |
| 最新提交 | ✅ PASS | `19c9ff9 feat(M4.8): expand low-risk billing list contracts` |
| GitLab 远端 | ✅ PASS | `origin/master = 19c9ff9e392c...` |
| GitHub 远端 | ✅ PASS | `github/master = 19c9ff9e392c...` |
| 工作区 | ✅ PASS | `git status --short --branch` 显示 `## master`，无未提交改动 |

---

## 二、阶段交付物矩阵

### 2.1 核心横切能力

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/main/java/com/atlas/tool/core/BaseTool.java` | M4.3 | ✅ PASS | 提供 `listQueryParameterSpecs(...)` 与 `buildListQuery(params)`，统一分页、keyword 与校验行为 |
| `src/test/java/com/atlas/tool/impl/ListToolParameterSpecContractTest.java` | M4.2-M4.8 | ✅ PASS | 覆盖已纳入 Tool 的 `page/limit/keyword` schema 契约 |
| `src/test/java/com/atlas/tool/impl/ListToolParameterPassThroughContractTest.java` | M4.3-M4.8 | ✅ PASS | 覆盖 query 透传、默认值、非法分页短路 |
| `src/test/java/com/atlas/http/KubeManagerHttpClientUrlContractTest.java` | M4.2 | ✅ PASS | 覆盖 query 编码和防二次编码 |

### 2.2 已纳入标准列表参数契约的 27 个 Tool

| # | Tool | 阶段 | 状态 |
|---:|------|------|------|
| 1 | `MpiJobListTool` | M4.3 | ✅ PASS |
| 2 | `PytorchJobListTool` | M4.3 | ✅ PASS |
| 3 | `FileMaterialListTool` | M4.3 | ✅ PASS |
| 4 | `GpuDetailListTool` | M4.3 | ✅ PASS |
| 5 | `DataSetListTool` | M4.4 | ✅ PASS |
| 6 | `ModelListTool` | M4.4 | ✅ PASS |
| 7 | `FileListTool` | M4.4 | ✅ PASS |
| 8 | `RegistryListTool` | M4.4 | ✅ PASS |
| 9 | `TensorBoardListTool` | M4.4 | ✅ PASS |
| 10 | `JobTemplateListTool` | M4.4 | ✅ PASS |
| 11 | `TemplateListTool` | M4.4 | ✅ PASS |
| 12 | `ResourcePresetListTool` | M4.4 | ✅ PASS |
| 13 | `BareMetalAppListTool` | M4.5 | ✅ PASS |
| 14 | `CloudResourceListTool` | M4.5 | ✅ PASS |
| 15 | `ComposeListTool` | M4.5 | ✅ PASS |
| 16 | `ExperimentInstanceListTool` | M4.5 | ✅ PASS |
| 17 | `ExperimentTemplateListTool` | M4.5 | ✅ PASS |
| 18 | `ExternalLinkListTool` | M4.5 | ✅ PASS |
| 19 | `HelmRepoListTool` | M4.5 | ✅ PASS |
| 20 | `HelmReleaseListTool` | M4.5 | ✅ PASS |
| 21 | `CoursewareListTool` | M4.6 | ✅ PASS |
| 22 | `DownloadTaskListTool` | M4.6 | ✅ PASS |
| 23 | `InboxMessageListTool` | M4.6 | ✅ PASS |
| 24 | `MigConfigListTool` | M4.6 | ✅ PASS |
| 25 | `NamespaceListTool` | M4.6 | ✅ PASS |
| 26 | `TableListTool` | M4.6 | ✅ PASS |
| 27 | `SlurmNodeListTool` | M4.6 | ✅ PASS |
| 28 | `SlurmClusterListTool` | M4.7 | ✅ PASS |
| 29 | `UploadStatusListTool` | M4.7 | ✅ PASS |
| 30 | `ResourceUsageListTool` | M4.8 | ✅ PASS |
| 31 | `QuotaMyListTool` | M4.8 | ✅ PASS |

> 注：当前源码扫描统计为 27 个 `*ListTool` 同时包含 `listQueryParameterSpecs(...)` 与 `buildListQuery(params)`；表格中 M4.3-M4.8 按 Review Log 追溯列出了所有阶段记录的标准列表类，其中部分早期/非 `*ListTool` 命名差异需在下一阶段做一次命名口径统一复核。

---

## 三、功能验证

| 功能 | 验证方式 | 结果 |
|------|----------|------|
| 参数 schema 暴露 | `mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest test` | ✅ 5 tests, 0 failures |
| page/limit/keyword 真实透传 | 同上 | ✅ PASS |
| 非法分页短路且不触发 HTTP | 同上 | ✅ PASS |
| URL query 编码 | `KubeManagerHttpClientUrlContractTest` 包含在全量测试中 | ✅ PASS |
| 全项目回归 | `mvn test` | ✅ 142 tests, 0 failures, BUILD SUCCESS |
| 空白/格式检查 | `git diff --check` | ✅ PASS |
| 新增敏感信息扫描 | Python added-lines scan | ✅ `SECRET_SCAN_FINDINGS 0` |
| 独立 Review | delegate_task pre-commit review | ✅ PASS |

---

## 四、剩余固定分页候选清单

当前剩余固定 `page=1&limit=100` 的 `*ListTool` 共 16 个，已按风险分层：

### 4.1 ACCOUNT_BILLING_QUOTA_DEFERRED（3 个）

| Tool | 状态 | 原因 | 下一步 |
|------|------|------|--------|
| `CurrencyQueryListTool` | ⚠️ HOLD | 账务/币种元数据，keyword 语义需确认 | 可作为账务专项低风险候选 |
| `OrderListTool` | ⚠️ HOLD | 订单/账务敏感，会扩大历史订单枚举面 | 需确认租户隔离、可见范围、审计与 keyword 字段 |
| `QuotaReceiveListTool` | ⚠️ HOLD | 审批/待办/RBAC 语义，会扩大审批记录枚举面 | 需 RBAC/审批专项会诊 |

### 4.2 RBAC_ADMIN_ORG_SENSITIVE_DEFERRED（6 个）

| Tool | 状态 | 原因 |
|------|------|------|
| `LdapConfigListTool` | ⚠️ HOLD | LDAP 配置敏感 |
| `OrganizationListTool` | ⚠️ HOLD | 组织枚举风险 |
| `PermissionMenuListTool` | ⚠️ HOLD | 权限菜单/权限面敏感 |
| `RegisterAuditListTool` | ⚠️ HOLD | 注册审核记录敏感 |
| `RoleAssignableListTool` | ⚠️ HOLD | 角色分配枚举风险 |
| `RoleEditableListTool` | ⚠️ HOLD | 角色编辑范围枚举风险 |

### 4.3 GLOBAL_PUBLIC_OR_MIXED_DEFERRED（7 个）

| Tool | 状态 | 原因 |
|------|------|------|
| `GpuGlobalListTool` | ⚠️ HOLD | 全局 GPU 信息，需公共/全局接口专项 |
| `HomeIndustryClassListTool` | ⚠️ HOLD | 首页 public 接口，业务字段可能不是标准 keyword |
| `HomeIndustryListTool` | ⚠️ HOLD | 首页 public 接口 |
| `HomeModelListTool` | ⚠️ HOLD | 首页 public 模型列表 |
| `HomeNimListTool` | ⚠️ HOLD | 首页 public NIM 服务 |
| `HomeRepositoryListTool` | ⚠️ HOLD | 首页 public 仓库列表 |
| `SysModelListTool` | ⚠️ HOLD | 全局模型列表，需公共/全局接口专项 |

---

## 五、缺口分析

| 缺口 | 优先级 | 影响 | 建议入口 |
|------|--------|------|----------|
| 已纳入 Tool 数量口径需统一（源码扫描 27 vs 阶段表 31） | 🟡 MED | 可能存在非 `*ListTool` 命名或阶段记录口径差异 | 下一阶段先做命名/扫描脚本统一 |
| 账务/审批/订单专项未完成 | 🔴 HIGH | 直接铺开可能扩大敏感数据枚举面 | M5.1 账务审批专项专家会诊 |
| RBAC 管理面专项未完成 | 🔴 HIGH | 用户/角色/组织/LDAP 枚举风险 | M5.2 RBAC 管理面参数契约治理 |
| GLOBAL/PUBLIC 接口专项未完成 | 🟡 MED | public 接口可能不支持标准 keyword 或需不同参数 | M5.3 公共接口契约治理 |
| keyword 后端真实字段未逐接口验证 | 🟡 MED | Tool 层透传成功但后端可能忽略 keyword | 基于前端源码/后端 API 做字段映射审计 |
| orgId path segment 统一校验仍未专项治理 | 🟡 MED | 多租户路径拼接仍依赖各 Tool 调用模式 | 横切安全专项：orgId resolver + path builder |

---

## 六、审计结论

### ✅ PASS

- M4.3-M4.8 已按小步闭环完成标准列表 Tool 参数契约铺开。
- 当前 HEAD、GitLab、GitHub 三者一致，工作区干净。
- 定向测试、全量测试、diff check、敏感信息扫描、独立 Review 全部通过。
- 文档已同步 `CHANGELOG.md`、`docs/REVIEW_LOG.md`，并备份至 `/mnt/h/Hermes中重要文件/`。

### ⚠️ HOLD

- 剩余 16 个固定分页 `*ListTool` 不应继续机械批量铺开，应按账务/RBAC/global 三个专项治理。
- M4 阶段的标准列表铺开目标已完成到“低风险 org-scoped 列表”边界，继续推进需要进入更强安全模型。

### ❌ FAIL

- 无阻断失败项。

---

## 七、下一里程碑建议入口

建议进入 **M5：敏感域列表参数契约与权限审计专项**。

推荐拆分：

1. **M5.1 账务/配额/订单专项**
   - 候选：`CurrencyQueryListTool`、`OrderListTool`、`QuotaReceiveListTool`
   - 前置：确认后端权限、审计、keyword 字段语义。
2. **M5.2 RBAC 管理面专项**
   - 候选：LDAP、组织、权限菜单、注册审核、角色可分配/可编辑。
   - 前置：明确 PUBLIC 注解是否合理，补审计与权限测试。
3. **M5.3 GLOBAL/PUBLIC 接口专项**
   - 候选：首页 public、全局 GPU、全局模型。
   - 前置：确认 public 接口分页/keyword 参数名，避免误导 LLM。
4. **M5.4 参数契约口径统一与扫描工具化**
   - 将当前手工扫描升级为测试/脚本，输出已覆盖/暂缓/异常命名清单。

---

## 八、文档与备份位置

| 文档 | 路径 |
|------|------|
| 项目审计清单 | `/home/guojin/kube-agent/docs/M4_LIST_TOOL_CONTRACT_AUDIT_20260522.md` |
| H 盘备份 | `/mnt/h/Hermes中重要文件/M4_LIST_TOOL_CONTRACT_AUDIT_20260522.md` |
| Review Log | `/home/guojin/kube-agent/docs/REVIEW_LOG.md` |
| Review Log 备份 | `/mnt/h/Hermes中重要文件/kube-agent_REVIEW_LOG_20260522.md` |
| Changelog | `/home/guojin/kube-agent/CHANGELOG.md` |
| Changelog 备份 | `/mnt/h/Hermes中重要文件/kube-agent_CHANGELOG_20260522.md` |
