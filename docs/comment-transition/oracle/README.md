# Comment-transition oracle (real projects)

This folder documents an oracle of **real** change histories, taken from open-source projects, in which code was commented out and/or restored. It complements the synthetic scenario tests of Phases 0–4. It uses the same format and the same TP/FP/FN scoring as the published CodeTracker oracles: FSE 2022 for methods and variables, TSE 2024 for blocks. The design decisions are in [ADR 0013](../../adr/0013-real-project-comment-transition-oracle.md).

- **Projects:**
  - javaparser (local clone `D:\GitHubProjects\javaparser`, HEAD `2761dca`, 10,039 commits);
  - elasticsearch (local clone `D:\GitHubProjects\elasticsearch`, HEAD `29caa16`, 100,544 commits);
  - checkstyle (local clone `D:\GitHubProjects\checkstyle`, HEAD `6b82789`, 16,684 commits).
- **Histories:** 42 in total: 13 block, 14 method, 10 variable, 5 attribute. javaparser has 13, elasticsearch 20, checkstyle 9.
- **Run:**

  ```bash
  mvn test -Dtest='BlockCommentTransitionOracleTest,MethodCommentTransitionOracleTest,VariableCommentTransitionOracleTest,AttributeCommentTransitionOracleTest' -Dct.oracle=true
  ```

  Use the JUnit-aligned temporary pom, as in `tools/run-oracles.sh`, while the 5.8.2/5.10.0 mix in `pom.xml` is not fixed. The run takes about 3 minutes. All 42 histories reproduce their expected TP/FP/FN.

## Files

| Path | Content |
|---|---|
| `src/main/resources/oracle/<kind>/comment-transition/*-ct.json` | The histories, in the published oracle format. |
| `src/test/resources/<kind>/comment-transition-expected.txt` | Current TP, FP, FN of each history: a regression baseline, not a target. |
| `src/test/resources/<kind>/<repo>-<Class>-<method>.json` | Commit logs read by the `*WithLocalFiles` trackers. Two javaparser files shared with the published oracle were *extended* (ADR 0013, item 4). |
| `docs/comment-transition/oracle/verdicts/<kind>/*.verdict.json` | For every history: how it was validated, and the tool changes that were dropped or added, with the reasons. |
| `src/test/java/org/codetracker/util/LocalOracleBuilder.java` | Builds, checks, finalizes and scores histories, and builds the commit cache from a local clone. |
| `src/test/java/org/codetracker/util/*CommentTransitionOracleTest.java` | Opt-in oracle tests (`-Dct.oracle=true`). |
| `docs/comment-transition/tools/MineCommentTransitions.java` | Miner that finds candidate comment-out/uncomment commits in a clone. |
| `docs/comment-transition/tools/blockevo.sh` | Prints a block's text at every version of a file and reports changes and code/commented/absent states. |

## How the histories were built

1. **Mining.** `MineCommentTransitions.java` reads `git log -p -U0` and finds commits where removed code lines come back as added `//` lines (comment out), or the reverse (uncomment). It pairs the two on the same file. Separate scans looked for `/* … */` wrappers that enclose code, and for field declarations.

   | | javaparser | elasticsearch | checkstyle |
   |---|---|---|---|
   | comment-out / uncomment events (≥ 3 lines) | 50 / 21 | 138 / 94 | 14 / 10 |
   | same-file pairs (≥ 3 lines) | 6 | 39 | 3 |
   | events / pairs with ≥ 1 line | – | – | 66 / 9 |
   | `/* */` unwraps enclosing code | not measured (1 real round trip found by hand) | 13 (≥ 3 lines) | 7 (≥ 2 lines), all deletions of dead code |
   | restored fields | 0 | 3 (all born commented) | 3 round trips (`TokenTypes`) |

   checkstyle has the fewest: it runs its own checks against commented-out code on itself (for example, commit `6c19362`: *"Sections of code should not be commented out. Issue #46"*), so dead code is mostly deleted, not restored.

2. **Selection.** Each candidate was inspected in git and classified as a round trip (active → commented → active), born-commented (first written as a comment, later made code), or out of scope (see *Rejected candidates*). Production code and test code were both used. Cases that hit known limits were kept on purpose, because they measure how often those limits occur.
3. **Tool run.** `LocalOracleBuilder <kind> <clone> <history>` runs the git-based tracker on the clone and saves its output. It also writes the commit logs and cache entries for the offline tracker.
4. **Validation**, independent of the tool:
   - `git log -L` on the element's exact line range, with the range found by brace matching;
   - `tools/blockevo.sh` on the element's text at every version;
   - `LocalOracleBuilder check`, which flags every *unreported* commit that changes one of the element's lines.

   When the tool lost a history at a restore, the active period before the comment-out was tracked separately. The run started from the element's **last active version**, using `-Dct.nocache=true` so no cache entries are written, and the result was validated the same way. Every flagged commit was read by hand.
5. **Finalize.** `LocalOracleBuilder finalize` writes the validated changes, which are the tool output minus the dropped changes plus the added ones from the verdict file.
6. **Score.** `LocalOracleBuilder score` runs the offline tracker exactly as `OracleTest` does. Its numbers equal the git-based ones for all 27 histories.

## Results

TP/FP/FN as `OracleTest` counts them (one key per commit and change type). *Transitions* counts only the 10 comment-transition change types.

> **Phase 7 update.** The per-history tables below show the Phase 6 results, which motivated the fix. After the Phase 7 fix (ADR 0014), ten histories improve and none gets worse. See the summary table and [phase-7.md](../phase-7.md) for the per-history before/after values. `comment-transition-expected.txt` holds the Phase 7 values.

### javaparser (13 histories, Phase 6 results)

| Kind | History (`javaparser-…-ct.json`) | Case | TP | FP | FN | Transitions found |
|---|---|---|---|---|---|---|
| block | MethodResolutionLogic-isApplicable-IF_STATEMENT-vararg-break | round trip, text unchanged | 3 | 0 | 0 | 2/2 |
| block | MethodResolutionLogic-isMoreSpecific-IF_STATEMENT-vararg | round trip, text unchanged | 3 | 0 | 0 | 2/2 |
| block | LexicalPreservingPrinter-concretePropertyChange-IF_STATEMENT-TYPE | round trip inside an anonymous class | 6 | 0 | 0 | 2/2 |
| block | LexicalPreservingPrinter-concretePropertyChange-IF_STATEMENT-INITIALIZER | same commit pair as TYPE | 0 | 1 | 4 | 0/2 |
| block | MethodResolutionLogic-isApplicable-FOR_STATEMENT | statements inside the loop commented and restored | 22 | 0 | 2 | 0/2 ¹ |
| block | MethodResolutionLogic-isApplicable-IF_STATEMENT-reachedVariadicParam | round trip, **edited while commented** | 2 | 1 | 4 | 1/2 |
| block | BulkParseTest-parseOpenJdkSourceRepository-IF_STATEMENT | round trip, **method renamed at the restore** | 1 | 1 | 2 | 1/2 |
| method | NodeWithMembersBuildersTest-testGetConstructorsWithParameterTypes | **`/* … */`** round trip, body rewritten | 4 | 0 | 0 | 2/2 |
| method | AnnotationDeclarationTransformationsTest-removingJavadoc | born commented, file moved while commented | 2 | 0 | 0 | 1/1 |
| method | JavaParserAPIIntegrationTest-enumConstantDeclarationResolve | born commented | 2 | 0 | 0 | 1/1 |
| method | JavaParserAPIIntegrationTest-annotationMemberDeclarationResolve | born commented, identical text | 1 | 0 | 1 | 0/1 |
| variable | MethodResolutionLogic-isApplicable-reachedVariadicParam | born commented | 2 | 0 | 0 | 1/1 |
| variable | MethodResolutionLogic-isApplicable-expectedType | **negative:** active copy kept next to a commented one | 3 | 0 | 0 | none expected, none reported |
| | **Total** | | **51** | **3** | **13** | **13/20** |

¹ The tool *does* report both statement-level transitions here. The scoring hides them, because `CommentedOutCodeInBlock` and `UncommentedCodeInBlock` extend `BlockBodyChange`, and `OracleTest.processHistory` skips that class (see finding 8). Without that filter this row is 24/0/0 and 2/2.

### elasticsearch (14 histories)

| Kind | History (`elasticsearch-…-ct.json`) | Case | TP | FP | FN | Transitions found |
|---|---|---|---|---|---|---|
| block | MutableSearchResponse-createSearchResponseMerger-IF_STATEMENT | production code, round trip, text unchanged (2024) | 3 | 0 | 0 | 2/2 |
| block | ESIntegTestCase-indexSettings-IF_STATEMENT | round trip, **edited while uncommented** | 7 | 0 | 0 | 2/2 |
| block | Querier-query-IF_STATEMENT | production code, round trip across a **branch merge** | 5 | 1 | 1 | 1/2 |
| block | InternalTestCluster-ensureEstimatedStats-TRY_STATEMENT | round trip; its statement was commented out twice over two years | 0 | 1 | 3 | 0/2 |
| method | JsonSourceFieldMapper-enabled | production code (2010), inner class, round trip, identical text | 0 | 1 | 3 | 0/2 |
| method | JavaJodaTimeDuellingTests-testCustomTimeFormats | round trip, **extended inside the comment**, restored to the original | 0 | 1 | 5 | 0/2 |
| method | FunctionScoreIT-checkWeightOnlyCreatesBoostFunction | round trip with `} NOCOMMIT fix this` marker, file move and rename | 0 | 1 | 9 | 0/2 |
| method | SearchFieldsIT-testSingleValueFieldDatatField | same refactoring, same marker | 0 | 1 | 6 | 0/2 |
| method | TemplateQueryIT-testRawFSTemplate | round trip under a `NORELEASE` note, rewritten | 0 | 1 | 8 | 0/2 |
| method | TransportTwoNodesSearchIT-testFailedSearchWithWrongQuery | round trip with a `NORELEASE` marker; 21 older changes since 2010 | 0 | 1 | 24 | 0/2 |
| variable | LifecyclePolicyTests-testToSteps-client | round trip, **method renamed at the restore** | 0 | 1 | 3 | 0/2 |
| variable | ESToParentBlockJoinQueryTests-testRewrite-expected | born commented, under a `do this once LUCENE-7685 …` note | 1 | 0 | 1 | 0/1 |
| attribute | SecurityActionFilter-authenticationService | born commented (2014) | 1 | 0 | 1 | 0/1 |
| attribute | TransportEqlStatsAction-planExecutor | born commented (2020) | 1 | 0 | 1 | 0/1 |
| | **Total** | | **18** | **9** | **65** | **5/25** |

### checkstyle (9 histories, added in Phase 7; results with the Phase 7 code)

| Kind | History (`checkstyle-…-ct.json`) | Case | TP | FP | FN |
|---|---|---|---|---|---|
| attribute | TokenTypes-EOF | round trip (2003), text unchanged | 3 | 0 | 0 |
| attribute | TokenTypes-LITERAL_INTERFACE | round trip; initializer **rewritten inside the comment** | 4 | 0 | 1 |
| attribute | TokenTypes-RBRACK | round trip; initializer rewritten inside the comment **three times** | 3 | 0 | 3 |
| block | Verifier-verifyOpBegin-IF_STATEMENT | round trip, condition extended at the restore | 4 | 0 | 0 |
| block | XdocsPagesTest-testAllStyleRules-ENHANCED_FOR_STATEMENT | one assertion disabled "till issue 5817" and restored | 10 | 0 | 2 ¹ |
| variable | PackageHtmlCheckTest-testPackageHtml-packageHtmlPath | round trip, text unchanged | 3 | 0 | 0 |
| variable | BaseCheckTestCase-createChecker-c | born commented | 2 | 0 | 0 |
| method | InputValidIfIndent-parenIfTest (test input) | born commented, extended at the restore | 3 | 0 | 0 |
| method | InputJavadocStyleCheck-method10 (test input) | born commented | 2 | 0 | 0 |
| | **Total** | | **34** | **0** | **6** |

### elasticsearch additions (6 histories, added in Phase 7; results with the Phase 7 code)

| Kind | History (`elasticsearch-…-ct.json`) | Case | TP | FP | FN |
|---|---|---|---|---|---|
| method | TransportSearchFailuresIT-testFailedSearchWithWrongQuery | commented out in two steps (loop, then whole method); 27 changes since 2010 | 27 | 0 | 0 |
| method | RangeQueryBuilderTest-testToQueryNonDateWithTimezone | round trip with its Javadoc, file moved twice before | 5 | 0 | 0 |
| variable | ChildQuerySearchIT-testQueryBeforeChildType-resp | round trip (2015 refactoring) | 3 | 0 | 0 |
| variable | GroovySecurityIT-assertSuccess-resp | round trip (2015 refactoring) | 3 | 0 | 0 |
| variable | OnDiskScriptIT-testFieldOnDiskScript-searchResponse | round trip (2015 refactoring) | 3 | 0 | 0 |
| variable | KibanaThreadPoolIT-assertThreadPoolsBlocked-e3 | round trip (2024), prose note **kept** above the restored code, nested `// //` comment | 0 | 1 | 3 |
| | **Total** | | **41** | **1** | **3** |

### Summary

TP / FP / FN, precision, recall:

| | Phase 6 (before the fix) | Phase 7 (after ADR 0014) |
|---|---|---|
| javaparser, all change types | 51 / 3 / 13 · 94.4% · 79.7% | 52 / 3 / 12 · 94.5% · 81.2% |
| elasticsearch, all change types | 18 / 9 / 65 · 66.7% · 21.7% | 77 / 2 / 6 · 97.5% · 92.8% |
| **Both, all change types** | **69 / 12 / 78 · 85.2% · 46.9%** | **129 / 5 / 18 · 96.3% · 87.8%** |
| **Both, transitions only** | **18 / 1 / 27 · 94.7% · 40.0%** | **35 / 1 / 10 · 97.2% · 77.8%** |

**All 42 histories** (javaparser 13, elasticsearch 20, checkstyle 9), Phase 7 code:

| | TP | FP | FN | Precision | Recall |
|---|---|---|---|---|---|
| javaparser (13) | 52 | 3 | 12 | 94.5% | 81.2% |
| elasticsearch (20) | 118 | 3 | 9 | 97.5% | 92.9% |
| checkstyle (9) | 34 | 0 | 6 | 100% | 85.0% |
| **All change types (42)** | **204** | **6** | **27** | **97.1%** | **88.3%** |
| **Transitions only (42)** | **58** | **1** | **14** | **98.3%** | **80.6%** |

**How to read these numbers.** The samples were chosen to contain comment transitions, and every missed restoration costs the element's *whole* earlier history (one miss in `TransportTwoNodesSearchIT` alone was 24 FNs). So the Phase 6 numbers are not comparable to the papers' 99% on random samples. They showed where the feature failed on real code, and they gave the baseline that Phase 7 was measured against.

## What the real samples show (tool findings)

Ordered by how many real histories each cause affects.

1. **Text around the commented code makes it unparseable.** This affects 8 elasticsearch histories (found by inspecting their commented runs; the fix below would confirm it). `CommentedCodeUncommenter` groups consecutive `//` lines into one run and accepts the run only if the *whole run* parses as Java. Developers almost always add a note:
   - a prose line *above* the code: `// source is always enabled for now`, `// this test requires tests to run with -Djava.locale.providers=COMPAT`, `// Mute this assertion until we have a new Lucene snapshot …`, `// do this once LUCENE-7685 is addressed`, `// NORELEASE These need to be tested in TemplateQueryBuilderTests`;
   - a marker *inside* the code: `//    } NOCOMMIT fix this`, `//    } NORELEASE can this still be tested? if so, how?`.

   Either way the restoration is not recognised, and the tool reports `introduced`. The synthetic scenarios never had such lines. **Fixed in Phase 7** (ADR 0014): note lines at the edges of a run are trimmed, and note text after the last code token is cut. Two related problems were fixed at the same time:
   - comments are now aligned in file order, so a shared line such as `//    } NOCOMMIT fix this` is attributed to the right method;
   - the commented-state search no longer follows an unrelated block with the same `try` signature.
2. **A container renamed at the restore loses the history.** This affects 2 histories: javaparser `BulkParseTest` (`parseOpenJdk` became `parseOpenJdkSourceRepository`) and elasticsearch `LifecyclePolicyTests` (`testSteps` became `testToSteps`, variable). The commented-state search needs the same method signature (ADR 0008, 0012).
3. **Edits made inside the comment.** This affects 2 histories:
   - javaparser `reachedVariadicParam`: the condition was rewritten in the commenting commit;
   - elasticsearch `JavaJodaTimeDuellingTests`: the test was extended inside the comment and later restored to its original text.

   The search for the commented form fails (ADRs 0008–0012). After Phase 7, `JavaJodaTimeDuellingTests` is found; only the edit itself (a `body change` inside the comment) is still missed.
4. **Born-commented code missed.** This affected 4 histories. After Phase 7, only one remains, the elasticsearch `SecurityActionFilter` field, which is not yet diagnosed.
   - The `LUCENE-7685` variable was cause 1.
   - `annotationMemberDeclarationResolve` and `TransportEqlStatsAction` were fixed by the comment alignment.
5. **Merge commits** (elasticsearch `Querier`, 1 history). The block was commented out on a feature branch (`06507eb`) and restored after a merge of `main` into the branch. In git-log order the tool reaches a `main` commit where the block was still active, and attributes the comment-out to the merge `5429621`.
6. **A restored block missed among many** (javaparser `LexicalPreservingPrinter`, INITIALIZER, 1 history). Not yet diagnosed.
7. **No false transitions apart from the merge case.** The negative variable case (an active declaration kept next to its commented copy) is correctly not reported. Every other FP is an `introduced` reported at a restore whose true origin is older.
8. **Scoring artifact, not a tool defect.** The statement-level types extend `BlockBodyChange`, which `OracleTest.processHistory` filters out, so they never count as detected (FOR_STATEMENT, footnote ¹). Counting them would also add them as FPs to every published block history where the tool reports one, because the published oracle does not contain these types. This decision is open (ADR 0005).

## Rejected candidates (and why)

| Project | Candidate | Reason |
|---|---|---|
| javaparser | `ClassMetaModel.equals`, `FieldMetaModel.is` (`cd7814e` → `d34926f`) | Moved between classes *while commented*, then restored with edits. No change type can express a commented move. |
| javaparser | `CoreGenerator` / `CoreVisitorsGenerator` `generate()` calls | Statements directly in a method body. Their method history only shows `body change`. |
| javaparser | `LiteralStringValueExprTest`, `InitializePropertyMetaModelsStatementsGenerator`, `LexicalPreservingPrinterTest` | Born-commented statements that are not inside a block. |
| both | Commented fields that are never restored | No start commit at which the field exists. Neither project has a field that went active → commented → active, so all attribute samples are born commented. |
| elasticsearch | `SearchService` (`NOCOMMIT` statements), `Version` (commented `assert` in a static initializer) | Statements in a method body or initializer, not in a tracked block. |
| elasticsearch | `TimeseriesLifecycleTypeTests`, `IndexLifecycleServiceTests` (51–133 shared lines) | Large test-class rewrites during a feature branch's development. The pairing reflects mass edits, not a deliberate disable and restore. |
| elasticsearch | Most `/* … */` changes (2,310 wrap/unwrap events) | Javadoc, license or prose. 13 unwraps enclose ≥ 3 code lines; none of them was a clean round trip of a trackable element within the time available. They remain candidates. |
| elasticsearch | `StatelessTranslogIT` (`@TestLogging` method), `SimpleChildQuerySearchTests`, `TelemetryIT`, `KnnFunctionIT`, `ToStringTests`, … | The commented method was deleted, not restored; or the commented text is a fragment of an expression (a chained call, an array element). |
| checkstyle | `CheckStyleTask.listenerClass` | The declaration only moved within the restoring commit; it was never commented out. |
| checkstyle | `TreeTableCellRenderer`, `EmptyBlockCheck`, `XdocsJavaDocsTest`, `InputJava19PatternsInNullSwitch1` | A statement directly in a method body, an array element, or a non-compilable test resource. |
| checkstyle | All 7 `/* */` unwraps that enclose code | Deletions of dead code, not restorations. |

## Reproducing on another machine

The cache entries these histories need are **not** in the `oracle` submodule: 247 files and folders for javaparser and about 1,840 for elasticsearch, under `oracle/commits`. Rebuild them from clones, with no network:

```bash
mvn -q -DskipTests test-compile && mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/test-classes;target/classes;$(cat cp.txt)" org.codetracker.util.LocalOracleBuilder cache /path/to/javaparser javaparser src/test/resources/block/javaparser-*.json src/test/resources/method/javaparser-*.json src/test/resources/variable/javaparser-*.json
java -cp "target/test-classes;target/classes;$(cat cp.txt)" org.codetracker.util.LocalOracleBuilder cache /path/to/elasticsearch elasticsearch src/test/resources/block/elasticsearch-*.json src/test/resources/method/elasticsearch-*.json src/test/resources/variable/elasticsearch-*.json src/test/resources/attribute/elasticsearch-*.json
java -cp "target/test-classes;target/classes;$(cat cp.txt)" org.codetracker.util.LocalOracleBuilder cache /path/to/checkstyle checkstyle src/test/resources/block/checkstyle-*.json src/test/resources/method/checkstyle-*.json src/test/resources/variable/checkstyle-*.json src/test/resources/attribute/checkstyle-*.json
```

(Use `:` instead of `;` as the classpath separator on Linux and macOS.) Entries that already exist are never overwritten. The `elasticsearch-*` globs also match commit-log files of the published elasticsearch oracle. Rebuilding entries for them is harmless.

## Adding a project

1. Mine it: `java docs/comment-transition/tools/MineCommentTransitions.java <clone> 3 events.tsv > pairs.tsv`.
2. Write a history JSON per element in `oracle/<kind>/comment-transition/`, with an empty `expectedChanges` and a name ending in `-ct`.
3. Run `LocalOracleBuilder <kind> <clone> <history>`, then `LocalOracleBuilder check …`, and validate by hand. If the tool loses the history at a restore, track the element from its last active version with `-Dct.nocache=true` and validate that part too.
4. Write `verdicts/<kind>/<name>.verdict.json` (`validation`, `drop`, `add`), then run `LocalOracleBuilder finalize …`.
5. Append the output of `LocalOracleBuilder score …` to `src/test/resources/<kind>/comment-transition-expected.txt`.
