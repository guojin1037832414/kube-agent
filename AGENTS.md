# kube-agent Codex 项目规则

## 中文注释规则

- 本项目是生产级 Agent 项目，也是 Agent 开发学习项目；所有新增或修改的代码都应优先补充中文注释。
- 类、接口、record、Controller endpoint、Service 编排入口、复杂私有方法、测试用例，都必须用中文说明“为什么存在、输入来自哪里、输出给谁用、不能做什么”。
- 安全边界必须写中文注释：例如是否只读、是否 admin-only、是否会调用 Tool / MCP / A2A / RAG / kube-manager / LLM / 外部网络、是否会写审计或内存。
- 注释要解释设计意图和风险边界，避免无意义逐行翻译。例如不要写“给变量赋值”，而要写“这里把多个只读证据源合并成前端可渲染的审阅视图，不触发运行时动作”。
- 修改旧代码时，应在触碰区域补齐中文注释；不要为了补注释一次性大规模重写无关历史代码。
- 测试代码也要有中文注释，尤其要说明测试保护的契约、安全不变量和学习价值。

## 全仓分批中文注释计划

- 中文注释要分批推进，避免一次性大规模改动导致 review、测试和恢复记忆失控。
- 批次顺序优先级：
  1. Controller / Security / Principal / HITL 等用户入口与权限边界。
  2. Tool / MCP / SafeToolExecutor / kube-manager HTTP outlet 等外部能力与执行边界。
  3. Orchestrator / Graph / ReAct / Plan 等 Agent 推理编排链路。
  4. Memory / RAG / Eval / Observability / Audit 等证据、评测和可观测链路。
  5. DTO / support / config / store 等支撑代码。
- 每一批都要做到：只改本批触碰文件、补充中文教学注释、运行对应测试或至少编译校验、更新 `codex-memory` 恢复记忆、提交并推送。
- 每一批注释要优先解释“为什么这样设计、不能做什么、未来扩展必须满足什么证据”，不要把代码逐行翻译成中文。

## 项目记忆规则

- 每个有意义的后端/前端切片完成后，都要同步更新 `codex-memory/kube-agent/current` 下的恢复记忆。
- 长期架构、技术点和学习总结继续维护在 `docs/顶级Agent架构与技术学习地图.md`。
- `H:\codex重要文件` 是历史外部备份路径；当前优先写入 workspace-local `codex-memory`。
