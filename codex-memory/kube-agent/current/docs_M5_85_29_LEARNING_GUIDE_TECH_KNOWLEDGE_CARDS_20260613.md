# M5.85-29 顶级 Agent 学习指南知识卡片增强

## 切片目标

继续丰富 `docs/learning/以kube-agent为例的顶级Agent开发学习指南.md`，让它不仅说明 kube-agent 的架构和学习路线，还能把项目中用到的技术、知识点、源码落点、常见误解和官方学习资料系统串起来。

## 已完成

- 新增 `19. 技术知识点学习卡片`：
  - Java / Maven / Spring Boot 控制平面。
  - Spring Security / 身份 / 租户。
  - LLM / Tool Calling / Structured Output。
  - Graph / ReAct / Plan / 多 Agent。
  - Tool 治理 / kube-manager HTTP 出口 / Resilience。
  - HITL / Audit / Release Gate。
  - Memory / RAG / Embedding / VectorStore。
  - Observability / Tracing / Eval / Replay。
  - MCP / 协议互操作 / 外部工具生态。
  - 测试 / 质量 / 供应链。
- 新增 `20. 源码精读路线`，把一次聊天请求拆成 Security、Principal、Orchestrator、Brain/Intent、Graph/ReAct/Plan、ToolRegistry、SafeToolExecutor、kube-manager outlet、Audit/Observability、Memory/RAG、MCP、测试等阅读步骤。
- 新增 `21. 官方文档精读顺序`，按后端基础、LLM 应用、Agent 编排、Kubernetes 边界、RAG、MCP、Observability/Eval、安全治理、工程质量来读官方资料。
- 新增 `22. 新技术采纳规则`，明确 Java 21/25、Spring Boot 4、Spring AI 2、MCP runtime、A2A handoff、GraphRAG、reranker、LLM-as-judge、CI blocking、真实写 Tool 等都必须先走官方来源、版本对比、兼容矩阵、安全设计、read model、focused test、reviewed trace、release gate、恢复记忆和小步提交。
- 新增 `23. 学习练习任务库`，把学习任务映射到真实后端切片。
- 抽样校验官方/参考链接，并修正已经迁移的 Spring AI / OpenAI 官方地址：
  - `https://docs.spring.io/spring-ai/reference/api/prompt.html`
  - `https://docs.spring.io/spring-ai/reference/api/generic-model.html`
  - `https://developers.openai.com/api/docs/guides/tools-connectors-mcp`

## 验证

- 官方/参考链接抽样检查通过。
- `mvn -q "-DskipTests" validate` 通过。
- `git diff --check` 通过，仅有 Windows LF-to-CRLF 提示。

## 安全不变量

- 本切片只修改文档和恢复记忆，不改变 Java 运行时行为。
- 学习最新技术不等于主干启用 runtime authority。
- 没有打开 MCP `tools/call`、kube-manager 写入、HITL marker 创建、audit/memory 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking、runtime catalog write 或二期 NIM/HPC/Slurm/BCM 权力。

## 下一步建议

- 继续 Eval trace evidence：实现 reviewed redacted fixture intake / catalog review 合同。
- 或继续补残余 support/test-helper 中文教学注释。
- 只有在 kube-manager 8100 已启动且提供当前用户 token/orgId 时，才运行 opt-in READ smoke。
