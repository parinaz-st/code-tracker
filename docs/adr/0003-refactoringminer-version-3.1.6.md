# 0003. Build the branch against RefactoringMiner 3.1.6

- Status: Accepted
- Date: 2026-11-11
- Phase: 0

## Context

`feature/CmtTransitionDetectionBlock` pinned `rm.version` to 3.1.4. Code merged from `master` (commit `c79add6`) calls `ChangeAttributeTypeRefactoring.getAttributeBefore()/getAttributeAfter()`, which do not exist in 3.1.4, so the branch did not compile. Current `master` needs an even newer, unreleased RefactoringMiner (CI builds RM from source and passes `-Drm.version=...`), and that build is not in the local Maven cache.

## Decision

Set `rm.version` to 3.1.6 on the rework branch, which is the version `master` had at the branch's merge-base (`375d0bb`). Phase 0 baselines are measured at that merge-base, not at current `master`.

## Consequences

- The branch compiles and runs offline with the cached RM 3.1.6.
- Before moving the work upstream, the branch must be rebased or merged onto current `master` and checked against the RM version that `master` requires. RM API changes may need small adaptations at that point.
