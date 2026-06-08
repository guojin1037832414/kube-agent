# kube-agent Workspace Recovery Status

- Project: kube-agent
- Recovery home: F:\gitProject\kube-agent\codex-memory\kube-agent\current
- Previous external backup: H:\codex重要文件\kube-agent
- Branch: codex/m521-29-top-agent-mission
- Latest completed wave: M5.21-124
- Latest completed title: NIM validation gate failure/shortcut lists closed
- Workspace: F:\gitProject\kube-agent
- Last synchronized: 2026-06-08 Asia/Shanghai
- Git HEAD: 803efb8acd6e7cd6ef1453e44d524f2c0d82f842
- Pushed to remote: true
- Recovery policy: new progress and memory files are written to this workspace-local directory first to avoid external filesystem approval prompts.
- Verification: git diff --check; targeted durable receipt validation gate and validation result migration tests; full mvn -q test all passed. Full Maven degraded to L1 embedding mode after local model.onnx download timeout but exited 0.
- Security invariant: nim_create remains HOLD/mock-first; no real 8100, NIM HTTP call, Authorization header sending, durable audit write, deployment POST, release signer, code switch, Elasticsearch, ISysLogService, or sys_log write was added.
- Teaching map: docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md is now the long-lived overall architecture and technical-learning document.
- Resume hint: close validation result migration's own failure/shortcut lists before downstream release decision gate consumes them, or consider exact digestChainRules.rules validation if rule rows become release criteria.
