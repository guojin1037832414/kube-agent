# kube-agent M5.3 GLOBAL/PUBLIC/NO_ORG 参数契约入口审计 — 20260522

> 生成时间: 2026-05-22 15:55 CST  
> 审计人: Hermes  
> 上游阶段: M5.2 RBAC 管理面 HOLD 保护  
> 目标: 为下一阶段 **M5.3 GLOBAL/PUBLIC/NO_ORG 专项** 建立候选清单、风险边界与执行入口。

---

## 一、上游阶段结论

- M5.1：账务域只开放低风险 `CurrencyQueryListTool`；订单与配额审批保持 HOLD。
- M5.2：RBAC 管理面 6 个 Tool 全部 HOLD，不开放 `page/limit/keyword`。
- 当前策略：越靠近全局、公共、跨组织、首页聚合、系统模型等接口，越不能机械铺开普通列表参数契约。

---

## 二、M5.3 初始候选范围

> 该清单来自 M4 收口审计中剩余 GLOBAL/PUBLIC/NO_ORG 类固定分页候选，M5.3 启动时必须重新读取源码确认。

| 分组 | 候选 Tool | 初始风险 | 初始建议 |
|------|-----------|----------|----------|
| GPU 全局视图 | `GpuGlobalListTool` | 中高 | 先审计后决定 |
| 首页行业/模型/NIM | `HomeIndustryClassListTool`、`HomeIndustryListTool`、`HomeModelListTool`、`HomeNimListTool`、`HomeRepositoryListTool` | 中高 | 不默认开放 keyword |
| 系统模型/公共资源 | `SysModelListTool` | 中高 | 先确认是否跨组织/公共市场 |

---

## 三、M5.3 必答问题

1. **是否跨组织**：接口是否无 `{orgId}` 或返回全局数据。
2. **是否首页公共展示**：如果本来就是首页公共资源，是否允许分页但禁止 keyword。
3. **keyword 搜索字段**：是否搜索模型名、镜像、仓库、供应商、行业标签等可能造成探测的字段。
4. **结果是否含敏感字段**：是否返回内部 ID、镜像地址、仓库路径、供应商信息、计费字段。
5. **权限注解是否一致**：`PUBLIC` 是否符合产品语义，还是仅因早期默认注解遗留。
6. **参数契约形态**：是否可使用标准 `page/limit/keyword`，还是需要只开放 page/limit 或完全 HOLD。

---

## 四、建议路线

### M5.3.A：只读恢复与候选确认

- 读取上述候选 Tool 源码。
- 对比前端首页/公共市场调用方式。
- 标记真实 API 路径、是否带 orgId、当前权限注解、当前固定分页参数。

### M5.3.B：专家会诊

- 后端/API：确认参数语义与真实字段。
- 安全/隐私：确认跨组织枚举风险。
- 产品/前端：确认是否本来就是公开展示能力。
- 测试架构：决定开放测试还是 HOLD 测试。

### M5.3.C：小样本实验

- 若存在明确低风险公共目录类 Tool，优先只开放 `page/limit`，不开放 `keyword`。
- 若无法证明低风险，则先纳入 HOLD 测试。
- 每一步继续遵循：红灯 → 绿灯 → 邻近回归 → 全量测试 → Review → 文档 → 双推。

---

## 五、质量门禁

- TDD 红灯/突变红灯证据。
- 定向契约测试通过。
- 全量 `/usr/share/maven/bin/mvn test` 通过。
- `git diff --check` 通过。
- 敏感信息扫描 0。
- 独立 pre-commit Review PASS。
- `CHANGELOG.md`、`docs/REVIEW_LOG.md` 更新并备份。
- 提交并双推 GitLab/GitHub。

---

## 六、当前状态

### ✅ PASS

- M5.3 入口已建立。
- 候选风险边界已初步定义。

### ⚠️ HOLD

- M5.3 尚未开始源码确认和专家会诊。
- 不得直接把 GLOBAL/PUBLIC/NO_ORG 候选加入标准列表参数契约。

### ❌ FAIL

- 无当前阻断项。
---

## 七、M5.3 执行结论（2026-05-22）

### 专家会诊最终分层

| 分组 | Tool | 决策 | 原因 |
|------|------|------|------|
| 首页公共展示 | `HomeIndustryClassListTool`、`HomeIndustryListTool`、`HomeModelListTool`、`HomeNimListTool`、`HomeRepositoryListTool` | ✅ 开放 `page/limit-only` | `/api/public/home-info/*` 属公开首页展示语义，可翻页浏览；但禁止搜索探测 |
| 全局 GPU | `GpuGlobalListTool` | ⚠️ full HOLD | `/api/gpu` 是全局/跨组织资源入口，分页会扩大枚举面 |
| 全局模型 | `SysModelListTool` | ⚠️ full HOLD | `/api/model` 不等同首页公开模型展示，可能包含系统/全局模型元数据 |

### 已落地安全边界

- home-info 5 个 Tool 只暴露 `page`、`limit`。
- 不暴露、不透传 `keyword/name/search/kw`。
- 不透传 `orgId/organizationId`，保持首页 PUBLIC no-org 语义。
- `limit` 最大值为 100，超过即返回 `VALUE_OUT_OF_RANGE`。
- `GpuGlobalListTool` 与 `SysModelListTool` 保持不暴露 `page/limit/keyword`。

### 验证结果

- TDD 红灯有效：新增测试初次运行 4 failures。
- 定向绿灯：7 tests 通过。
- 邻近回归：12 tests 通过。
- 全量测试：149 tests, 0 failures, BUILD SUCCESS。
- `git diff --check`：通过。
- 本次 diff 敏感扫描：`DIFF_SECRET_SCAN_FINDINGS 0`。
- 独立 pre-commit Review：PASS。

### 后续 HOLD

- `/api/gpu` 与 `/api/model` 后续若要开放分页，必须先完成跨组织可见性、字段脱敏、限流、审计日志、权限注解合理性确认。
- 所有 PUBLIC/no-org 接口后续不得机械套用普通 `page/limit/keyword` 标准三件套。
