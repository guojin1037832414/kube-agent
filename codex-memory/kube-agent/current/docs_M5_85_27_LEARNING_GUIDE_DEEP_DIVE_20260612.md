# kube-agent 当前恢复快照 - M5.85-27

- Date: 2026-06-12
- Branch: codex/m521-29-top-agent-mission
- Latest wave: M5.85-27
- Latest title: Top-tier Agent learning guide technical deep dive

## 本次完成

- 将 `docs/learning/以kube-agent为例的顶级Agent开发学习指南.md` 进一步扩写为技术专题精读手册。
- 新增专题索引：Java/Maven/Spring 控制平面、LLM 接入、Tool Calling、结构化输出、Graph/ReAct/Plan/多 Agent 编排、Tool 治理、kube-manager HTTP 出口、HITL/guardrails/audit/release gates、Memory/RAG/VectorStore/GraphRAG、Eval/Replay/Observability、MCP resources/prompts/tools、Agent 安全、测试与质量门。
- 新增“从小白到高手的项目化学习路线”，把学习阶段映射到源码阅读、切片交付和恢复记忆。
- 新增官方资料导航清单，方便后续继续深挖官方文档。

## 验证

- 官方/参考链接抽样检查通过。
- `mvn -q "-DskipTests" validate` 通过。
- `git diff --check` 通过，仅有 Windows LF-to-CRLF 提示。

## 安全边界

- 仅文档和恢复记忆更新。
- 未打开 Tool/MCP/kube-manager 写入、HITL marker 创建、audit/memory 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking 或 Phase 2 NIM/HPC/Slurm/BCM 权力。

## 下一步建议

- 继续 M5.85 计划中的后端切片：Eval trace promotion workflow、reviewed redacted fixture intake/catalog review、残余 support/test-helper 教学注释，或 kube-manager 8100 READ smoke（服务端启动后）。
