# M5.7 fallbackOrgId 可信语义彻底收口建议

## 背景与现状

M5.5/M5.6 已经把 Tool / Graph / Orchestrator 执行链路的租户来源收口到可信 `ThreadLocal/session orgId`，缺失 orgId 时 fail-safe，不再把 `fallbackOrgId` 当作可执行租户边界。

当前残留风险集中在 `KubeManagerHttpClient`：

- 仍存在 `@Value("${atlas.backend.fallback-org-id:100001}") private String fallbackOrgId;`
- 仍暴露 `getFallbackOrgId()`，且注释描述为“ThreadLocal 无 orgId 且 Tool 参数未提供时使用默认值”
- `resolveOrgId(username, token)` 在以下失败场景返回并缓存 `fallbackOrgId`：
  - `username` 为空
  - `authToken` 缺失且 fallback sysadmin token 不可用
  - 桶式搜索所有已知组织后找不到用户

这会把“登录态 orgId 解析失败”洗白成“默认租户 100001”，与 M5.6 的 fail-safe 原则冲突。

## 安全结论

### 1. fallbackOrgId 不应保留为可信租户配置

`fallbackOrgId` 不应再表示“默认组织”或“兜底执行组织”。在多租户系统中，orgId 是授权边界，不是普通业务默认值。任何从配置推导出的 orgId 都无法证明当前 token / session 属于该租户。

建议：

- **删除 `atlas.backend.fallback-org-id` 配置语义**，至少在 kube-agent 执行链路中彻底禁用。
- 如果历史兼容必须短期保留配置项，应重命名或降级为测试/迁移专用，例如：
  - `atlas.backend.legacy-org-id-probe-disabled=true`（默认禁用）
  - 不建议继续使用 `fallback-org-id` 这个名字，因为它天然暗示可 fallback。

### 2. 应删除 `getFallbackOrgId()` getter

`getFallbackOrgId()` 是未来误用的最大入口。M5.6 已经确认执行链路不再需要它，继续暴露 public getter 会让新增 Graph/Tool/Orchestrator 代码重新把默认组织当作可信上下文。

建议：

- **M5.7 直接删除 getter**。
- 增加代码扫描/契约测试，禁止生产代码出现 `getFallbackOrgId(` 调用。
- 如果短期不能删除字段，也不要暴露 getter；字段只能在内部迁移期被删除前使用，且不得返回给上层。

### 3. `resolveOrgId` 失败时应抛异常，而不是返回 fallbackOrgId/null

`resolveOrgId(username, token)` 当前调用点在登录流程。它的语义应是“用已认证 token 反查当前用户所属组织”。若反查失败，说明无法建立可信 session orgId，应拒绝登录或要求重新认证，而不是分配默认组织。

建议行为：

| 场景 | M5.7 建议 |
| --- | --- |
| `username` 为空 | 抛 `OrgIdResolutionException` / `IllegalArgumentException`，登录失败 401/400 |
| `authToken` 为空 | 抛 `OrgIdResolutionException`，不使用 sysadmin fallback token 代查登录用户租户 |
| token 查询 kube-manager 失败 | 抛 `OrgIdResolutionException`，登录失败 503/502 或内部安全错误 |
| 用户不存在于任何可信响应 | 抛 `OrgIdResolutionException`，登录失败，禁止缓存默认 orgId |
| 解析出空/`1`/非法 orgId | 抛 `OrgIdResolutionException`，禁止创建 session |
| `sysadmin/sysadmin02` | 仅允许显式超管路径返回 `sysadmin` 标记，且下游必须按全局模式单独授权，不得转换成普通 orgId |

不建议返回 `null`：

- `null` 很容易被调用方再次用默认值兜底。
- 抛出强类型异常更容易在测试中锁定 fail-safe 契约。

### 4. `resolveOrgId` 不应使用 fallback sysadmin token 替代用户 token

当前逻辑在用户 token 缺失时调用 `ensureFallbackAuthenticated()` 并用 sysadmin token 桶式搜索用户。这会产生两个问题：

1. 登录用户身份与查询权限不再原子绑定；解析结果来自系统身份而非当前用户身份。
2. 一旦 username 可控或重名/缓存污染，就可能把用户映射到错误租户。

建议：

- `resolveOrgId` 必须要求当前登录返回的用户 token 非空。
- 不允许使用 `fallbackAuthToken`/sysadmin token 补偿用户身份反查失败。
- 如 kube-manager 登录响应长期不返回 orgId，应优先推动 kube-manager 在 `/api/login` 中返回 `orgId/organizationId`，或提供“当前 token 自省”接口，而不是用跨租户桶式搜索。

### 5. 缓存只能缓存可信成功结果，不能缓存 fallback/失败结果

当前 `orgIdCache.put(username, fallbackOrgId, 60s)` 会放大风险：一次解析失败即可在短时间内把同 username 固定到默认租户。

建议：

- cache key 至少包含 token/session 维度，避免同 username 多会话污染：例如 token hash + username。
- 只缓存成功且可信的 orgId。
- 失败不缓存，或仅缓存“失败原因”用于限流/观测，不得返回可执行 orgId。
- 登出/登录刷新时清理相关缓存。

## 推荐落地方案

### 首选方案：M5.7 严格删除 fallbackOrgId 执行语义

1. 删除 `KubeManagerHttpClient#fallbackOrgId` 字段与 `@Value("${atlas.backend.fallback-org-id:100001}")`。
2. 删除 `getFallbackOrgId()`。
3. 新增强类型运行时异常，例如 `OrgIdResolutionException extends RuntimeException`。
4. 重写 `resolveOrgId(username, authToken)`：
   - username/token 为空直接抛异常；
   - 不调用 `ensureFallbackAuthenticated()`；
   - 桶式搜索只使用当前用户 token；
   - 未找到用户、返回空 orgId、返回非法 orgId 均抛异常；
   - 只缓存可信成功结果。
5. 修改 `AuthController`：
   - 登录响应中若无法得到可信 orgId，则调用 `resolveOrgId`；
   - `resolveOrgId` 抛异常时，登录失败，不创建 session；
   - 日志文案改为“无法解析可信 orgId，拒绝创建 session”，不要写“使用默认值”。
6. 增加静态/契约测试，禁止 fallbackOrgId 重新进入执行链路。

### 兼容方案：短期保留字段但去可信化

如果担心配置删除影响部署，可以先：

1. 保留配置读取但改名为 `legacyFallbackOrgId`，标记 `@Deprecated`。
2. 删除 public getter。
3. 所有解析失败路径仍抛异常，不返回该字段。
4. CHANGELOG 明确该配置不再生效，将在下一阶段删除。

该方案只适合一版过渡；从安全角度不如直接删除。

## 测试边界建议

### 单元/契约测试

1. `KubeManagerHttpClientResolveOrgIdSecurityTest`
   - username 为空：断言抛 `OrgIdResolutionException`，不返回 `100001`。
   - token 为空：断言抛异常，且不触发 fallback sysadmin 登录。
   - 所有桶查询失败：断言抛异常，不缓存 fallback orgId。
   - 用户不存在：断言抛异常，不缓存 fallback orgId。
   - 用户存在且 organizationId 有效：返回该 orgId，并缓存成功值。
   - organizationId 为空/`1`/`sysadmin` 非预期：断言抛异常或走显式超管分支。

2. `FallbackOrgIdRemovalContractTest`
   - 源码扫描 `src/main/java`：不得出现 `getFallbackOrgId(`。
   - 源码扫描：不得出现 `atlas.backend.fallback-org-id`。
   - 源码扫描：不得在 `resolveOrgId` 中出现 `return fallbackOrgId` 或 `orgIdCache.put(...fallback...)`。

3. `AuthControllerOrgIdFailSafeTest`
   - kube-manager 登录返回 token 但无 orgId，且 `resolveOrgId` 抛异常：断言登录失败，不创建 session。
   - kube-manager 登录返回 orgId：断言正常创建 session。
   - kube-manager 登录返回 `orgId=1`：断言必须成功反查可信 orgId，否则登录失败。

### 集成/回归测试

1. Graph / Orchestrator / HITL 回归：缺 ThreadLocal/session orgId 时仍返回安全错误，不调用任何 fallback getter。
2. 异步上下文回归：token 与 orgId 仍原子传播；空 orgId 不被任何配置补齐。
3. 缓存隔离：同 username 不同 token/session 不应串用 orgId 缓存。
4. 日志审计：失败日志只记录“拒绝创建 session/拒绝执行”，不得出现“回退默认组织”。

## 风险与迁移建议

- 如果 kube-manager `/api/login` 经常不返回 orgId，M5.7 改动可能导致部分用户登录失败；这是安全上正确的 fail-safe，但需要提前在测试环境验证登录响应格式。
- 最稳妥的长期方案是推动 kube-manager 提供 token 自省接口，例如 `/api/me` 或 `/api/current-user`，由当前 token 返回当前用户 orgId；kube-agent 不应维护跨租户桶式用户搜索。
- 对 sysadmin 应保持独立授权模型：`sysadmin` 是全局身份标记，不是普通租户 orgId，也不能作为其他用户 orgId 解析失败的 fallback。

## 最终建议

M5.7 应采用“删除 getter、删除 fallback 返回、失败抛异常、登录 fail-safe”的严格方案：

- 不保留 `fallbackOrgId` 作为可信配置；
- 删除 `getFallbackOrgId()`；
- `resolveOrgId` 失败抛强类型异常；
- `AuthController` 捕获后拒绝创建 session；
- 只缓存可信成功 orgId；
- 用源码扫描契约测试防止 fallbackOrgId 回流。

这样才能与 M5.5/M5.6 的多租户边界治理保持一致，避免把认证/解析失败洗白为默认租户，彻底关闭跨租户默认组织风险。
