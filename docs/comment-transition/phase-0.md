# Phase 0: Ground truth (scenario baseline)

- **Branch:** `claude/comment-transition-rework`, created from `feature/CmtTransitionDetectionBlock` at `7c8d874576a`
- **Date:** 2026-11-11
- **ADRs:** [0001](../adr/0001-record-architecture-decisions.md), [0002](../adr/0002-scenario-tests-with-in-test-fixture-repositories.md), [0003](../adr/0003-refactoringminer-version-3.1.6.md)
- **Goal:** Write down the target behaviour as tests *before* changing any logic, and measure where the branch and the code without the feature stand.

## Changes

| File | Change |
|---|---|
| `src/test/java/org/codetracker/commenttransition/FixtureRepo.java` | New. Builds a throwaway local Git repository with JGit, one labelled commit at a time (A, B, C, …) for `src/main/java/demo/Sample.java`. |
| `src/test/java/org/codetracker/commenttransition/CommentTransitionScenarioTest.java` | New. 10 scenarios that track the `if` block of `Sample.run(int)` from the last commit and compare the history with the target behaviour (ADR 0002). Opt-in with `-Dct.spec=true`. |
| `pom.xml` | `rm.version` changed from 3.1.4 to 3.1.6, because the branch did not compile (ADR 0003). |
| `docs/`, `AGENTS.md`, `CLAUDE.md` | Analysis, ADRs, this report, and the agent instructions. |

Run the scenarios with:

```bash
mvn -o test -Dtest=CommentTransitionScenarioTest -Dct.spec=true
```

## Scenarios

Every scenario also checks that the history has exactly one `introduced` change and that it is the oldest entry.

| # | Scenario | Target history (oldest → newest) |
|---|---|---|
| S1 | Whole block commented out (D), method edited while commented (E), block restored (F); report Fig. 9 | A intro, B body, C body, D commented out, F uncommented |
| S2 | Same as S1, but nothing else is edited while commented | same as S1 |
| S3 | One statement inside the block is commented out (D) and restored (F), with a method edit in between (E) | same as S1 |
| S4 | The commented-out code uses compact formatting (`//if(x>0){`) | same as S1 |
| S5 | The condition is edited during the restore (`x > 0` → `x >= 0`) | same as S1 |
| S6 | The block is commented out with `/* … */` | same as S1 |
| S7 | The block is first written as a comment (B) and uncommented later (C) | exactly one origin (open question 2) |
| S8 | Negative control: a prose comment is removed together with a real edit | A intro, B body |
| S9 | Negative control: a commented statement is removed in a **sibling** block | A intro, B body |
| S10 | A statement is uncommented in the same commit as a real edit | …, D commented out, F uncommented **and** body change |

## Results

The baseline is the branch's merge-base with `master` (`375d0bb6932`), which is the same code without the feature. Current `master` could not be built offline (ADR 0003).

| # | Baseline (no feature) | Branch `7c8d874` | Branch verdict |
|---|---|---|---|
| S1 | F intro | **F uncommented, no origin** | ✗ regression (analysis 4.1) |
| S2 | F intro | A intro, B, C body, F uncommented | ✗ D missing (4.1, 4.6) |
| S3 | A intro, B, C, D, F body | A intro, B, C body, D commented out stmt, F uncommented stmt | ✓ |
| S4 | F intro | F intro | ✗ not detected (4.2) |
| S5 | F intro | F intro | ✗ not detected (4.2) |
| S6 | F intro | A intro, B, C body, F uncommented | ✗ D missing (4.1, 4.6) |
| S7 | C intro | **C uncommented, no origin** | ✗ regression (4.1) |
| S8 | A intro, B body | A intro, B body | ✓ |
| S9 | A intro, B body | A intro, B body | ✓ |
| S10 | …, D body, F body | …, D commented out stmt, F uncommented stmt | ✗ real edit lost (4.3) |

**Pass count:** the baseline passes 3/10 (S7, S8, S9) and the branch passes 3/10 (S3, S8, S9).

## Conclusions

- The branch does well on statement-level detection (S3), on detecting a restored whole block with unchanged formatting (at commit F in S1, S2, S6), and on avoiding false positives (S8, S9).
- Confirmed defects:
  - 4.1: the history loses its origin (S1, S7, which are regressions compared with the baseline) or silently skips the commented period (S2, S6).
  - 4.2: formatting and edits defeat the hash matching (S4, S5).
  - 4.3: `BODY_CHANGE` is replaced by the comment transition (S10).
- **Not covered yet:**
  - the oracle regression run on real projects (`BlockOracleTest`, slow, needs the network),
  - oracle JSON entries for the new change types (to be added once the names are final),
  - a test for the keyword-prefix bug (4.4).
