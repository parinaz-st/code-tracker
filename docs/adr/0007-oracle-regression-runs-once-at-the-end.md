# 0007. The oracle regression run happens once, after the last phase

- Status: Accepted
- Date: 2026-11-11
- Phase: 1
- Decided by: Parinaz Sattarzadeh (branch author)

## Context

The block oracle (`BlockOracleTest`, 1,280 histories from real projects, read from the cached commits in `oracle/commits`) is the only check that the comment-transition work does not change results for ordinary code. A full run takes a long time, and a fair comparison needs two runs: the phase under test and a baseline. Running it after every phase would slow the work down a lot.

Some changes reach beyond comments, though. Phase 1 changed `ChangeHistory.matchElements`, which all trackers use, and the `BODY_CHANGE` handling in `BlockTrackerChangeHistory.isMatched`, which every block history goes through.

## Decision

1. After each phase, only the fast checks run: `CommentTransitionScenarioTest` (with `-Dct.spec=true`) and `CommentTransitionAnalyzerTest`, a few seconds in total.
2. The full block oracle runs **once, after the last phase**, on the final code and on the baseline (the Phase 0 commit `81dcf47`, which is the original feature with the pom fix).
3. Each phase stays its own commit. If the final run shows regressions, the oracle is run on the phase commits to find which phase caused them.
4. Each phase report lists the shared code it changed, so that a later regression can be traced quickly.

## Consequences

- Phases move faster.
- A regression caused by an early phase may only be found late, and later phases may build on it. The risk is limited, because the changes to shared code so far are small and listed in the phase reports.
- The oracle cannot run at all with the current `pom.xml`: JUnit 5.8.2 and 5.10.0 are mixed, so every `@MethodSource` test fails. The final run needs either a temporary pom with JUnit aligned to 5.10.0 (as tried in Phase 1), or a fix to `pom.xml` made as its own change.
