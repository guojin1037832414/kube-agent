# M5.85-37 全量验证硬化

## 交付内容

- 按用户要求补齐 M5.85-36 后的“一次性完整验证”。
- 初次全量 `mvn -q test` 暴露 `AgentReviewedTraceFixtureManifestService` 的 Spring 构造器注入问题。
- 修复方式：给生产构造器补 `@Autowired`，让 Spring 在存在 package-private 测试构造器时仍明确选择生产构造器。
- 该问题属于 full-context wiring 暴露出的历史遗留问题，不是 kube-manager READ smoke 批次本身的行为回归。

## 验证结果

- `mvn -q "-Dtest=RuleMatcherTest,ToolRegistryPermissionTest" test` 通过。
- `mvn -q test` 通过。
- 登录型真实 smoke 通过：连接 `http://localhost:8100`，登录测试账号，解析可信 orgId，并跑通 6 条 GET/READ/no-HITL Tool 链路。
- `mvn -q "-DskipTests" package` 通过。
- `git diff --check` 通过，仅有 Windows LF-to-CRLF 提示。
- 敏感扫描通过，只命中 `<当前密码>`、`password=xxx` 等占位符，没有真实测试密码或 token。
- Git 分支和工作区检查将在本切片提交后保持 `develop` 对齐 `origin/develop`。

## 安全边界

- 本切片只修改 Spring DI 注入元数据并补验证，不新增 Controller，不改变生产 Tool 行为，不打开任何运行时权力。
- 没有 kube-manager 写入、敏感读 smoke、MCP `tools/call`、HITL marker、audit/memory 写入、retrieval/vector runtime、CI blocking、release authority、依赖升级或二期 NIM/HPC/Slurm/BCM 权力。

## 下一步

- 这次后端验证口径已比较完整；后续可回到功能推进：继续 Eval trace evidence/catalog patch review readiness，或在真实 timing probe 稳定后再纳入新的低风险 READ Tool。
