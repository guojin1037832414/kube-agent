# kube-agent Workspace Recovery Status

- Project: kube-agent
- Recovery home: F:\gitProject\kube-agent\codex-memory\kube-agent\current
- Previous external backup: H:\codex重要文件\kube-agent
- Branch: codex/m521-29-top-agent-mission
- Latest completed wave: M5.21-131
- Latest completed title: NIM runtime binding maps closed
- Workspace: F:\gitProject\kube-agent
- Last synchronized: 2026-06-08 Asia/Shanghai
- Git HEAD: 7ebd82c121c4b4e4734276545d4a3d2bb0c69ee2
- Pushed to remote: true
- Recovery policy: new progress and memory files are written to this workspace-local directory first to avoid external filesystem approval prompts.
- Verification: git diff --check; targeted runtime binding and source guard tests; targeted runtime binding, source guard, state-machine, and durable executor tests; full mvn -q test all passed. Full Maven degraded to L1 embedding mode after local model.onnx download timeout but exited 0.
- Security invariant: nim_create remains HOLD/mock-first; no real 8100, NIM HTTP call, Authorization header sending, durable audit write, deployment POST, release signer, code switch, Elasticsearch, ISysLogService, or sys_log write was added.
- Teaching map: docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md is now the long-lived overall architecture and technical-learning document.
- Resume hint: continue closing release decision contract binding maps and validation result evidence binding maps with producer-owned exact key-set or exact map validation. Keep real durable audit writer, release signer, code switch implementation, and runtime write behavior HOLD until the full evidence chain is reviewed end to end.
