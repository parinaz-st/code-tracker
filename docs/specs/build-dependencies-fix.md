# Spec and plan: make `master` build and run its oracle tests again

- Tier: 4 (dependencies; may change tracking results, so research-impacting)
- Status: In progress (RefactoringMiner part done; JUnit part waiting for approval)
- Author: agent (Claude), 2026-11-14
- ADRs: none

## 1. Evidence

Sources: commands run on `chore/ai-agent-foundation` (identical to `master` for all code and `pom.xml`) on 2026-11-14, and the fetched `upstream/master` (`a0e3d4e`, 2026-11-10).

**Observed facts**

- F1. `mvn -q -DskipTests install` fails: `VariableTrackerChangeHistory.java:334` calls `VariableDeclaration.equalVariableDeclarationKind(...)`, which does not exist. The call came from upstream commit `7afe3cb` ("Adapt to RefactoringMiner API change", 2026-10-15).
- F2. `pom.xml:35` sets `rm.version` to `3.1.6`. `javap` on the Maven Central jar `refactoring-miner-3.1.6.jar` shows `equalVariableDeclarationType` but not `equalVariableDeclarationKind`.
- F3. The locally built `CI-RM-SNAPSHOT` jar has the method. With `-Drm.version=CI-RM-SNAPSHOT`, `test-compile` succeeds (about 18 s) and `CodeElementLocatorWithLocalFilesTest` passes (50 tests).
- F4. `upstream/master` sets `rm.version` to `3.2.0`. Maven Central's metadata lists `3.2.0` as the latest release. `3.2.0` is not in the local Maven cache (`~/.m2`).
- F5. `upstream/master` has two commits that `master` does not: `a0e3d4e` (`rm.version` 3.1.6 → 3.2.0) and `279da60` (updates two blame expected files, `src/test/resources/blame/blameTestUntilCommitZero.txt` and `blameTestWithLocalRepo7.txt`, "after RefactoringMiner improvements").
- F6. `mvn -o -Drm.version=CI-RM-SNAPSHOT test -Dtest=MethodOracleTest` fails in about 7 s, before any history runs: `NoSuchMethodError: ClassLoaderUtils.getClassLoader` and "You must configure at least one set of arguments for this @ParameterizedTest".
- F7. `mvn dependency:tree` shows `junit-jupiter-api` 5.8.2 and `junit-jupiter-engine` 5.8.2 (with `junit-platform-commons` and `junit-platform-engine` 1.8.2) next to `junit-jupiter` 5.10.0 (with `junit-jupiter-params` 5.10.0). The oracle tests are `@ParameterizedTest`s.
- F8. `upstream/master`'s `pom.xml` has `junit:junit` 4.13.2 and `junit-jupiter` 5.10.0, and no 5.8.2 entries. The 5.8.2 entries and the commented-out `junit:junit` block exist only in this fork.
- F9. One test, `CodeTrackerBlameTest`, imports JUnit 4. JUnit 4 (4.8.2) currently reaches the test classpath only transitively (RefactoringMiner → rendersnake, runtime scope).
- F10. The published oracle expected files (`src/test/resources/*/*-expected.txt`) were last changed on 2026-07-21 (`f3404cd`).

**Inferences**

- I1. F6 and F7 together: `junit-jupiter-params` 5.10.0 needs JUnit Platform 1.10, but the 5.8.2 entries pin Platform 1.8.2, so every parameterized test fails to start. Removing the 5.8.2 entries (or setting them to 5.10.0) should give one consistent platform version.
- I2. From F1, F4 and F5: upstream builds `7afe3cb` against RefactoringMiner 3.2.0, so 3.2.0 very likely contains `equalVariableDeclarationKind`.

**Assumptions** (each is checked by a plan task)

- A1. RefactoringMiner 3.2.0 compiles with the current code. Checked by task 3.
- A2. The published oracle expected values still hold with RefactoringMiner 3.2.0. Unknown: upstream changed only blame expected files for this upgrade (F5), not the oracle ones. Checked by tasks 6 and 7.
- A3. `CodeTrackerBlameTest` still compiles when only the transitive JUnit 4 is present. It does today (F3).

**Unresolved questions** (for the developer)

- Q1. Bring in upstream's two commits by merging `upstream/master` (pom bump and blame expected files), or change only `rm.version`? Merging keeps the fork aligned with upstream and is the smaller long-term diff. Changing only the version leaves the two blame tests to fail, if F5's improvements apply.
- Q2. The JUnit 5.8.2 entries come from your commit `f6b0ee3`, which you asked me not to use. The fix has to touch those lines. Shall I remove them (so `junit-jupiter` 5.10.0 supplies the API and engine), or set them to 5.10.0?
- Q3. Do you approve downloading RefactoringMiner 3.2.0 from Maven Central (a normal Maven dependency download, about 6–7 MB)?
- Q4. Work on a separate branch `fix/build-dependencies` from `master` (recommended: one change per branch, easy upstream-style review), then bring it into `chore/ai-agent-foundation`?
- Q5. Do you approve running the four published oracle tests in the background (slow; duration not yet measured)?

## 2. Goal

`master` builds with a plain `mvn -q -DskipTests install`, with no local-only override, and the published oracle tests start and can be scored. Today neither works, so no change to tracking logic (including the comment-transition feature) can be checked for regressions.

## 3. Scope and non-goals

- In scope: `rm.version` in `pom.xml`; the JUnit test dependencies in `pom.xml`; the two upstream blame expected files, only if Q1 is answered "merge"; removing the "Known build problem" and "Known test problem" notes from `AGENTS.md`, `CODEBASE-ONBOARDING.md` and `docs/architecture/05-testing-and-evaluation.md` once fixed.
- Not in scope: production Java code; oracle JSON or `*-expected.txt` files (any mismatch is reported, not edited); the Maven wrapper (`./mvnw`); the compiler settings in `pom.xml` (`maven.compiler.source` 1.8 next to the compiler plugin's `source` 11); other dependency versions.

## 4. Behaviour

- Before: `mvn -q -DskipTests install` → compilation error at `VariableTrackerChangeHistory.java:334`. `mvn test -Dtest=MethodOracleTest` → `NoSuchMethodError` before any history runs.
- After: `mvn -q -DskipTests install` → success. `mvn dependency:tree` shows one JUnit Jupiter version (5.10.0) and one JUnit Platform version. `mvn test -Dtest=MethodOracleTest` → runs all method histories and compares each with `src/test/resources/method/*-expected.txt`.
- Edge case: some oracle histories may differ under RefactoringMiner 3.2.0 (A2). Each difference is reported with its history file and TP/FP/FN, and classified (regression, existing limitation, incorrect oracle, or intended RefactoringMiner improvement). Nothing is edited to make it pass.

## 5. Acceptance criteria

1. `mvn -q -DskipTests install` succeeds with no `-D` override.
2. `mvn dependency:tree -Dincludes=org.junit.jupiter,org.junit.platform` shows a single Jupiter version and a single Platform version.
3. `mvn -q -o test -Dtest=CodeElementLocatorWithLocalFilesTest` passes (50 tests).
4. `MethodOracleTest` starts and runs every history. Its result (pass, or each failing history with expected and actual TP/FP/FN) is recorded in section 8.
5. With Q5 approved: the four published oracle tests run, and every failing history is listed and classified in section 8.
6. `git diff --stat master` shows only `pom.xml` (plus the two blame expected files if Q1 = merge, plus the doc notes in task 8), with no line-ending changes.

## 6. Impact on shared code and research results

- Shared code: `pom.xml` (a shared file under `AGENTS.md`). Reason: it is the only place the RefactoringMiner and JUnit versions are set. Alternative considered: rewriting the `7afe3cb` call to the 3.1.6 API. Rejected, because it diverges from upstream in production code and keeps an outdated RefactoringMiner.
- Research results: RefactoringMiner is the core of the matching, so a new version can change detected changes, and therefore the oracle TP/FP/FN and the experiment numbers. The published numbers (`experiments/`) were produced with an older RefactoringMiner. This change does not rerun the experiments or touch `experiments/`. Oracle differences are reported, not hidden (A2).
- Oracle tests to run: `MethodOracleTest` first (smallest), then `AttributeOracleTest`, `VariableOracleTest` and `BlockOracleTest` (Q5).

## 7. Plan

Run on the branch chosen in Q4. Commands assume the `oracle` submodule is present.

| # | What it does | Files | Test that proves it (run first, must fail) | Command |
|---|---|---|---|---|
| 1 | Reproduce both failures on the fix branch, before any change (RED). | none | Build fails (F1); `MethodOracleTest` fails to start (F6). | `mvn -q -DskipTests install`; `mvn -o -Drm.version=CI-RM-SNAPSHOT test -Dtest=MethodOracleTest` |
| 2 | Move to RefactoringMiner 3.2.0: merge `upstream/master`, or edit `rm.version` only (Q1). Needs the download in Q3. | `pom.xml` (+ 2 blame files if merge) | Task 1's build failure. | `git merge upstream/master` or edit; then task 3 |
| 3 | Confirm the build (GREEN for criterion 1). | none | Same command as task 1, now passing. | `mvn -q -DskipTests install` |
| 4 | Align JUnit: remove or update the 5.8.2 entries (Q2). | `pom.xml` | Task 1's oracle-test failure. | edit; then `mvn dependency:tree -Dincludes=org.junit.jupiter,org.junit.platform` |
| 5 | Fast check (criterion 3). | none | — | `mvn -q -o test -Dtest=CodeElementLocatorWithLocalFilesTest` |
| 6 | Run `MethodOracleTest` and record the result (criterion 4). | section 8 of this file | — | `mvn -o test -Dtest=MethodOracleTest` |
| 7 | With Q5: run the other three oracle tests in the background and classify every failing history (criterion 5). | section 8 | — | `mvn -o test -Dtest=AttributeOracleTest,VariableOracleTest,BlockOracleTest` |
| 8 | Remove the "Known build/test problem" notes from the docs, replacing them with the verified commands and timings. | `AGENTS.md`, `CODEBASE-ONBOARDING.md`, `docs/architecture/05-testing-and-evaluation.md` | — | link and line-ending check |

Nothing is committed until the developer says so. If task 3 fails (A1 is wrong), stop and report before task 4.

## 8. Results

**Decisions taken (2026-11-14).** The developer resolved Q1, Q3 and Q4 by updating the fork's `master` from upstream (`131fc8b`, merging `a0e3d4e` and `279da60`). `master` was then merged into `chore/ai-agent-foundation` (`61b13bb`); the developer chose to keep that merge. Q2 and Q5 are still open.

**Tasks 2 and 3: RefactoringMiner 3.2.0**

- VERIFIED: `rm.version` is `3.2.0` in `pom.xml` (from `a0e3d4e`).
- VERIFIED (criterion 1): `mvn -q -DskipTests install` succeeds on `master` `131fc8b` with no `-D` override, in a separate worktree. It took 3 min 20 s, including the RefactoringMiner 3.2.0 download. Assumption A1 holds.
- VERIFIED (criterion 3): `mvn -q -o test -Dtest=CodeElementLocatorWithLocalFilesTest` on `chore/ai-agent-foundation` after the merge: 50 tests, 0 failures, 0 errors, about 12 s.
- NOT VERIFIED: the build of `chore/ai-agent-foundation` itself with `install`. Its `pom.xml` equals `master`'s, and the fast check compiled it.

**Tasks 4 to 7: JUnit and oracle tests**

- Not started. Waiting for Q2 (remove or update the JUnit 5.8.2 entries) and Q5 (run the oracle tests).
- NOT VERIFIED: whether the oracle tests still fail to start with RefactoringMiner 3.2.0. The JUnit entries are unchanged, so F6 most likely still applies.

**Task 8: docs**

- Done for the build: the "Known build problem" note is replaced by the verified build fact in `AGENTS.md` and `CODEBASE-ONBOARDING.md`. The "Known test problem" note stays, marked as not rechecked since the update.

