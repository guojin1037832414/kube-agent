# M5.21-19 虚拟机只读 Tool 审计

> 日期: 2026-06-06
> 范围: mature kube-manager `VirtualMachineController`
> 结论: 本批只接入 VM 列表和详情，启动/停止/删除/创建暂缓到高风险 HITL。

## 专家会诊结论

- mature 后端提供 `GET /api/{organizationId}/virtual-machine` 与 `GET /api/{organizationId}/virtual-machine/{name}` 两个只读入口，可为 AI 助手补齐虚拟机状态分析的数据面。
- mature 后端同时提供 `POST` 创建、`PUT /start`、`PUT /stop`、`DELETE` 删除，这些会改变集群资源状态，必须等独立高风险 Tool、人审确认和审计日志设计完备后再接入。
- 外部 Agent 安全实践强调最小权限、read/write 分离、敏感动作人工审批和工具权限清单。本批按普通 READ 只读接入，不要求确认，但仍要求登录态和可信 orgId。

参考资料:

- OpenAI Agents SDK Human-in-the-loop: https://openai.github.io/openai-agents-python/human_in_the_loop/
- Microsoft Zero Trust - Reduce autonomous agentic AI risk: https://learn.microsoft.com/en-us/security/zero-trust/sfi/manage-agentic-risk
- Microsoft Zero Trust - Secure autonomous agentic AI systems: https://learn.microsoft.com/en-us/security/zero-trust/sfi/secure-agentic-systems
- OWASP Agentic AI / Excessive Agency 相关实践: https://owasp.org/www-community/attacks/Lies_in_the_Loop

## 本批交付

- 新增 `VirtualMachineListTool`:
  - `GET /api/{orgId}/virtual-machine`
  - 权限: `AUTHENTICATED`
  - 操作类型: `READ`
- 新增 `VirtualMachineDetailTool`:
  - `GET /api/{orgId}/virtual-machine/{name}`
  - 权限: `AUTHENTICATED`
  - 操作类型: `READ`
- 新增 `VirtualMachineQuerySupport`:
  - VM 名称最大 253 字符。
  - 仅允许字母、数字、点、下划线、短横线。
  - URL path segment 编码后再拼入路径。
- 新增 `VirtualMachineReadToolHttpContractTest`:
  - 验证可信 orgId 不被 LLM 参数覆盖。
  - 验证详情路径使用安全 VM 名称。
  - 验证非法路径片段 fail-closed，且不会调用 HTTP client。
  - 验证两个 Tool 都是普通 READ 且不触发 HITL。

## 安全边界

- 没有调用真实 8100。
- 没有接入 VM 创建、启动、停止、删除。
- 不透传 `organizationId/orgId/token` 等伪造字段到 kube-manager。
- VM 名称进入 path 前必须校验，拒绝 `../`、`/`、`\`、`?`、`#` 等路径或查询注入字符。

## HOLD 清单

- `POST /api/{orgId}/virtual-machine`
- `PUT /api/{orgId}/virtual-machine/{name}/start`
- `PUT /api/{orgId}/virtual-machine/{name}/stop`
- `DELETE /api/{orgId}/virtual-machine/{name}`

上述接口后续应归入高风险动作 Tool，至少需要 `requiresConfirmation=true`、操作审计、目标 VM 名称强校验和二次确认文案。
