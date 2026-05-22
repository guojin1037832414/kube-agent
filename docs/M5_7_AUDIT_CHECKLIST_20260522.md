# kube-agent M5.7 阶段审计清单

> 生成时间: 2026-05-22 23:30
> 审计人: Hermes
> 审计范围: M5.7 fallbackOrgId 可信语义彻底收口与登录 fail-safe 治理

## 一、工程目录审计

### 1. 生产源码

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/main/java/com/atlas/http/KubeManagerHttpClient.java` | M5.7 | ✅ PASS | 删除 fallbackOrgId 字段/getter；resolveOrgId 改为本次 token 可信反查；移除 username-only cache；失败抛强类型异常。 |
| `src/main/java/com/atlas/http/OrgIdResolutionException.java` | M5.7 | ✅ PASS | 新增强类型异常与 Reason，用于登录链路 fail-safe 与测试断言。 |
| `src/main/java/com/atlas/controller/AuthController.java` | M5.7 | ✅ PASS | 登录响应缺可信 orgId 时反查；反查失败 502 且不创建 session。 |
| `src/main/java/com/atlas/orchestrator/AtlasOrchestrator.java` | M5.7 | ✅ PASS | 清理 fallbackOrgId 文案残留，缺可信 orgId 直接拒绝执行。 |
| `src/main/java/com/atlas/auth/async/AsyncContextHolder.java` | M5.7 | ✅ PASS | 清理 fallbackOrgId 文案残留，表达 fail-safe 语义。 |
| `src/main/java/com/atlas/graph/config/AtlasGraphConfig.java` | M5.7 | ✅ PASS | 清理 “state/ThreadLocal/fallback” 旧注释，避免维护误导。 |

### 2. 测试代码

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/test/java/com/atlas/contract/M57FallbackOrgIdSourceContractTest.java` | M5.7 | ✅ PASS | 源码级契约扫描，禁止 fallbackOrgId/default tenant 语义回流。 |
| `src/test/java/com/atlas/http/KubeManagerHttpClientResolveOrgIdSecurityTest.java` | M5.7 | ✅ PASS | 7 个边界测试，断言具体 OrgIdResolutionException Reason。 |
| `src/test/java/com/atlas/controller/AuthControllerLoginFailSafeTest.java` | M5.7 | ✅ PASS | 登录反查失败不得创建 session。 |

### 3. 文档

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `docs/M5_7_FALLBACK_ORG_ID_GOVERNANCE_PROPOSAL_20260522.md` | M5.7 | ✅ PASS | 专家会诊后的治理方案与边界说明。 |
| `docs/REVIEW_LOG.md` | M5.7 | ✅ PASS | 已补完整背景、实现、测试、Review、风险和经验教训。 |
| `CHANGELOG.md` | M5.7 | ✅ PASS | 已将 proposal 升级为正式 M5.7 条目，并关闭 M5.6 deferred。 |
| `docs/M5_7_AUDIT_CHECKLIST_20260522.md` | M5.7 | ✅ PASS | 本阶段审计清单。 |

## 二、功能验证

| 功能/契约 | 验证方式 | 结果 |
|-----------|----------|------|
| 生产源码无 fallbackOrgId 默认租户语义 | `M57FallbackOrgIdSourceContractTest` + search_files 扫描 | ✅ PASS，生产源码 0 命中 |
| resolveOrgId 空用户名 fail-closed | 单元测试断言 `USERNAME_EMPTY` | ✅ PASS |
| resolveOrgId 缺 token fail-closed | 单元测试断言 `TOKEN_UNAVAILABLE` | ✅ PASS |
| sysadmin 也需 token 非空 | 单元测试 | ✅ PASS |
| 普通用户未找到不返回默认组织 | MockRestServiceServer 桶式搜索全空 | ✅ PASS |
| 命中用户但 orgId=1 不继续扫桶洗白 | 单元测试断言 `INVALID_RESOLVED_ORG_ID` | ✅ PASS |
| 可信 orgId 正向返回 | 单元测试返回 `100002` | ✅ PASS |
| AuthController 反查失败不创建 session | Mockito verify never createSession | ✅ PASS |

## 三、质量门禁

| 门禁 | 命令/方式 | 结果 |
|------|-----------|------|
| M5.7 定向测试 | `mvn -Dtest=M57FallbackOrgIdSourceContractTest,KubeManagerHttpClientResolveOrgIdSecurityTest,AuthControllerLoginFailSafeTest test` | ✅ 9 tests, 0 failures |
| M5.6/M5.7 组合回归 | `mvn -Dtest=TokenPropagatingTaskDecoratorTest,AsyncContextHolderTest,AtlasOrchestratorOrgIdGuardTest,M57FallbackOrgIdSourceContractTest,KubeManagerHttpClientResolveOrgIdSecurityTest,AuthControllerLoginFailSafeTest test` | ✅ 21 tests, 0 failures |
| 全量测试 | `mvn test` | ✅ 177 tests, 0 failures |
| 打包 | `mvn -DskipTests package` | ✅ BUILD SUCCESS |
| 空白检查 | `git diff --check` | ✅ PASS |
| 敏感信息扫描 | added-lines scan | ✅ `SECRET_SCAN_FINDINGS 0` |
| 独立 Review | 两轮 delegate_task review | ✅ 第一轮发现 blocker，修复后二轮 PASS |

## 四、缺口分析

| 缺口/风险 | 优先级 | 当前影响 | 建议 |
|-----------|--------|----------|------|
| kube-manager 登录响应仍不直接返回 organizationId | 🟡 MED | 需要桶式搜索反查 orgId | 推动后端 `/api/login` 或 token introspection 返回 orgId。 |
| sysadmin 标记仍基于 username + 非空 token | 🟡 MED | 当前登录链路风险可控，但 public 方法未来复用需谨慎 | 后续增加 token 自省或限制 `resolveOrgId` 可见性。 |
| fallbackAuthToken 兼容模式仍存在 | 🟢 LOW | 已不用于 orgId 可信解析 | 后续单独评估兼容 HTTP 调用是否可移除。 |

## 五、审计结论

✅ **M5.7 PASS**：fallbackOrgId 作为可信租户上下文的入口已清理；登录链路无法确认 orgId 时 fail-safe；测试、Review、文档、打包均已完成。
⚠️ **后续关注**：推动 kube-manager 提供原生 orgId 返回/自省能力，从根上替代桶式搜索。
