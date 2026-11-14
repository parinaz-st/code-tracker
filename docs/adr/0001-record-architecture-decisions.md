# 0001. Record architecture decisions

- Status: Accepted
- Date: 2026-11-14
- Spec: none

## Context

This fork extends CodeTracker, a research tool whose accuracy is reported in published papers (FSE 2022, TSE 2024). Changes to tracking logic, identity, matching or change classification can change those results. Reviewers, including the upstream maintainers, need to know *why* each such choice was made, not only what the code does. Work is also done with AI coding agents, which start each session without the history of earlier decisions.

## Decision

Every significant architectural decision is recorded as an ADR in `docs/adr/`, using the template in `docs/adr/README.md`. The spec and plan document of the change (in `docs/specs/`) links to the ADRs it introduces.

## Alternatives considered

- **Commit messages only.** They are hard to find later and cannot be superseded explicitly.
- **One design document per feature.** It mixes decisions with plans and loses the reasoning when the document is rewritten.

## Consequences

- Decisions can be reviewed and reversed one at a time. A reversal is a new ADR that supersedes the old one, so the reasoning is kept.
- There is a small writing overhead for each significant decision.
