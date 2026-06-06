# M5.21-8 第八批 Helm/Compose 写操作补齐审计

日期: 2026-06-05

## 专家会诊结论

本批目标是继续向“通过 AI 助手覆盖 kube-manager 操作”推进，但仍保持高风险写操作的安全边界：

- 后端契约专家: 只接入成熟 `kube-manager` controller 已公开且 `vue-kube-manager` 已调用的接口。Helm install/upgrade/rollback、repo update/remove 与 Compose delete/update 均有明确证据。
- 安全专家: 所有新增写 Tool 必须 `requiresConfirmation=true`。Helm repo update/remove 在后端是 `SYS_ADMIN_ONLY`，Agent 侧同步设为 `ADMIN_ONLY`。
- Agent 体验专家: 每个写 Tool 必须有参数 schema，审批前能展示真实 release、chart、repoName、composeId 等目标字段。
- 测试专家: 禁止调用真实 8100 写接口；用 mock 锁定 HTTP method/path/query/body，并检查 DTO 白名单不透传服务端上下文。

## 成熟系统证据

| 能力 | 成熟前端证据 | 成熟后端证据 | Agent 接入 |
| --- | --- | --- | --- |
| Helm install | `vue-kube-manager/src/api/helm.js` `helmInstall(release, chart, data)` | `HelmController#helmInstall` `POST /releases/{release}` + `chart` query + `InstallBodyDTO` | `HelmReleaseInstallTool` |
| Helm upgrade | `helmUpgrade(release, chart, data)` | `HelmController#helmUpgrade` `PUT /release/{release}/upgrade` + `chart` query + `UpgradeBodyDTO` | `HelmReleaseUpgradeTool` |
| Helm rollback | `helmRollback(release, revision, data)` | `HelmController#helmRollback` `PUT /release/{release}/rollback/{version}` + `RollbackBodyDTO` | `HelmReleaseRollbackTool` |
| Helm repo update | `helmRepoUpdate()` | `HelmController#helmRepoUpdate` `PUT /repositories`，`SYS_ADMIN_ONLY` | `HelmRepoUpdateTool` |
| Helm repo remove | `helmRepoRemove(repoName)` | `HelmController#removeRepo` `DELETE /repositories/{repoName}`，`SYS_ADMIN_ONLY` | `HelmRepoRemoveTool` |
| Compose delete | `deleteComposeDeploy(composeId)` | `ComposeController#deleteCompose` `DELETE /compose/{composeId}` | `ComposeDeployDeleteTool` |
| Compose update | 成熟后端已有，前端当前未显式封装 | `ComposeController#updateCompose` `PUT /compose/{composeId}`，说明当前仅支持更新组合名称 | `ComposeDeployUpdateTool`，仅发送 `composeName` |

## 代码变更

| 文件 | 变更 |
| --- | --- |
| `KubeManagerHttpClient.java` | 新增 `post(path, query, body)` 与 `put(path, query, body)`，支持 Helm install/upgrade 的 query + body 契约 |
| `HelmReleaseBodyBuilder.java` | 新增 Helm Release 写操作 DTO 白名单构造器 |
| `HelmReleaseInstallTool.java` | 新增 Helm install Tool |
| `HelmReleaseUpgradeTool.java` | 新增 Helm upgrade Tool |
| `HelmReleaseRollbackTool.java` | 新增 Helm rollback Tool |
| `HelmRepoUpdateTool.java` | 新增 Helm repo update Tool，`ADMIN_ONLY` |
| `HelmRepoRemoveTool.java` | 新增 Helm repo remove Tool，`ADMIN_ONLY` |
| `ComposeDeployDeleteTool.java` | 新增 Compose delete Tool |
| `ComposeDeployUpdateTool.java` | 新增 Compose update Tool，仅改名 |
| `intents.yml` | 新增 7 个写操作意图 |
| `M511AtlasToolHttpContractTest.java` | 高风险 endpoint 白名单覆盖新增写 Tool |
| `HighRiskMutationToolHttpContractTest.java` | 新增 mock HTTP 契约与 schema 契约 |

## 安全收口

- Helm install/upgrade/rollback 的 body 由 `HelmReleaseBodyBuilder` 构造，只保留成熟 DTO 字段。
- `organizationId/orgId/token/sessionId/approved` 等服务端上下文不会进入业务 body。
- Compose update 明确限定为改名，不允许通过该 Tool 传 `contentYaml` 修改内部 Deployment。
- Helm repo update/remove 是系统级配置变更，必须管理员权限和人工确认。
- 本批所有验证都是 mock，不访问真实 kube-manager 写接口。

## 验证结果

已通过针对性回归：

```bash
mvn -q "-Dtest=HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M4Px4ToolParameterAliasContractTest,ToolRegistryPermissionTest" test
```

观察结果：

- Tool 注册数量从 110 增至 117。
- intents 加载数量从 113 增至 120。
- Spring 权限分布更新为 `PUBLIC=89, AUTHENTICATED=17, ADMIN_ONLY=11`，与本批新增 5 个普通写操作、2 个管理员写操作一致。

## 后续建议

- 下一批继续审计网络、端口、服务暴露、Ingress、DNS 等可能影响线上流量的写 Tool。
- 对 Helm install/upgrade 增加“先查 chart values 再安装/升级”的 ReAct 规划提示，避免用户只给 chart 名称时盲目部署默认 values。
- 对 Compose 删除增加“先 list/detail 再删”的规划提示，减少按自然语言名称误删的风险。
