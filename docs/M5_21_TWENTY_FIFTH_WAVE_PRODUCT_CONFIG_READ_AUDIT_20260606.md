# M5.21-25 组织内产品配置敏感只读 Tool 审计

> 日期: 2026-06-06
> 结论: 本批仅接入组织内产品分类和组织内按量付费产品 GET 读取能力；产品分类保存/删除、折扣增删改和支付相关接口继续 HOLD。

## 专家会诊结论

- mature 前端 `src/api/cloud.js#listProductType` 调用 `GET /api/{organizationId}/product/type`，云产品和云服务器配置页使用该接口。
- mature 前端 `src/api/cloud.js#listPostPayProduct` 调用 `GET /api/{organizationId}/product/post-pay`，资源预设页在 product 模式下使用该接口。
- mature 后端 `ProductConfigController` 对应:
  - `GET /api/{organizationId}/product/type`
  - `GET /api/{organizationId}/product/post-pay`
- 这些接口不改变状态，但读取的是组织内产品分类、资源编码、软件组合和计费配置，不等同于公开商品目录。
- 因此 Agent 侧按 `SENSITIVE_READ + requiresConfirmation=true` 处理，并要求登录态 `AUTHENTICATED`。
- 外部 Agent 安全实践强调最小权限、敏感工具审批、输入白名单和 read/write 分离。本批只接入 GET，不接入保存、删除、支付或折扣写操作。

参考资料:
- OpenAI Agents SDK Human-in-the-loop: https://openai.github.io/openai-agents-python/human_in_the_loop/
- Microsoft Agent Framework Function Tools with Approval: https://learn.microsoft.com/en-us/agent-framework/tutorials/agents/function-tools-approvals
- OWASP LLM06 Excessive Agency: https://genai.owasp.org/llmrisk/llm062025-excessive-agency/

## 交付清单

- `ProductTypeListTool`
  - endpoint: `GET /api/{orgId}/product/type`
  - 固定空 query，避免把分页、关键字或折扣字段误传给分类接口。
- `PostPayProductListTool`
  - endpoint: `GET /api/{orgId}/product/post-pay`
  - query: 复用 `SaleProductQuerySupport.buildProductQuery`，只透传成熟产品筛选字段。
- `product_type_list` 与 `post_pay_product_list` 意图
  - 支持“组织内产品分类”“内部按量产品”“按量付费配置”等自然语言命中。

## HOLD 清单

- `POST /api/{organizationId}/product/type`
- `DELETE /api/{organizationId}/product/type/{productTypeId}`
- `GET /api/{organizationId}/product/discount/{productConfigId}`
- `DELETE /api/{organizationId}/product/discount/{productDiscountId}`
- `POST /api/{organizationId}/product/discount`
- `PUT /api/{organizationId}/product/discount`

折扣查询/修改涉及价格策略和账务结果，后续若接入，必须单独做财务/RBAC/审计专项，并按敏感读取或高风险配置变更处理。

## 验证

- 新增 `ProductConfigReadToolHttpContractTest`，覆盖可信 org、query 白名单、非法参数 fail-closed 和敏感读取元数据。
- 扩展 `M511AtlasToolHttpContractTest`，将组织内产品配置读取纳入 SENSITIVE_READ endpoint 精确白名单。
- 本批测试仅使用 mock HTTP client，未访问真实 `8100`。
