# kube-agent M5.2 RBAC 管理面参数契约入口审计 — 20260522

> 生成时间: 2026-05-22 14:55 CST  
> 审计人: Hermes  
> 上游阶段: M5.1 账务域低风险货币列表参数契约与敏感 HOLD 保护  
> 目标: 为下一个里程碑 **M5.2 RBAC 管理面专项** 建立候选清单、风险边界与执行入口

---

## 一、M5.1 收口结论

### ✅ 已纳入

| Tool | 状态 | 说明 |
|------|------|------|
| `CurrencyQueryListTool` | ✅ PASS | 作为账务域低风险元数据列表，已接入标准 `page/limit/keyword` 参数契约与 `buildListQuery(params)` |

### ⚠️ 继续 HOLD

| Tool | 状态 | HOLD 原因 |
|------|------|-----------|
| `OrderListTool` | ⚠️ HOLD | 订单/租赁账务敏感列表，开放翻页与 keyword 会扩大历史订单枚举与敏感搜索能力 |
| `QuotaReceiveListTool` | ⚠️ HOLD | 配额审批/RBAC 语义敏感，开放 keyword 可能搜索申请人、审批人、资源配额和审批备注 |

M5.1 已通过 `SensitiveListToolHoldContractTest` 将订单与配额审批的 HOLD 约束测试化。

---

## 二、M5.2 候选范围：RBAC 管理面

| # | Tool | 风险等级 | 初始建议 | 主要风险 |
|---:|------|----------|----------|----------|
| 1 | `LdapConfigListTool` | 🔴 HIGH | HOLD 优先 | LDAP 配置可能包含目录域、同步配置、登录源信息 |
| 2 | `OrganizationListTool` | 🔴 HIGH | HOLD 优先 | 组织枚举、租户结构泄露、跨组织探测 |
| 3 | `PermissionMenuListTool` | 🔴 HIGH | HOLD 优先 | 权限菜单/权限树暴露会辅助越权路径推断 |
| 4 | `RegisterAuditListTool` | 🔴 HIGH | HOLD 优先 | 注册审核记录可能包含用户身份、组织申请、审批状态 |
| 5 | `RoleAssignableListTool` | 🔴 HIGH | HOLD 优先 | 可分配角色枚举会暴露权限边界 |
| 6 | `RoleEditableListTool` | 🔴 HIGH | HOLD 优先 | 可编辑角色范围会暴露管理权限边界 |

---

## 三、M5.2 专家会诊必答问题

1. **PUBLIC 注解是否合理**：这些 Tool 是否应继续使用 PUBLIC，还是应显式要求 SYS_ADMIN / ORG_ADMIN / RBAC_ADMIN 等策略。
2. **后端权限是否强过滤**：后端是否根据 token + orgId + 当前角色限制返回范围。
3. **page/limit 枚举面**：开放分页后是否会扩大组织、角色、用户或权限树枚举。
4. **keyword 搜索字段**：keyword 是否会搜索用户名、邮箱、手机号、LDAP DN、组织名、角色名、权限编码等敏感字段。
5. **审计日志**：敏感 RBAC 查询是否记录调用人、orgId、toolName、page/limit、keyword 是否非空、结果状态。
6. **HOLD 测试策略**：是否先扩展 HOLD 保护测试，而不是直接开放参数契约。

---

## 四、建议执行路线

### M5.2.A：只读审计与专家会诊

- 读取 6 个 Tool 源码与注解。
- 查询前端源码/API 调用，确认真实参数名和业务字段。
- 三路专家会诊：后端/API、安全/RBAC、测试架构。

### M5.2.B：优先建立 HOLD 保护

- 若专家无法证明某 Tool 低风险，则先加入 HOLD 测试，至少禁止 keyword。
- 对明显高敏 Tool（LDAP/权限/角色）不应直接套标准三件套。

### M5.2.C：最小样本开放

- 只有在确认后端权限、字段脱敏、审计要求后，才选择 1 个最低风险样本开放。
- 开放时必须同 M4/M5.1 一样完成 schema、执行层、异常语义、测试四件套。

---

## 五、质量门禁

M5.2 任一小批必须满足：

- TDD 红灯 → 绿灯证据。
- 定向契约测试通过。
- 全量 `/usr/share/maven/bin/mvn test` 通过。
- `git diff --check` 通过。
- 新增行敏感信息扫描 0。
- 独立 pre-commit Review PASS。
- `CHANGELOG.md`、`docs/REVIEW_LOG.md` 更新，并备份到 `/mnt/h/Hermes中重要文件/`。
- 提交并双推 GitLab/GitHub。

---

## 六、审计结论

### ✅ PASS

- M5.1 已形成从普通列表参数契约到敏感域治理的过渡样板。
- 下一个里程碑入口明确：M5.2 RBAC 管理面专项。

### ⚠️ HOLD

- M5.2 不建议默认开放任何 RBAC 管理面 Tool 的 keyword。
- 若未完成权限模型确认和专家会诊，应优先写 HOLD 测试与审计文档，而非实现开放。

### ❌ FAIL

- 无当前阻断项。
