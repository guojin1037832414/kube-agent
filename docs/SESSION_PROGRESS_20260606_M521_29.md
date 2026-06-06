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

- M5.21-29 is complete.
- Code, tests, changelog, wave index, and audit doc have all been updated.
- Branch switched from `master` to `codex/m521-29-top-agent-mission` for the checkpoint.

## Files Changed By This Continuation

- Tool implementation metadata/path fixes:
  - `src/main/java/com/atlas/tool/impl/DataSetListTool.java`
  - `src/main/java/com/atlas/tool/impl/FileListTool.java`
  - `src/main/java/com/atlas/tool/impl/DownloadTaskListTool.java`
  - `src/main/java/com/atlas/tool/impl/FileMaterialListTool.java`
  - `src/main/java/com/atlas/tool/impl/InboxMessageListTool.java`
  - `src/main/java/com/atlas/tool/impl/ImageQueryTool.java`
  - `src/main/java/com/atlas/tool/impl/PytorchJobListTool.java`
- Tests:
  - `src/test/java/com/atlas/contract/M511AtlasToolHttpContractTest.java`
  - `src/test/java/com/atlas/http/KubeManagerHttpClientTokenFallbackSecurityTest.java`
  - `src/test/java/com/atlas/tool/impl/ListToolParameterPassThroughContractTest.java`
- Docs:
  - `docs/M5_21_TWENTY_NINTH_WAVE_LEGACY_GET_METADATA_AUDIT_20260606.md`
  - `docs/M5_21_WAVE_INDEX_20260606.md`
  - `CHANGELOG.md`
  - `docs/SESSION_PROGRESS_20260606_M521_29.md`

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

## Final M5.21-29 Decisions

- `DataSetListTool`, `FileListTool`, `DownloadTaskListTool`, `FileMaterialListTool`, and `InboxMessageListTool` are `SENSITIVE_READ + requiresConfirmation=true`.
- `ImageQueryTool` and `PytorchJobListTool` are plain `READ`.
- Mature path fixes:
  - `FileMaterialListTool`: `/api/{orgId}/material/folders`
  - `InboxMessageListTool`: `/api/{orgId}/inbox-message`
- Continued HOLD:
  - `RegistryListTool`
  - `MigConfigListTool`
  - `UploadStatusListTool`
  - `ExperimentInstanceListTool`
  - `ExperimentTemplateListTool`
  - RBAC/organization/quota approval sensitive management lists protected by `SensitiveListToolHoldContractTest`

## Next Step

Pick the next M5.21 mature kube-manager Tool alignment wave. Prefer a narrow evidence-backed slice, likely one of:
- Registry/repository path migration after deciding `/api/registry` vs `/api/{orgId}/repository`.
- MIG config migration requiring explicit `gpuId`.
- Download status/progress migration requiring task `id`.
- Another mature GET area with clean backend/frontend evidence.

## Recovery Reminder

- Ultimate mission: build a top-tier Kubernetes/cloud/HPC Agent on top of mature `kube-manager` and `vue-kube-manager`, while leaving Chinese documentation/comments that help the owner learn Agent engineering deeply.
- External recovery folder: `H:\codex重要文件\kube-agent`.
- After every completed chunk: update repo docs, sync external memory, run tests, commit, and push.
