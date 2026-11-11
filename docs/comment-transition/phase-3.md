# Phase 3: Edit-tolerant uncomment detection

- **Branch:** `claude/comment-transition-rework` (on top of Phase 2, `faf5624`)
- **Date:** 2026-11-11
- **ADRs:** [0009](../adr/0009-virtual-uncomment-with-refactoringminer-mapping.md) (*Proposed*)
- **Goal:** Detect a block as uncommented even when it was edited while being restored (S5: condition changed; S11: a statement changed), by letting RefactoringMiner map a "virtual" parent in which the commented-out code is code again (option C in the analysis).

## How it works

```
parent file (commit E)                     virtual parent (in memory)                 current file (commit F)
-------------------------------            -------------------------------            -------------------------------
int result = 0;                            int result = 0;                            int result = 0;
// if (x > 0) {           ─uncomment─►        if (x > 0) {          ─RM maps─►      if (x >= 0) {
//     result = x * 2;    (in place, same      result = x * 2;      (tolerant)          result = x * 2;
//     count++;            offsets/lines)       count++;                                count++;
// }                                          }                                     }
```

1. The normal steps run first: refactored, matched, then the exact uncomment detection from Phase 1.
2. If the tracked block is still unmatched and the method lost comments in this commit:
   - `CommentedCodeUncommenter` uncomments every deleted comment run that the JDT parser accepts as Java statements.
   - The parent file is re-parsed.
   - RefactoringMiner maps the virtual parent method to the current method.
3. If RM maps the tracked block to a statement on an uncommented line, `uncommented block` is recorded, plus `expression change` and/or `body change` for the differences.
4. The pending entry remembers the **commented form**, so the Phase 2 search finds the earlier active version (`x > 0`) even though the restored block says `x >= 0`.

## Changes

| File | Change |
|---|---|
| `util/CommentedCodeUncommenter.java` (new) | Groups comments into runs, strips markers in place, and keeps a run only if JDT parses it as statements without errors. |
| `CommitFileSource.java` (new) | Reads a file at a commit or at its parent, for both trackers. |
| `BaseTracker.java` | `commitFileSource()` backed by Git. |
| `BaseTrackerWithLocalFiles.java` | `commitFileSource()` backed by the `oracle/commits` cache (no network). |
| `BlockTrackerChangeHistory.java` | `isBlockUncommentedWithEdits(...)`. `followCommentedBlock(...)` moved here from `BlockTrackerImpl`. The pending map stores the commented form (`PendingUncomment`). `setCommitFileSource(...)` and `isPendingUncommented(...)`. |
| `BlockTrackerImpl.java` | Uses the shared search. The hook now runs after the commit list is computed and reuses that list. |
| `BlockTrackerWithLocalFiles.java` | **Now wired up at all.** Sets the file source, intercepts pending nodes, and calls `closePendingUncommentedBlocks()`. |
| `pom.xml` | `org.eclipse.jdt.core` 3.47.0 as a direct dependency (same version RM resolves). |
| `CommentedCodeUncommenterTest.java` (new) | 17 unit tests: what parses as statements (code vs prose and fragments), in-place uncommenting of `//` and `/* */`, prose and partial runs left alone, Javadoc skipped. |
| `CommentTransitionScenarioTest.java` | New scenarios S11 (body edited during restore) and S12 (negative control). |

## Finding: the oracle tracker had none of the Phase 1–2 behaviour

`buildWithLocalFiles()` creates `BlockTrackerWithLocalFiles`, which has its **own copy** of the tracking loop and reads files from the `oracle/commits` cache. Phases 1 and 2 had only been wired into `BlockTrackerImpl`, so the oracle tests would have measured the original behaviour. This phase wires the local-files tracker to the same shared code. `AGENTS.md` now warns about the third copy of the loop.

## Scenario results

```bash
mvn -o test -Dtest='CommentTransitionScenarioTest,CommentTransitionAnalyzerTest,CommentedCodeUncommenterTest' -Dct.spec=true
```

Histories are listed oldest → newest.

| # | Phase 2 | Phase 3 | Verdict |
|---|---|---|---|
| S1–S4, S6–S10 | ✓ | unchanged | ✓ |
| S5 | F introduced | A intro, B body, C body, D commented out block, **F uncommented block + expression change** | ✓ |
| S11 (new) | F introduced *(expected)* | A intro, B body, C body, D commented out block, **F uncommented block + body change** | ✓ |
| S12 (new, negative) | B introduced *(expected)* | B introduced | ✓ |

**Pass count:** 12/12. Unit tests: 43 analyzer and 17 uncommenter, all passing.

The rows marked *(expected)* were not run on Phase 2. The scenarios were added in this phase. For S11, the Phase 2 result follows from the exact detection, which fails the body check when a statement differs.

## Shared code changed (for the final oracle run, ADR 0007)

- `BlockTrackerChangeHistory.checkBodyOfMatchedOperations`: one more check before `isAdded`. It only does work when the tracked block is unmatched **and** comments were deleted in that commit.
- `BlockTrackerWithLocalFiles.track()`: the Phase 1–3 hooks, which the oracle uses. This is the first time the oracle path runs any comment-transition code beyond the original branch's.

## Limitations (see ADR 0009)

- A run is uncommented only when it parses as complete statements on its own. A commented-out *signature line* alone (with an active body) is handled only by the exact detection.
- RefactoringMiner's mapping decides what "the same block" is. Precision on real code is unmeasured until the oracle run.
- Statement-level transitions inside a matched block still use hash matching (Phase 1).
- The older-commit search matches the commented form by signature. A signature change *while commented* is not followed.
- The local-files port is not covered by the scenarios, which use the Git tracker.
