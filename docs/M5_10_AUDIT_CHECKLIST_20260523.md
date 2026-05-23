# kube-agent M5.10 审计清单

> 生成时间: 2026-05-23 17:36:57 CST
> 审计人: Hermes
> 审计范围: M5.10 ArchUnit 架构级安全边界契约治理
> 核心约束: 避免影响 kube-manager 数据；只做静态架构测试、单元逻辑验证和打包，不执行真实删除/修改/查询请求。

## 一、工程目录审计

### 1. 构建配置

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `pom.xml` | M5.10 | ✅ PASS | 新增 `archunit-junit5:1.3.0`，scope 为 `test`，不进入生产运行时。 |

### 2. 测试代码

| 文件 | 阶段 | 状态 | 说明 |
|------|------|------|------|
| `src/test/java/com/atlas/contract/M510ArchitectureBoundaryTest.java` | M5.10 | ✅ PASS | 新增 ArchUnit 架构级契约测试，不使用 `@SpringBootTest`，不启动 Spring，不访问 kube-manager。 |
| `src/test/java/com/atlas/contract/M59HttpSecurityBoundaryContractTest.java` | M5.9 回归 | ✅ PASS | 继续负责 `resolveToken()` 与 `resolveUserTokenRequired()` 方法体语义源码契约。 |
| `src/test/java/com/atlas/http/KubeManagerHttpClientTokenFallbackSecurityTest.java` | M5.8 回归 | ✅ PASS | 继续验证业务请求缺用户 Token 时 fail-closed。 |
| `src/test/java/com/atlas/contract/M57FallbackOrgIdSourceContractTest.java` | M5.7 回归 | ✅ PASS | 继续验证 fallbackOrgId 可信语义不回流。 |

### 3. 生产代码

| 文件/目录 | 阶段 | 状态 | 说明 |
|----------|------|------|------|
| `src/main/java/com/atlas/http/**` | M5.10 白名单 | ✅ PASS | 允许作为统一 kube-manager HTTP 网关依赖底层 HTTP 客户端。 |
| `src/main/java/com/atlas/controller/AuthController.java` | M5.10 白名单 | ✅ PASS | 登录代理入口允许直接请求 kube-manager 登录接口。 |
| `src/main/java/com/atlas/intent/embedding/ModelDownloader.java` | M5.10 白名单 | ✅ PASS | 外部 Embedding 模型下载出口，不访问 kube-manager 数据面。 |
| `src/main/java/com/atlas/tool/**` | M5.10 保护对象 | ✅ PASS | ArchUnit 约束 Tool 层不得依赖底层 HTTP 客户端。 |
| `src/main/java/com/atlas/controller/**` | M5.10 保护对象 | ✅ PASS | ArchUnit 约束 Controller 不得直接依赖 `tool.impl`。 |

## 二、功能与安全验证

| 验证项 | 命令/方式 | 结果 |
|--------|-----------|------|
| 专家会诊 | Java 架构专家 + 安全专家复核 + ArchUnit 开源调研 | ✅ PASS |
| M5.10 定向 ArchUnit 验证 | `mvn test -q -Dtest=M510ArchitectureBoundaryTest` | ✅ 通过 |
| M5.7-M5.10 安全组合回归 | `mvn test -q -Dtest=M510ArchitectureBoundaryTest,M59HttpSecurityBoundaryContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,M57FallbackOrgIdSourceContractTest` | ✅ 通过 |
| 打包验证 | `mvn -q -DskipTests package` | ✅ BUILD SUCCESS |
| 格式检查 | `git diff --check` | ✅ 通过 |
| Diff 安全扫描 | 新增 diff 行扫描密钥/PAT/危险执行 | ✅ 通过 |
| kube-manager 数据影响检查 | 未启动服务、未调用真实 kube-manager API、未执行真实删除/修改 | ✅ 无数据影响 |

## 三、M5.10 交付内容

1. 引入 ArchUnit 架构测试能力：
   - 仅 test scope，不影响生产运行时。
   - 使用 `@AnalyzeClasses` 静态分析生产 class 依赖，不启动 Spring。
2. 新增三条架构边界规则：
   - 白名单外生产代码不得直接依赖底层 HTTP 客户端；
   - Tool 层不得依赖底层 HTTP 客户端；
   - Controller 不得直接依赖具体 Tool 实现。
3. 明确 M5.9 与 M5.10 分工：
   - M5.9：源码字符串契约，负责方法体语义。
   - M5.10：ArchUnit 架构契约，负责包/类依赖边界。

## 四、风险与缺口分析

| 风险/缺口 | 级别 | 当前处理 | 后续建议 |
|----------|------|----------|----------|
| ArchUnit 规则初期覆盖范围仍较小 | 🟡 MED | 本阶段先落三条最小规则，避免大规模历史返工 | 后续逐步扩展 service/orchestrator/react/config 层级规则 |
| 方法体语义不是 ArchUnit 强项 | 🟢 LOW | 保留 M5.9 源码契约继续守 `resolveToken()` 边界 | 不要用 M5.10 替代 M5.9 |
| 白名单粒度仍可进一步精细化 | 🟢 LOW | 当前明确 `http..`、`AuthController`、`ModelDownloader` 用途 | 后续可改为更细粒度 class-level 或 custom predicate |

## 五、审计结论

### ✅ PASS

M5.10 达成本阶段目标：在不影响 kube-manager 数据的前提下，引入 ArchUnit 架构级静态测试，把 M5.9 的 HTTP 出口源码契约升级为更稳定的包/类依赖边界守护。

### 下一步建议

1. M5.11 可继续做“Tool 注解方法与实际 HTTP 调用一致性契约”，仍然只做源码/静态逻辑验证，不碰真实数据。
2. 后续可逐步引入 ArchUnit layer rules，保护 controller → orchestrator/react → tool/core → http 的清晰依赖方向。
