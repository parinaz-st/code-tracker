# Phase 2: Commented-state tracking

- **Branch:** `claude/comment-transition-rework` (on top of Phase 1, `ae15cce`)
- **Date:** 2026-11-11
- **ADRs:** [0008](../adr/0008-commented-state-tracking-by-direct-search.md) (*Proposed*); [0004](../adr/0004-pending-uncomment-falls-back-to-introduced.md) partly superseded (cycle guard)
- **Goal:** When a block is restored from a comment, follow it back through the commits where it was commented out, record the commit that commented it out, and continue its normal history. This is the "pending → confirmed" half of the report's Algorithm 3.

## How it works

```
A ── B ── C ──────────── D ───────────── E ────────── F
intro body body   commented out   (still commented)   uncommented
            │                │                 │            │
   active block ──COMMENTED_OUT_BLOCK──► node@D ──NO_CHANGE──► virtual@E ──UNCOMMENTED_BLOCK──► block@F
   (tracking continues normally from here)
```

1. Phase 1 already detects the uncommented block at F and queues a *pending* virtual node for E.
2. When the queue polls that node, `BlockTrackerImpl.followCommentedBlock` walks the file's commits back from F:
   - The enclosing method is found by signature.
   - If the block is **active** there, the comment-out is confirmed (`confirmCommentedOut`) and the active block is queued for normal tracking.
   - If the block is found in the method's **comments**, the search moves on to the next older commit.
   - Otherwise the search stops, and Phase 1's `closePendingUncommentedBlocks` gives the block an `introduced` origin.
3. The confirmed chain is `active@C → node@D → virtual@E → block@F`, with the change types shown above. `NO_CHANGE` edges are not listed in the history.

## Changes

| File | Change |
|---|---|
| `change/Change.java`, `change/ChangeFactory.java`, `change/block/CommentedOutBlock.java` (new) | New change type `COMMENTED_OUT_BLOCK` ("commented out block"). |
| `BlockTrackerImpl.java` | `followCommentedBlock(...)`, called from both `track()` and `blame()` when a pending uncommented node is polled. |
| `BlockTrackerChangeHistory.java` | `getPendingUncommentedBlock(...)` and `confirmCommentedOut(...)`. |
| `util/CommentTransitionAnalyzer.java` | `findActiveBlock(...)` (same kind and signature, an identical body preferred) and `containsCommentedBlock(...)`. |
| `ChangeHistory.java` | The cycle guard now checks reachability instead of a direct edge (shared by all trackers). |
| `CommentTransitionScenarioTest.java` | New check on every scenario: the history graph is one chain, with one root, one sink, and every node reachable from the root. |
| `AGENTS.md` | Corrected: REST and the oracle look change types up by title, so new types need no extra mapping there. |

## Scenario results

```bash
mvn -o test -Dtest='CommentTransitionScenarioTest,CommentTransitionAnalyzerTest' -Dct.spec=true
```

Histories are listed oldest → newest.

| # | Phase 1 | Phase 2 | Verdict |
|---|---|---|---|
| S1 | F introduced + uncommented block | A intro, B body, C body, **D commented out block**, F uncommented block | ✓ |
| S2 | A, B, C, F uncommented block | A intro, B body, C body, **D commented out block**, F uncommented block | ✓ |
| S3 | ✓ | unchanged | ✓ |
| S4 | A, B, C, F uncommented block | A intro, B body, C body, **D commented out block**, F uncommented block | ✓ |
| S5 | F introduced | F introduced | ✗ (Phase 3: edit during restore) |
| S6 | A, B, C, F uncommented block | A intro, B body, C body, **D commented out block**, F uncommented block | ✓ |
| S7 | C introduced + uncommented block | unchanged | ✓ |
| S8 | ✓ | unchanged | ✓ |
| S9 | ✓ | unchanged | ✓ |
| S10 | ✓ | unchanged | ✓ |

**Pass count:** 9/10 (Phase 0: 3/10, Phase 1: 5/10). The 43 analyzer unit tests pass.

**Check that the tests detect the feature:** with `followCommentedBlock` disabled, S1, S2, S4 and S6 fail again (the keyword checks catch the missing D), so the scenarios cover the new behaviour. The new graph-chain check did **not** fire in that run, because the Phase 1-style histories are still connected graphs. It is a safety net against cycles and disconnected pieces, but it has not yet been seen to fail.

## Shared code changed (for the final oracle run, ADR 0007)

- `ChangeHistory.matchElements`: reachability-based cycle guard. It affects all trackers, and it runs once per leaf/root candidate pair in `connectRelatedNodes`.
- `BlockTrackerImpl.track()/blame()`: the new branch only runs for pending uncommented nodes, so ordinary histories take the same path as before.

## Limitations (see ADR 0008)

- The enclosing method must keep its signature while the block is commented out.
- The block must keep its signature between the active, commented, and restored forms (S5 is Phase 3).
- Edits made inside the comment while it is commented out are not recorded.
- If several active blocks share the signature, the first one with an identical body is chosen, otherwise the first one.
- Only blocks. Method-level restoration (report Algorithm 3) is Phase 4.

## Not done

- Oracle JSON for `commented out block` / `uncommented block` / `commented out statement` / `uncommented statement`. The names now look stable, so this can be done whenever you approve them (open question 3 in the analysis).
- The oracle regression run (postponed, ADR 0007).
