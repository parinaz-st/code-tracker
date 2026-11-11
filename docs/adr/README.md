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

ADRs from Phase 1 onward stay **Proposed** until the branch author (Parinaz Sattarzadeh) reviews them and moves the changes to `feature/CmtTransitionDetectionBlock`.
