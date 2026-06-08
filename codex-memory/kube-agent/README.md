# kube-agent Codex Memory

This directory is the workspace-local recovery home for the long-running `kube-agent` project.

It replaces the previous external default location:

`H:\codex重要文件\kube-agent`

## Purpose

- Keep project progress, memory, recovery status, and SHA256 manifests inside the current writable workspace.
- Avoid repeated external filesystem approval prompts.
- Make future Codex sessions recover the latest project state from the repository/workspace directly.

## Layout

- `current/RECOVERY_STATUS.md`: latest resumable checkpoint.
- `current/M5_21_120_RECOVERY_SHA256.json`: latest file hash manifest at the time this local memory directory was introduced.
- `current/*`: current copies of the key progress and learning documents.

## Operating Rule

After every completed meaningful chunk:

1. Update repo docs and tests.
2. Commit and push the project code/docs.
3. Update `codex-memory/kube-agent/current/RECOVERY_STATUS.md`.
4. Copy the relevant latest docs into `codex-memory/kube-agent/current/`.
5. Generate a new recovery SHA256 manifest in `codex-memory/kube-agent/current/`.

The old H drive folder can remain as historical backup, but new recovery writes should use this workspace-local directory first.
