# Architecture Decision Records

This folder records significant design decisions for this fork, in the format proposed by Michael Nygard ("Documenting Architecture Decisions", 2011). Each record is short and immutable once accepted. To change a decision, add a new ADR that **supersedes** the old one, and update the old one's status line to point to it.

## Template

```markdown
# NNNN. Title

- Status: Proposed | Accepted | Superseded by NNNN | Deprecated
- Date: YYYY-MM-DD
- Phase: (which phase of docs/comment-transition/ introduced it)

## Context
What forces are at play, and what problem needs a decision.

## Decision
What we decided, stated in full sentences.

## Consequences
What becomes easier or harder, including the known downsides.
```

## Index

| # | Title | Status | Phase |
|---|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted | 0 |
| [0002](0002-scenario-tests-with-in-test-fixture-repositories.md) | Scenario tests with in-test fixture repositories | Accepted | 0 |
| [0003](0003-refactoringminer-version-3.1.6.md) | Build the branch against RefactoringMiner 3.1.6 | Accepted | 0 |
| [0004](0004-pending-uncomment-falls-back-to-introduced.md) | An unresolved uncommented block ends the history with "introduced" | Proposed (guard superseded by 0008) | 1 |
| [0005](0005-comment-transitions-complement-body-change.md) | Comment transitions are recorded in addition to BODY_CHANGE | Proposed | 1 |
| [0006](0006-whitespace-insensitive-code-matching.md) | Whitespace-insensitive matching and trivial-line filtering | Proposed | 1 |
| [0007](0007-oracle-regression-runs-once-at-the-end.md) | The oracle regression run happens once, after the last phase | Accepted | 1 |
| [0008](0008-commented-state-tracking-by-direct-search.md) | Commented-state tracking by a direct backward search | Proposed | 2 |
| [0009](0009-virtual-uncomment-with-refactoringminer-mapping.md) | Edit-tolerant uncomment detection with a virtual parent and RefactoringMiner mapping | Proposed | 3 |
| [0010](0010-commented-out-methods.md) | Commented-out methods (report Algorithm 3) | Proposed | 4a |
| [0011](0011-commented-out-attributes.md) | Commented-out attributes | Proposed | 4b |
| [0012](0012-commented-out-variables.md) | Commented-out variables | Proposed | 4c |
| [0013](0013-real-project-comment-transition-oracle.md) | A real-project oracle for comment transitions | Proposed | 6 |
| [0014](0014-tolerate-notes-around-commented-code.md) | Tolerate notes around commented-out code | Proposed | 7 |

ADRs from Phase 1 onward stay **Proposed** until the branch author (Parinaz Sattarzadeh) reviews them and moves the changes to `feature/CmtTransitionDetectionBlock`.
