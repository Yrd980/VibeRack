# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Layout

This is a single-context Android app repository.

Read these in order before architecture, diagnosis, PRD, issue-breakdown, or implementation work:

1. `CONTEXT.md` at the repo root.
2. `docs/智能物料管理系统_项目技术文档_v1.0.md` for full product, hardware, firmware, algorithm, roadmap, and risk context.
3. `docs/智能底盘BLE接口规格_v0.1.md` for byte-level BLE/NFC protocol details.
4. Relevant Kotlin code under `app/src/main/java/com/viberack/app`.
5. `docs/adr/` if it exists later.

If `docs/adr/` or another optional domain file does not exist, proceed silently. Do not suggest creating it upfront. Decision records can be added lazily when decisions actually get resolved.

## Current Baseline

`CONTEXT.md` is the merged working context for agents. The two Chinese docs remain the detailed source of truth for product and protocol decisions. If a lower-level note conflicts with those documents or with byte-accurate Kotlin protocol constants, surface the conflict before changing behavior.

## Vocabulary Rules

Use the project's canonical terms when creating issues, PRDs, refactor proposals, tests, or code:

- VibeRack
- smart chassis
- binding table
- table_seq
- container
- slot
- digital twin
- find-by-light
- BOM pick-to-light
- hardware restore

If a needed concept is missing from `CONTEXT.md`, note the gap rather than inventing a parallel term.
