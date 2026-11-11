# Phase 4a: Commented-out methods (report Algorithm 3)

- **Branch:** `claude/comment-transition-rework` (on top of Phase 3, `5992174`)
- **Date:** 2026-11-11
- **ADRs:** [0010](../adr/0010-commented-out-methods.md) (*Proposed*)
- **Goal:** Implement the report's Algorithm 3: a whole method that was commented out and later restored keeps its history, including the commit that commented it out.

Phase 4 is split into three parts, each its own commit: **4a methods** (this report), **4b attributes**, and **4c variables**. Each tracker has two or three copies of its loop, so one combined change would be too large to review.

## How it works

```
A ─── B ─── C ──────── D ──────────── E ─────────────── F
intro body  body   commented out   (helper() edited,   uncommented
                    (class level)   run() still a comment)
```

1. **At F**, step 3 finds no method `run(int)` in the parent. Before the expensive steps 4 and 5, `isMethodUncommented` runs:
   - It takes the class-level comments deleted in this commit.
   - It uncomments the runs that parse as class members.
   - It re-parses the parent and finds `run(int)` on the uncommented lines.
   - It records `uncommented method`, plus `body change` if the body was edited during the restore.
2. **The pending node** is then followed back by `followCommentedMethod`. While `run(int)` is still found among the class's commented-out members, the walk continues. At C it is active, so `commented out method` is recorded on the commit D, and normal tracking continues from C.
3. **If no active version is found**, `introduced` is added (M4).

## Changes

| File | Change |
|---|---|
| `change/Change.java`, `change/ChangeFactory.java`, `change/method/UncommentedMethod.java`, `change/method/CommentedOutMethod.java` | New types `UNCOMMENTED_METHOD` ("uncommented method") and `COMMENTED_OUT_METHOD` ("commented out method"). |
| `MethodTrackerChangeHistory.java` | `isMethodUncommented`, `followCommentedMethod`, `closePendingUncommentedMethods`, and helpers. |
| `MethodTrackerImpl.java` | Hooks in `track()` and `blame()`: the check after step 3, the pending intercept, and the close-out. |
| `MethodTrackerWithLocalFilesImpl.java` | The same hooks in its `track()` (the oracle path). |
| `util/CommentedCodeUncommenter.java` | New `Kind.CLASS_BODY_DECLARATIONS` (JDT `K_CLASS_BODY_DECLARATIONS`) next to `STATEMENTS`. |
| `CommentedCodeUncommenterTest.java` | +11 tests: members vs statements and prose, and a commented method uncommented as a member but not as statements. |
| `ScenarioAssertions.java` (new) | The scenario harness, moved out of the block test so both scenario classes share it. No behaviour change: the block scenarios still pass 12/12. |
| `MethodCommentTransitionScenarioTest.java` (new) | Scenarios M1–M5. |

## Scenario results

```bash
mvn -o test -Dtest='CommentTransitionScenarioTest,MethodCommentTransitionScenarioTest,CommentTransitionAnalyzerTest,CommentedCodeUncommenterTest' -Dct.spec=true
```

Histories are listed oldest → newest. "Before" is this phase's code with the method hooks not yet added (run after writing the scenarios).

| # | Scenario | Before | After | Verdict |
|---|---|---|---|---|
| M1 | Method commented out (D), another method edited (E), restored (F); report Fig. 9 | F introduced | A intro, B body, C body, **D commented out method**, **F uncommented method** | ✓ |
| M2 | Same with `/* … */` | F introduced | A intro, B body, C body, D commented out method, F uncommented method | ✓ |
| M3 | Body edited during the restore | F introduced | …, D commented out method, **F uncommented method + body change** | ✓ |
| M4 | First written as a comment (B), uncommented (C) | C introduced | C introduced + uncommented method | ✓ (one origin) |
| M5 | Negative: unrelated commented-out method deleted while `run` is added | B introduced | B introduced | ✓ |

**Totals:** method scenarios 5/5, block scenarios 12/12, unit tests 71 (43 analyzer, 28 uncommenter).

## Notes

- **Line endings:** `MethodTrackerImpl.java` and `MethodTrackerWithLocalFilesImpl.java` use CRLF. Git Bash's `sed` hides the `\r`, which made a first scripted edit miss two hooks. The files were checked and normalized, and the diff is pure insertions.

## Shared code changed (for the final oracle run, ADR 0007)

- All three method loops: one new check after step 3. It does work only when the class lost comments in that commit.
- The pending intercept and close-out run only for restored methods.

## Limitations (see ADR 0010)

- The method must keep its signature and class while commented out and when restored. A rename or move falls back to the normal steps.
- Partial comment-outs (only the signature commented) are not detected.
- `blame()` and the local-files tracker are not covered by the scenarios.

## Next

- **Phase 4b, attributes:** class-level like methods, so it reuses `Kind.CLASS_BODY_DECLARATIONS` and the deleted class-comment logic.
- **Phase 4c, variables:** statement-level, like blocks.
