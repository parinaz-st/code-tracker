# 0002. Follow upstream's RefactoringMiner release

- Status: Proposed
- Date: 2026-11-14
- Spec: [build-dependencies-fix](../specs/build-dependencies-fix.md)

## Context

CodeTracker's matching is built on RefactoringMiner. Upstream regularly raises `rm.version` and adapts the code to RefactoringMiner API changes; commit `7afe3cb` calls an API that the version this fork pins (3.1.6) does not have, so `master` no longer compiles. Upstream now uses 3.2.0. A RefactoringMiner upgrade can change detected changes, so it can change oracle results.

## Decision

This fork uses the same RefactoringMiner release as `upstream/master`. When upstream raises `rm.version`, the fork follows it in a separate, dependency-only change, and runs the published oracle tests to report any difference.

## Alternatives considered

- **Stay on 3.1.6 and rewrite the new API calls back.** It diverges from upstream in production code, and every future upstream merge would conflict again.
- **Build against a locally built snapshot.** It works on one machine only, and results cannot be reproduced by anyone else.

## Consequences

- The fork builds from Maven Central on any machine, and upstream merges stay simple.
- Each upgrade may shift oracle results. The difference has to be reported and classified, never fixed by editing the oracle.
- Results produced before and after an upgrade are not directly comparable; reports must state the RefactoringMiner version.
