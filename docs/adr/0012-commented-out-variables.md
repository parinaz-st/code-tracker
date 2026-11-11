# 0012. Commented-out variables

- Status: Proposed
- Date: 2026-11-12
- Phase: 4c
- Related: [0009](0009-virtual-uncomment-with-refactoringminer-mapping.md) (same detection for blocks), [0004](0004-pending-uncomment-falls-back-to-introduced.md), [0008](0008-commented-state-tracking-by-direct-search.md)

## Context

Local variables live inside method bodies, like blocks. RefactoringMiner reports an uncommented declaration among the body mapper's *added variables*, and `VariableTrackerChangeHistory.isAdded` turns it into `introduced`. A variable's identity includes its method's identity, and so the method's body hash, exactly like blocks. A variable's history records only declaration-level changes (rename, type, modifiers, annotations), not initializer edits.

Two facts came up while implementing this:

- `UMLOperationBodyMapper.getMatchedVariables()` does **not** list every pair of declarations RefactoringMiner maps. It was empty for a plain `long bonus = x;` mapped to itself. The reliable signal is the statement mapping between the two declaration statements.
- `ChangeFactory` builds `RENAME` and `TYPE_CHANGE` for variables only from a RefactoringMiner refactoring. Without one it throws a `NullPointerException`. The first version recorded `uncommented variable`, then failed on the type change, and fell back to `introduced` with a half-recorded edge.

## Decision

1. **Detection**, in `checkBodyOfMatchedOperations` right before `isAdded`, so both the Git and the local-files tracker use it. `isVariableUncommented`:
   - uses the method's deleted comments that parse as statements;
   - builds the virtual parent and maps it to the current method;
   - finds the declaration the tracked variable is mapped to on an uncommented line, first through `getMatchedVariables()` and then through the statement mappings.
2. **Rename and type changes made during the restore** are recorded only when RefactoringMiner reports the matching `RenameVariableRefactoring` / `ChangeVariableTypeRefactoring` in the virtual mapping. The factory gets that refactoring.
3. **Prepare, then record.** All change factories are built first. The history is only changed after that succeeds, so an exception can never leave a partial edge.
4. **Pending, follow, confirm, close:** this follows ADR 0004 and 0008. `followCommentedVariable` walks older commits (same method signature) and looks for a variable with the commented form's **name and type**: active means confirmed (`COMMENTED_OUT_VARIABLE`), still commented means continue, otherwise stop. `closePendingUncommentedVariables` adds `introduced` when no active version is found.
5. New change types: `UNCOMMENTED_VARIABLE` ("uncommented variable") and `COMMENTED_OUT_VARIABLE` ("commented out variable").
6. Hooks: the file source, the pending intercept, and the close-out in `VariableTrackerImpl.track()` and `VariableTrackerWithLocalFiles.track()`. The variable tracker has no `blame()`.

## Consequences

- V1–V5 pass: a method edit while the variable is commented out, a `/* */` comment, a type edit during the restore, first written as a comment, and a negative control.
- **Limitations:**
  - The older-commit search matches by name and type. A method with several variables of the same name and type (for example, in sibling scopes) may confirm against the wrong one.
  - The enclosing method must keep its signature while the variable is commented out.
  - A rename or type change during the restore that RefactoringMiner does not report as a refactoring is not recorded; the uncomment itself still is.
  - Parameters and lambda parameters are not covered by a scenario.
  - The local-files path is covered by compilation and the shared code only.
- The prepare-then-record rule is applied here only. The block, method, and attribute paths use change types that need no refactoring (expression, body, and initializer changes), which scenarios S5, M3, and F3 exercise.
