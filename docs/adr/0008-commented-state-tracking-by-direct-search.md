# 0008. Commented-state tracking by a direct backward search

- Status: Proposed
- Date: 2026-11-11
- Phase: 2
- Related: analysis 4.1, 4.6, 5.1; report *Enhancing CodeTracker Accuracy*, Section 5.1 (Algorithm 3, pending → confirmed); partly supersedes the cycle guard in [0004](0004-pending-uncomment-falls-back-to-introduced.md)

## Context

After Phase 1, an uncommented block either ended its history with `introduced` (when no older version could be linked), or it was linked to an older version only when some older commit happened to have a byte-identical method body. The commit that commented the block out was never recorded (S1, S2, S4, S6).

The analysis (Section 5.1) proposed a "virtual model provider": rebuild each older commit's model with its commented-out code uncommented, and run the normal tracking steps on those models. That design reuses everything, but it needs the comment parser and in-memory model building planned for Phase 3, and it changes the hot path of the tracker.

## Decision

1. When the tracking queue polls a block that is a **pending** uncommented node (Phase 1), `BlockTrackerImpl.followCommentedBlock` handles it instead of the generic loop. Both `track()` and `blame()` do this.
2. The search walks back through the commits that touched the file, newest first, starting just before the uncommit commit (F). For each commit:
   - It finds the enclosing method by its signature (`equalIdentifierIgnoringVersionAndDocumentAndBody`). If the method is gone, it stops.
   - It looks for the block as **active code** (`CommentTransitionAnalyzer.findActiveBlock`): same kind of statement and same signature, ignoring whitespace. A candidate with an identical body is preferred.
   - If the block is active, the comment-out is **confirmed** (`BlockTrackerChangeHistory.confirmCommentedOut`). That method:
     - records `COMMENTED_OUT_BLOCK` on the edge from the active block to a node at the commit that commented it out (D),
     - links that node to the virtual node with `NO_CHANGE` when D is not the commit just before F,
     - removes the pending mark,
     - puts the active block in the queue, so normal tracking continues from it.
   - Otherwise, if the block's signature is found in the method's comments, it marks the commit as analysed and moves on.
   - Otherwise it stops. The block stays pending, and Phase 1's `closePendingUncommentedBlocks()` gives it an `introduced` origin. This is the report's "discarded" case.
3. A new change type `COMMENTED_OUT_BLOCK` ("commented out block") mirrors `UNCOMMENTED_BLOCK`.
4. The cycle guard in `ChangeHistory.matchElements` now checks **reachability** (`Graphs.reachableNodes`) instead of a direct edge. The confirmed chain `C → D → E(virtual) → F` is longer than two nodes, and `connectRelatedNodes` would otherwise link F (which shares the identifier of C's block whenever the bodies are equal) back to C.

## Consequences

- S1, S2, S4 and S6 now produce the full target history: A introduced, B and C body change, D `commented out block`, F `uncommented block`. S1 includes an unrelated method edit while the block was commented out.
- **Limitations**, all documented in the code:
  - The enclosing method must keep its signature during the commented period. A rename or move ends the search, and the block then gets `introduced` as before.
  - The block's signature must stay the same while it is commented out, and between the commented and the active form. Edits during the restore (S5) still fail; that is Phase 3.
  - Edits made *inside the comment* while commented out are not recorded as changes.
  - When several active blocks share the signature, the one with an identical body is chosen, otherwise the first. This could pick the wrong block in methods with repeated identical conditions.
  - Only statement-level comment transitions inside a matched block (Phase 1) and whole-block transitions (this phase) are covered. Method-level restoration (report Algorithm 3 proper) is Phase 4.
- The search adds model loads only for blocks that were detected as uncommented, so ordinary histories pay no extra cost.
- The reachability check runs for each leaf/root candidate pair in `connectRelatedNodes`. History graphs are small (one node per change), so the cost should be negligible, but this is shared code and the final oracle run (ADR 0007) should confirm it.
- The virtual-model design from the analysis is not dropped. Phase 3 may still introduce it if the direct search proves too narrow.
