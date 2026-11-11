# Comment-transition oracle (real projects)

This folder documents an oracle of **real** change histories, taken from open-source projects, in which code was commented out and/or restored. It complements the synthetic scenario tests of Phases 0–4. It uses the same format and the same TP/FP/FN scoring as the published CodeTracker oracles: FSE 2022 for methods and variables, TSE 2024 for blocks. The design decisions are in [ADR 0013](../../adr/0013-real-project-comment-transition-oracle.md).

- **Project so far:** javaparser (local clone `D:\GitHubProjects\javaparser`, HEAD `2761dca`, 10,039 commits).
- **Histories:** 13 (7 block, 4 method, 2 variable). There are no attribute histories, because javaparser has no restored field (see below).
- **Run:**

  ```bash
  mvn test -Dtest='BlockCommentTransitionOracleTest,MethodCommentTransitionOracleTest,VariableCommentTransitionOracleTest' -Dct.oracle=true
  ```

  Use the JUnit-aligned temporary pom, as in `tools/run-oracles.sh`, while the 5.8.2/5.10.0 mix in `pom.xml` is not fixed. The run takes about 70 s.

## Files

| Path | Content |
|---|---|
| `src/main/resources/oracle/<kind>/comment-transition/*-ct.json` | The histories, in the published oracle format. |
| `src/test/resources/<kind>/comment-transition-expected.txt` | Current TP, FP, FN of each history: a regression baseline, not a target. |
| `src/test/resources/<kind>/<repo>-<Class>-<method>.json` | Commit logs read by the `*WithLocalFiles` trackers. Two files shared with the published oracle were *extended* (ADR 0013, item 4). |
| `docs/comment-transition/oracle/verdicts/<kind>/*.verdict.json` | For every history: how it was validated, and the tool changes that were dropped or added, with the reasons. |
| `src/test/java/org/codetracker/util/LocalOracleBuilder.java` | Builds, checks, finalizes and scores histories, and builds the commit cache from a local clone. |
| `src/test/java/org/codetracker/util/*CommentTransitionOracleTest.java` | Opt-in oracle tests (`-Dct.oracle=true`). |
| `docs/comment-transition/tools/MineCommentTransitions.java` | Miner that finds candidate comment-out/uncomment commits in a clone. |

## How the histories were built

1. **Mining.** `MineCommentTransitions.java` reads `git log -p -U0` and finds commits where removed code lines come back as added `//` lines (comment out), or the reverse (uncomment). It pairs the two on the same file. Separate `git log` scans looked for `/* … */` wrappers and for field declarations. On javaparser it found 71 events: 50 comment-outs, 21 uncomments, and 6 same-file pairs.
2. **Selection.** Each candidate was inspected in git and classified as a round trip (active → commented → active), born-commented (first written as a comment, later made code), or out of scope (see *Rejected candidates*). One element kind was chosen per case: a block, method or variable whose own text was commented, plus control elements nearby.
3. **Tool run.** `LocalOracleBuilder <kind> <clone> <history>` runs the git-based tracker on the clone and saves its output. It also writes the commit logs and cache entries for the offline tracker.
4. **Validation**, independent of the tool:
   - `git log -L` on the element's lines;
   - `tools/blockevo.sh` extracts the element's text, uncommented for comparison, at every version of the file and reports when it changes or when its state changes (code, commented, absent);
   - `LocalOracleBuilder check` prints, for each reported change, the lines that differ before and after, and flags every *unreported* commit that changes one of the element's lines.

   Every flagged commit was read by hand. The papers built their oracles the same way: they took the union of several tools' outputs and hand-validated each commit.
5. **Finalize.** `LocalOracleBuilder finalize` writes the validated changes, which are the tool output minus the dropped changes plus the added ones from the verdict file.
6. **Score.** `LocalOracleBuilder score` runs the offline tracker exactly as `OracleTest` does. Its numbers equal the git-based ones for all 13 histories, which shows the cache is complete.

## Histories and current results

TP/FP/FN as `OracleTest` counts them (one key per commit and change type). *Transitions* counts only the 10 comment-transition change types.

| Kind | History (`-ct.json`) | Case | TP | FP | FN | Transitions found |
|---|---|---|---|---|---|---|
| block | MethodResolutionLogic-isApplicable-IF_STATEMENT-vararg-break | round trip, text unchanged | 3 | 0 | 0 | 2/2 |
| block | MethodResolutionLogic-isMoreSpecific-IF_STATEMENT-vararg | round trip, text unchanged | 3 | 0 | 0 | 2/2 |
| block | LexicalPreservingPrinter-concretePropertyChange-IF_STATEMENT-TYPE | round trip inside an anonymous class, 4 older edits | 6 | 0 | 0 | 2/2 |
| block | LexicalPreservingPrinter-concretePropertyChange-IF_STATEMENT-INITIALIZER | same commit pair as TYPE | 0 | 1 | 4 | 0/2 |
| block | MethodResolutionLogic-isApplicable-FOR_STATEMENT | statements inside the loop commented and restored; 22 older changes since 2015 | 22 | 0 | 2 | 0/2 |
| block | MethodResolutionLogic-isApplicable-IF_STATEMENT-reachedVariadicParam | round trip, **edited while commented** | 2 | 1 | 4 | 1/2 |
| block | BulkParseTest-parseOpenJdkSourceRepository-IF_STATEMENT | round trip, **method renamed at the restore** | 1 | 1 | 2 | 1/2 |
| method | NodeWithMembersBuildersTest-testGetConstructorsWithParameterTypes | **`/* … */`** round trip, body rewritten at the restore | 4 | 0 | 0 | 2/2 |
| method | AnnotationDeclarationTransformationsTest-removingJavadoc | born commented, file moved while commented | 2 | 0 | 0 | 1/1 |
| method | JavaParserAPIIntegrationTest-enumConstantDeclarationResolve | born commented | 2 | 0 | 0 | 1/1 |
| method | JavaParserAPIIntegrationTest-annotationMemberDeclarationResolve | born commented, restored with identical text | 1 | 0 | 1 | 0/1 |
| variable | MethodResolutionLogic-isApplicable-reachedVariadicParam | born commented | 2 | 0 | 0 | 1/1 |
| variable | MethodResolutionLogic-isApplicable-expectedType | **negative:** an active copy stays next to a commented one | 3 | 0 | 0 | none expected, none reported |
| | **Total** | | **51** | **3** | **13** | **13/20** |

- **All change types:** precision 51/54 = 94.4%, recall 51/64 = 79.7%.
- **Comment transitions only:** precision 13/13 = 100% (no false transition was ever reported), recall 13/20 = 65%.

## What the real samples show (tool findings)

1. **Edits made while code is commented out lose the history** (reachedVariadicParam). The commit that comments the block out also rewrites its condition inside the comment, so the search for the commented form fails, and the restore is reported as `introduced` (ADRs 0008–0012 list this limit).
2. **A container renamed at the restore loses the history** (BulkParseTest). `parseOpenJdk()` became `parseOpenJdkSourceRepository()` in the restoring commit. The commented-state search needs the same method signature (ADR 0008).
3. **A restored block missed among many** (LexicalPreservingPrinter, INITIALIZER). One commit uncommented about 15 sibling if-statements, all with identical text. The TYPE block is tracked, but the INITIALIZER block, which has a nested if/else, is reported as `introduced` with no `uncommented block`. Not yet diagnosed; this is the most useful case for debugging.
4. **Statement-level transitions missed in a long loop** (FOR_STATEMENT). Statements nested two levels deep were commented and restored, together with an edited loop header. The tool reports `body change` and `expression change` correctly, but neither `commented out statement` nor `uncommented statement`.
5. **An identical restoration missed** (annotationMemberDeclarationResolve). Its twin `enumConstantDeclarationResolve`, in the same class and with the same pattern, is detected. The difference may be the 11 commits in between, or the 2-week gap. Not yet diagnosed.
6. **No false transitions.** In particular, the negative variable case (an active declaration kept next to its commented copy) is not reported as uncommented.

## Rejected candidates (and why)

| Candidate | Reason |
|---|---|
| `ClassMetaModel.equals`, `FieldMetaModel.is` (`cd7814e` → `d34926f`) | Moved between classes *while commented* (from `OldClassMetaModel`), then restored with edits. No change type can express a commented move, so a fair label is unclear. |
| `CoreGenerator` / `CoreVisitorsGenerator` `generate()` calls | Statements directly in a method body, not inside a block. Their method history shows only `body change`, so they add nothing beyond the published oracle. |
| `LiteralStringValueExprTest`, `InitializePropertyMetaModelsStatementsGenerator`, `LexicalPreservingPrinterTest` | Statements written as comments in the same commit that added them (born commented) and not inside a block. |
| Commented fields (`PrettyPrinterConfiguration`, `OldFieldMetaModel`, `ReferenceTypeUsage`, …) | Commented out and never restored, so there is no start commit at which the field exists. javaparser has **no restored field**, so attribute histories need another project. |
| Most `/* … */` changes | Javadoc or license edits. The one real `/* */` round trip was used (testGetConstructorsWithParameterTypes). |

## Reproducing on another machine

The cache entries these histories need (247 files and folders under `oracle/commits`) are **not** in the `oracle` submodule. Rebuild them from a clone, with no network:

```bash
git clone https://github.com/javaparser/javaparser.git /path/to/javaparser
mvn -q -DskipTests test-compile && mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/test-classes;target/classes;$(cat cp.txt)" org.codetracker.util.LocalOracleBuilder cache /path/to/javaparser javaparser src/test/resources/block/javaparser-*.json src/test/resources/method/javaparser-*.json src/test/resources/variable/javaparser-*.json
```

(Use `:` instead of `;` as the classpath separator on Linux and macOS.) Entries that already exist are never overwritten.

## Adding a project

1. Mine it: `java docs/comment-transition/tools/MineCommentTransitions.java <clone> 3 events.tsv > pairs.tsv`.
2. Write a history JSON per element in `oracle/<kind>/comment-transition/` with an empty `expectedChanges`, and a name ending in `-ct`.
3. Run `LocalOracleBuilder <kind> <clone> <history>`, then `LocalOracleBuilder check …`, and validate by hand.
4. Write `verdicts/<kind>/<name>.verdict.json` (`validation`, `drop`, `add`), then run `LocalOracleBuilder finalize …`.
5. Append the output of `LocalOracleBuilder score …` to `src/test/resources/<kind>/comment-transition-expected.txt`.
