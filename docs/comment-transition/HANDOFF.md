# Handoff: comment-transition rework (written 2026-11-12, end of session 1)

Read this first in the next session. It summarizes what was done, what is running or unfinished, the decisions still open, and the traps already found. Details live in the files it links to.

> **Update, session 2 (2026-11-12):**
> - **Phase 6, a real-project oracle (javaparser):** commit `e86227a` on the rework branch. It has 13 hand-validated histories and opt-in tests (`-Dct.oracle=true`). See [phase-6.md](phase-6.md) and [oracle/README.md](oracle/README.md).
> - **Phase 5 status:**
>   - The final-code run finished at 01:24: block 1280/1280, variable 1416/1416, attribute 480/480, method 199/200 (`lucene-solr-Field-tokenStream`). Its reports are in `reports-final`.
>   - The baseline run was killed when session 1 ended. Session 2 restarted it in `p0-wt` (log `oracle-base.log`, reports copied to `reports-base` at the end).
>   - When it finishes, continue with step 3 of section 4 (`CompareOracle`).
> - **Phase 5 is done:** no regression in any published oracle. The baseline fails 11 block histories (the prototype dropped `body change`). The final code's one method "failure" is a harness file-name collision (`lucene-solr-Field-tokenStream`). See the update section of [phase-6.md](phase-6.md).
> - **elasticsearch:** 14 more real histories, including the first attribute ones; 27 in total, all passing their expected values. The main new finding is that a prose line above commented code, or a `NOCOMMIT` marker inside it, makes the commented run unparseable. That explains 8 of 14 misses. The fix in `CommentedCodeUncommenter` is the next candidate.
> - **Phase 7 (ADR 0014):** the notes-around-commented-code fix, plus comment alignment and a stricter active-block match. Real oracle: 42 histories (javaparser, elasticsearch, checkstyle), precision 97.1%, recall 88.3%. No regression in the published oracles. See [phase-7.md](phase-7.md).
> - **Evaluation guide for students:** `docs/evaluation/` (Markdown + PDF), committed as `6706a77`.
> - The temporary worktrees (`D:\ct-oracle-wt` and the Phase 0 `p0-wt`) and the merged branch `claude/comment-transition-oracle` were removed on 2026-11-14, junctions first. All their commits are on `claude/comment-transition-rework`.

---

## 1. Context in five lines

- **Project:** CodeTracker (fork of `jodavimehran/code-tracker`) at `D:\codeTracker`. It is a refactoring-aware tool that builds commit histories of Java code elements on top of RefactoringMiner 3.1.6.
- **Goal:** keep an element's history when its code is **commented out and later restored**, instead of reporting it as "introduced" at the restore commit.
- **Author:** Parinaz Sattarzadeh. Her reports:
  - *Enhancing CodeTracker Accuracy* (April 2026), whose Algorithm 3 covers restored methods;
  - *Comment Transition Detection in Block Tracking* (May 2026, sent to Nikolaos Tsantalis), the block prototype.
- **Her prototype:** branch `feature/CmtTransitionDetectionBlock`, head `7c8d874`.
- **This work:** branch **`claude/comment-transition-rework`**, created from her branch. It is **local only and not pushed**.

## 2. Rules the author set (follow them)

1. **Work only on `claude/comment-transition-rework`.** She moves the changes to `feature/CmtTransitionDetectionBlock` herself once she is convinced of the approach.
2. **Do not push** anything until she says so.
3. **Document every phase** in `docs/comment-transition/phase-N.md`, and **keep ADRs** in `docs/adr/` (Nygard format; supersede, never rewrite).
4. **The oracle regression runs once, at the end** (ADR 0007). After each phase, only the fast checks run.
5. `AGENTS.md` (imported by `CLAUDE.md`) holds the repo conventions. Read it.

## 3. What is done (all committed on the rework branch)

| Phase | Commit | Result | Report |
|---|---|---|---|
| 0: scenario suite, RM 3.1.6 build fix, analysis | `81dcf47` | baseline 3/10, prototype 3/10 | [phase-0.md](phase-0.md), [analysis.md](analysis.md) |
| 1: origin guarantee, cycle guard, normalized hashing, BODY_CHANGE kept | `ae15cce` | 5/10 | [phase-1.md](phase-1.md) |
| 2: commented-state tracking (`commented out block`) | `faf5624` | 9/10 | [phase-2.md](phase-2.md) |
| 3: virtual parent and edit tolerance; local-files trackers wired | `5992174` | 12/12 | [phase-3.md](phase-3.md) |
| 4a: methods (her Algorithm 3) | `9737598` | 17/17 | [phase-4a.md](phase-4a.md) |
| 4b: attributes | `c462408` | 22/22 | [phase-4b.md](phase-4b.md) |
| 4c: variables | `c3994df` | **27/27** | [phase-4c.md](phase-4c.md) (includes the Phase 4 summary) |

- ADRs 0001–0012 are indexed in [../adr/README.md](../adr/README.md). Those from Phase 1 on are **Proposed**, waiting for her review.
- **Fast checks**, a few seconds each, run after every change:

  ```bash
  mvn -o test -Dtest='CommentTransitionScenarioTest,MethodCommentTransitionScenarioTest,AttributeCommentTransitionScenarioTest,VariableCommentTransitionScenarioTest,CommentTransitionAnalyzerTest,CommentedCodeUncommenterTest' -Dct.spec=true
  ```

  The expected result is 27 scenarios and 71 unit tests, all passing. The scenario classes are opt-in (`-Dct.spec=true`), so CI (`mvn package`) does not run them.

## 4. In flight when this session ended: Phase 5, the oracle regression

- **What:** all four oracles (1,280 block, 200 method, 1,416 variable and 480 attribute histories, read from the `oracle/commits` cache) on the **final code** (`D:\codeTracker`, `c3994df`), then on the **baseline** (Phase 0 commit `81dcf47`, in a temporary worktree).
- **Started:** 2026-11-12 00:11 as a background job of session 1. It may have been stopped when the session ended.
- **State at handoff (00:55):** the final-code run was still going. **The method oracle had finished: 200 run, 1 failure**: `lucene-solr-Field-tokenStream.json`, *"Should have 4 True Positives but has 26"*. Whether the baseline fails the same way was not yet known. Block, variable and attribute were still running, and the baseline run had not started.
- **Where the outputs are**, in session 1's scratch folder, which may be gone:
  `C:\Users\asus\AppData\Local\Temp\claude\4223ea30a8c3fa29\a6f1a683-7f1c-4339-98a0-b1b6cab84739\scratchpad\`
  - `oracle-final.log`, `oracle-base.log`: Maven logs;
  - `reports-final\`, `reports-base\`: surefire XML, copied when each run ends;
  - `p0-wt\`: the baseline worktree (see the warning in section 7).
- **How to finish:**
  1. Check whether the runs completed: look for `reports-final` and `reports-base` and the `[final] end` / `[base] end` lines in the logs above.
  2. If not, run them again with the copy in the repo:

     ```bash
     bash docs/comment-transition/tools/run-oracles.sh /d/ct-oracle /d/codeTracker <baseline-worktree>
     ```

     A full pair of runs takes well over an hour; the method oracle alone took about 42 minutes. Run it in the background, and **do not compile or run tests in the same checkout while it runs**.
  3. Compare the two runs:

     ```bash
     java docs/comment-transition/tools/CompareOracle.java <out>/reports-base <out>/reports-final
     ```

     It lists every history whose pass/fail status or TP/FP/FN counts differ, plus a per-oracle summary.
  4. Explain every difference. Each is either a regression to fix (find the phase with the per-phase commits), an expected effect of the new change types (they count as FP until the oracle JSON knows them), or noise. Start with `lucene-solr-Field-tokenStream`.
  5. Write `docs/comment-transition/phase-5.md`, then fill in **§6.3 of the report** (all three versions: see section 5) and rebuild.

## 5. Report deliverables (not committed yet)

In [report/](report/), in three formats with the same content:

- `report.html`: source for the PDF and Word versions. Rebuild both with `powershell -ExecutionPolicy Bypass -File docs/comment-transition/report/build.ps1`, which uses Edge headless for the PDF and Word automation for the DOCX.
- `Comment-Aware-Change-History-CodeTracker.pdf` (16 pages) and `.docx` (editable).
- `report.tex` and `Comment-Aware-Change-History-CodeTracker-latex.zip` for Overleaf. This was **never compiled** (no TeX installed), but it passes `tools/CheckTex.java`.
- `figures/`: six hand-drawn SVGs with 2× PNG renders.
- **§6.3 (oracle) is a placeholder.** The Acknowledgements mention AI assistance; she may keep or edit that.
- Editing the `.tex` does **not** update the HTML/PDF/DOCX, or the other way round. Ask her which one is the main copy.

## 6. Decisions waiting for the author

1. **ADR 0005: body change "instead of" or "in addition to".** Her prototype reports a statement-level comment transition *instead of* BODY_CHANGE (as a refined subtype). The rework records it *in addition*, so other edits stay visible and the oracle still sees `body change`. Her call, possibly Nikolaos's too.
2. **Review and accept ADRs 0004–0012**, and the names of the 10 change types. Then add oracle JSON for them.
3. **Commit the report** (`docs/comment-transition/report/`, `tools/`, this file) to the rework branch: offered, not yet answered.
4. Whether to fix the **JUnit 5.8.2/5.10.0 mix in `pom.xml`** as a separate small change.
5. Whether `BlockTrackerImpl` should call `setStart(true)` on its start node, as other trackers do (ADR 0004 notes).

## 7. Known gaps and next work

- **Her May report's Table 1, cases 2, 5 and 6, have no scenario tests:**
  - case 2: only `if (…) {` and its `}` commented, with the body active;
  - case 5: RefactoringMiner similarity mismatch;
  - case 6: `else` inside `/* */`.

  Case 2 should still go through the exact path, which was kept, but this is unproven. Add tests S13–S15 when no oracle run is using the checkout.
- **Partial `else`/`catch`/`finally` restorations** are not implemented.
- **Untested paths:** every `blame()` loop and every `*WithLocalFiles` loop. They are covered only by compilation and shared code. The oracle exercises the local-files ones.
- **Limits per ADR 0008–0012:**
  - the container must keep its signature while the element is commented out;
  - variables are searched by name and type;
  - edits made inside a comment are not recorded.
- **Afterwards:**
  - merge or rebase onto current `master` (it needs a newer, unreleased RefactoringMiner; ADR 0003);
  - move the work to her feature branch (her decision);
  - prepare small upstream PRs;
  - discuss RefactoringMiner-level support with the maintainers.

## 8. Traps already hit (save yourself the time)

- **Temporary worktree with a junction:** `p0-wt` (in the scratch folder above) has `oracle` as a **directory junction to `D:\codeTracker\oracle`** (27,741 cached commits). **Remove the junction first**, with PowerShell `(Get-Item <wt>\oracle -Force).Delete()`, *then* run `git worktree remove --force <wt>`. A recursive delete could otherwise follow the junction and wipe the cache.
- **Do not commit** the `oracle` submodule pointer (it shows as modified), `pom-oracle-tmp.xml` (deleted by the runner; remove it by hand if a run was killed), the experiment outputs, or `src/main/java/org/codetracker/exception/` (her untracked folder).
- **CRLF files:**
  - `Method*`, `Attribute*` and `Variable*` tracker files, `BaseTracker*.java` and `BlockTrackerWithLocalFiles.java` are CRLF.
  - Git Bash `sed`/`grep` hide the `\r`, so scripted edits silently fail to match. Use the Edit tool, or `perl` with `\r\n` patterns, and check with PowerShell `[regex]::Matches($t,'(?<!\r)\n')`.
- **Three copies of each tracking loop:** git `track()`, git `blame()` (absent for variables), and `*WithLocalFiles.track()`, which is the one the oracle uses. Put the logic in `*TrackerChangeHistory` and only small hooks in the loops.
- **RefactoringMiner surprises:**
  - `UMLComment.isCommentedCode()` is useless here (true only for a lone `}`);
  - `getMatchedVariables()` is incomplete (use the statement mappings);
  - JDT core is only a runtime dependency of RM, so it was added to `pom.xml` (3.47.0).
- **CodeTracker `ChangeFactory`:** `RENAME`/`TYPE_CHANGE` need `.refactoring(...)` or it throws a `NullPointerException`. Build changes before touching the history ("prepare, then record").
- **API differences between trackers:** the attribute tracker's `blameReturn()` takes no argument (the block and method ones take the start element).
- **Current `master` does not compile** against the cached RefactoringMiner; the baselines use merge-base `375d0bb` or Phase 0 `81dcf47`.
- **Tooling on this machine:** no Python, LaTeX, pandoc or Node. Java 24 runs single-file programs (`java X.java`). In Windows PowerShell 5.1, Word's `SaveAs2` needs `[ref]` plain-string arguments, or it hangs.
- **Do not compile in `D:\codeTracker` while an oracle run uses it:** the run loads classes from `target/` while it goes.
