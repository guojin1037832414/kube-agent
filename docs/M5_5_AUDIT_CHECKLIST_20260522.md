# kube-agent M5.5 orgId 来源治理阶段审计清单

> 生成时间: 2026-05-22
> 审计人: Hermes
> 审计范围: M5.5 orgId 来源治理、跨租户参数污染防护、Review Concern 修复
> 基线 HEAD: `e2b59635655325478b17f8e14e5b20af0974d0ee`

## 一、交付物矩阵

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/main/java/com/atlas/tool/core/BaseTool.java` | M5.5 | ✅ PASS | `resolveOrganizationId` 收口到可信 ThreadLocal，不再信 params orgId |
| `src/main/java/com/atlas/react/ReActEngine.java` | M5.5 | ✅ PASS | Action params 过滤受保护上下文字段，防止 LLM 覆盖租户上下文 |
| `src/main/java/com/atlas/graph/config/AtlasGraphConfig.java` | M5.5 | ✅ PASS | tool_call/delegate 透传、绑定、清理 orgId；系统上下文最后写入 |
| `src/main/java/com/atlas/tool/impl/GpuQueryTool.java` | M5.5 Review fix | ✅ PASS | legacy Tool 改用 `resolveOrganizationId` |
| `src/main/java/com/atlas/tool/impl/ClusterOverviewTool.java` | M5.5 Review fix | ✅ PASS | legacy Tool 改用 `resolveOrganizationId` |
| `src/main/java/com/atlas/tool/impl/ImageQueryTool.java` | M5.5 Review fix | ✅ PASS | legacy Tool 改用 `resolveOrganizationId` |
| `src/test/java/com/atlas/tool/core/BaseToolOrganizationIdGovernanceTest.java` | M5.5 | ✅ PASS | BaseTool orgId 权威来源契约测试 |
| `src/test/java/com/atlas/tool/impl/OrganizationIdGovernanceRepresentativeToolTest.java` | M5.5 | ✅ PASS | Dashboard/Deployment/Storage/legacy Tool 代表样本测试 |
| `src/test/java/com/atlas/react/ReActEngineParamMergeTest.java` | M5.5 | ✅ PASS | ReAct protected context 合并契约更新 |
| `src/test/java/com/atlas/tool/impl/ListToolParameterPassThroughContractTest.java` | M5.5 | ✅ PASS | 旧列表回归改为 ThreadLocal 租户上下文 |
| `src/test/java/com/atlas/tool/impl/DashboardFixedQueryHoldContractTest.java` | M5.5 | ✅ PASS | Dashboard 固定查询 HOLD 回归改为 ThreadLocal 租户上下文 |
| `docs/M5_5_ORG_ID_SOURCE_AUDIT_SEED_20260522.md` | M5.5 | ✅ PASS | 审计种子 + 落地结论 |
| `docs/REVIEW_LOG.md` | M5.5 | ✅ PASS | 阶段实现、测试、Review、风险、经验教训已记录 |
| `CHANGELOG.md` | M5.5 | ✅ PASS | 变更日志已同步 |

## 二、验证结果

| 验证项 | 结果 |
|--------|------|
| M5.5 定向测试 | ✅ 13 tests, 0 failures, BUILD SUCCESS |
| M5 参数治理回归 | ✅ 28 tests, 0 failures, BUILD SUCCESS |
| 全量 Maven | ✅ 161 tests, 0 failures, BUILD SUCCESS |
| `git diff --check` | ✅ 通过 |
| diff 敏感信息扫描 | ✅ `NO_NEW_SENSITIVE_IN_DIFF` |
| 独立 Review #1 | ⚠️ CONCERN，已按意见修复 |
| 独立 Review #2 | ✅ PASS，可提交 |

## 三、Git 状态快照

### Diff Stat

```text
CHANGELOG.md                                       | 36 +++++++++
 docs/REVIEW_LOG.md                                 | 72 +++++++++++++++++
 .../com/atlas/graph/config/AtlasGraphConfig.java   | 90 ++++++++++++++++++----
 src/main/java/com/atlas/react/ReActEngine.java     | 39 ++++++++--
 src/main/java/com/atlas/tool/core/BaseTool.java    | 30 +++-----
 .../com/atlas/tool/impl/ClusterOverviewTool.java   |  5 +-
 .../java/com/atlas/tool/impl/GpuQueryTool.java     |  5 +-
 .../java/com/atlas/tool/impl/ImageQueryTool.java   |  5 +-
 .../com/atlas/react/ReActEngineParamMergeTest.java | 23 +++++-
 .../impl/DashboardFixedQueryHoldContractTest.java  | 13 ++++
 .../ListToolParameterPassThroughContractTest.java  | 13 ++++
 11 files changed, 278 insertions(+), 53 deletions(-)
```

### Status

```text
M CHANGELOG.md
 M docs/REVIEW_LOG.md
 M src/main/java/com/atlas/graph/config/AtlasGraphConfig.java
 M src/main/java/com/atlas/react/ReActEngine.java
 M src/main/java/com/atlas/tool/core/BaseTool.java
 M src/main/java/com/atlas/tool/impl/ClusterOverviewTool.java
 M src/main/java/com/atlas/tool/impl/GpuQueryTool.java
 M src/main/java/com/atlas/tool/impl/ImageQueryTool.java
 M src/test/java/com/atlas/react/ReActEngineParamMergeTest.java
 M src/test/java/com/atlas/tool/impl/DashboardFixedQueryHoldContractTest.java
 M src/test/java/com/atlas/tool/impl/ListToolParameterPassThroughContractTest.java
?? docs/M5_5_ORG_ID_SOURCE_AUDIT_SEED_20260522.md
?? src/test/java/com/atlas/tool/core/BaseToolOrganizationIdGovernanceTest.java
?? src/test/java/com/atlas/tool/impl/OrganizationIdGovernanceRepresentativeToolTest.java
```

## 四、审计结论

✅ **PASS**：M5.5 orgId 来源治理闭环完成，测试、Review、文档均满足阶段提交要求。

## 五、遗留项 / 下一步

1. M5.6 建议专项治理 `fallbackOrgId` 的可信语义。
2. M5.6 建议专项治理 `AtlasAsyncConfig` / `AsyncContextHolder` / 旧 `/chat/graph` 入口的 orgId 异步传播一致性。
3. 继续保持 orgId 来源治理与 page/limit/keyword 普通参数治理分离。
