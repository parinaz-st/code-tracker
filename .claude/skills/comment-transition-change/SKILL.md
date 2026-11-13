---
name: comment-transition-change
description: Checklist for changing CodeTracker's comment-out/uncomment detection (blocks, methods, attributes, variables) - branch rules, where the logic lives, CRLF files, fast checks, real-oracle re-scoring, phase doc and ADR, commit. Use for any code change to comment-transition detection or to the *Tracker*/*ChangeHistory classes it touches.
---

# Changing comment-transition detection

Background: `docs/comment-transition/HANDOFF.md` (read it first), the phase docs `docs/comment-transition/phase-*.md`, and the ADRs in `docs/adr/`. Repository conventions are in `AGENTS.md`.

## Before you start

- **Branch:** work only on `claude/comment-transition-rework`. The author moves changes to `feature/CmtTransitionDetectionBlock` herself. **Do not push** unless she says so.
- **Where the logic goes:** every tracker has up to three copies of its loop: `*TrackerImpl.track()`, `*TrackerImpl.blame()` (none for variables), and `*TrackerWithLocalFiles.track()` (the one the oracle tests use). Put the logic in `*TrackerChangeHistory` or `util/CommentTransitionAnalyzer` / `util/CommentedCodeUncommenter`, and add only small hooks to the loops.
- **Line endings:** the `Method*`, `Attribute*` and `Variable*` tracker files, `BaseTracker*.java` and `BlockTrackerWithLocalFiles.java` are **CRLF**. Edit them with the Edit tool, or with `perl -pi` on the line content only. Afterwards check with PowerShell that no bare LF slipped in: `[regex]::Matches($t,'(?<!\r)\n').Count` must be 0. Git Bash `sed`/`grep` hide the `\r`.
- **RefactoringMiner surprises:**
  - `UMLComment.isCommentedCode()` is useless here;
  - `getMatchedVariables()` is incomplete, so use the statement mappings;
  - `RENAME` / `TYPE_CHANGE` changes need `.refactoring(...)`;
  - build all changes before touching the history ("prepare, then record").

## Loop

1. **Test first.** Write a unit test, in `CommentedCodeUncommenterTest`, `FindActiveBlockTest`, `DeletedCommentRunsTest` or `CommentTransitionAnalyzerTest`, or a scenario in `src/test/java/org/codetracker/commenttransition/*ScenarioTest`, that fails for the reason you are fixing. Take the pattern from a real case when there is one.
2. **Implement.**
3. **Fast checks**, a few minutes:

   ```bash
   mvn -o test -Dtest='CommentTransitionScenarioTest,MethodCommentTransitionScenarioTest,AttributeCommentTransitionScenarioTest,VariableCommentTransitionScenarioTest,CommentTransitionAnalyzerTest,CommentedCodeUncommenterTest,FindActiveBlockTest,DeletedCommentRunsTest' -Dct.spec=true
   ```

   All must pass: 27 scenarios and 82 unit tests as of Phase 7.
4. **Re-score the real oracle** (42 histories). A detection change can make tracking reach older commits, so first refresh the commit logs and cache with `LocalOracleBuilder <kind> <clone> <spec>...`, then run `score` and compare with `comment-transition-expected.txt` history by history (see the `oracle-history` skill). Every changed history must be explained. Update the expected files only for improvements you understand.
5. **Published-oracle regression**, once per phase rather than after every change (ADR 0007). See the `oracle-regression` skill.
6. **Document:**
   - `docs/comment-transition/phase-N.md`: what changed (file, change, ADR item), the tests, before/after numbers on the real oracle, the regression result, and what is still missed;
   - an ADR `docs/adr/NNNN-<title>.md` in Nygard format (Status: Proposed, Context, Decision, Consequences), added to the index `docs/adr/README.md`. **Supersede, never rewrite** an accepted ADR;
   - a short update block at the top of `HANDOFF.md`.
7. **Commit** on the rework branch with explicit paths. Do not commit:
   - the `oracle` submodule pointer;
   - `pom-oracle-tmp.xml`;
   - `experiments/**` outputs;
   - `src/main/java/org/codetracker/exception/` (the author's own untracked folder);
   - temporary diagnostic classes.

## Open decisions (ask, do not decide)

- ADR 0005: a statement-level transition *instead of* or *in addition to* `body change`. Currently it is recorded in addition.
- Whether statement-level types should count in oracle scoring. Currently `OracleTest` filters them out with the `BlockBodyChange` subclasses.
- The labelling conventions in ADR 0013, item 6.
