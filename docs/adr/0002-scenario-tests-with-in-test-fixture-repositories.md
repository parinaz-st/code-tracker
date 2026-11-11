# 0002. Scenario tests with in-test fixture repositories

- Status: Accepted
- Date: 2026-11-11
- Phase: 0

## Context

The existing block tests (`BlockTrackerImplTest`, `BlockOracleTest`) need network access to clone real repositories, or they depend on a hard-coded local clone path (`D:\work\programming\CodeTrackerTesting`). These tests are slow, and their histories are long and hard to reason about. Comment-out/uncomment cases are rare in the existing oracle, and the target behaviour is still being designed, so the expected change-type names will change.

## Decision

1. Each scenario builds its own throwaway Git repository with JGit (`FixtureRepo`), one labelled commit at a time (A, B, C, …, with fixed increasing dates). It then tracks one block with `CodeTracker.blockTracker().repository(...)`.
2. Expectations are written per commit label as lower-case **keywords** that must appear in `Change.Type#getTitle()` (`introduced`, `body change`, `commented out`, `uncommented`), not as enum names.
3. Every scenario also asserts two invariants: exactly one `introduced` change, and it is the oldest entry.
4. The test class (`CommentTransitionScenarioTest`) describes the **target** behaviour, so some scenarios fail until later phases are done. It only runs with `-Dct.spec=true`, so the CI build (`mvn package`) stays green.

## Consequences

- Fast (about 5 s for 10 scenarios), deterministic, and needs no network.
- The keyword matching tolerates renames of change types, but a title that contains a keyword by accident would match it. For example, any title containing "comment" counts in the negative controls.
- Because the class is opt-in, CI does not catch regressions in these scenarios. When the feature stabilizes, passing scenarios should move to a class that always runs.
- Real-world precision and recall still need the oracle (Phase 5). Synthetic fixtures only show that the mechanisms work.
