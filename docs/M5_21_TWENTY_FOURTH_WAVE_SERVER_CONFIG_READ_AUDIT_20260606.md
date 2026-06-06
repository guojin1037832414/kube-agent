# M5.21-24 服务器产品配置敏感只读 Tool 审计

> 日期: 2026-06-06
> 结论: 本批仅接入组织内服务器产品配置 GET 读取能力；服务器配置保存和删除继续 HOLD。

## 专家会诊结论

- mature 前端 `src/api/cloud.js#listServerConfig` 调用 `GET /api/{organizationId}/server`，云服务器配置页使用该数据展示内部服务器规格。
- mature 后端 `ServerConfigController#listServerConfig` 对应 `GET /api/{organizationId}/server`，参数类型为 `ProductConfigParamDTO`。
- 该接口不改变 kube-manager/Kubernetes 状态，但会暴露组织内服务器规格、库存、价格、软件和推荐配置，不等同于公开商品目录。
- 因此 Agent 侧按 `SENSITIVE_READ + requiresConfirmation=true` 处理，并要求登录态 `AUTHENTICATED`。
- 外部 Agent 安全实践强调最小权限、敏感工具审批、输入白名单和 read/write 分离。本批只接入 GET，不接入保存或删除。

参考资料:
- OpenAI Agents SDK Human-in-the-loop: https://openai.github.io/openai-agents-python/human_in_the_loop/
- Microsoft Agent Framework Function Tools with Approval: https://learn.microsoft.com/en-us/agent-framework/tutorials/agents/function-tools-approvals
- OWASP LLM06 Excessive Agency: https://genai.owasp.org/llmrisk/llm062025-excessive-agency/

## 交付清单

- `ServerConfigListTool`
  - endpoint: `GET /api/{orgId}/server`
  - 风险: `SENSITIVE_READ`
  - 权限: `AUTHENTICATED`
  - query: 复用 `SaleProductQuerySupport.buildProductQuery`，只透传成熟产品筛选字段。
- `server_config_list` 意图
  - 支持“服务器配置”“内部服务器规格”“server config”等自然语言命中。
- `ServerConfigReadToolHttpContractTest`
  - 校验可信组织上下文优先，忽略伪造 `organizationId/orgId`。
  - 校验 query 只允许 `page/limit/productTypeCode/resourceCode/software/startTime/endTime/gpuModel/gpuPercentLimits`。
  - 校验非法分页在 HTTP 调用前 fail-closed。

## HOLD 清单

- `POST /api/{organizationId}/server`
- `DELETE /api/{organizationId}/server/{id}`

这些接口会改变服务器产品配置，后续若接入，必须按高风险配置变更处理，至少包含 HITL 确认、目标复述、DTO 白名单、管理员权限和审计日志。

## 验证

- 新增 `ServerConfigReadToolHttpContractTest`，覆盖可信 org、query 白名单、非法参数 fail-closed 和敏感读取元数据。
- 扩展 `M511AtlasToolHttpContractTest`，将 `server_config_list` 纳入 SENSITIVE_READ endpoint 精确白名单。
- 本批测试仅使用 mock HTTP client，未访问真实 `8100`。
