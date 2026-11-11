# Phase 4b: Commented-out attributes

- **Branch:** `claude/comment-transition-rework` (on top of Phase 4a, `9737598`)
- **Date:** 2026-11-11
- **ADRs:** [0011](../adr/0011-commented-out-attributes.md) (*Proposed*)
- **Goal:** A field that was commented out and later restored keeps its history, including the commit that commented it out.

## How it works

This is the same scheme as for methods (Phase 4a), applied to fields:

1. **At the restore commit**, step 2 finds no identical attribute in the parent. Before refactoring detection, `isAttributeUncommented` runs:
   - It takes the class-level comments deleted in this commit and uncomments the runs that parse as class members.
   - It re-parses the parent and looks for a field with the same identity on the uncommented lines.
   - It records `uncommented attribute`, plus `initializer change` / `added` / `removed` if the initializer was edited during the restore.
2. **`followCommentedAttribute`** walks back while the field is still commented out. It records `commented out attribute` at the commit that commented it out, and normal tracking continues from the active field.
3. **If no active version is found**, `introduced` is added.

## Changes

| File | Change |
|---|---|
| `change/Change.java`, `change/ChangeFactory.java`, `change/attribute/UncommentedAttribute.java`, `change/attribute/CommentedOutAttribute.java` | New types `UNCOMMENTED_ATTRIBUTE` ("uncommented attribute") and `COMMENTED_OUT_ATTRIBUTE` ("commented out attribute"). |
| `AttributeTrackerChangeHistory.java` | `isAttributeUncommented`, `followCommentedAttribute`, `closePendingUncommentedAttributes`, and a side-effect-free initializer comparison. |
| `AttributeTrackerImpl.java` | Hooks in `track()` and `blame()`. |
| `AttributeTrackerWithLocalFiles.java` | The same hooks in its `track()` (the oracle path). |
| `util/CommentTransitionAnalyzer.java` | `findClass` and `deletedComments`, moved here from `MethodTrackerChangeHistory` and shared. |
| `MethodTrackerChangeHistory.java` | Uses the shared helpers (no behaviour change; the method scenarios still pass 5/5). |
| `AttributeCommentTransitionScenarioTest.java` (new) | Scenarios F1–F5. |

## Scenario results

```bash
mvn -o test -Dtest='CommentTransitionScenarioTest,MethodCommentTransitionScenarioTest,AttributeCommentTransitionScenarioTest,CommentTransitionAnalyzerTest,CommentedCodeUncommenterTest' -Dct.spec=true
```

Histories are listed oldest → newest. "Before" is the run after writing the scenarios and before adding the attribute hooks.

| # | Scenario | Before | After | Verdict |
|---|---|---|---|---|
| F1 | Field commented out (D), method edited (E), restored (F) | F introduced | A intro, B init change, C init change, **D commented out attribute**, **F uncommented attribute** | ✓ |
| F2 | Same with `/* … */` | F introduced | A intro, B, C init change, D commented out attribute, F uncommented attribute | ✓ |
| F3 | Initializer edited during the restore | F introduced | …, D commented out attribute, **F uncommented attribute + initializer change** | ✓ |
| F4 | First written as a comment (B), uncommented (C) | C introduced | C introduced + uncommented attribute | ✓ (one origin) |
| F5 | Negative: unrelated commented-out field deleted while `limit` is added | B introduced | B introduced | ✓ |

**Totals:** attributes 5/5, methods 5/5, blocks 12/12, unit tests 71.

## Notes

- All three attribute files use CRLF line endings. The edits were made in CRLF, and the diffs are insertions only.
- The attribute tracker's `blameReturn()` takes no argument. A first build failed because the hook called `blameReturn(start)`, as the block and method trackers do. This was fixed before the test run.

## Shared code changed (for the final oracle run, ADR 0007)

- All three attribute loops: one new check after step 2. It does work only when the class lost comments in that commit.
- `CommentTransitionAnalyzer`: two helpers moved in. Pure functions, no behaviour change.

## Limitations (see ADR 0011)

- Type, modifier, visibility, or annotation changes between the commented and restored forms are not matched.
- Enum constants are not covered by a scenario.
- `blame()` and the local-files path are not covered by the scenarios.

## Next

- **Phase 4c, variables:** statement-level, like blocks.
