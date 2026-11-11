# 0011. Commented-out attributes

- Status: Proposed
- Date: 2026-11-11
- Phase: 4b
- Related: [0010](0010-commented-out-methods.md) (same scheme for methods), [0004](0004-pending-uncomment-falls-back-to-introduced.md), [0008](0008-commented-state-tracking-by-direct-search.md)

## Context

Fields are class members, like methods, so a commented-out field sits in the class-level comments. The attribute tracker has no "body change" step. Step 2 looks for an attribute with the same identity in the parent. An attribute's identity is its class, modifiers, visibility, name, type, and annotations; the **initializer is not part of it**. If step 2 finds nothing, the tracker goes straight to refactoring detection and finally reports the attribute as added. The existing `checkInitializerChange` records initializer differences, but it also re-queues the node, which would interfere with the pending mechanism.

## Decision

1. The method scheme from ADR 0010 is reused for attributes in `AttributeTrackerChangeHistory`:
   - `isAttributeUncommented` runs right after step 2 fails, before local refactoring detection. It uses the class-level comments deleted in the commit that parse as class members.
   - `followCommentedAttribute` walks older commits while the field is still commented out, and records `COMMENTED_OUT_ATTRIBUTE` where it was commented out.
   - `closePendingUncommentedAttributes` adds `introduced` if no earlier active version exists.
2. The commented and restored forms are matched by the attribute's normal identity (`equalIdentifierIgnoringVersion`). Because the initializer is not part of it, an initializer edited during the restore is recorded on the uncommented edge as `INITIALIZER_CHANGE` / `INITIALIZER_ADDED` / `INITIALIZER_REMOVED`. This uses a small helper with no side effects instead of `checkInitializerChange`.
3. New change types: `UNCOMMENTED_ATTRIBUTE` ("uncommented attribute") and `COMMENTED_OUT_ATTRIBUTE` ("commented out attribute").
4. The helpers `findClass` and `deletedComments` moved from `MethodTrackerChangeHistory` into `CommentTransitionAnalyzer`, so methods and attributes share them.
5. The hooks are in all three attribute loops: `AttributeTrackerImpl.track()`, `AttributeTrackerImpl.blame()`, and `AttributeTrackerWithLocalFiles.track()`. The attribute tracker's `blameReturn()` takes no argument, unlike the block and method trackers.

## Consequences

- F1–F5 pass: a class edit while the field is commented out, a `/* */` comment, an initializer edit during the restore, a field first written as a comment, and a negative control.
- **Limitations:**
  - A field whose type, modifiers, visibility, or annotations change between the commented and the restored form is not matched, and falls back to the normal steps.
  - A comment run that declares several fields (`// int a, b;`) produces several attributes; the matching one is chosen by identity.
  - Enum constants are found by `AbstractTracker.getAttribute`, but they are not covered by a scenario.
  - `blame()` and the local-files path are covered by compilation and the shared code only.
- Ordinary histories pay nothing extra: the check only reads the file again when the class lost comments in that commit.
