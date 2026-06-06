# kube-agent Progress Memory - 2026-06-06 M5.21-29

## Recovery Anchor

- Workspace: `F:\gitProject\kube-agent`
- External memory folder requested by user: `H:\codex重要文件\kube-agent`
- Current task: continue M5.21 kube-manager Tool alignment/audit waves.
- Current wave: M5.21-29, legacy GET HTTP metadata convergence.

## User Requirements To Preserve

- Keep project memories grouped by project under `H:\codex重要文件`.
- After each completed chunk, update this project progress/memory and sync it to `H:\codex重要文件\kube-agent`.
- Do not revert unrelated dirty worktree changes.
- Use cautious, evidence-based Tool migration; do not call real kube-manager/8100.
- The user clarified the ultimate mission is not just a production-grade Agent, but a top-tier Agent and learning project that helps the owner progress from Agent beginner to Agent master.
- Continue using latest reasonable Agent engineering patterns, multi-expert/multi-round review, Chinese comments and technical docs, and commit/push after each completed chunk.

## Completed In This Continuation

- Confirmed repository worktree is already dirty with prior M5.21/M4-PX changes.
- Confirmed `H:\codex重要文件\kube-agent` exists.
- Re-read key candidate Tool files and contract tests:
  - `M511AtlasToolHttpContractTest`
  - `ListToolParameterPassThroughContractTest`
  - `SensitiveListToolHoldContractTest`
  - legacy GET candidate Tool implementations.
- Completed first M5.21-29 edit slice:
  - Added `@AtlasToolMapping` HTTP/risk metadata to `DataSetListTool`, `FileListTool`, `DownloadTaskListTool`, `ImageQueryTool`, `PytorchJobListTool`, `InboxMessageListTool`, and `FileMaterialListTool`.
  - Marked data/file/download/material/inbox-message list reads as `SENSITIVE_READ + requiresConfirmation=true`.
  - Kept image and PyTorch list reads as plain `READ`.
  - Fixed mature path mismatches:
    - `InboxMessageListTool`: `/api/{orgId}/message` -> `/api/{orgId}/inbox-message`
    - `FileMaterialListTool`: `/api/{orgId}/file-material` -> `/api/{orgId}/material/folders`

## Current Candidate Notes

- Strong candidates for M5.21-29 metadata: data set, file, download task, image, PyTorch job, inbox message, file material.
- Known path fixes under consideration:
  - `InboxMessageListTool`: old `/api/{orgId}/message`; mature evidence points to `/api/{orgId}/inbox-message`.
  - `FileMaterialListTool`: old `/api/{orgId}/file-material`; mature evidence points to `/api/{orgId}/material/folders`.
- Hold/caution candidates:
  - `MigConfigListTool`: current list path does not match mature `GET /api/mig/{gpuId}` shape.
  - `UploadStatusListTool`: current list path does not match mature `GET /api/{orgId}/download/status/{id}` shape.
  - `ExperimentInstanceListTool` / `ExperimentTemplateListTool`: need stronger backend evidence before metadata whitelist.

## Current Status

- M5.21-35 NIM deployment preflight sensitive read orchestration is implemented, verified, committed, and pushed:
  - Commit: `ddb5f9a feat(M5.21): add NIM deployment preflight tool`.
  - Added `NimDeploymentPreflightTool`.
  - Added `NimDeploymentPreflightSupport`.
  - Added `NimDeploymentPreflightToolHttpContractTest`.
  - Updated `M511AtlasToolHttpContractTest` so a reviewed read-only orchestration Tool can declare multiple mature GET endpoints.
  - Updated `intents.yml` with `nim_deployment_preflight` and clarified `nim_create` remains a safety placeholder.
  - Added `docs/M5_21_THIRTY_FIFTH_WAVE_NIM_DEPLOYMENT_PREFLIGHT_AUDIT_20260606.md`.
  - Updated `CHANGELOG.md`, `docs/M5_21_WAVE_INDEX_20260606.md`, and project memory.
  - The Tool reads only:
    - `GET /api/{orgId}/repository`
    - `GET /api/{orgId}/repository/nim/tags`
    - `GET /api/{orgId}/template`
  - It does not call `POST /api/{orgId}/deployment`, does not create NIM services, and returns `sideEffect=NONE`.
  - `nim_create` remains HOLD until license/system-org policy, NIM template merge, GPU/defaults, HITL card, status polling, and audit logging are designed and tested.
  - Verification passed:
    - `mvn -q "-Dtest=NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
    - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
    - Static secret scan found 0 matches.
    - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.

- M5.21-34 repository catalog/tag sensitive read alignment is implemented, committed, and pushed:
  - Commit: `404d80e feat(M5.21): add repository catalog read tools`.
  - Added `RepositoryCatalogListTool`, `RepositoryCatalogCategoryListTool`, `RepositoryCatalogTagListTool`, and `RepositoryCatalogNimTagListTool`.
  - Added `RepositoryCatalogQuerySupport` to keep product/application repository catalog semantics separate from site registry config and ordinary image repository list.
  - Tool schema supports mature `RepositoryParamDTO` fields for catalog list and requires explicit `repository` for tag reads.
  - Operation metadata is `AUTHENTICATED + SENSITIVE_READ + requiresConfirmation=true`.
  - Image pull/retry/delete/push/build/load, NIM deployment creation, and `GET /api/registry/repo-tag` remain HOLD.

- M5.21-29 is complete.
- Code, tests, changelog, wave index, and audit doc have all been updated.
- Branch switched from `master` to `codex/m521-29-top-agent-mission` for the checkpoint.
- M5.21-30 MIG config read alignment is complete, committed, and pushed:
  - `MigConfigListTool` now uses mature `GET /api/mig/{gpuId}`.
  - Tool schema now requires `gpuId` and no longer exposes `page/limit/keyword`.
  - Permission tightened from `PUBLIC` to `AUTHENTICATED`; operation metadata is `READ + requiresConfirmation=false`.
  - MIG create/update/delete endpoints remain HOLD.
  - MCP manifest export was tightened to `PUBLIC + READ + requiresConfirmation=false + declared endpoint`, preventing authenticated read Tools from leaking into the external safe manifest.
- M5.21-31 download task status read alignment is implemented and targeted tests passed:
  - `UploadStatusListTool` now uses mature `GET /api/{orgId}/download/status/{id}`.
  - Tool schema now requires `id` and no longer exposes `page/limit/keyword`.
  - Operation metadata is `SENSITIVE_READ + requiresConfirmation=true`.
  - Download start/pause/resume/delete remain HOLD.
- M5.21-32 download task progress read alignment is implemented and targeted tests passed:
  - Added `DownloadTaskProgressTool` for mature `GET /api/{orgId}/download/progress/{id}`.
  - Added `DownloadTaskQuerySupport` for shared task ID schema and URL path validation.
  - Operation metadata is `SENSITIVE_READ + requiresConfirmation=true`.
  - Download start/pause/resume/delete remain HOLD.
- M5.21-33 registry site read alignment is implemented and targeted tests passed:
  - `RegistryListTool` now uses mature site endpoint `GET /api/registry`.
  - Tool schema now exposes optional `keyWord` only; `page/limit` are removed.
  - Operation metadata is `SENSITIVE_READ + requiresConfirmation=true`.
  - Registry create/update/delete and `/api/registry/repo-tag` remain HOLD.
  - `/api/{orgId}/repository` is a separate product/application repository catalog candidate, not mixed into `registry_list`.
  - Implementation commit pushed: `e5ba040 fix(M5.21): align registry site read tool`.

## Files Changed By This Continuation

- M5.21-29 tool implementation metadata/path fixes:
  - `src/main/java/com/atlas/tool/impl/DataSetListTool.java`
  - `src/main/java/com/atlas/tool/impl/FileListTool.java`
  - `src/main/java/com/atlas/tool/impl/DownloadTaskListTool.java`
  - `src/main/java/com/atlas/tool/impl/FileMaterialListTool.java`
  - `src/main/java/com/atlas/tool/impl/InboxMessageListTool.java`
  - `src/main/java/com/atlas/tool/impl/ImageQueryTool.java`
  - `src/main/java/com/atlas/tool/impl/PytorchJobListTool.java`
- M5.21-30 tool implementation and MCP safety fixes:
  - `src/main/java/com/atlas/tool/impl/MigConfigListTool.java`
  - `src/main/java/com/atlas/mcp/McpToolManifestService.java`
- M5.21-31 tool implementation fixes:
  - `src/main/java/com/atlas/tool/impl/UploadStatusListTool.java`
- M5.21-32 tool implementation fixes:
  - `src/main/java/com/atlas/tool/impl/DownloadTaskProgressTool.java`
  - `src/main/java/com/atlas/tool/impl/DownloadTaskQuerySupport.java`
  - `src/main/java/com/atlas/tool/impl/UploadStatusListTool.java`
- M5.21-33 tool implementation fixes:
  - `src/main/java/com/atlas/tool/impl/RegistryListTool.java`
- M5.21-34 tool implementation fixes:
  - `src/main/java/com/atlas/tool/impl/RepositoryCatalogQuerySupport.java`
  - `src/main/java/com/atlas/tool/impl/RepositoryCatalogListTool.java`
  - `src/main/java/com/atlas/tool/impl/RepositoryCatalogCategoryListTool.java`
  - `src/main/java/com/atlas/tool/impl/RepositoryCatalogTagListTool.java`
  - `src/main/java/com/atlas/tool/impl/RepositoryCatalogNimTagListTool.java`
- M5.21-35 tool implementation fixes:
  - `src/main/java/com/atlas/tool/impl/NimDeploymentPreflightSupport.java`
  - `src/main/java/com/atlas/tool/impl/NimDeploymentPreflightTool.java`
- Tests:
  - `src/test/java/com/atlas/contract/M511AtlasToolHttpContractTest.java`
  - `src/test/java/com/atlas/http/KubeManagerHttpClientTokenFallbackSecurityTest.java`
  - `src/test/java/com/atlas/tool/impl/ListToolParameterPassThroughContractTest.java`
  - `src/test/java/com/atlas/tool/impl/ListToolParameterSpecContractTest.java`
  - `src/test/java/com/atlas/tool/impl/MigConfigReadToolHttpContractTest.java`
  - `src/test/java/com/atlas/tool/impl/DownloadTaskStatusToolHttpContractTest.java`
  - `src/test/java/com/atlas/tool/impl/DownloadTaskProgressToolHttpContractTest.java`
  - `src/test/java/com/atlas/tool/impl/RegistrySiteToolHttpContractTest.java`
  - `src/test/java/com/atlas/tool/impl/RepositoryCatalogToolHttpContractTest.java`
  - `src/test/java/com/atlas/tool/impl/NimDeploymentPreflightToolHttpContractTest.java`
  - `src/test/java/com/atlas/mcp/M520McpManifestSafetyContractTest.java`
- Intent config:
  - `src/main/resources/intents.yml`
- Docs:
  - `docs/M5_21_TWENTY_NINTH_WAVE_LEGACY_GET_METADATA_AUDIT_20260606.md`
  - `docs/M5_21_THIRTIETH_WAVE_MIG_CONFIG_READ_AUDIT_20260606.md`
  - `docs/M5_21_THIRTY_FIRST_WAVE_DOWNLOAD_STATUS_READ_AUDIT_20260606.md`
  - `docs/M5_21_THIRTY_SECOND_WAVE_DOWNLOAD_PROGRESS_READ_AUDIT_20260606.md`
  - `docs/M5_21_THIRTY_THIRD_WAVE_REGISTRY_SITE_READ_AUDIT_20260606.md`
  - `docs/M5_21_THIRTY_FOURTH_WAVE_REPOSITORY_CATALOG_READ_AUDIT_20260606.md`
  - `docs/M5_21_THIRTY_FIFTH_WAVE_NIM_DEPLOYMENT_PREFLIGHT_AUDIT_20260606.md`
  - `docs/M5_21_WAVE_INDEX_20260606.md`
  - `CHANGELOG.md`
  - `docs/SESSION_PROGRESS_20260606_M521_29.md`
  - `docs/PROJECT_MISSION_AND_MEMORY.md`

## Verification Completed

- Passed:
  - `mvn -q "-Dtest=M511AtlasToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest" test`
  - `mvn -q "-Dtest=M511AtlasToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,SensitiveListToolHoldContractTest,M520McpManifestSafetyContractTest,ToolRegistryPermissionTest" test`
  - `mvn -q "-Dtest=KubeManagerHttpClientTokenFallbackSecurityTest" test`
  - `mvn -q test`
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
- Note: `ToolRegistryPermissionTest` attempted to download the embedding model in test profile and timed out, then degraded as expected; tests still passed.
- Full test initially exposed a narrow stale source-contract assertion in `KubeManagerHttpClientTokenFallbackSecurityTest`: it looked for `delete(String path, Map<String, Object> body)` while the production method is `delete(String path, Map<String, Object> queryParams)`. The test was corrected to lock the real public method signature and re-run successfully.
- After the user clarified the ultimate goal is a top-tier learning Agent project, not merely a production Agent, the checkpoint was re-verified on branch `codex/m521-29-top-agent-mission`:
  - `mvn -q test` passed again.
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check` passed.
  - Static secret scan found no real credentials; matches were only documentation/config comments about avoiding api-key/password in source.
- M5.21-30 targeted verification passed:
  - `mvn -q "-Dtest=MigConfigReadToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest" test`
- M5.21-30 final pre-commit verification passed:
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - Static secret scan found no real credentials; matches were only documentation/config comments about avoiding api-key/password in source.
  - `mvn -q test`
  - External recovery docs synced to `H:\codex重要文件\kube-agent`.
- M5.21-31 targeted verification passed:
  - `mvn -q "-Dtest=DownloadTaskStatusToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
- M5.21-31 final pre-commit verification passed:
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - Static secret scan found no real credentials; matches were only documentation/config comments about avoiding api-key/password in source.
  - `mvn -q test`
- M5.21-32 targeted verification passed:
  - `mvn -q "-Dtest=DownloadTaskProgressToolHttpContractTest,DownloadTaskStatusToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
- M5.21-32 final pre-commit verification passed:
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - Static secret scan found no real credentials; matches were only documentation/config comments about avoiding api-key/password in source.
  - `mvn -q test`
- M5.21-33 targeted verification passed:
  - `mvn -q "-Dtest=RegistrySiteToolHttpContractTest,ListToolParameterPassThroughContractTest,ListToolParameterSpecContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
- M5.21-33 final pre-commit verification passed:
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - Static secret scan found no real credentials; matches were only documentation/config comments about avoiding api-key/password in source.
  - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced to `H:\codex重要文件\kube-agent`.
- M5.21-34 targeted verification passed:
  - `mvn -q "-Dtest=RepositoryCatalogToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
- M5.21-34 final pre-commit verification passed:
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - Static secret scan found no real credentials; matches were 0.
  - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.
  - External recovery docs synced to `H:\codex重要文件\kube-agent`.
- M5.21-35 targeted verification passed:
  - `mvn -q "-Dtest=NimDeploymentPreflightToolHttpContractTest,M511AtlasToolHttpContractTest,M520McpManifestSafetyContractTest" test`
- M5.21-35 final pre-commit verification passed:
  - `git -c safe.directory=F:/gitProject/kube-agent diff --check`
  - Static secret scan found 0 matches.
  - `mvn -q test`
  - Full test note: embedding model download timed out in test profile and degraded as expected; final test result passed.

## Final M5.21-29 Decisions

- `DataSetListTool`, `FileListTool`, `DownloadTaskListTool`, `FileMaterialListTool`, and `InboxMessageListTool` are `SENSITIVE_READ + requiresConfirmation=true`.
- `ImageQueryTool` and `PytorchJobListTool` are plain `READ`.
- Mature path fixes:
  - `FileMaterialListTool`: `/api/{orgId}/material/folders`
  - `InboxMessageListTool`: `/api/{orgId}/inbox-message`
- Continued HOLD:
  - `ExperimentInstanceListTool`
  - `ExperimentTemplateListTool`
  - RBAC/organization/quota approval sensitive management lists protected by `SensitiveListToolHoldContractTest`
- `MigConfigListTool` moved out of HOLD for read-only `GET /api/mig/{gpuId}` only; `POST/PUT/DELETE /api/mig` remain HOLD.
- `UploadStatusListTool` moved out of HOLD for sensitive read-only `GET /api/{orgId}/download/status/{id}` only; download start/pause/resume/delete remain HOLD.
- `DownloadTaskProgressTool` added for sensitive read-only `GET /api/{orgId}/download/progress/{id}`; download start/pause/resume/delete remain HOLD.
- `RegistryListTool` moved out of HOLD for sensitive read-only `GET /api/registry` only; registry create/update/delete and repo-tag remain HOLD.
- `NimDeploymentPreflightTool` added for sensitive read-only NIM repository/tag/template preflight only; `nim_create` remains HOLD.

## Next Step

Continue NIM orchestration only through safe slices:
- design NIM HITL card and DTO merge tests,
- or pick another mature GET area with clean backend/frontend evidence.

## Recovery Reminder

- Ultimate mission: build a top-tier Kubernetes/cloud/HPC Agent on top of mature `kube-manager` and `vue-kube-manager`, while leaving Chinese documentation/comments that help the owner learn Agent engineering deeply.
- External recovery folder: `H:\codex重要文件\kube-agent`.
- After every completed chunk: update repo docs, sync external memory, run tests, commit, and push.
