# 0009. Edit-tolerant uncomment detection with a virtual parent and RefactoringMiner mapping

- Status: Proposed
- Date: 2026-11-11
- Phase: 3
- Related: analysis 4.2, 5 (option C), 5.2; [0006](0006-whitespace-insensitive-code-matching.md), [0008](0008-commented-state-tracking-by-direct-search.md)

## Context

After Phase 2, an uncommented block was only detected when its signature and body matched the deleted comments exactly, ignoring whitespace. A block that was edited while being uncommented was reported as newly introduced: a changed condition (S5) or a changed statement inside (S11). Hand-written fuzzy matching (option B in the analysis) would need tuned thresholds. RefactoringMiner already maps statements tolerantly and reports the differences as replacements.

Two practical constraints came up while building this:

- RefactoringMiner's `UMLComment.isCommentedCode()` is not reliable for this purpose. In a probe it returned `true` only for lone `}` lines and `false` for `// if (x > 0) {`, `// count++;`, and a commented-out `for` loop.
- `BlockTrackerWithLocalFiles`, the tracker used by the oracle tests, has its own copy of the tracking loop and reads files from the `oracle/commits` cache instead of Git. Phases 1 and 2 had only been wired into `BlockTrackerImpl`.

## Decision

1. **Virtual parent.** When the tracked block is unmatched in the real body mapping and the method lost comments in the commit, `BlockTrackerChangeHistory.isBlockUncommentedWithEdits` runs these steps:
   - `CommentedCodeUncommenter.uncomment` turns the *deleted* comments that are valid Java statements back into code, **in place**. Markers are replaced by spaces, so offsets and line numbers do not move.
   - The parent file is re-parsed with that content.
   - A `UMLOperationBodyMapper` maps the virtual parent method against the current method.
   - If RefactoringMiner maps the tracked block to a statement on an uncommented line, the block is uncommented. The difference between the commented form and the restored block is recorded on the same edge as `EXPRESSION_CHANGE` (signature) and/or `BODY_CHANGE` (body).
   - This runs **after** the exact detection from Phase 1, and only when the exact detection did not match.
2. **What counts as commented-out code.** Comments are grouped into runs: consecutive `//` lines, or one `/* */` comment. Javadoc is skipped. A run counts as code only if the Eclipse JDT parser accepts it as Java statements with no `MALFORMED`/`RECOVERED` nodes. `org.eclipse.jdt.core` becomes a direct dependency, at the version RefactoringMiner already resolves (3.47.0). Before, it was only on the runtime classpath.
3. **Searching older commits uses the commented form.** The pending entry now stores the block *as it looked while commented out* (the mapped virtual statement), and the Phase 2 search looks for that form. So the search works even when the restored block differs from the commented one (S5, S11).
4. **One implementation for both trackers.** A new interface, `CommitFileSource`, reads a file at a commit or at its parent. `BaseTracker` implements it with Git, and `BaseTrackerWithLocalFiles` implements it with the `oracle/commits` cache, without network access. The Phase 2 search (`followCommentedBlock`) moved from `BlockTrackerImpl` into `BlockTrackerChangeHistory`, and both trackers call it. `BlockTrackerWithLocalFiles` now also calls `closePendingUncommentedBlocks()`.

## Consequences

- All 12 scenarios pass, including S5 (edited condition), S11 (edited body), and S12, a new negative control where an unrelated commented-out loop is deleted while a new block is written.
- The virtual parent is built only for blocks that would otherwise be reported as added, and only when comments were deleted in that commit. Ordinary histories pay nothing. When it does run, it costs one extra parse of one file and one body mapping.
- **Partial runs are not uncommented.** If only a block's signature line is commented (`// if (x > 0) {` with an active body), the run does not parse on its own and is left alone. This case is still handled by the exact detection from Phase 1 only.
- RefactoringMiner decides what "the same block" means. If it maps the restored block to unrelated commented-out code, that is reported as an uncomment. S12 checks one such risk, but the oracle run (ADR 0007) is the real measure.
- Statement-level transitions inside a matched block (Phase 1, `detectCommentTransitionsInsideMatchedBlock`) still use the hash matching. Moving them to the virtual mapping is possible but not done.
- The older-commit search (ADR 0008) still matches the commented form by signature, ignoring whitespace. A block whose signature changed *while commented out* is not followed.
- The JDT version must be kept in line with RefactoringMiner's when `rm.version` changes (see the comment in `pom.xml`).
- The local-files port is covered only by compilation and the shared code path. The scenario tests use the Git tracker, so the final oracle run is the first real test of `BlockTrackerWithLocalFiles`.
