# kube-agent Workspace Recovery Status

- Project: kube-agent
- Recovery home: F:\gitProject\kube-agent\codex-memory\kube-agent\current
- Previous external backup: H:\codex重要文件\kube-agent
- Branch: codex/m521-29-top-agent-mission
- Latest completed wave: M5.24-1
- Latest completed title: kube-manager HTTP outlet trace propagation
- Workspace: F:\gitProject\kube-agent
- Last synchronized: 2026-06-08 Asia/Shanghai
- Latest implementation commit: pending local commit for M5.24-1
- Pushed to remote: pending local commit/push for M5.24-1
- Recovery policy: new progress and memory files are written to this workspace-local directory first to avoid external filesystem approval prompts.
- Verification: M5.24-1 targeted HTTP/trace/security tests passed: `mvn -q "-Dtest=AgentTraceContextTest,KubeManagerHttpClientTracePropagationTest,KubeManagerHttpClientUrlContractTest,KubeManagerHttpClientTokenFallbackSecurityTest,KubeManagerHttpClientResolveOrgIdSecurityTest" test`. Earlier M5.23-1 targeted trace/security tests, M5.22-5 Orchestrator/execute-entrypoint tests, SafeToolExecutor/callback/ReAct regressions, mvn -q -DskipTests validate, and git diff --check passed in prior waves.
- Security invariant: Graph Bridge ToolCallback, ReActEngine, legacy core AtlasToolCallback, and AtlasOrchestrator fallback no longer directly call BaseTool.execute; they delegate to SafeToolExecutor with source-specific audit markers. Production code now has exactly one permanent real BaseTool.execute boundary: SafeToolExecutor. M5.23-1 adds a shared traceId kernel across Orchestrator, `/chat/graph`, HITL resume, ReAct, Graph state, SafeToolExecutor, ToolCallbacks, SSE timeline metadata, and Graph updates. M5.24-1 propagates trace evidence to kube-manager user/business HTTP outlet through `X-Trace-Id` and W3C `traceparent` when possible. External `X-Trace-Id` and LLM/action `traceId` values are validated and treated as control-plane data, not business Tool params. Non-W3C external traces do not forge `traceparent`. nim_create remains HOLD/mock-first and is now Phase 2; no real 8100 write, NIM HTTP call, Authorization header sending, durable audit write, deployment POST, validation result signer, release decision signer, code switch implementation, runtime write behavior, Elasticsearch, ISysLogService, or sys_log write was added.
- Teaching map: docs/AGENT_ARCHITECTURE_AND_TECHNICAL_LEARNING.md is now the long-lived overall architecture and technical-learning document.
- Goal truth source: Phase 1 must deliver the full top-tier kube-manager Agent Core. Moving NIM / HPC / Slurm / BCM to Phase 2 only postpones specialist domain plugins; it must not reduce Phase 1 standards for orchestration, Tool governance, safe execution, frontend workflow, observability, evaluation, documentation, and recovery memory.
- M5.22-1 learning note: advanced engineering means verified upgrade paths, not unbuildable version chasing. Java 17 remains the current verified baseline; Java 21/25, Spring Boot 4, and Spring AI 2 are compatibility-matrix follow-ups.
- M5.22-1 delivered: Spring Boot 3.5.14, Spring AI 1.1.7, Resilience4j, Micrometer Tracing/OpenTelemetry OTLP, Testcontainers, Maven Enforcer, Surefire/Failsafe, JaCoCo, CycloneDX SBOM, SpotBugs quality profile, GitHub Actions backend quality workflow, environment-driven production defaults, and docs/tech-stack/BACKEND_ADVANCED_TECH_STACK_ROADMAP_20260608.md.
- M5.22-2 delivered: Graph Bridge AtlasToolCallback safe execution migration, SafeToolExecutionSource.TOOL_CALLBACK result marker, forged protected/control param regression tests, missing trusted org fail-closed regression, and direct-execute allowlist shrink from 4 to 3 entries.
- M5.22-3 delivered: ReActEngine safe execution migration, SafeToolExecutionSource.REACT_ENGINE request construction, trusted execution/display timeline parameter separation, sanitized ReAct memory/SSE event tests, HITL fail-closed ReAct contract updates, and direct-execute allowlist shrink from 3 to 2 entries.
- M5.22-4 delivered: legacy core AtlasToolCallback safe execution migration, injectable compatibility runtime, forged protected/control param regression, missing trusted org fail-closed regression, and direct-execute allowlist shrink from 2 to 1 entry.
- M5.22-5 delivered: AtlasOrchestrator fallback safe execution migration, SafeToolExecutionSource.ORCHESTRATOR_FALLBACK marker, direct-execute allowlist shrink from 1 to 0, and production SafeToolExecutor-only real execution boundary.
- M5.23-1 delivered: AgentTraceContext trace kernel, safe external trace candidate validation, traceId propagation through SafeToolExecutionRequest/Result, SafeToolExecutor, Orchestrator, `/chat/graph`, HITL resume, ReAct timeline events, Graph tool/execute nodes, Graph Bridge callback, legacy core callback, and protected trace control-plane filtering.
- M5.24-1 delivered: AgentTraceContext W3C traceparent conversion, kube-manager HTTP outlet `X-Trace-Id` / `traceparent` propagation for GET/POST/PATCH/PUT/DELETE/resolveOrgId, HTTP trace propagation contract tests, and source-level guard against hand-written business `X-Token` headers that skip trace helper.
- M5.24-1 multi-expert note: current Java/Spring technology line is advanced enough; Phase 1 P0 priorities are now real Resilience4j HTTP outlet governance, CI hard gates, SafeToolExecutor-only enforcement, OTel span mapping, Spring Security identity migration, and Testcontainers-backed integration tests rather than blind framework stacking.
- Resume hint: continue Phase 1 generic manager Agent Core. Next technical slice should connect traceId to audit event model, OpenTelemetry span mapping, frontend replay contracts, Agent eval reports, and real HTTP resilience policy. Do not start new NIM/HPC/Slurm/BCM implementation slices unless the user explicitly reopens Phase 2 scope.



