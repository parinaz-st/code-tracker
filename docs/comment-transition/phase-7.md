# Phase 7: Notes around commented-out code

- **Branch:** `claude/comment-transition-rework`
- **Date:** 2026-11-13
- **ADR:** [0014](../adr/0014-tolerate-notes-around-commented-code.md) (*Proposed*)
- **Goal:** Fix the main failure cause found by the real-project oracle (Phase 6). Developers put a note next to commented-out code, which made the commented run unparseable, so restorations were reported as `introduced`.

## What changed

| File | Change | ADR 0014 item |
|---|---|---|
| `util/CommentedCodeUncommenter.java` | If a `//` run with code-like lines does not parse: cut note text after the last `;{}` of a line (`} NOCOMMIT fix this`), and trim up to 3 *prose* lines at each end of the run; retry, fewest lines dropped first. Code-like lines are never trimmed. | 1–5 |
| `util/CommentTransitionAnalyzer.java` | `deletedComments` aligns parent and child comments in file order (longest common subsequence) instead of counting texts. A repeated line such as `//    }` is no longer credited to the wrong method. | 6 |
| `util/CommentTransitionAnalyzer.java` | New `deletedCommentRuns`: every comment of a run that lost at least one line. Used by method and attribute detection. | 7 |
| `util/CommentTransitionAnalyzer.java` | `findActiveBlock`: a signature-only match must share ≥ 50% of the target's leaf statements; the best match wins. Before, any block of the same kind and signature matched (`try`, `else`). | 8 |
| `MethodTrackerChangeHistory.java`, `AttributeTrackerChangeHistory.java` | Use `deletedCommentRuns`. | 7 |
| `BlockTrackerWithLocalFiles.java`, `MethodTrackerWithLocalFilesImpl.java`, `VariableTrackerWithLocalFiles.java` | `commits == null` guard, as the attribute tracker already had. Reaching older versions exposed it. | – |

## Tests

- **New unit tests:**
  - `CommentedCodeUncommenterTest`: 7 new, built from the real cases (prose above or below, `NOCOMMIT` and `NORELEASE` markers, prose-only runs, `} else` not cut);
  - `FindActiveBlockTest` (2);
  - `DeletedCommentRunsTest` (2).
- **Fast checks:** 27 scenarios and 82 unit tests pass.
- **One regression caught during development by an existing test:** the first version could trim a commented method down to `return x;` and accept that as a statement. It was fixed by allowing only non-code lines to be trimmed.

## Results on the real-project oracle (27 histories)

| | Before (Phase 6) | After (Phase 7) |
|---|---|---|
| javaparser, all types | 51 / 3 / 13 → P 94.4%, R 79.7% | 52 / 3 / 12 → P 94.5%, R 81.2% |
| elasticsearch, all types | 18 / 9 / 65 → P 66.7%, R 21.7% | **77 / 2 / 6 → P 97.5%, R 92.8%** |
| **Both, all types** | 69 / 12 / 78 → P 85.2%, R 46.9% | **129 / 5 / 18 → P 96.3%, R 87.8%** |
| **Both, transitions only** | 18 / 1 / 27 → P 94.7%, R 40.0% | **35 / 1 / 10 → P 97.2%, R 77.8%** |

(TP / FP / FN.) Ten histories improved and none got worse:

| History | Before | After | Fixed by |
|---|---|---|---|
| es `TransportTwoNodesSearchIT.testFailedSearchWithWrongQuery` | 0/1/24 | 24/0/0 | marker cut (`} NORELEASE …?`) |
| es `FunctionScoreIT.checkWeightOnlyCreatesBoostFunction` | 0/1/9 | 9/0/0 | marker cut (`} NOCOMMIT fix this`) |
| es `TemplateQueryIT.testRawFSTemplate` | 0/1/8 | 8/0/0 | prose trim (`// NORELEASE These need …`) |
| es `SearchFieldsIT.testSingleValueFieldDatatField` | 0/1/6 | 6/0/0 | marker cut + comment alignment (shared `} NOCOMMIT` line) |
| es `JavaJodaTimeDuellingTests.testCustomTimeFormats` | 0/1/5 | 4/0/1 | prose trim + whole runs (part of the comment moved) |
| es `JsonSourceFieldMapper.Builder.enabled` | 0/1/3 | 3/0/0 | prose trim |
| es `InternalTestCluster.ensureEstimatedStats` try | 0/1/3 | 3/0/0 | prose trim + active-block overlap (an unrelated `try` had been followed) |
| es `ESToParentBlockJoinQueryTests.expected` (variable) | 1/0/1 | 2/0/0 | prose trim |
| es `TransportEqlStatsAction.planExecutor` (attribute) | 1/0/1 | 2/0/0 | trim / alignment |
| jp `JavaParserAPIIntegrationTest.annotationMemberDeclarationResolve` | 1/0/1 | 2/0/0 | comment alignment (it was undiagnosed in Phase 6) |

## What is still missed (18 FN, 5 FP)

| History | Cause |
|---|---|
| jp `BulkParseTest` block, es `LifecyclePolicyTests` variable | Enclosing method renamed in the restoring commit (ADR 0008/0012 limit). |
| jp `reachedVariadicParam` block | Condition rewritten inside the comment. |
| es `JavaJodaTimeDuellingTests` | One FN: the `body change` made inside the comment at the comment-out commit (labelled by convention, ADR 0013). |
| jp `LexicalPreservingPrinter` INITIALIZER block | Not diagnosed. |
| jp `MethodResolutionLogic` FOR loop | Statement-level types are found but filtered out by `OracleTest` (scoring decision open). |
| es `Querier` block | Comment-out attributed to a merge commit instead of the first-parent commit. |
| es `SecurityActionFilter.authenticationService` | Born-commented field; not diagnosed. |

## Published-oracle regression

The four published oracles were re-run with the Phase 7 code (2026-11-13, 00:14–01:56) and compared with the Phase 5 final run (`reports-final`, code `c3994df`):

| Oracle | Phase 5 final | Phase 7 |
|---|---|---|
| Block | 1280 / 1280 | 1280 / 1280 |
| Method | 199 / 200 | 200 / 200 |
| Variable | 1416 / 1416 | 1416 / 1416 |
| Attribute | 480 / 480 | 480 / 480 |

**No regression.** The only difference is the method history `lucene-solr-Field-tokenStream`. Its expected values collide with an attribute history of the same file name (Phase 6 update), so whether it passes depends on the order in which the test classes load their expected files. It is not an effect of the code.

## Oracle extended: checkstyle and 6 more elasticsearch histories

The oracle now has **42 real histories**: javaparser 13, elasticsearch 20, checkstyle 9. They break down as 13 block, 14 method, 10 variable and 5 attribute histories. Each one was validated against git as in Phase 6 (verdicts in `oracle/verdicts/`).

- **checkstyle** (16,684 commits) has few restorations: it checks itself against commented-out code, so dead code is mostly deleted. It contributed the first **attribute round trips**: `TokenTypes.EOF`, `LITERAL_INTERFACE` and `RBRACK`, commented out in 2003 and restored later. Two of them had their initializer rewritten *inside the comment* (`RBRACK` three times) as the generated grammar changed.
- **elasticsearch** gained:
  - 4 variable round trips, including `var e3` in `KibanaThreadPoolIT` (2024), where the prose note was *kept* above the restored code;
  - 2 test methods, one commented out in two steps with 27 changes back to 2010.

| | TP | FP | FN | Precision | Recall |
|---|---|---|---|---|---|
| javaparser (13) | 52 | 3 | 12 | 94.5% | 81.2% |
| elasticsearch (20) | 118 | 3 | 9 | 97.5% | 92.9% |
| checkstyle (9) | 34 | 0 | 6 | 100% | 85.0% |
| **All change types (42)** | **204** | **6** | **27** | **97.1%** | **88.3%** |
| **Transitions only (42)** | **58** | **1** | **14** | **98.3%** | **80.6%** |

**New misses:**
- 4 initializer changes made inside comments (checkstyle `TokenTypes`; a known limit);
- 2 hidden statement-level transitions (checkstyle `XdocsPagesTest`; the scoring filter);
- `KibanaThreadPoolIT.e3`, not yet diagnosed.

All 42 histories pass their expected values (`-Dct.oracle=true`).
