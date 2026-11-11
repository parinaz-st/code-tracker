# Phase 1: Quick fixes on the hash-based detector

- **Branch:** `claude/comment-transition-rework` (on top of Phase 0, `81dcf47`)
- **Date:** 2026-11-11
- **ADRs:** [0004](../adr/0004-pending-uncomment-falls-back-to-introduced.md), [0005](../adr/0005-comment-transitions-complement-body-change.md), [0006](../adr/0006-whitespace-insensitive-code-matching.md) (all *Proposed*)
- **Goal:** Remove the regressions found in Phase 0 and fix the cheap defects, without changing the overall design (option A in the analysis).

## Changes

| File | Change | Fixes |
|---|---|---|
| `BlockTrackerChangeHistory.java` | Tracks pending uncommented blocks. New `closePendingUncommentedBlocks()` adds `introduced` to every uncommented edge that was never linked to an older version. | 4.1 (origin) |
| `BlockTrackerImpl.java` | Calls `closePendingUncommentedBlocks()` after the loop in `track()` and `blame()`. In `blame()`, the result is re-evaluated only if something was closed. | 4.1 |
| `ChangeHistory.java` | `matchElements` never adds a `NO_CHANGE` edge that would close a cycle. This affected uncommented blocks because `BlockTrackerImpl` does not mark its start node with `setStart(true)`. | 4.1 (root cause of the hidden cycle) |
| `BlockTrackerChangeHistory.java` (`isMatched`) | Always records `BODY_CHANGE`, and adds comment transitions to the same edge. | 4.3 |
| `CommentTransitionAnalyzer.java` | Everything is compared through `codeHash` (whitespace removed). Block keywords use word boundaries. Trivial lines are not counted as evidence. Dead code (`partialBlockUncommented`) and unused parameters removed. `generateUnCommentedBlockSignaturetHashMap` renamed to `generateUncommentedBlockSignatureHashMap`. Explicit imports. `isBlank()` replaced with Java 8 compatible code. | 4.2 (formatting), 4.3, 4.4, 4.8 |
| `BlockTrackerChangeHistory.java` (imports) | Wildcard imports replaced with the original explicit imports, plus the new ones. | 4.8 |
| `Change.java` | Label `"uncommented Block"` → `"uncommented block"`. | 4.8 |
| `CommentTransitionAnalyzerTest.java` (new) | 43 unit tests for keyword detection, trivial lines, whitespace-insensitive hashing, and comment-marker stripping. | 4.4 |

## How the origin bug was actually caused

The Phase 0 analysis predicted that the virtual node could not be found in older commits. That is true, but debugging showed a second cause that hid the first:

- `BlockTrackerImpl` never calls `setStart(true)` on its start node.
- So `ChangeHistory.connectRelatedNodes()` treats the block at F as a leaf.
- The virtual "before" node has the same identifier as the block at F, so `connectRelatedNodes()` linked F back to it with a `NO_CHANGE` edge, creating a 2-node cycle (`virtual → F → virtual`).
- Because of that edge, the virtual node never looked unresolved.

The cycle guard in `ChangeHistory` fixes this in general (ADR 0004).

## Scenario results

```bash
mvn -o test -Dtest='CommentTransitionScenarioTest,CommentTransitionAnalyzerTest' -Dct.spec=true
```

Histories are listed oldest → newest.

| # | Phase 0 (branch `7c8d874`) | Phase 1 | Verdict | Left for |
|---|---|---|---|---|
| S1 | F uncommented, **no origin** | F introduced + uncommented block | ✗ (has an origin now; no continuity) | Phase 2 |
| S2 | A, B, C, F uncommented | A, B, C, F uncommented block | ✗ D missing | Phase 2 |
| S3 | ✓ | A, B, C, D body + commented out stmt, F body + uncommented stmt | ✓ | |
| S4 | F introduced | A, B, C, F uncommented block | ✗ D missing (**now detected at F**) | Phase 2 |
| S5 | F introduced | F introduced | ✗ edit during restore | Phase 3 |
| S6 | A, B, C, F uncommented | A, B, C, F uncommented block | ✗ D missing | Phase 2 |
| S7 | C uncommented, **no origin** | C introduced + uncommented block | ✓ | |
| S8 | ✓ | A intro, B body | ✓ | |
| S9 | ✓ | A intro, B body | ✓ | |
| S10 | real edit lost | …, D body + commented out stmt, F body + uncommented stmt | ✓ | |

**Pass count:** 5/10, up from 3/10 for both the branch and the baseline. No scenario got worse. All four remaining whole-block failures (S1, S2, S4, S6) fail only because commit D (`commented out`) is missing, which needs the Phase 2 tracking mode. S5 needs the Phase 3 matcher.

## Block oracle regression run

Postponed until after the last phase ([ADR 0007](../adr/0007-oracle-regression-runs-once-at-the-end.md)). A run was started and then stopped, so no partial results are recorded.

Shared code changed in this phase, to check first if the final run shows regressions:

- `ChangeHistory.matchElements`: the cycle guard. It affects **all trackers**, but it only removes edges that would close a cycle.
- `BlockTrackerChangeHistory.isMatched`: always records `BODY_CHANGE`. This changes the result only when a comment transition is also detected, and then it only adds change types.
- `BlockTrackerImpl.track()/blame()`: `closePendingUncommentedBlocks()`. It does nothing unless a block was detected as uncommented.

## Notes and open items

- **Existing pom problem:** `junit-jupiter-api`/`-engine` are pinned at 5.8.2, but the `junit-jupiter` 5.10.0 aggregator brings `junit-jupiter-params` 5.10.0. Every `@MethodSource` test, including all `*OracleTest` classes, fails with `NoSuchMethodError: ClassLoaderUtils.getClassLoader`. A temporary pom copy with all JUnit artifacts at 5.10.0 works around it. The real fix (align the versions) belongs in its own small change and has not been made.
- **Open question (ADR 0004):** should `BlockTrackerImpl` mark its start node with `setStart(true)`, as the attribute and class trackers do?
- **Not done in Phase 1:** oracle JSON for the new change types (waiting for final names, Phase 2), and the commented-out side for whole blocks (Phase 2).
