# M5.85-25 顶级 Agent 学习指南扩写

## 目标

响应用户要求：把 `docs/learning/以kube-agent为例的顶级Agent开发学习指南.md` 丰富成更像课程教材的文档，详细解释 kube-agent 中用到的技术和知识点，并附相关文档和学习地址。

## 修改范围

- `docs/learning/以kube-agent为例的顶级Agent开发学习指南.md`
- `docs/项目使命与当前记忆.md`
- `docs/文档索引.md`
- `变更日志.md`
- `codex-memory/kube-agent/current/当前恢复状态.md`
- `codex-memory/kube-agent/current` 文档镜像

## 学习增强内容

- 技术栈学习地图：Java/Maven、Spring Boot、Spring Security、Spring AI、OpenAI tool/agent concepts、Spring AI Alibaba Graph/ReAct、MCP、kube-manager/Kubernetes、Resilience4j、Micrometer/OpenTelemetry、Memory/RAG、ONNX/DJL、Caffeine、OWASP LLM Top 10、NIST AI RMF、测试与质量工具。
- 核心概念详解：Agent 不是单次 LLM 调用、Tool calling 是应用侧执行、Prompt Injection 不能只靠 Prompt 防、身份/租户/session/conversation 分离、HITL 必要但不充分、Graph/ReAct/Plan 不是授权结构、RAG 难点是证据托管、Eval 是发布闸门、Observability 要解释 Agent 决策、MCP 不是安全豁免、Kubernetes API 思维、质量工具服务 release gate。
- 项目化学习材料：请求生命线、身份边界、HITL、Tool 安全链、写操作、MCP、Memory/RAG、Eval/Replay/Observability、Graph/ReAct/Plan、前后端分工和切片闭环的常见误区、验收清单、审查路径和练习题。
- 阶段路线：从入门、身份安全、Tool 安全、编排、治理到进阶 RAG/MCP/Multi-Agent 设计。

## 参考资料入口

指南中已加入官方或主流入口，包括 Spring Boot/Security/AI、OpenAI Agents/Tool Calling/Eval、Spring AI Alibaba、MCP、Kubernetes、Resilience4j、Micrometer/OpenTelemetry、ONNX Runtime、DJL、Caffeine、OWASP、NIST、JUnit、AssertJ、Mockito、ArchUnit、Testcontainers、JaCoCo、SpotBugs、CycloneDX。

## 验证

```powershell
mvn -q "-DskipTests" validate
git diff --check
```

结果：全部通过。`git diff --check` 仅有 Windows LF-to-CRLF 提示。

## 安全边界

本切片只修改文档和恢复记忆，不改变 Java 运行时行为。没有打开 Tool/MCP/kube-manager 写入、HITL marker 创建、audit/memory 写入、retrieval/vector runtime、A2A handoff、依赖升级、CI blocking 或 Phase 2 NIM/HPC/Slurm/BCM 权力。

## 下一步

继续后端 Phase 1 顶级 Agent Core：可选方向包括剩余 support/test-helper 注释收尾、kube-manager 8100 READ smoke、Orchestrator/Eval/Multi-Agent hardening，或继续把学习指南拆成章节化课程。
