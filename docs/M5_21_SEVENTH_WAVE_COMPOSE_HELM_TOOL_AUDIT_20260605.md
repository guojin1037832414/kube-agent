# M5.21-7 第七批 Compose/Helm 高风险 Tool 审计

日期: 2026-06-05

## 专家会诊结论

本批继续采用“成熟系统证据优先 + Agent 安全边界保守收口”的会诊方式：

- 后端契约专家: Tool 的真实 HTTP method/path/body 必须以成熟 `kube-manager` controller 和 `vue-kube-manager` API 封装为准，不能让 Agent 根据历史占位路径猜测写接口。
- 安全专家: Compose/Helm 都属于会改变集群或系统配置的高风险操作，必须保留 HITL；其中 Helm 仓库新增在成熟后端是系统管理员隔离策略，因此 Agent 侧也必须是 `ADMIN_ONLY`。
- Agent 体验专家: 高风险审批需要展示“真实目标”和“真实 DTO 字段”，所以必须补齐参数 schema，并兼容历史自然语言别名，减少用户补参成本。
- 测试专家: 不允许调用真实 8100 写接口；用 mock 锁定 HTTP method/path/body/query，并用白名单契约防止后续回退到旧路径。

## 成熟系统证据

| 能力 | 成熟前端证据 | 成熟后端证据 | 本批结论 |
| --- | --- | --- | --- |
| Compose 部署 | `F:\gitProject\vue-kube-manager\src\api\compose.js` 的 `createComposeDeploy(data)` 调用 `POST /api/{organizationId}/compose/deploy` | `F:\gitProject\kube-manager\...\ComposeController.java` 的 `@PostMapping("/deploy")` 接收 `ComposeDeployDTO` | Tool 改为 `/compose/deploy`，body 使用 `contentYaml/composeName/resourceList/sizeList` |
| Compose 删除 | 成熟前端 `deleteComposeDeploy(composeId)` 调用 `DELETE /api/{organizationId}/compose/{composeId}` | `ComposeController` 暴露 `@DeleteMapping("/{composeId}")` | 本批只处理创建部署，删除留在后续批次审计 |
| Helm 仓库新增 | `F:\gitProject\vue-kube-manager\src\api\helm.js` 的 `helmRepoAdd(data)` 调用 `POST /api/{organizationId}/helm/repositories` | `HelmController#addRepo` 使用 `@PostMapping("/repositories")`，并标注 `SYS_ADMIN_ONLY` | Tool 路径改为 `/helm/repositories`，权限收紧为 `ADMIN_ONLY` |
| Helm Release 卸载 | 成熟前端和后端均使用 `DELETE /api/{organizationId}/helm/releases/{releaseName}` | `HelmController` 使用 path variable `releaseName` | 路径保持不变，补齐 `releaseName` 参数 schema |

## 代码变更

| 文件 | 变更 |
| --- | --- |
| `src/main/java/com/atlas/tool/impl/ComposeDeployCreateTool.java` | endpoint 从 `/compose` 改为 `/compose/deploy`；构造 `ComposeDeployDTO` 白名单 body；兼容 `name/yaml` 别名；缺少 `composeName/contentYaml` 时 fail-closed |
| `src/main/java/com/atlas/tool/impl/HelmRepoAddTool.java` | endpoint 统一为 `/helm/repositories`；权限从 `AUTHENTICATED` 收紧为 `ADMIN_ONLY`；body 只保留 `name/url` |
| `src/main/java/com/atlas/tool/impl/HelmReleaseDeleteTool.java` | 补齐 `releaseName` 参数 schema 和自然语言别名 `release/name` |
| `src/test/java/com/atlas/tool/impl/HighRiskMutationToolHttpContractTest.java` | 新增 Compose/Helm mock HTTP 契约和 schema 契约 |
| `src/test/java/com/atlas/contract/M511AtlasToolHttpContractTest.java` | 更新 Compose 高风险 endpoint 白名单 |
| `CHANGELOG.md` | 新增 M5.21-7 批次记录 |

## 安全收口

- 本批不调用真实 kube-manager 写接口，所有新增验证均为 mock。
- Compose 创建部署只发送后端 DTO 需要的字段，不透传 `organizationId/orgId/token/sessionId/approved`。
- Helm 仓库新增只发送 `name/url`，并按照后端系统管理员策略在 Agent 侧收紧为 `ADMIN_ONLY`。
- Helm Release 卸载继续保持 `ADMIN_ONLY + requiresConfirmation=true`，审批展示会包含明确的 `releaseName`。
- Compose 缺少核心字段时返回 `MISSING_COMPOSE_DEPLOY_PARAMS`，避免把半成品 YAML 部署请求交给真实后端。

## 验证结果

已通过针对性回归：

```bash
mvn -q "-Dtest=HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest,M4Px4ToolParameterAliasContractTest,ToolRegistryPermissionTest" test
```

说明:

- `ToolRegistryPermissionTest` 显示注册表权限分布已随 Helm repo add 收紧而更新，未破坏现有权限链路。
- 测试过程中曾尝试下载本地缺失的 embedding 模型并超时，但 Spring 已降级到可用路径，测试最终通过；未影响本批 Tool 契约验证。

## 后续建议

- 下一批优先审计 Compose 删除、Helm install/upgrade/list 等未完全覆盖的 Helm/Compose 链路。
- 在高风险 Tool 审计完成后，增加一个总表，按 intent 维度标记“已对齐成熟接口 / fail-closed / 待后端补真实 API”。
- 对所有 `ADMIN_ONLY` Tool 补一组前端审批文案示例，确保用户能在 HITL 弹窗里看懂真实变更范围。
