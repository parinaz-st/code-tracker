# 0005. Comment transitions are recorded in addition to BODY_CHANGE

- Status: Proposed
- Date: 2026-11-11
- Phase: 1
- Related: analysis 4.3; open question 3 in the analysis

## Context

On `feature/CmtTransitionDetectionBlock`, when a matched block's body changed and `detectCommentTransitionsInsideMatchedBlock` found any comment transition, the transition types (`UNCOMMENTED_STATEMENT`, `COMMENTED_OUT_STATEMENT`) were recorded **instead of** `BODY_CHANGE`. A single uncommented line was enough to hide every other edit in the same commit (scenario S10). It also turns every true `body change` in the oracle into a false negative plus a false positive whenever a transition is detected.

## Decision

`isMatched` always records `BODY_CHANGE` for a block whose body changed. The detected comment transitions are added to the same edge as **additional** change types that explain part of the body change. They never replace it.

## Consequences

- No real edit is lost (S10 passes). For blocks without comment transitions, histories are identical to the baseline.
- Histories of uncommented statements carry two change types (for example, `body change` + `uncommented statement`). Tools and oracles that count change types will see one extra type per transition. Until the oracle vocabulary includes the new types, they count as false positives in accuracy experiments.
- The alternative of recording a transition *only* when it explains the whole body diff was rejected for now. It needs a reliable statement-level diff, which Phase 3 (RefactoringMiner re-mapping) will provide. If Phase 3 makes it cheap, this ADR can be superseded.
