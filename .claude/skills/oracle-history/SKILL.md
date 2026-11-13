---
name: oracle-history
description: Add hand-validated real-project histories to CodeTracker's comment-transition oracle (mine a local clone, classify candidates, run the tracker, validate against git, write verdicts, finalize, score). Use when asked to find real comment-out/uncomment cases in a project, to add or fix oracle histories, or to re-score the real oracle.
---

# Adding real-project oracle histories

The comment-transition oracle is a set of real histories, validated by hand, in which code was commented out and/or restored. Full background: `docs/comment-transition/oracle/README.md`; design decisions: ADR 0013 (oracle) and ADR 0014 (notes around commented code). The general method is in `docs/evaluation/README.md`, section 5.

**The ground truth comes from git, never from the tool.** The tool's output is only a starting point, and every change is checked against the code before and after.

## Setup

- The local clones live in `D:\GitHubProjects\<project>`.
- Build once, and save the classpath:

  ```bash
  mvn -o -q test-compile
  mvn -o -q dependency:build-classpath -Dmdep.outputFile=<scratch>/cp.txt
  CP="target/test-classes;target/classes;$(cat <scratch>/cp.txt)"
  ```

- `LocalOracleBuilder` is `org.codetracker.util.LocalOracleBuilder`; run it as `java -cp "$CP" …` from the repo root. Its modes are documented in its Javadoc.
- **Do not run it while a published-oracle regression is running in the same checkout,** if your histories share a commit-log file with the published oracle (same `<repo>-<Class>-<method>`). Additive changes are safe, but check first.

## Steps

1. **Mine.**

   ```bash
   java docs/comment-transition/tools/MineCommentTransitions.java <clone> 3 events.tsv > pairs.tsv
   ```

   Use threshold `1` for projects with little dead code (checkstyle). Also scan for restored fields and for `/* */` unwraps that enclose code; the awk scans are in the oracle README's mining section. Report how many events, pairs and usable cases each project produced.

2. **Classify every candidate**, and record the rejected ones with their reason in the README:
   - **round trip:** active, then commented, then active;
   - **born commented:** first written as a comment, later made code;
   - **out of scope:** statements directly in a method body (no element kind tracks them), commented code that was later deleted, a moved commented method, an expression fragment, Javadoc or prose.

   For a round trip, find the **last active version** and the comment-out commit: walk `git log <restore>^ -- <file>` back to the first version where the line is code. Check **merge topology** too (`git merge-base --is-ancestor`): the comment-out commit is the one on the first-parent history.

3. **Write the spec** in `src/main/resources/oracle/<kind>/comment-transition/<repo>-<Class>-<element>[-<detail>]-ct.json`:
   - copy the shape of an existing `-ct.json`, with an empty `expectedChanges`;
   - the file name **must end in `-ct`**, because `OracleTest` keys expected values by file name in static maps shared by all oracle classes;
   - locate the element *at the start commit* (usually the restore commit): file path, element name, declaration line, and for blocks the start/end lines from brace matching.

4. **Run the tracker:**

   ```bash
   java -cp "$CP" org.codetracker.util.LocalOracleBuilder <kind> <clone> <spec.json>...
   ```

   It writes `target/oracle-detected/<kind>/<name>.detected.json`; change the folder with `-Dct.out=<dir>`. It also writes the commit logs and the cache entries for the offline tracker.

5. **Validate** each history:
   - **Blocks and variables:** `java … LocalOracleBuilder check <kind> <clone> <spec>` prints the before/after text of every reported change and flags unreported commits that touch the element. Read every flag.
   - **Methods, or any element:**
     - `docs/comment-transition/tools/mtrace.sh <clone> <commit> <file> "<declaration line>"`, which runs `git log -L` on the exact range;
     - `docs/comment-transition/tools/blockevo.sh <clone> <commit> <file> "<first line>"`, which prints the text at every version, uncommented, with its code/commented/absent state.
   - **If the tool lost the history at a restore:** track the element from its last active version with `-Dct.nocache=true` (output only, no cache), then validate that part the same way.
   - **Before calling something an FN,** look at the raw tracker output. `OracleTest` filters out the `BlockBodyChange` subclasses, which includes `commented out statement` and `uncommented statement`.

6. **Label with the conventions** of ADR 0013, item 6:
   - code first written as a comment is `introduced` + `uncommented …` at the commit where it becomes code;
   - edits made inside a comment are labelled at the commit where they happened, for example `initializer change` or `body change` while commented;
   - whitespace-only edits are not changes, and neither is an `@Test` annotation moved to its own line;
   - comment-only edits inside a method body are a `documentation change`, as in the published method oracle.

7. **Write the verdict** in `docs/comment-transition/oracle/verdicts/<kind>/<name>.verdict.json`:

   ```json
   {"validation": "<how it was checked and what happened, with commits>",
    "drop": ["<commit prefix>:<change type>"],
    "add": [{"commitId": "…", "changeType": "…", "elementFileBefore": "…", "elementNameBefore": "…",
             "elementFileAfter": "…", "elementNameAfter": "…", "comment": "manually validated: …"}]}
   ```

   `parentCommitId` and `commitTime` are filled from git. For a long validated active history, generate the `add` list from the helper run's `.detected.json` instead of typing it by hand.

8. **Finalize and score:**

   ```bash
   java -cp "$CP" org.codetracker.util.LocalOracleBuilder finalize <kind> <clone> <spec> <verdict>
   java -cp "$CP" org.codetracker.util.LocalOracleBuilder score <kind> <spec>...
   ```

   Add the `score` lines to `src/test/resources/<kind>/comment-transition-expected.txt` (format `name, TP, FP, FN`). The offline score must equal the git-based result; if it doesn't, the cache or the commit logs are incomplete.

9. **Run the opt-in tests.** `pom.xml` mixes JUnit 5.8.2 and 5.10.0, which breaks `@MethodSource`, so use a temporary pom aligned to 5.10.0 and delete it afterwards:

   ```bash
   rm -rf target/classes/oracle/*/comment-transition   # drop stale copies after renaming files
   sed 's#<version>5.8.2</version>#<version>5.10.0</version>#g' pom.xml > pom-ct-tmp.xml
   mvn -o -f pom-ct-tmp.xml test -Dtest='*CommentTransitionOracleTest' -Dct.oracle=true -Dsurefire.failIfNoSpecifiedTests=false
   rm -f pom-ct-tmp.xml
   ```

10. **Document.** Update the oracle README (mining table, per-project table, summary, rejected candidates), the current phase doc, and `docs/comment-transition/HANDOFF.md`. Compute the transitions-only metrics from the oracle files and the detected outputs, not by hand.

## Do not

- Do not edit histories under `oracle/<kind>/training` or `oracle/<kind>/test`. They are the published oracles.
- Do not commit the `oracle` submodule pointer or its untracked cache files. The cache is rebuilt from clones with `LocalOracleBuilder cache <clone> <repoName> <commit-log json>...`.
- Do not trust the tool's labels for the period before a comment-out without checking them against git.
