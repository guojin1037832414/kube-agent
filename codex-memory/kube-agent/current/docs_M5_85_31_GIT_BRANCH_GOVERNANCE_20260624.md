# M5.85-31 Git branch governance

## 切片目标

根据用户要求，把仓库分支收敛为两条长期分支：一个主分支、一个开发分支，避免后续继续堆积 `codex/...` 临时分支导致恢复、review 和学习路径混乱。

## 已完成

- 将远端默认主分支 `master` 快进到当前最新成果提交。
- 将当前开发线重命名并推送为 `develop`。
- 删除远端旧分支：
  - `codex/m521-29-top-agent-mission`
  - `codex/m521-29-add-agents-md`
- 删除本地旧 `codex/m521-29-add-agents-md` 分支。
- 清理 Git worktree 注册，只保留主工作区 `F:\gitProject\kube-agent`。

## 当前分支约定

- `master`：主分支，保持为远端默认分支，用于稳定恢复点。
- `develop`：开发分支，后续默认在这里继续推进后端 Agent Core、中文注释、文档和证据链切片。
- 历史恢复快照中的 `codex/...` 分支名不批量重写，因为它们记录的是当时状态；新的恢复入口以 `develop` 为准。

## 验证

- Focused review tests passed:
  - `mvn -q "-Dtest=AgentReviewedTraceFixtureIntakeContractServiceTest,Batch4ReviewedTraceFixtureIntakeChineseCommentContractTest,ObservabilityControllerSecurityContractTest,ObservabilityControllerTest,AgentSecurityConfigWebMvcTest" test`
- Compile validation passed:
  - `mvn -q "-DskipTests" validate`
- Whitespace check passed:
  - `git diff --check`
- Remote heads after cleanup:
  - `refs/heads/master`
  - `refs/heads/develop`

## 安全不变量

- 这次只治理 Git 分支和恢复记忆，不修改 Java 运行时能力。
- 没有打开 Tool / MCP / kube-manager 写能力。
- 没有启用 eval CI blocking、release authority、HITL marker 创建、audit/memory 写入或二期 NIM/HPC/Slurm/BCM 权力。
