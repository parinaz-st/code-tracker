# Phase 6: Real-project oracle (javaparser)

- **Branch:** `claude/comment-transition-rework` (commit `e86227a`, built on `claude/comment-transition-oracle` in the worktree `D:\ct-oracle-wt` while the Phase 5 run used the main checkout)
- **Date:** 2026-11-12
- **ADR:** [0013](../adr/0013-real-project-comment-transition-oracle.md) (*Proposed*)
- **Goal:** Measure comment-transition detection on real histories. The published oracles of the papers contain no comment transitions, so the synthetic scenarios were the only evidence so far.

## What was done

1. A miner (`tools/MineCommentTransitions.java`) scanned all 10,039 commits of javaparser for code turned into `//` comments and back. Separate scans looked for `/* */` wrappers and for commented field declarations.
2. 13 elements were chosen from the candidates: 7 blocks, 4 methods and 2 variables. They include round trips, born-commented code, one `/* */` round trip, edits made while commented, a container renamed at the restore, and one negative case.
3. Each history was validated against git, not against the tool. The validation used `git log -L`, `tools/blockevo.sh` and `LocalOracleBuilder check`. Every correction and its reason is kept in `oracle/verdicts/`.
4. The offline commit cache was built from the local clone by `LocalOracleBuilder`, in the GitHub-API format (checked field by field against existing entries).
5. The new set is called `comment-transition`. It lives next to `training` and `test` and is read by opt-in tests (`-Dct.oracle=true`). The published oracles are unchanged.

Full description, per-history table and reproduction steps: [oracle/README.md](oracle/README.md).

## Results

| | TP | FP | FN | Precision | Recall |
|---|---|---|---|---|---|
| All change types (13 histories) | 51 | 3 | 13 | 94.4% | 79.7% |
| Comment-transition types only | 13 | 0 | 7 | 100% | 65% |

The three FPs are all `introduced` reported at a restore commit where the true origin is older.

## Defects found on real code (none fixed yet)

1. A block edited inside the comment, in the same commit that commented it out, loses its history (`reachedVariadicParam`).
2. A method renamed in the restoring commit loses its block's history (`BulkParseTest`, ADR 0008 limit).
3. `LexicalPreservingPrinter`: one of about 15 if-statements restored in the same commit (INITIALIZER, which contains a nested if/else) is missed, while its sibling TYPE is found.
4. ~~Statement-level transitions nested two levels deep in a `for` loop are not reported.~~ **Correction:** they *are* reported. `OracleTest` filters the classes out of scoring (they extend `BlockBodyChange`); see the update below.
5. A born-commented test method restored with identical text is missed (`annotationMemberDeclarationResolve`), while its twin in the same class is detected.

## Checks

- New oracle tests: 13/13 pass (7 block, 4 method, 2 variable; about 70 s).
- Fast checks: 27 scenarios and 71 unit tests pass.
- The 35 published histories whose commit-log files were extended were re-scored. All give their published TP/FP/FN.

## Open

- Attribute samples: javaparser never restores a commented field, so they must come from the next project.
- Diagnose defects 3 and 5. They are the cheapest to reproduce.
- The labelling conventions in ADR 0013 (item 6) need the author's decision.
- The ~247 cache entries are not in the `oracle` submodule. `LocalOracleBuilder cache` rebuilds them from a clone.

---

## Update: elasticsearch and the Phase 5 regression result

- **Date:** 2026-11-12, session 2.
- Full details: [oracle/README.md](oracle/README.md).

### Phase 5: oracle regression, baseline vs final

| Oracle | Baseline (author's prototype, `81dcf47`) | Final (`c3994df`) |
|---|---|---|
| Block | 1269 / 1280 | **1280 / 1280** |
| Method | 200 / 200 | 199 / 200 * |
| Variable | 1416 / 1416 | 1416 / 1416 |
| Attribute | 480 / 480 | 480 / 480 |

- **The baseline's 11 block failures are all false negatives:** javaparser `SourceFileInfoExtractor.solve` (7), javaparser `Difference.apply` (3), and one checkstyle history. In the three histories re-run with both versions, the cause is the same: the prototype replaces `body change` with `commented out statement` / `uncommented statement` whenever one line in the block matches a deleted or added comment (for example, `re.printStackTrace();` commented out in `558fc9b`). Those classes extend `BlockBodyChange`, which `OracleTest` filters out, so the commit disappears and the oracle's `body change` is missed. Phase 1 keeps `body change`, so the final code passes them all.
- **\* The method "failure" is a harness artifact, not a regression.** `lucene-solr-Field-tokenStream.json` exists both as a method history (expected TP 26) and as an attribute history (expected TP 4). It is the only duplicate file name across the published oracles. `OracleTest` keys expected values by file name in shared static maps, so the last loaded value wins. The final code's method result (26/0/0) is exactly right.
- **Conclusion:** no regression in any published oracle.

### elasticsearch: 14 more histories

- The 14 histories are 4 block, 6 method, 2 variable and 2 attribute. They include the first attribute samples; both fields were first written as comments, because neither project has a field that went active → commented → active.
- New infrastructure:
  - `AttributeOracle.commentTransition()` and `AttributeCommentTransitionOracleTest`;
  - `LocalOracleBuilder -Dct.nocache=true` for helper runs that track an element from its last active version;
  - about 1,840 cache files and folders, rebuilt with `LocalOracleBuilder cache`.
- **Results:**

  | | TP | FP | FN | Precision | Recall |
  |---|---|---|---|---|---|
  | elasticsearch, all change types | 18 | 9 | 65 | 66.7% | 21.7% |
  | elasticsearch, transitions only | 5 | 1 | 20 | 83.3% | 20.0% |
  | Both projects (27), all change types | 69 | 12 | 78 | 85.2% | 46.9% |
  | Both projects, transitions only | 18 | 1 | 27 | 94.7% | 40.0% |

- **Main new finding: text around the commented code.** The uncommenter accepts a run of `//` lines only if the whole run parses. Real developers add a prose line above the code (`// source is always enabled for now`) or a marker inside it (`//    } NOCOMMIT fix this`). This one cause explains 8 of the 14 elasticsearch misses.
- **Other new findings:**
  - a comment-out attributed to a merge commit instead of the first-parent commit (`Querier`);
  - a test extended inside the comment and later restored to its original (`JavaJodaTimeDuellingTests`);
  - a check that was commented out and re-enabled twice over two years (`InternalTestCluster`).
- **Checks:** all 27 real histories pass their expected values (11 block, 10 method, 4 variable, 2 attribute).

### Open

- Fix the prose/marker problem in `CommentedCodeUncommenter`: trim lines that do not parse at the edges of a run, cut trailing non-code text, and retry. Then re-score both projects and the published oracles.
- Decide whether statement-level types should count in oracle scoring (finding 8 in the oracle README, ADR 0005).
- Merge commits: should the commented-state search follow first parents only?
- The 13 elasticsearch `/* */` unwraps that enclose code are still unexamined candidates.
