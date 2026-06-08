# kube-agent Workspace Recovery Status

- Project: kube-agent
- Recovery home: F:\gitProject\kube-agent\codex-memory\kube-agent\current
- Previous external backup: H:\codex重要文件\kube-agent
- Branch: codex/m521-29-top-agent-mission
- Latest completed wave: M5.21-126
- Latest completed title: NIM release decision gate failure/shortcut lists closed
- Workspace: F:\gitProject\kube-agent
- Last synchronized: 2026-06-08 Asia/Shanghai
- Git HEAD: 09638c3538a249b8a12b52aa62ca11ada902a847
- Pushed to remote: true
- Recovery policy: new progress and memory files are written to this workspace-local directory first to avoid external filesystem approval prompts.
- Verification: git diff --check; targeted durable release decision gate and state-machine release decision requirement tests; full mvn -q test all passed. Full Maven degraded to L1 embedding mode after local model.onnx download timeout but exited 0.
- Security invariant: nim_create remains HOLD/mock-first; no real 8100, NIM HTTP call, Authorization header sending, durable audit write, deployment POST, release signer, code switch, Elasticsearch, ISysLogService, or sys_log write was added.
- Teaching map: docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md is now the long-lived overall architecture and technical-learning document.
- Resume hint: continue scanning downstream release requirement and state-machine proof fields for remaining subset/non-empty acceptance, or close state-machine requirement's own failure/shortcut lists if consumed.
