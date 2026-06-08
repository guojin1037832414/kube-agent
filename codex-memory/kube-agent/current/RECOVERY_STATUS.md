# kube-agent Workspace Recovery Status

- Project: kube-agent
- Recovery home: F:\gitProject\kube-agent\codex-memory\kube-agent\current
- Previous external backup: H:\codex重要文件\kube-agent
- Branch: codex/m521-29-top-agent-mission
- Latest completed wave: M5.22-1
- Latest completed title: Advanced backend engineering baseline
- Workspace: F:\gitProject\kube-agent
- Last synchronized: 2026-06-08 Asia/Shanghai
- Latest implementation commit: 0c7cd93 feat(M5.22): add advanced backend engineering baseline
- Pushed to remote: yes, origin/codex/m521-29-top-agent-mission includes 0c7cd93
- Recovery policy: new progress and memory files are written to this workspace-local directory first to avoid external filesystem approval prompts.
- Verification: mvn -q -DskipTests validate; mvn -q test; mvn -q verify; git diff --check all passed on Spring Boot 3.5.14 / Spring AI 1.1.7. Maven generated CycloneDX SBOM files under target/bom.json and target/bom.xml plus JaCoCo reports under target/site/jacoco. Full Maven still degraded to L1 embedding mode after local model.onnx download timeout but exited 0.
- Security invariant: nim_create remains HOLD/mock-first and is now Phase 2; no real 8100 write, NIM HTTP call, Authorization header sending, durable audit write, deployment POST, validation result signer, release decision signer, code switch implementation, runtime write behavior, Elasticsearch, ISysLogService, or sys_log write was added.
- Teaching map: docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md is now the long-lived overall architecture and technical-learning document.
- Goal truth source: Phase 1 must deliver the full top-tier kube-manager Agent Core. Moving NIM / HPC / Slurm / BCM to Phase 2 only postpones specialist domain plugins; it must not reduce Phase 1 standards for orchestration, Tool governance, safe execution, frontend workflow, observability, evaluation, documentation, and recovery memory.
- M5.22-1 learning note: advanced engineering means verified upgrade paths, not unbuildable version chasing. Java 17 remains the current verified baseline; Java 21/25, Spring Boot 4, and Spring AI 2 are compatibility-matrix follow-ups.
- M5.22-1 delivered: Spring Boot 3.5.14, Spring AI 1.1.7, Resilience4j, Micrometer Tracing/OpenTelemetry OTLP, Testcontainers, Maven Enforcer, Surefire/Failsafe, JaCoCo, CycloneDX SBOM, SpotBugs quality profile, GitHub Actions backend quality workflow, environment-driven production defaults, and docs/tech-stack/BACKEND_ADVANCED_TECH_STACK_ROADMAP_20260608.md.
- Resume hint: continue Phase 1 generic manager Agent Core. Next technical slice should migrate remaining direct Tool execution paths in ReAct/ToolCallback/Graph/legacy fallback to the single SafeToolExecutor invocation kernel, then add traceId propagation through intent, plan, tool, HTTP, HITL, audit, and final answer. Do not start new NIM/HPC/Slurm/BCM implementation slices unless the user explicitly reopens Phase 2 scope.
