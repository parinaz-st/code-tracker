# 0004. An unresolved uncommented block ends the history with "introduced"

- Status: Proposed
- Date: 2026-11-11
- Phase: 1
- Related: analysis 4.1; report *Enhancing CodeTracker Accuracy*, Section 5.1 ("pending" restoration mark)

## Context

When `isBlockUncommented` detects a restored block at commit F, it records `UNCOMMENTED_BLOCK` on the edge from a **virtual** "before" node to the block at F, and puts the virtual node back in the tracking queue. The virtual node is built from the *current* method (`container2`), so it has the same identifier as the block at F. Phase 0 showed two problems:

1. In older commits the method body differs, so the virtual node is never found again. The history then has no `introduced` change at all (S1, S7), which is worse than the behaviour without the feature ("introduced at F").
2. `BlockTrackerImpl` never marks its start node with `setStart(true)`, so the block at F counts as a "leaf" in `ChangeHistory.connectRelatedNodes()`. Because the virtual node and the block at F share an identifier, `connectRelatedNodes` added a `NO_CHANGE` edge from F back to the virtual node. That created a 2-node cycle (`virtual → F → virtual`), and it also made the virtual node look resolved.

Real continuity (finding the earlier active version and recording `COMMENTED_OUT`) needs the commented-state tracking mode planned for Phase 2.

## Decision

1. `BlockTrackerChangeHistory` keeps a map of **pending** uncommented blocks (virtual before node → after node).
2. After the tracking loop, `closePendingUncommentedBlocks()` adds an `introduced` change (comment: "uncommented block; no earlier active version found") on the uncommented edge of every pending virtual node that has no older predecessor. It is called at the end of both `track()` and `blame()`. In `blame()`, the result is re-evaluated only when something was closed, so other blame results are unchanged.
3. `ChangeHistory.matchElements` (used by `connectRelatedNodes`) no longer adds a `NO_CHANGE` edge from a leaf to a root when the root already has an edge to that leaf, because that edge would close a cycle. This guard is generic and applies to all trackers.

This is the "pending → discarded" half of the report's Algorithm 3. The "pending → confirmed" half still happens only by accident: when some older commit has a byte-identical method body, `connectRelatedNodes` links the virtual node to it (S2, S4, S6).

## Consequences

- Every history has exactly one origin again (S1 and S7 pass the invariant). When the earlier version cannot be found, the uncommit commit shows both `introduced` and `uncommented block`, so the result is never worse than the baseline.
- The commented-out side (commit D) is still missing, and the accidental link depends on identical method bodies. Phase 2 replaces this with a real tracking mode.
- The cycle guard changes `ChangeHistory`, which all trackers share. It can only remove edges that would create a cycle, which a history graph must never contain. The block oracle run in phase-1.md checks for side effects.
- Open question: should `BlockTrackerImpl` call `setStart(true)` on its start node, as the attribute and class trackers do? It was left unchanged to keep this phase small. It deserves a separate check against the oracle.
