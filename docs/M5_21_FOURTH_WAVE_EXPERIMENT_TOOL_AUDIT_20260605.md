# M5.21 第四批 Experiment 高风险 Tool 审计

> 日期：2026-06-05
> 范围：实验实例启动、停止、删除
> 约束：只做源码对照和 mock 契约测试，不调用线上 kube-manager 写接口。

## 专家会诊结论

本轮继续沿用高风险 Tool 的 HITL 审计原则：审批页和日志必须能展示真实 Tool、真实参数和真实 kube-manager 动作。实验实例属于会改变运行状态的资源操作，因此不能使用历史规划里的猜测路径。

成熟前端 `vue-kube-manager/src/api/experiment.js` 明确暴露：

- `PUT /api/{organizationId}/experiment/instance/start/{id}`
- `PUT /api/{organizationId}/experiment/instance/restart/{id}`
- `PUT /api/{organizationId}/experiment/instance/shutdown/{id}`

当前本地成熟后端源码未检索到可审计的 experiment controller。基于“有证据才放行”的原则，本轮只对齐成熟前端已存在的 start/shutdown 动作；删除实例没有成熟前端调用，也没有后端 controller 证据，因此改为 fail-closed。

## 接口矩阵

| Tool | 本轮处理 | 后端动作 | 安全策略 |
| --- | --- | --- | --- |
| `experiment_start` | 改为路径变量 PUT | `PUT /api/{orgId}/experiment/instance/start/{id}` | ACTION + AUTHENTICATED + HITL |
| `experiment_instance_stop` | 对齐成熟前端 shutdown 语义 | `PUT /api/{orgId}/experiment/instance/shutdown/{id}` | ACTION + AUTHENTICATED + HITL |
| `experiment_instance_delete` | 改为 fail-closed placeholder | 不发送 HTTP 请求 | PLACEHOLDER + ADMIN_ONLY + HITL |

## 关键安全收口

- `experiment_start` 不再把 `id` 放入 body，避免审批展示和真实接口路径不一致。
- `experiment_instance_stop` 保留历史意图名，但代码注释明确真实语义是 shutdown，减少后续维护者误接 `stop` 猜测路径。
- `experiment_instance_delete` 返回 `UNSUPPORTED_BACKEND_OPERATION`，要求先在 kube-manager 增加并审计真实删除 API 后再打开。
- `KubeManagerHttpClient.put` 与 POST/PATCH/DELETE 一样强制真实用户 Token，缺少用户上下文时不会降级到 sysadmin。

## 验证

- `mvn -q "-Dtest=HighRiskMutationToolHttpContractTest,M511AtlasToolHttpContractTest" test` 通过。
- 本轮没有访问真实 8100 写接口。

## 后续验收项

1. 继续审计 `user_create/user_delete`：成熟前端和成熟后端存在 `/api/{organizationId}/user` 与历史 `/api/account/...` 差异，需要确认线上实际路由和权限策略。
2. 若后续需要恢复实验删除能力，必须先在 kube-manager 中找到或新增 controller，并补齐端点白名单、mock 契约测试和 HITL 风险说明。
3. 可单独接入 `experiment_instance_restart`，但同样需要确认成熟后端是否真实支持 `PUT /experiment/instance/restart/{id}`。
