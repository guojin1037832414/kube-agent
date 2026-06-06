# M5.21 第二批高风险 Tool 接口审计

> 日期：2026-06-05
> 范围：继续审计 kube-agent 中会改变 kube-manager 线上状态的 Tool，重点对齐训练任务动作与未被后端支持的部署重启动作。
> 约束：本批只使用 mock 测试和源码对照，没有调用真实线上更新、删除、终止接口。

## 专家会诊结论

本轮继续参考 OpenAI Agents SDK Human-in-the-loop 与 LangGraph review tool calls 的实践：高风险 Tool 必须在执行前暴露真实工具名、参数和后端动作，允许人工确认、拒绝或补充参数。对 kube-manager 这种已上线系统，还需要额外遵守一个更强的工程原则：Tool 的 HTTP 方法、路径变量和 body 必须以成熟前后端代码为准；没有成熟后端接口时，必须 fail-closed。

## 接口矩阵

| Tool | 成熟项目依据 | 本批处理 | 安全策略 |
| --- | --- | --- | --- |
| `pytorch_job_submit` | `F:\gitProject\vue-kube-manager\src\api\pytorch-job.js` 与 `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\client\controller\PyTorchJobController.java` | 改为 `POST /api/{orgId}/pytorch-job/submit/{pyTorchJobId}`，空 body | ACTION + HITL |
| `mpi_job_abort` | `F:\gitProject\vue-kube-manager\src\api\mpi-job.js` 与 `F:\gitProject\kube-manager\src\main\java\com\cgm\kube\client\controller\MpiJobController.java` | 保留历史 Tool 名，底层改为 `POST /api/{orgId}/mpi-job/{jobId}`，语义说明为终止运行中的任务 | ACTION + HITL |
| `deploy_restart` | `DeploymentController` 当前只有 create/update/scale/delete，没有 restart | 改为 `UNSUPPORTED_BACKEND_OPERATION` fail-closed，不再调用猜测路径 `/deployment/{target}/restart` | ADMIN_ONLY + HITL + fail-closed |

## 代码 Review 结论

- `PytorchJobSubmitTool` 已补充结构化参数说明，审批界面可以展示明确的 PyTorch Job ID。
- `MpiJobAbortTool` 已补充中文注释，明确历史 “abort” 意图在成熟后端里的真实语义是 stop/终止运行，并且数据库记录保留。
- `DeployRestartTool` 不再向线上发送不存在的重启请求，避免 agent 给用户制造“已执行重启”的错觉。
- `HighRiskMutationToolHttpContractTest` 新增 mock 契约测试，锁定 PyTorch 提交、MPI 终止和部署重启 fail-closed 行为。

## 后续验收项

1. 继续扫描剩余 CREATE/UPDATE/DELETE/ACTION Tool，优先处理 image、storage、user、experiment 相关动作。
2. 对所有高风险 Tool 补齐 `getParameterSpecs()`，让 ReAct 规划、Tool schema 和 HITL 审批展示使用同一套参数契约。
3. 若未来要支持实例重启，需要先在 kube-manager 增加真实 controller/service API，再补前端确认流、权限校验、审计日志和 mock HTTP 契约测试。
