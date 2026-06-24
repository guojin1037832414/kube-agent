# Reviewed Trace Fixtures

这个目录只存放已经通过人工 Git review 的 reviewed redacted trace fixture JSON 文件。

安全边界：

- 不要把模板或占位 JSON 提交到本目录。
- 不要提交 fake traceId。
- 不要提交 raw audit、token、password、principal、organization、conversation、endpoint、reason 或原始参数。
- 不要通过运行时上传生成 fixture。
- 不要在这里直接修改 `observability/eval-trace-sets.json`。
- fixture 文件只证明“可进入 catalog patch review”，不授予 CI blocking、release authority、Tool/MCP/kube-manager 调用或 Phase 2 NIM/HPC/Slurm/BCM 权力。

请先查看只读模板端点：

- `GET /api/agent/observability/eval/reviewed-trace-fixture-template`

再用 manifest 端点确认覆盖缺口：

- `GET /api/agent/observability/eval/reviewed-trace-fixture-manifest`
