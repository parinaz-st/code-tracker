# Phase 6: Real-project oracle (javaparser)

- **Branch:** `claude/comment-transition-rework` (commit `e86227a`, built on `claude/comment-transition-oracle` in the worktree `D:\ct-oracle-wt` while the Phase 5 run used the main checkout)
- **Date:** 2026-11-12
- **ADR:** [0013](../adr/0013-real-project-comment-transition-oracle.md) (*Proposed*)
- **Goal:** Measure comment-transition detection on real histories. The published oracles of the papers contain no comment transitions, so the synthetic scenarios were the only evidence so far.

## What was done

1. A miner (`tools/MineCommentTransitions.java`) scanned all 10,039 commits of javaparser for code turned into `//` comments and back. Separate scans looked for `/* */` wrappers and for commented field declarations.
2. 13 elements were chosen from the candidates: 7 blocks, 4 methods and 2 variables. They include round trips, born-commented code, one `/* */` round trip, edits made while commented, a container renamed at the restore, and one negative case.
3. Each history was validated against git, not against the tool. The validation used `git log -L`, `tools/blockevo.sh` and `LocalOracleBuilder check`. Every correction and its reason is kept in `oracle/verdicts/`.
4. The offline commit cache was built from the local clone by `LocalOracleBuilder`, in the GitHub-API format (checked field by field against existing entries).
5. The new set is called `comment-transition`. It lives next to `training` and `test` and is read by opt-in tests (`-Dct.oracle=true`). The published oracles are unchanged.

Full description, per-history table and reproduction steps: [oracle/README.md](oracle/README.md).

## Results

| | TP | FP | FN | Precision | Recall |
|---|---|---|---|---|---|
| All change types (13 histories) | 51 | 3 | 13 | 94.4% | 79.7% |
| Comment-transition types only | 13 | 0 | 7 | 100% | 65% |

The three FPs are all `introduced` reported at a restore commit where the true origin is older.

## Defects found on real code (none fixed yet)

1. A block edited inside the comment, in the same commit that commented it out, loses its history (`reachedVariadicParam`).
2. A method renamed in the restoring commit loses its block's history (`BulkParseTest`, ADR 0008 limit).
3. `LexicalPreservingPrinter`: one of about 15 if-statements restored in the same commit (INITIALIZER, which contains a nested if/else) is missed, while its sibling TYPE is found.
4. Statement-level transitions nested two levels deep in a `for` loop are not reported.
5. A born-commented test method restored with identical text is missed (`annotationMemberDeclarationResolve`), while its twin in the same class is detected.

## Checks

- New oracle tests: 13/13 pass (7 block, 4 method, 2 variable; about 70 s).
- Fast checks: 27 scenarios and 71 unit tests pass.
- The 35 published histories whose commit-log files were extended were re-scored. All give their published TP/FP/FN.

## Open

- Attribute samples: javaparser never restores a commented field, so they must come from the next project.
- Diagnose defects 3 and 5. They are the cheapest to reproduce.
- The labelling conventions in ADR 0013 (item 6) need the author's decision.
- The ~247 cache entries are not in the `oracle` submodule. `LocalOracleBuilder cache` rebuilds them from a clone.
