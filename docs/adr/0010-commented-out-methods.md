# 0010. Commented-out methods (report Algorithm 3)

- Status: Proposed
- Date: 2026-11-11
- Phase: 4a
- Related: report *Enhancing CodeTracker Accuracy*, Section 5 and Algorithm 3; [0004](0004-pending-uncomment-falls-back-to-introduced.md), [0008](0008-commented-state-tracking-by-direct-search.md), [0009](0009-virtual-uncomment-with-refactoringminer-mapping.md)

## Context

The report's Algorithm 3 targets methods. When a whole method is commented out and later restored, CodeTracker reports it as introduced at the restore commit, and its earlier history is lost. Phases 1–3 solved the same problem for blocks: detection at the restore commit, a pending node, a backward search, and a close-out with `introduced`. Methods differ in three ways:

- The comments of a commented-out method sit at **class level** (`UMLAbstractClass.getComments()`), not inside an operation, so there is no `UMLCommentListDiff` for them.
- The method tracker classifies a method as added only in step 5, after building the full commit model. That is the most expensive step.
- Commented-out *methods* are class members, not statements, so they must be parsed as class body declarations.

## Decision

1. **Where:** right after step 3 fails (no method with the same signature in the parent) and before step 4, `MethodTrackerChangeHistory.isMethodUncommented` checks for a restored method. This avoids the step 4/5 diffs for restored methods. It is wired into all three method loops: `MethodTrackerImpl.track()`, `MethodTrackerImpl.blame()`, and `MethodTrackerWithLocalFilesImpl.track()`.
2. **How it is detected:**
   - Take the class-level comments of the method's class in the parent whose text (whitespace ignored) no longer appears in the current class. These are the deleted comments.
   - Uncomment the runs among them that parse as **class body declarations** (`CommentedCodeUncommenter.Kind.CLASS_BODY_DECLARATIONS`).
   - Re-parse the parent file, and look for a method with the same signature whose closing line was uncommented.
   - If found, record `UNCOMMENTED_METHOD` ("uncommented method"), plus `BODY_CHANGE` if the restored body differs from the commented one.
3. **Pending, follow, confirm, close:** this is the same scheme as for blocks (ADR 0004 and 0008), implemented in `MethodTrackerChangeHistory`:
   - `followCommentedMethod` walks older commits. It stops at the first commit where a method with the commented form's signature is active, and records `COMMENTED_OUT_METHOD` ("commented out method") at the commit that commented it out.
   - The walk continues only while the method is still found among the class's uncommented comments.
   - Otherwise `closePendingUncommentedMethods` adds `introduced`.
4. Two new change types and classes: `UNCOMMENTED_METHOD` / `UncommentedMethod` and `COMMENTED_OUT_METHOD` / `CommentedOutMethod`.

## Consequences

- M1–M5 pass, including a class edit while the method is commented out, a `/* */` comment, a body edit during the restore, a method first written as a comment, and a negative control (an unrelated commented-out method deleted while a new one is added).
- The check runs for every method whose signature has no match in the parent, but it only reads the file again when the class lost comments in that commit. Ordinary histories pay nothing extra.
- **Limitations:**
  - The method must keep its **signature and class** across the commented period, and between the commented and the restored form. A method that is renamed while uncommented, or moved to another file, falls back to the normal steps and is reported as before.
  - The deleted-comment check compares comment texts. A comment that is only *edited* while code is uncommented is also treated as deleted, which is harmless because it still has to parse as code.
  - Partial comment-outs (for example, only the method's signature commented) are not detected.
  - The `blame()` path and the local-files path are covered by compilation and the shared code only. The scenarios use `track()` with Git.
- Statement-level comment transitions *inside* a method body are not recorded in method histories. That is the block tracker's job (Phases 1–3).
