# M5.85-32 kube-manager 登录型 READ smoke

## 交付内容

- `KubeManagerReadOnlySmokeTest` 保留默认跳过策略，只有显式开启 `atlas.kube-manager.smoke.enabled=true` 或 `ATLAS_KUBE_MANAGER_SMOKE_ENABLED=true` 才访问真实 `8100`。
- 新增登录型 smoke：测试从 `ATLAS_KUBE_MANAGER_SMOKE_USERNAME` / `ATLAS_KUBE_MANAGER_SMOKE_PASSWORD` 或对应 system property 读取凭据，在测试进程内调用 kube-manager `/api/login` 获取临时 token。
- 登录成功后复用生产同款 `KubeManagerHttpClient.resolveOrgId(username, token)`，用当前用户 token 逐桶读取 `/api/{orgId}/user`，从 kube-manager 响应中确认服务端可信 orgId。
- token/orgId 直传模式仍可用，但必须成对提供；只提供其中一个会 fail-fast，避免旧 token、手填 orgId 或混合登录态形成不可审计身份。
- 业务链路仍只执行 `NodeQueryTool -> KubeManagerHttpClient -> GET /api/{orgId}/node`，并继续传入伪造 `organizationId/orgId/token` 参数证明 Tool 使用 ThreadLocal 可信上下文，而不是相信调用参数。

## 安全边界

- 登录表单里的 `organizationId` 只是 kube-manager 登录接口要求的参数，不是 kube-agent 的可信租户来源。
- 密码和 token 只存在于当前测试进程的 system property / 环境变量 / 局部变量中，不写入代码、文档、恢复记忆、git 或测试失败消息。
- 源码级 guard 只允许一个认证 bootstrap POST `/api/login`；业务 smoke 仍禁止 POST/PUT/PATCH/DELETE、MCP runtime、HITL 触发、audit/memory 写入、retrieval/vector runtime、A2A handoff 和二期 NIM/HPC/Slurm/BCM Tool。
- 本切片不改变生产登录、SessionStore、AuthController、SafeToolExecutor、ToolRegistry 或 kube-manager 写入策略。

## 验证

- `mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" test`
- 登录型真实 smoke：通过环境变量注入 username/password 后运行
  `mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" "-Datlas.kube-manager.smoke.enabled=true" "-Datlas.kube-manager.smoke.base-url=http://localhost:8100" test`
- `mvn -q "-DskipTests" validate`
- `git diff --check`

## 下一步

- 真实 kube-manager READ E2E 的第一条链路已经跑通，下一步优先回到 Eval trace evidence：推进 reviewed redacted fixture 文件、目录 patch review 和可审计 gate-bundle 证据。
- 如需扩展更多 kube-manager READ Tool 的真实 smoke，必须逐个增加 GET/READ/no-HITL 元数据守卫、参数白名单和真实后端只读验证，不能把本测试顺手扩成业务写联调。
