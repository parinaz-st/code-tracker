# Architecture decision records

This folder records **significant architectural decisions** for this fork: decisions about tracking logic, element identity, matching, change classification, public APIs, data structures, dependencies, or the evaluation method. Ordinary implementation choices do not need an ADR (see "Rule zero" in `AGENTS.md`).

The format follows Michael Nygard, "Documenting Architecture Decisions" (2011). An ADR is short. Once accepted it is not rewritten: to change a decision, add a new ADR that **supersedes** the old one, and update the old one's status line to point to it.

## How to add one

1. Copy the template below into `NNNN-short-title.md`, using the next free number.
2. Set the status to **Proposed**. Only the developer moves it to **Accepted**.
3. Add a row to the index.
4. Link the ADR from the spec and plan document of the change that introduced it.

## Template

```markdown
# NNNN. Title

- Status: Proposed | Accepted | Superseded by NNNN | Deprecated
- Date: YYYY-MM-DD
- Spec: (link to the spec and plan document in docs/specs/, if any)

## Context
What problem needs a decision, and what forces are at play. Cite the evidence (code, tests, papers, oracle data).

## Decision
What we decided, in full sentences.

## Alternatives considered
The other options, and why each was not chosen.

## Consequences
What becomes easier or harder, including the known downsides and any effect on oracle or experiment results.
```

## Index

| # | Title | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
