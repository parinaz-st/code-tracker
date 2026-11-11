# Phase 4c: Commented-out variables (and Phase 4 summary)

- **Branch:** `claude/comment-transition-rework` (on top of Phase 4b, `c462408`)
- **Date:** 2026-11-12
- **ADRs:** [0012](../adr/0012-commented-out-variables.md) (*Proposed*)
- **Goal:** A local variable that was commented out and later restored keeps its history.

## How it works

This is the block scheme (Phase 3) applied to variables:

1. **At the restore commit**, RefactoringMiner reports the declaration as added. Before `isAdded` turns it into `introduced`, `isVariableUncommented` runs:
   - It uncomments the method's deleted comment runs that parse as statements.
   - It maps the virtual parent to the current method.
   - It looks for the declaration that the tracked variable is mapped to, on an uncommented line.
   - It records `uncommented variable`, plus `rename` / `type change` when RefactoringMiner reports one.
2. **`followCommentedVariable`** walks back while a variable with the commented form's name and type is still commented out. It records `commented out variable` where it was commented out, and normal tracking continues from the active variable.
3. **If no active version is found**, `introduced` is added.

## Changes

| File | Change |
|---|---|
| `change/Change.java`, `change/ChangeFactory.java`, `change/variable/UncommentedVariable.java`, `change/variable/CommentedOutVariable.java` | New types `UNCOMMENTED_VARIABLE` ("uncommented variable") and `COMMENTED_OUT_VARIABLE` ("commented out variable"). |
| `VariableTrackerChangeHistory.java` | `isVariableUncommented` (called from `checkBodyOfMatchedOperations` before `isAdded`), `findCommentedForm`, `followCommentedVariable`, `closePendingUncommentedVariables`. |
| `VariableTrackerImpl.java`, `VariableTrackerWithLocalFiles.java` | The file source, the pending intercept, and the close-out. |
| `VariableCommentTransitionScenarioTest.java` (new) | Scenarios V1–V5. |

## Two problems found and fixed during this phase

1. **`getMatchedVariables()` is not complete.** It was empty for `long bonus = x;` mapped to itself in the virtual parent (found with a temporary debug print, since removed). The commented form is now taken from the **statement mapping** when `getMatchedVariables()` has no match.
2. **Variable rename and type changes need a refactoring object.** `ChangeFactory` throws a `NullPointerException` for `RENAME` / `TYPE_CHANGE` on variables without one. In V3 this left `uncommented variable` recorded, and then the fallback added `introduced` on the same edge. The fix:
   - pass RefactoringMiner's `ChangeVariableTypeRefactoring` / `RenameVariableRefactoring` from the virtual mapping to the factory;
   - build every change before touching the history, so a failure leaves nothing behind (ADR 0012).

## Scenario results

```bash
mvn -o test -Dtest='CommentTransitionScenarioTest,MethodCommentTransitionScenarioTest,AttributeCommentTransitionScenarioTest,VariableCommentTransitionScenarioTest,CommentTransitionAnalyzerTest,CommentedCodeUncommenterTest' -Dct.spec=true
```

Histories are listed oldest → newest. A variable's history records declaration changes, so the active period uses a rename (B) and a type change (C).

| # | Scenario | Before | After | Verdict |
|---|---|---|---|---|
| V1 | Variable commented out (D), method edited (E), restored (F) | F introduced | A intro, B rename, C type change, **D commented out variable**, **F uncommented variable** | ✓ |
| V2 | Same with `/* … */` | F introduced | A intro, B rename, C type change, D commented out variable, F uncommented variable | ✓ |
| V3 | Type edited during the restore (`long` → `double`) | F introduced | …, D commented out variable, **F uncommented variable + type change** | ✓ |
| V4 | First written as a comment (B), uncommented (C) | C introduced | C introduced + uncommented variable | ✓ (one origin) |
| V5 | Negative: unrelated commented-out variable deleted while `bonus` is added | B introduced | B introduced | ✓ |

## Phase 4 summary

| Element | Scenarios | Hooks | ADR |
|---|---|---|---|
| Blocks (Phases 1–3) | 12/12 | `BlockTrackerImpl` `track()`, `blame()`; `BlockTrackerWithLocalFiles` `track()` | 0004–0009 |
| Methods (4a) | 5/5 | `MethodTrackerImpl` `track()`, `blame()`; `MethodTrackerWithLocalFilesImpl` `track()` | 0010 |
| Attributes (4b) | 5/5 | `AttributeTrackerImpl` `track()`, `blame()`; `AttributeTrackerWithLocalFiles` `track()` | 0011 |
| Variables (4c) | 5/5 | `VariableTrackerImpl` `track()`; `VariableTrackerWithLocalFiles` `track()` | 0012 |

**Totals:** 27/27 scenarios, 71 unit tests (43 analyzer, 28 uncommenter).

**Not covered by any scenario** (compilation and shared code only): every `blame()` path and every `*WithLocalFiles` path. The final oracle run (ADR 0007) is the first real test of the local-files paths.

## Shared code changed (for the final oracle run, ADR 0007)

- `VariableTrackerChangeHistory.checkBodyOfMatchedOperations`: one more check before `isAdded`. It does work only when the tracked variable is reported as added **and** the method lost comments in that commit.

## Next

All element types are done. What remains:

- **Phase 5:** the single oracle regression run postponed by ADR 0007. Run it on the final code and on the Phase 0 commit `81dcf47` with a temporary pom that aligns JUnit, then compare case by case.
- Oracle JSON entries for the new change types, once you approve the names.
