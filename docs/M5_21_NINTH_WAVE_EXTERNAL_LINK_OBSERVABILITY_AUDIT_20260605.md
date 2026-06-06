# M5.21-9 第九批外部链接与可观测入口审计

日期: 2026-06-05

## 专家会诊结论

本批原计划审计网络、Service、Ingress、端口暴露类写操作。对照成熟 `vue-kube-manager` 与 `kube-manager` 后，结论如下：

- 后端契约专家: 当前成熟前端没有清晰的 Service/Ingress/DNS 写操作封装；不能为了覆盖“网络操作”而让 Agent 猜测写接口。
- 可观测专家: 成熟系统已有一组外部链接接口，包含 Grafana 集群、节点、Pod、容器、PyTorch Job 日志等入口，属于 AI 数据分析和排障体验的重要补齐项。
- 安全专家: Kubernetes Dashboard 链接在成熟后端标注 `SYS_ADMIN_ONLY`。它虽然是 GET，但会暴露运维入口，必须拆成 `ADMIN_ONLY + SENSITIVE_READ + HITL`。
- 测试专家: 历史 `ExternalLinkListTool` 调用 `/api/{orgId}/external-link`，成熟后端没有该接口，必须改掉并用 mock 锁定真实路径。

## 成熟系统证据

| 能力 | 成熟前端证据 | 成熟后端证据 | Agent 接入 |
| --- | --- | --- | --- |
| Grafana 集群概览 | `vue-kube-manager/src/api/external-link.js#getGrafanaClusterUrl` | `ExternalLinkController#getGrafanaClusterUrl` `GET /api/external-link/grafana/cluster` | `ExternalLinkListTool(category=cluster/all)` |
| Grafana 节点监控 | `getGrafanaNodeUrl` | `GET /api/external-link/grafana/node` | `ExternalLinkListTool(category=node/all)` |
| Grafana Pod 列表 | `getGrafanaPodTableUrl` | `GET /api/external-link/grafana/pod-table` | `ExternalLinkListTool(category=pod-table/all)` |
| Grafana 容器曲线 | `getGrafanaContainerLineUrl` | `GET /api/external-link/grafana/container-line` | `ExternalLinkListTool(category=container-line/all)` |
| Grafana 示例/汇总 | `getGrafanaExampleUrl/getGrafanaSummaryUrl` | `GET /api/external-link/grafana/example`、`/summary` | `ExternalLinkListTool` |
| PyTorch Job 日志 | `getGrafanaPodLogUrl` | `GET /api/external-link/grafana/pytorch-job-log` | `ExternalLinkListTool(category=pytorch-job-log/all)` |
| Kubernetes Dashboard | `getKubernetesDashboardUrl` | `GET /api/external-link/kubernetes/dashboard`，`SYS_ADMIN_ONLY` | `KubernetesDashboardLinkTool` |

## 代码变更

| 文件 | 变更 |
| --- | --- |
| `ExternalLinkListTool.java` | 从不存在的 org 级列表接口改为成熟 Grafana 单项链接聚合；新增 `category` 参数；未知类别 fail-closed |
| `KubernetesDashboardLinkTool.java` | 新增管理员敏感读取 Tool |
| `ExternalLinkToolHttpContractTest.java` | 新增外链 HTTP 契约测试 |
| `ListToolParameterPassThroughContractTest.java` | 移除 external link 的分页列表契约 |
| `ListToolParameterSpecContractTest.java` | 移除 external link 的标准分页 schema 断言 |
| `M511AtlasToolHttpContractTest.java` | 将 Kubernetes Dashboard 加入敏感读取 endpoint 白名单 |
| `intents.yml` | 新增 `kubernetes_dashboard_link` 意图 |
| `CHANGELOG.md` | 新增 M5.21-9 记录 |

## 安全收口

- `external_link_list` 只暴露普通 Grafana 链接，不包含 Kubernetes Dashboard。
- `kubernetes_dashboard_link` 继承成熟后端管理员隔离语义，并要求 HITL。
- 未发现成熟系统中可以直接对齐的 Service/Ingress/DNS 写操作，因此本批不创建猜测型写 Tool。
- 所有验证均使用 mock，没有访问真实 8100 写接口。

## 验证结果

已通过定向回归：

```bash
mvn -q "-Dtest=ExternalLinkToolHttpContractTest,M511AtlasToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,ToolRegistryPermissionTest" test
```

观察结果：

- Tool 注册数量增至 118。
- intent 加载数量增至 121。
- `ADMIN_ONLY` Tool 数量增至 12，符合新增 Dashboard 敏感读取 Tool 的预期。

## 后续建议

- 下一批优先补齐“可观测数据分析”能力：资源趋势、Pod/Job 日志摘要、Grafana 链接与实际资源上下文联动。
- 对网络写操作继续保持证据优先，只有成熟前端和后端均存在明确接口时才接入；否则应先在 kube-manager 中设计真实 API。
- 可以为 Kubernetes Dashboard 增加前端审批文案，明确“该链接可能进入集群运维界面”。
