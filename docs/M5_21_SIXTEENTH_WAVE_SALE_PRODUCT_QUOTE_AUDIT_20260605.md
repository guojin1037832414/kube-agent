# M5.21-16 产品与租赁报价分析 Tool 审计

## 背景

AI 助手要帮助用户完成 kube-manager 中的部署和资源规划，不能只知道资源余量，还需要理解“有哪些产品规格、按量/预付费/服务器产品如何筛选、租赁某个服务器配置大概多少钱”。本批接入成熟 sale 域中的产品目录与订单金额预估 GET 接口，为后续部署前成本/规格建议提供数据基础。

本批只接入 GET；不访问真实 8100；测试全部使用 mock。

## 专家会诊结论

| 角色 | 结论 |
| --- | --- |
| kube-manager 后端专家 | `ProductPublicController` 提供公开产品分类、按量付费、预付费、服务器产品查询；`LeaseOrderController#count` 是成熟前端购买页使用的订单金额预估入口。 |
| vue-kube-manager 前端专家 | 购买页使用 `getOrderAmount` 先计算价格，再由用户提交订单；Agent 也应遵循“先分析/报价，再确认下单”的交互边界。 |
| Agent 安全专家 | 产品目录可作为 READ；金额预估只允许 `id/startTime/endTime` 三个白名单字段。创建订单、支付、状态变更、删除订单必须留在 HITL 写操作治理之后。 |
| 成本分析专家 | 产品目录 + 资源余量 + 计费配置 + 订单金额预估，可支撑部署前成本解释和资源规格推荐。 |

## 接入范围

| Tool | 方法 | 成熟接口 | 风险等级 | 权限 |
| --- | --- | --- | --- | --- |
| `public_product_type_list` | GET | `/api/public/product/type` | READ | PUBLIC |
| `public_post_pay_product_list` | GET | `/api/public/product/post-pay` | READ | PUBLIC |
| `public_pre_pay_product_list` | GET | `/api/public/product/pre-pay` | READ | PUBLIC |
| `public_server_product_list` | GET | `/api/public/product/server` | READ | PUBLIC |
| `lease_order_amount_estimate` | GET | `/api/{orgId}/lease/order/count` | READ | AUTHENTICATED |

## 安全约束

- 不接入 `POST /api/{orgId}/lease/order` 创建订单。
- 不接入订单状态变更、取消、删除、支付回调、支付二维码等接口。
- 不接入产品类型、折扣、服务器配置的保存/更新/删除。
- 不接入 `GET /api/{orgId}/discount/common/{billId}`，因为它暴露调试型 `policyExpression` 自由表达式。
- 产品查询只透传 `ProductConfigParamDTO` 白名单字段：`page/limit/productTypeCode/resourceCode/software/startTime/endTime/gpuModel/gpuPercentLimits`。
- 订单金额预估只透传 `id/startTime/endTime`，且 `id` 必须为正整数。
- `organizationId/orgId` 只来自可信 `UserPermissionContext`。

## 变更清单

- 新增 `SaleProductQuerySupport`
- 新增 `PublicProductTypeListTool`
- 新增 `PublicPostPayProductListTool`
- 新增 `PublicPrePayProductListTool`
- 新增 `PublicServerProductListTool`
- 新增 `LeaseOrderAmountEstimateTool`
- 新增 `SaleProductAnalysisToolHttpContractTest`
- 更新 `M511AtlasToolHttpContractTest` READ endpoint 白名单
- 更新 `intents.yml` 新增 5 个产品与报价分析意图
- 更新 `CHANGELOG.md`

## 验证计划

- 定向测试：`SaleProductAnalysisToolHttpContractTest,M511AtlasToolHttpContractTest,ToolRegistryPermissionTest`
- 主回归：覆盖 ReAct、HITL、SafeToolExecutor、高风险写操作、EasyFlow、财务分析、行业应用、资源监控、权限、参数契约和 HTTP 安全边界。
