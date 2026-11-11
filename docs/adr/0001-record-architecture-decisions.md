# 0001. Record architecture decisions

- Status: Accepted
- Date: 2026-11-11
- Phase: 0

## Context

The comment-transition work changes how CodeTracker builds histories: it adds change types and new tracking states, and may affect the accuracy numbers reported for the tool. The work is done in phases on a separate branch (`claude/comment-transition-rework`), reviewed by the branch author, and later moved to `feature/CmtTransitionDetectionBlock` and possibly proposed upstream. Reviewers, including the upstream maintainers, will need to know *why* each choice was made, not only what the code does.

## Decision

Every significant design decision is recorded as an ADR in `docs/adr/`, using the template in `docs/adr/README.md`. Each phase also gets a report in `docs/comment-transition/phase-N.md` that lists what changed, the test results, and the ADRs it introduced.

## Consequences

- Decisions can be reviewed and reversed one at a time. A reversal is a new ADR that supersedes the old one, so the history of the reasoning is kept.
- There is a small writing overhead for each phase.
