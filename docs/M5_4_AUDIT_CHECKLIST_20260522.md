# kube-agent M5.4 阶段审计清单 — 20260522

> 生成时间：2026-05-22 19:12 CST
> 审计人：Hermes
> 审计范围：M5.4 固定分页候选复扫、专家会诊、Dashboard/SysInfoMap HOLD 保护测试、提交前质量门禁

## 一、阶段结论

M5.4 采用 fail-safe 策略：三路专家未对 Dashboard count 参数开放达成一致，因此本阶段**不修改生产代码**，只新增文档与 HOLD 契约测试，防止后续批量脚本误把 Dashboard/SysInfoMap 接入普通列表参数能力。

## 二、交付物矩阵

| 文件 | 阶段归属 | 状态 | 说明 |
|---|---|---|---|
| `docs/M5_4_FIXED_PAGINATION_AUDIT_SEED_20260522.md` | M5.4 审计输入 | ✅ PASS | 自动复扫 35 个固定分页候选并按风险语义分组 |
| `docs/M5_4_SECURITY_RBAC_EXPERT_REPORT_20260522.md` | M5.4 专家会诊 | ✅ PASS | 安全/RBAC 风险分层报告；已补充最终 fail-safe 采纳说明 |
| `src/test/java/com/atlas/tool/impl/DashboardFixedQueryHoldContractTest.java` | M5.4 HOLD 测试 | ✅ PASS | 锁定 3 个 Dashboard Tool 不暴露 page/limit/keyword，执行层保持固定 query |
| `src/test/java/com/atlas/tool/impl/SensitiveListToolHoldContractTest.java` | M5.4 HOLD 扩展 | ✅ PASS | 新增 SysInfoMapTool no-param HOLD 保护 |
| `docs/M5_4_AUDIT_CHECKLIST_20260522.md` | M5.4 审计闭环 | ✅ PASS | 本文件，记录阶段交付物、测试、风险与下一步 |

## 三、功能/质量验证

| 验证项 | 命令/方式 | 结果 |
|---|---|---|
| M5.4 定向测试 | `mvn -Dtest=DashboardFixedQueryHoldContractTest,SensitiveListToolHoldContractTest test` | ✅ 6 tests, 0 failures |
| M5 参数治理回归 | `mvn -Dtest=ListToolParameterSpecContractTest,ListToolParameterPassThroughContractTest,SensitiveListToolHoldContractTest,HomeInfoPublicPageLimitContractTest,DashboardFixedQueryHoldContractTest test` | ✅ 15 tests, 0 failures |
| 全量测试 | `mvn test` | ✅ 152 tests, 0 failures, BUILD SUCCESS |
| 格式门禁 | `git diff --check` | ✅ PASS |
| 敏感信息扫描 | 工作区改动 secret/token/PAT 模式扫描 | ✅ `WORKTREE_SECRET_SCAN_FINDINGS 0` |
| 独立 Review | delegate_task pre-commit reviewer | ✅ 安全/逻辑方向 PASS；已修复 trailing whitespace，已增强唯一 HTTP 调用断言 |

## 四、Git 状态快照

### 当前改动统计

```text
docs/M5_4_FIXED_PAGINATION_AUDIT_SEED_20260522.md  |  63 ++++
 docs/M5_4_SECURITY_RBAC_EXPERT_REPORT_20260522.md  | 317 +++++++++++++++++++++
 .../impl/DashboardFixedQueryHoldContractTest.java  |  99 +++++++
 .../impl/SensitiveListToolHoldContractTest.java    |   5 +
 4 files changed, 484 insertions(+)
```

### 当前工作区

```text
A docs/M5_4_FIXED_PAGINATION_AUDIT_SEED_20260522.md
 A docs/M5_4_SECURITY_RBAC_EXPERT_REPORT_20260522.md
 A src/test/java/com/atlas/tool/impl/DashboardFixedQueryHoldContractTest.java
 M src/test/java/com/atlas/tool/impl/SensitiveListToolHoldContractTest.java
```

### 最近提交

```text
c854cb2 feat(M5.3): gate public home-info pagination
68b4165 test(M5.2): hold RBAC management list parameters
d6c40f2 feat(M5.1): gate sensitive list contracts
582e7bb docs(M4): add list tool contract audit checklist
19c9ff9 feat(M4.8): expand low-risk billing list contracts
```

## 五、发现的问题与处理

| 问题 | 根因 | 本阶段处理 | 后续建议 |
|---|---|---|---|
| Dashboard count 是否可开放 page/limit 专家意见不一致 | count/easy-flow 是否低敏、是否会形成枚举面未达成共识 | 本阶段不开放，新增 HOLD 保护测试 | 后续若开放，先确认后端字段/RBAC，再做小样本 pageLimit-only TDD |
| `organizationId` 可影响 Dashboard path orgId | `resolveOrganizationId(params)` 会读取调用参数，属于权限链路债务 | 本阶段不混修，仅记录风险 | 单独做 orgId 来源/跨租户负向测试专项 |
| SysInfoMap 是 public no-org map | 公共配置 map 不适合机械 page/limit/keyword | 新增 no-param HOLD 规格测试 | 后续若要查询配置，采用白名单 schema 而非 keyword |

## 六、PASS/FAIL 结论

✅ **M5.4 PASS（保护性阶段）**

- 未修改生产代码，避免在无共识时扩大攻击面。
- Dashboard 固定查询和 SysInfoMap no-org 配置入口已被测试锁住。
- M5 参数治理回归通过。
- 格式、安全扫描、独立 Review 问题已处理。

## 七、下一步建议

1. M5.5 建议做 `orgId` 来源治理专项：禁止 LLM/用户参数覆盖路径租户边界，改由 Session/ThreadLocal 可信上下文提供。
2. Dashboard count 若后续开放，必须先做响应字段审计 + RBAC 证明 + page/limit-only 小样本 TDD。
3. `tenant-list-like` 与 `special-field/detail/option` 分组继续 HOLD，不做批量参数化。
