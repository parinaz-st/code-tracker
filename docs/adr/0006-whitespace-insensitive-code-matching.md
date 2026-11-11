# 0006. Whitespace-insensitive matching and trivial-line filtering

- Status: Proposed
- Date: 2026-11-11
- Phase: 1
- Related: analysis 4.2, 4.3, 4.4

## Context

`CommentTransitionAnalyzer` compares SHA-512 hashes of code and comment text. On the original branch:

- Whitespace was collapsed to single spaces, but not removed, so `//if(x>0){` never matched `if (x > 0) {` (S4).
- Some comparisons used RefactoringMiner's normalized `getString()` and others used `getActualSignature()`, each with different trimming.
- Block keywords were detected with `startsWith`, so `double x;`, `format(…)`, `ifPresent(…)`, and `doSomething();` counted as block signatures.
- Every comment line counted as evidence, including lines such as `return;`, `break;`, or `}` that match many unrelated statements.

## Decision

1. All code/comment comparisons go through one function, `codeHash(s) = SHA-512(s with all whitespace removed)`.
2. Block keywords are matched with `^(if|else|for|while|do|try|catch|finally|switch|synchronized|static)\b`.
3. `generateCommentTextHashMap(comments, includeTrivialLines)` skips *trivial* lines (braces, `return;`, `break;`, `continue;`, `return null/true/false/0;`, `else`, `try {`, `finally {`, `default:`) when the map is used as **evidence** of a transition (statement-level detection). Trivial lines are still included when checking that an uncommented block's body is complete, because a real block may contain them.
4. String helpers that can be tested without RefactoringMiner objects (`hasBlockKeyword`, `isLeafStatement`, `isTrivialCodeLine`, `codeHash`, `normalizeCommentText`) are covered by `CommentTransitionAnalyzerTest`.

## Consequences

- Restored code with different formatting is detected (S4 is now detected at commit F).
- Removing all whitespace could in theory make different code look equal (`int ab` vs `inta b`), but such text is not valid Java, and the comparison is always against a statement that RefactoringMiner already parsed. String literals that differ only in spaces (`"a b"` vs `"ab"`) would collide. This is accepted for now and goes away with the token- or AST-based matching in Phase 3.
- Code edited while being uncommented is still not detected (S5). That is the purpose of Phase 3.
- An uncommented statement that consists of a single trivial line (for example, `return;` alone) is no longer reported as `uncommented statement`. It is still reported as `body change`.
