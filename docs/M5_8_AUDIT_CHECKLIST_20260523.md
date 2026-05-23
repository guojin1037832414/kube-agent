# kube-agent M5.8 审计清单

> 生成时间: 2026-05-23 12:50 CST  
> 审计人: Hermes  
> 审计范围: M5.8 业务 Tool 禁止 sysadmin fallback token 自动降级

## 一、工程目录审计

### 1. 生产代码

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/main/java/com/atlas/http/KubeManagerHttpClient.java` | M5.8 | ✅ PASS | `get/post/delete` 从 `resolveToken()` 切换为 `resolveUserTokenRequired()`，业务请求缺用户 ThreadLocal Token 时 fail-closed。 |

### 2. 测试代码

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/test/java/com/atlas/http/KubeManagerHttpClientTokenFallbackSecurityTest.java` | M5.8 | ✅ PASS | 新增 5 个安全边界测试，锁定缺用户 Token 不触发 fallback 登录、存在用户 Token 只携带用户 Token、系统任务 fallback 能力保留。 |

### 3. 文档

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `CHANGELOG.md` | M5.8 | ✅ PASS | 新增 M5.8 变更日志，记录背景、变更、验证与风险。 |
| `docs/M5_8_AUDIT_CHECKLIST_20260523.md` | M5.8 | ✅ PASS | 本审计清单。 |

## 二、功能验证

| 功能/边界 | 测试方式 | 结果 |
|-----------|----------|------|
| GET 缺用户 Token 禁止 fallback | `KubeManagerHttpClientTokenFallbackSecurityTest` | ✅ PASS |
| POST 缺用户 Token 禁止 fallback | `KubeManagerHttpClientTokenFallbackSecurityTest` | ✅ PASS |
| DELETE 缺用户 Token 禁止 fallback | `KubeManagerHttpClientTokenFallbackSecurityTest` | ✅ PASS |
| 用户 Token 存在时只用用户 Token | `MockRestServiceServer` 断言 Authorization Header | ✅ PASS |
| 系统任务 fallback 能力保留 | 反射调用 `resolveToken()` 并 Mock fallback 登录 | ✅ PASS |
| M5.7/M5.8 安全组合回归 | 17 tests | ✅ PASS |
| 全量测试 | 182 tests | ✅ PASS |
| 打包 | `mvn -q -DskipTests package` | ✅ PASS |
| Diff 格式检查 | `git diff --check` | ✅ PASS |
| Diff 敏感信息/危险执行扫描 | Python 正则扫描新增 diff 行 | ✅ PASS |

## 三、代码 Review

### 优点

1. **安全边界更清楚**：业务 Tool HTTP 请求必须绑定真实用户 Token，不再透明使用 sysadmin fallback token。
2. **改动面小**：只改 `KubeManagerHttpClient` 的业务入口和新增对应测试，没有扩大到上层编排逻辑，符合“小样本先验证”。
3. **兼容未来系统任务**：`resolveToken()` 未被删除，避免未来健康探测/后台同步等系统任务无入口，但文档明确限制不能被业务 Tool 默认路径调用。
4. **测试断言具体**：MockRestServiceServer 不仅断言成功路径，还断言缺 Token 时不会发出 fallback 登录请求。

### 风险

1. `resolveToken()` 仍保留 fallback 语义，未来新增调用方必须有白名单和测试保护。
2. 本轮没有审计所有可能绕过 `get/post/delete` 的独立 HTTP 出口，后续可做源码扫描和契约测试。
3. 本轮未重启本地服务做真实 SSE；考虑到边界在 HTTP 客户端 token 解析层，单测已覆盖核心安全分支。

## 四、缺口分析

| 缺口 | 优先级 | 影响 | 建议 |
|------|--------|------|------|
| 系统任务 fallback 白名单尚未正式建模 | 🟡 MED | 未来有人误用 `resolveToken()` 仍可能引入权限放大 | 后续引入 `SystemContextPolicy` 或源码契约测试，禁止业务入口调用 fallback 方法。 |
| 独立 HTTP 出口未做全量扫描测试 | 🟡 MED | 若存在不经 `KubeManagerHttpClient#get/post/delete` 的出口，可能绕开治理 | 下一批做“HTTP 出口契约审计”。 |
| 缺真实 SSE 回归 | 🟢 LOW | 本轮安全分支不依赖真实后端，但服务路径仍建议后续阶段做冒烟 | 下一批服务重启后补一次登录+只读查询冒烟。 |

## 五、审计结论

✅ **PASS**：M5.8 小样本安全闭环完成。业务 Tool 默认 HTTP 请求路径已从“可 sysadmin fallback”调整为“必须用户 Token，缺失则 fail-closed”，并通过定向、组合、全量、打包和安全扫描验证。
