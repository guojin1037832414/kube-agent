# kube-agent M5.9 审计清单

> 生成时间: 2026-05-23 16:31:16 CST
> 审计人: Hermes
> 审计范围: M5.9 HTTP 出口与 fallback token 源码契约治理
> 核心约束: 避免影响 kube-manager 数据；删除/修改类不做真实操作，只跑通源码契约与单元逻辑。

## 一、工程目录审计

### 1. 测试代码

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/test/java/com/atlas/contract/M59HttpSecurityBoundaryContractTest.java` | M5.9 | ✅ PASS | 新增源码级契约测试，不启动服务、不访问 kube-manager、不执行真实删除/修改。 |
| `src/test/java/com/atlas/http/KubeManagerHttpClientTokenFallbackSecurityTest.java` | M5.8 回归 | ✅ PASS | 继续验证业务 get/post/delete 缺用户 token 时 fail-closed，不触发 sysadmin fallback。 |
| `src/test/java/com/atlas/contract/M57FallbackOrgIdSourceContractTest.java` | M5.7 回归 | ✅ PASS | 继续验证 fallbackOrgId 可信语义不回流。 |

### 2. 生产代码

| 文件/目录 | 阶段 | 状态 | 说明 |
|----------|------|------|------|
| `src/main/java/com/atlas/http/KubeManagerHttpClient.java` | M5.8/M5.9 审计对象 | ✅ PASS | 本阶段未修改生产代码；源码契约确认 get/post/delete 使用用户 token 必填路径。 |
| `src/main/java/com/atlas/controller/AuthController.java` | M5.9 白名单审计对象 | ✅ PASS | 登录代理入口允许直接 HTTP；未新增数据面写操作。 |
| `src/main/java/com/atlas/intent/embedding/ModelDownloader.java` | M5.9 白名单审计对象 | ✅ PASS | 外部 Embedding 模型下载出口，不访问 kube-manager 数据面。 |
| `src/main/java/com/atlas/tool/**` | M5.9 扫描对象 | ✅ PASS | 未发现业务 Tool 白名单外直接创建/注入 HTTP 客户端绕过统一出口。 |

### 3. 文档

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `CHANGELOG.md` | M5.9 | ✅ PASS | 新增 M5.9 变更、验证、风险记录。 |
| `REVIEW_LOG.md` | M5.9 | ✅ PASS | 追加本阶段背景、专家 Review、验证、风险与后续建议。 |
| `docs/M5_9_AUDIT_CHECKLIST_20260523.md` | M5.9 | ✅ PASS | 本审计清单。 |

## 二、功能与安全验证

| 验证项 | 命令/方式 | 结果 |
|--------|-----------|------|
| 快速专家 Review 会诊 | delegate_task，只读审查 diff 和关键源码 | ✅ PASS with Notes |
| M5.9 定向源码契约 | `mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest` | ✅ 通过 |
| M5.7/M5.8/M5.9 安全组合回归 | `mvn test -q -Dtest=M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` | ✅ 通过 |
| 打包验证 | `mvn -q -DskipTests package` | ✅ BUILD SUCCESS |
| 格式检查 | `git diff --check` | ✅ 通过 |
| Diff 安全扫描 | 新增 diff 行扫描密钥/PAT/危险执行 | ✅ 通过 |
| kube-manager 数据影响检查 | 未启动服务、未调用真实 kube-manager API、未执行真实删除/修改 | ✅ 无数据影响 |

## 三、M5.9 交付内容

1. 新增源码契约防线：
   - 白名单外生产代码不得直接使用常见 HTTP 客户端绕过统一出口；
   - 覆盖 `RestClient/RestTemplate/WebClient/HttpURLConnection/HttpClient/openConnection/OkHttp/Feign/Apache HttpClient` 等模式。
2. 锁定 M5.8 token fallback 边界：
   - `KubeManagerHttpClient#get/post/delete` 必须调用 `resolveUserTokenRequired`；
   - 不得调用允许 sysadmin fallback 的 `resolveToken()`。
3. 明确 HTTP 出口分类：
   - kube-manager 数据面：统一走 `KubeManagerHttpClient`；
   - 登录代理：`AuthController`；
   - 外部模型下载：`ModelDownloader`，不访问 kube-manager 数据面。

## 四、风险与缺口分析

| 风险/缺口 | 级别 | 当前处理 | 后续建议 |
|----------|------|----------|----------|
| 源码字符串扫描不如 AST/ArchUnit 稳定 | 🟡 MED | 当前作为轻量 CI 契约已可防主要回归 | 后续可引入 ArchUnit 做包依赖与方法调用结构化约束 |
| `AuthController` 文件级白名单粒度较粗 | 🟢 LOW | 当前仅登录代理用途，未新增生产代码 | 如后续加入非登录调用，应拆分白名单或迁移到统一 client |
| 外部模型下载出口与 kube-manager 出口需长期区分 | 🟢 LOW | 已显式将 `ModelDownloader` 标注为非 kube-manager 数据面 | 后续新增外部 HTTP 出口必须在契约中说明用途 |

## 五、审计结论

### ✅ PASS

M5.9 达成本阶段目标：在不影响 kube-manager 数据的前提下，用源码契约测试锁定 HTTP 出口和 fallback token 安全边界。当前变更只新增测试与文档，不执行真实删除/修改，不访问真实 kube-manager 数据面。

### 下一步建议

1. 后续若继续增强架构级约束，可引入 ArchUnit，把源码字符串扫描升级为包依赖/类依赖规则。
2. 继续推进下一阶段前，保持“专家会诊 → 小样本 → 逻辑验证 → Review → 文档 → 双推”的闭环。
