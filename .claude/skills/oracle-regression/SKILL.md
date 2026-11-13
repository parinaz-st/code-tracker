---
name: oracle-regression
description: Run CodeTracker's four published oracle tests (block, method, variable, attribute) on a baseline and on the changed code, and compare them history by history. Use after any change to tracking or change-detection logic, before committing a phase, or when asked whether a change regresses the published FSE/TSE results.
---

# Published-oracle regression

The published oracles have 3,376 histories: 1,280 block, 200 method, 1,416 variable and 480 attribute. Each test passes when its TP/FP/FN **equal** the line in `src/test/resources/<kind>/<set>-expected.txt`. So the tests detect *change*, not imperfection, and every difference must be explained.

## Steps

1. **Baseline checkout.** Pick the commit before the change, usually the previous phase commit, and create a worktree for it:

   ```bash
   git worktree add --detach <dir> <commit>
   ```

   The worktree needs the commit cache. Link `<dir>\oracle` to `D:\codeTracker\oracle` with a directory junction (PowerShell: `cmd /c mklink /J <dir>\oracle D:\codeTracker\oracle`, after removing the empty folder).

2. **Run both checkouts**, in the background (about 75–100 minutes each):

   ```bash
   bash docs/comment-transition/tools/run-oracles.sh <out-dir> <final-checkout> <baseline-checkout>
   ```

   The script works around the JUnit 5.8.2/5.10.0 mix in `pom.xml`: it writes `pom-oracle-tmp.xml` with every JUnit version set to 5.10.0, runs the tests with `-f pom-oracle-tmp.xml`, copies `target/surefire-reports` to `<out-dir>/reports-<name>`, and deletes the temporary pom. To run only one checkout, run the same `sed` / `mvn -o -f pom-oracle-tmp.xml test -Dtest='BlockOracleTest,MethodOracleTest,VariableOracleTest,AttributeOracleTest' -Dsurefire.failIfNoSpecifiedTests=false` steps yourself.

3. **Compare:**

   ```bash
   java docs/comment-transition/tools/CompareOracle.java <out-dir>/reports-base <out-dir>/reports-final
   ```

   It lists every test whose status or TP/FP/FN changed, plus a per-oracle summary. Map a test index to its history: the order is `training` then `test`, each sorted by file name (`ls … | LC_ALL=C sort | sed -n <index>p`).

4. **Explain every difference.** Each one is either:
   - a regression to fix;
   - an expected effect, for example a new change type that the old oracle does not contain, which counts as an FP; or
   - an artifact.

   To see the cause, run the old and the new code on that one history and print the change keys. The git-based tracker on a local clone is enough (see `LocalOracleBuilder`).

5. **Record the result** in the phase doc as a table (baseline vs final, per oracle), with every difference explained.

## Known traps

- **Do not compile or run other tests in a checkout while its oracle run is going.** The run loads classes from `target/` as it goes.
- **`lucene-solr-Field-tokenStream.json`** exists both as a method history (expected TP 26) and as an attribute history (expected TP 4). The expected values sit in static maps keyed by file name, so this history passes or fails depending on class load order. It is not a regression.
- **Removing a worktree:** delete its `oracle` junction **first**, with PowerShell `(Get-Item <dir>\oracle -Force).Delete()`, then `git worktree remove --force <dir>`. Otherwise the removal can follow the junction and wipe the shared cache (about 30,900 entries).
- **A leftover `pom-oracle-tmp.xml`** means a run was killed. Delete it; never commit it.
- The current `master` does not compile against the cached RefactoringMiner (`rm.version` in `pom.xml`). Use phase commits as baselines.
