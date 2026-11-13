# Comment Transition Detection in CodeTracker: Analysis and Plan

- **Author of the idea / implementation:** Parinaz Sattarzadeh (`parinaz-st`)
- **Branch reviewed:** `feature/CmtTransitionDetectionBlock` (head `7c8d874576a`, 5 commits beyond `master`)
- **Date of this analysis:** 2026-11-11 (Phase 0 results added the same day, Section 8)
- **Upstream project:** CodeTracker by Mehran Jodavi and Nikolaos Tsantalis (Concordia University, Montreal), https://github.com/jodavimehran/code-tracker

This document records the session in which the project, the report *Enhancing CodeTracker Accuracy* (P. Sattarzadeh, April 5, 2026), and the branch implementation were reviewed. It lists the limitations found, gives a plan for fixing or redesigning comment-transition detection, and records the Phase 0 baseline.

---

## 0. Session log

1. **"Are you there?"**: Session started on `D:\codeTracker`, branch `master`.
2. **Request:** Scan the project, read the papers, read the findings and the implementation on `feature/CmtTransitionDetectionBlock`, plan corrections or alternative implementations, export everything to Markdown, and create a first `CLAUDE.md` / `AGENTS.md`.
3. **About the "blocked" PDF:** The PDF (`Enhancing-Code-Tracker.pdf`, 1.7 MB) was not blocked. It was too large to attach inline, so it had to be read separately. Page rendering (`pdftoppm`) was not installed, so the text was extracted with `pdftotext` (from Git for Windows). The figures (code screenshots, Fig. 1–8) could not be seen. Only the text and tables were available.
4. **Sources actually read:**
   - The report *Enhancing CodeTracker Accuracy* (12 pages, full text).
   - The repository README, including the citations for the FSE 2022 paper (method/variable tracking) and the TSE 2024 paper (block tracking).
   - `BlockTrackerImpl`, `BlockTrackerChangeHistory`, `element/Block`, `element/Method`, and `util/Util`.
   - The complete diff `master...feature/CmtTransitionDetectionBlock`.
   - The FSE 2022 and TSE 2024 PDFs were **not** attached. Section 1 summarizes their algorithm from the code, which implements it directly.
5. **Outputs of the first turn:** this document, `AGENTS.md`, and `CLAUDE.md` (which imports `AGENTS.md`).
6. **"Yes, start Phase 0":**
   - Switched to the branch.
   - Fixed the stale RefactoringMiner version in `pom.xml`.
   - Added 10 fixture-based scenario tests.
   - Ran them on the branch and on the branch's merge-base with `master`.
   - Results are in Section 8. They confirm defects 4.1, 4.2, and 4.3.
   - This file had been removed from `docs/` between the two turns. It was recreated from the session.

---

## 1. CodeTracker concept (as implemented)

CodeTracker builds a **refactoring-aware commit change history** for one code element: a method, variable, attribute, class, block (TSE 2024), comment, import, or annotation. It starts at a given commit and walks backward through the commits that touched the file, until the element is *introduced*.

### 1.1 Identity

Every element has an `identifierIgnoringVersion`. Two elements in different commits are "the same, unchanged" when these identifiers are equal.

- **Method** (`element/Method.java`): the identifier is built by `getIdentifierExcludeVersion(op, true, true, true)` and **includes the documentation and body hash**. A second identifier, `identifierIgnoringVersionAndDocumentationAndBody`, is used to find "the same method with a changed body".
- **Block** (`element/Block.java:42-48`): the identifier is `method.getIdentifierIgnoringVersion() + "$" + type + ":{" + SHA512(all statements) + "," + signature + "}"`. As a result, **a block's identity depends on the body hash of its enclosing method.** This fact is what the reconnection heuristic in Section 4.1 relies on, and what limits it.

### 1.2 Backward tracking loop (block tracker, `BlockTrackerImpl.java:57-300`)

For each element that is still "open" in the queue (`changeHistory.poll()`), and for each commit that touched its file:

| Step | What happens | Code |
|---|---|---|
| 1 | Load the right model (current commit), find the method by its full identifier, then find the block by its identifier. If either is missing, `continue` to the next commit. | `:80-90` |
| 2 | **No change:** the same method identifier exists in the parent model, so skip this commit. | `:115-119` |
| 3 | **Body/doc change:** the method matches by identifier without body. Build a lightweight `UMLOperationBodyMapper`, then call `checkBodyOfMatchedOperations`, which tries **matched**, then **added** (and, on the branch, **uncommented**). | `:121-159` |
| 4 | **Local refactorings:** run `leftModel.diff(rightModel)` and check merge, extract/inline, refactored method, and the body of the matched operation. | `:160-184` |
| 5 | **Commit-level refactorings:** build partial or full commit models, including cross-file moves, and check class-level refactorings. Section 2 of the report extends the dependency regex used here (`getRightSideFileNames`). | `:186-300` |

When a match is found, a `Change` edge is added between the `blockBefore` node (parent version) and the `blockAfter` node (current version). The `blockBefore` node goes back into the queue. When the element is marked as added (`handleAdd`), the queue stops processing it (`currentBlock.isAdded()` at `:59`).

### 1.3 Role of RefactoringMiner

RefactoringMiner supplies the UML models, the `UMLOperationBodyMapper` (statement mappings, non-mapped leaves and inner nodes on each side T1/T2, replacements), and `UMLCommentListDiff` (added and deleted comments for the mapped operation). RefactoringMiner does **not** know that a comment contains code. It treats commenting and uncommenting as "delete statement + add comment" and the reverse.

---

## 2. The report: summary

*Enhancing CodeTracker Accuracy* lists four enhancements:

| # | Problem | Proposed fix | Status in report |
|---|---|---|---|
| 1 | Extract Class + Move Method is detected as **Rename Class** when the dependency A→B is not detected (DI by field, constructor, or setter; static calls; `B::new`; `List<B>` attributes). | Replace the dependency regexes in `getRightSideFileNames` with `(?<![\w$])ClassName(?![\w$])`, so every changed file that references B is included (Algorithm 1). | Complete |
| 2 | Splitting `int m, n = 0;` into separate declarations makes the variable look newly introduced. | In step 3, scan the non-mapped leaves of the parent for multi-variable declarations and compare identifiers that ignore the method body hash. Add a `VARIABLE_DECLARATION_SPLIT` change type (Algorithm 2). | Prototype |
| 3 | The method body hash is `String.hashCode()` (32-bit), which collides (`"Aa"` and `"BB"`), so real changes are reported as NoChange. | Use SHA-512 in `getIdentifierExcludeVersion` and `equalBody` over the reconstructed body string. | Complete |
| 4 | **Code that is commented out and later restored** (Fig. 9: A introduced → B, C active → D, E commented → F restored) is reported as *introduced* at F, so the history before F is lost. | When an element is classified as added, look for its normalized text in the parent's comments, then mark it `COMMENTED_CODE_RESTORED_PENDING`. Confirm the mark if an active version is found further back, then add `COMMENTED_OUT` (C→D) and `COMMENTED_CODE_RESTORED` (E→F). Otherwise discard the mark (Algorithm 3, written for **methods**). | Idea only ("potential direction") |

The rest of this document covers **#4**. The branch implements it at the **block level**, not at the method level as Algorithm 3 describes.

---

## 3. What the branch implements

The diff adds 505 lines and removes 35 across 7 files.

| File | Change |
|---|---|
| `change/Change.java` | New types: `COMMENTED_OUT_STATEMENT`, `UNCOMMENTED_STATEMENT`, `UNCOMMENTED_BLOCK` ("uncommented Block"). |
| `change/ChangeFactory.java` + 3 new classes | `CommentedOutCodeInBlock` and `UncommentedCodeInBlock` (both extend `BlockBodyChange`); `UncommentedBlock` (extends `BlockChange`). |
| `util/CommentTransitionAnalyzer.java` (new, 395 lines) | Normalizes comment text, hashes comments with SHA-512 (one hash for each line, plus one for the whole `/* */` comment), reconstructs block signatures that span several comment lines, checks scope, and checks body continuity. |
| `BlockTrackerChangeHistory.java` | (a) `checkBodyOfMatchedOperations` now tries `isBlockUncommented` **before** `isAdded`. (b) In `isMatched`, when a matched block's body changed, `detectCommentTransitionsInsideMatchedBlock` can replace `BODY_CHANGE` with `UNCOMMENTED_STATEMENT` and/or `COMMENTED_OUT_STATEMENT`. |

### 3.1 Feature A: transitions inside a matched block (`isMatched` → `detectCommentTransitionsInsideMatchedBlock`)

1. Take `deletedComments` and `addedComments` from `bodyMapper.getCommentListDiff()`.
2. Keep only the comments whose lines fall strictly inside `blockBefore` (deleted comments) or `blockAfter` (added comments).
3. Hash each normalized comment line with SHA-512.
4. **Uncommented:** look for a match in one of these places:
   - a non-mapped leaf of T2 inside the block (`getString().trim()`),
   - a non-mapped inner node of T2 inside the block (`getActualSignature()`),
   - fragment 2 of a `LeafMapping` **that has replacements** (commit `7e3fd60`: RefactoringMiner sometimes maps an uncommented statement to a different but similar statement).
5. **Commented out:** the same check, mirrored on T1.
6. If any transition is found, emit those transition types **instead of** `BODY_CHANGE`.

### 3.2 Feature B: a whole block was uncommented (`isBlockUncommented`)

1. Reconstruct block signatures from the deleted comments, both single-line ones and ones that span several `//` lines or a `/* */` comment (commit `7c8d874`).
2. For each non-mapped inner node of T2, normalize the whitespace of its `getActualSignature()`, hash it, and look it up.
3. Require the block to be the tracked block (`equalOperator`).
4. Check body continuity: every inner node and leaf of the uncommented block must be either mapped or present in the deleted comments.
5. Create a **virtual** `blockBefore = Block.of(node, container2, parentVersion)`, add an `UNCOMMENTED_BLOCK` change, and put the virtual block back in the queue (`elements.add`). The virtual block is **not** marked as added, so tracking is meant to continue further back.

This covers the left half of the report's Algorithm 3 for blocks. The *pending → confirmed/discarded* state machine and the `COMMENTED_OUT` edge at the far end are not implemented.

---

## 4. Limitations and defects found

Severity levels: **H** means the result is wrong or the history is broken, **M** means precision or recall suffers, **L** means quality or maintainability. Section 8 lists which scenario test confirms each item.

### 4.1 Limits of the reconnection heuristic after `UNCOMMENTED_BLOCK` (H, **confirmed** by S1, S7; D missing in S2, S6)

`virtualBlockBefore` is built from `container2`, the **current** operation, so its identifier contains the **current method's body hash** (Section 1.1). This is **deliberate**: it is the prototype's reconnection heuristic. In the next iteration of `BlockTrackerImpl`:

- `currentMethod = Method.of(currentBlock.getOperation(), …)` uses F's operation.
- `getMethod(rightModel, …, currentMethod::equalIdentifierIgnoringVersion)` is evaluated at each older commit. While the block is still a comment, the method body differs, so the result is `null` and the loop calls `continue` (`:81-84`).
- The loop therefore skips commits until it reaches one whose method body equals F's. When nothing else in the method changed during the commented period, that commit is C, the block's last active version, and tracking continues from there down to the true origin. **This works as intended in S2 and S6.**

The heuristic has two limits:

- **The comment-out commit is never recorded.** D's method body differs from F's (the block is a comment there), so D is skipped like E. The history is continuous, but no `COMMENTED_OUT` change is recorded and the D/E period is not visible. (S2, S6; see 4.6.)
- **It needs a byte-identical method body.** If any other line of the method changes during the commented period or in the restoring commit, no older commit matches. The queue empties **with no "introduced" node**, which is worse than the original "introduced at F". (Confirmed by S1. S7, a block first written as a comment, ends the same way.) Real restorations do this often; for example, javaparser `fc0e703` restored a block while rewriting much of `isApplicable(MethodUsage)`.

Every skipped commit also pays for model loading, so long histories become slow.

The prototype's case study used a method that was otherwise unchanged during the commented period, the case the heuristic was designed for, so these limits did not show up there.

`isAdded` builds its node the same way, but it calls `handleAdd`, so the queue stops there. The uncommented path does not stop.

### 4.2 Exact-hash matching is brittle (M, recall; **confirmed** by S4, S5)

- The comparison uses whitespace collapsing plus SHA-512. Formatting differences still break it: `if(x){` vs `if (x) {`, `a+b` vs `a + b`, a trailing comment inside the commented code, or a different line break inside an argument list.
- A leaf is compared using `AbstractCodeFragment.getString()`, which is RefactoringMiner's *normalized* AST printout, against **raw** comment text. These two forms often differ even for identical code: RM drops redundant parentheses, normalizes spacing, and puts `;\n` at the end.
- Statements that span several `//` lines (builder chains, long calls) are reconstructed only for **block signatures**, not for leaves. As a result, an uncommented multi-line leaf is never detected.
- Code edited during uncommenting (for example, a changed condition) is never detected. That case is common in practice.

### 4.3 Per-line hashing causes false positives; `BODY_CHANGE` is dropped (M, precision; the dropping part is **confirmed** by S10)

- Every comment line is hashed on its own, so trivial lines such as `return;`, `break;`, `i++;`, `continue;`, `return null;`, or `super.foo();` match unrelated new statements whenever such a line was deleted from a comment in the same block.
- A **single** matching line makes the whole body change count as "uncommented", even if many other real edits happened (`return true` on the first hit). In addition, `BODY_CHANGE` is **dropped**, so those real edits disappear from the history.

### 4.4 Keyword detection uses `startsWith` with no word boundary (M, a real bug; not covered by a scenario yet)

`hasBlockKeyword` matches the prefixes `"if"`, `"do"`, `"for"`, `"try"`, `"else"`, `"static"`, and so on. As a result:

- `double x = 1;`, `format(…)`, `forEach(…)`, `doSomething();`, `tryLock();`, `ifPresent(…)`, `elseValue = …`, and `statics.add(…)` are all treated as block-keyword lines.
- `isLeafStatement` returns `false` for them, and `extractFromSingleLineComments` may join them with the following comment lines into a fake signature.

The fix is to use `^(if|else|for|while|do|try|catch|finally|switch|synchronized|static)\b`, or better, to use a tokenizer (Section 5). A unit test on `extractBlockSignaturesFromComments` is the easiest way to cover this.

### 4.5 Scope filter and nested blocks (M)

- `removeOutOfScopeComments` uses strict line containment of the outer block. Comments inside **nested** child blocks are therefore attributed to the parent as well, so both the child and the parent get `UNCOMMENTED_STATEMENT`. This may be acceptable, because CodeTracker also propagates body changes upward, but the choice should be made on purpose and tested.
- A comment that shares a line with the opening `{` or the closing `}` is excluded.
- Sibling blocks are handled correctly (S9 passes).

### 4.6 Coverage gaps (M)

- Detection only runs inside `checkBodyOfMatchedOperations`. That means steps 3 and 4 with a body mapper of the **same** method. It does not run when the uncommenting happens together with a method rename or move, or inside an extract/inline path (`checkForExtractionOrInline` and `isBlockRefactored` call `isMatched` but never reach `isBlockUncommented`).
- Only **blocks** are handled. Algorithm 3 in the report targets **methods** (restoring an entire commented-out method). For methods, the comments sit at **class level** (`UMLClass.getComments()` or the file's comments), not in the operation's `UMLCommentListDiff`, so a different source of comments is needed. Variables and attributes are not handled either.
- Feature B only considers `getNonMappedInnerNodesT2`. If RefactoringMiner maps the uncommented composite to *another* composite (the same mis-mapping that commit `7e3fd60` handles for leaves), the case is missed.
- `else`, `catch`, and `finally` handling is a TODO (`partialBlockUncommented`, which is dead code). Standalone `{}` blocks are also a TODO.
- There is no "commented out" counterpart for **whole blocks**. The D side of S2 and S6 can never be reported.

### 4.7 History model issues (M)

- The pending → confirm/discard logic from the report is missing. There is no `COMMENTED_OUT` edge at the start of the commented period, and the node stays "virtual" without any flag (`// add metadata later, flag isVirtual = true;`).
- Several transition types can be emitted for one edge (a loop over `commentTransitions`). Check how `ChangeHistory`, the REST output (`RESTChange`), and the oracle comparison (`experiment/oracle/history`) handle the new types. They are not registered in the oracle/JSON change vocabulary yet, so accuracy experiments will count them as mismatches.

### 4.8 Code quality and build (L; would block an upstream PR)

- **`pom.xml` pinned `rm.version` 3.1.4, but the code merged from `master` needs 3.1.6** (`ChangeAttributeTypeRefactoring.getAttributeBefore/After`). The branch did not compile as committed. This is fixed in the working tree (Section 8).
- Wildcard imports replace explicit ones (`java.util.*`, `gr.uom.java.xmi.*`, `gr.uom.java.xmi.diff.*`). Upstream uses explicit imports.
- The code has dead code (`partialBlockUncommented`), unused parameters (`currentVersion` and `parentVersion` in `detectCommentTransitionsInsideMatchedBlock`), inconsistent indentation, and a typo (`generateUnCommentedBlockSignaturetHashMap`). The label `"uncommented Block"` is inconsistent with other labels, which are all lowercase.
- `String.isBlank()` is a Java 11 API, while `pom.xml` sets `maven.compiler.source/target` to 1.8. This works only when built with JDK 11+, which the README requires anyway.
- No oracle entries were added for the new change types. Scenario tests now exist (Section 8).

---

## 5. Implementation options

| Option | Idea | Pros | Cons |
|---|---|---|---|
| **A. Patch the current hash approach** | Keep the SHA-512 lookups and fix 4.1, 4.4, 4.3, and 4.8. | Smallest diff, fast. | Still brittle to formatting and edits (4.2), and still per-line. |
| **B. Token-based fuzzy matching** | Tokenize the comment text and the statement source with the JDT `Scanner`, ignoring whitespace and comments, then compare token sequences (exact, or similarity ≥ τ via LCS or Dice). | Ignores formatting, can report "uncommented with modification", cheap. | Still a hand-written matcher, and τ needs tuning. |
| **C. "Virtual uncomment" + RefactoringMiner re-mapping** (**recommended**) | When the normal path would say *added* (or *body change*), build a **virtual parent**: in the parent file, strip comment markers from comment runs **that parse as Java statements**, keeping line numbers, then re-parse and re-run `UMLOperationBodyMapper(virtualLeft, right)`. Mappings whose fragment 1 lies in a virtually uncommented range are uncomment transitions. Mirror this for commenting out (virtual right). | Reuses RM's statement matching, replacements, and nested structure. Handles formatting, edits during uncommenting, multi-line statements, nested blocks, and `else/catch/finally` for free. Line numbers stay valid. | Needs a second parse and mapping, so it must be **gated** to keep the cost down (Section 6, phase 3). |
| **D. Upstream support in RefactoringMiner** | Teach RM's `UMLCommentListDiff` and mapper to recognize commented-out code and map it ("Comment Out Code" and "Uncomment Code" as pseudo-refactorings). | Benefits all trackers and RM users. | Outside this repo, and needs the maintainers' agreement. A long-term direction. |

**Recommendation:** Use **C** as the core, with **B** as a cheap pre-filter: only build a virtual model when some deleted or added comment run parses as code and token-overlaps the candidate element. Keep **A**'s quick fixes in the meantime, so the branch is usable while C is being built.

### 5.1 Making continuity work: the "commented state" tracking mode

This mode fixes 4.1 and implements the report's pending/confirm logic using the existing loop:

1. At commit F (restored), option C produces `blockBefore` from the **virtual-uncommented parent model** (version E). Its identifier is computed against the *virtual* E method, so it is consistent with what E "would be" if uncommented. Mark the node `commented = true` (add a field on `BaseCodeElement` or `Block`) and record `UNCOMMENTED` on edge E→F.
2. When the queue polls a `commented` node, `BlockTrackerImpl` uses a **virtual model provider**: for each older commit, it loads the model with comment runs virtually uncommented. Steps 2–4 then run unchanged:
   - **No change** (E vs D, both commented, nothing else changed) is skipped as usual.
   - **Body change while commented** is recorded as a normal change on the virtual element. This is optional; it could also be filtered out.
3. **Confirmation:** at commit D, virtual(D) contains the block, but the **real** parent C contains it as active code. Diff virtual(D) against real(C). If the block is matched, record `COMMENTED_OUT` on edge C→D, clear the flag, and continue in normal mode with C's real block. This is the report's "pending becomes confirmed".
4. **Discard:** if the element disappears from the virtual model as well (it was first written as a comment), call `handleAdd(…, "introduced as commented-out code")`, or fall back to plain "introduced at F" with a configuration switch. This is the report's "pending is discarded", and the history always ends with an origin node.

The same mode works for **methods** (report Algorithm 3). For methods, the virtual uncommenting is applied to class-level comment runs in the file, and the trigger is the method tracker's "added" classification.

### 5.2 Recognizing "commented-out code" robustly

- Group consecutive `//` comments, and treat each `/* … */` (not Javadoc `/** */`) as one run. Strip the markers, plus a leading `*` on each line inside block comments.
- Parse the run with JDT `ASTParser` (`K_STATEMENTS` for method bodies, `K_CLASS_BODY_DECLARATIONS` for class level). Accept the run if it parses with no problems, or if only a trailing statement is incomplete (for example, a run that covers only `if (x) {`). For such signature-only runs, allow `K_STATEMENTS` after appending a matching `}`.
- Reject prose: require at least one statement, and require that most of the tokens are consumed by the parse. This removes the need for keyword lists (4.4).

---

## 6. Plan

### Phase 0: Ground truth first (before changing logic). **Done**, see Section 8.
1. Fixture repositories built in-test with JGit for the report's Fig. 9 scenario and its variants. *(done: 10 scenarios)*
2. Expected histories as assertions. Oracle JSON entries under `src/main/resources/oracle/block/…` are still to do once the change-type names are final.
3. Baseline on the branch and on the branch's merge-base with `master`. *(done)* The full `BlockOracleTest` regression run on real projects is still to do (slow, needs the network).

### Phase 1: Quick fixes on the current branch (option A)
1. Fix the continuity problem (4.1), at least minimally: if no earlier active version can be found, end the history with `handleAdd`. Do not leave it without an origin. *(target: S1 and S7 origin checks pass)*
2. Add word boundaries to the keyword detection (4.4), with a unit test.
3. Stop dropping `BODY_CHANGE`. Emit the transition type **in addition**, or only when *all* body differences are explained by comment transitions (4.3). *(target: S10 passes)*
4. Ignore trivial comment lines (fewer than N tokens, or a stop-list such as `return;` and `break;`) unless they are part of a larger matched run (4.3).
5. Normalize both sides the same way. Compare RM's `getString()` against the comment text after it is parsed and printed by the same AST stringifier, or at least compare token sequences (4.2). *(target: S4 passes)*
6. Clean up: explicit imports, remove dead code, fix names and labels (4.8).

### Phase 2: Commented-state tracking mode (Section 5.1)
1. Add `commented` / `virtual` metadata to tracked elements and expose it in `History` and REST.
2. Add a virtual model provider to `BaseTracker` (reuse the existing model and file-content caches) and a mode switch in `BlockTrackerImpl` (both `track()` and `blame()` copies).
3. Add `COMMENTED_OUT` / `UNCOMMENTED` edges with the confirm/discard semantics, then register the new types in the oracle `ChangeHistory` vocabulary and `RESTChange`. *(target: S1, S2, S6 pass)*

### Phase 3: Virtual uncomment + RM re-mapping (option C)
1. Write a `CommentedCodeDetector`: comment runs → JDT parse → line-preserving "uncommented" file text.
2. Gate it: only run when the normal path returns *added* or *body change*, the method's `UMLCommentListDiff` has deleted or added comment runs that parse as code, and a token-overlap check (option B) passes.
3. Build the virtual `UMLModel` from in-memory contents, build the body mapper, and classify the mappings that touch the virtual ranges (exact = uncommented, with replacements = uncommented and modified). *(target: S4, S5 pass)*
4. Replace `CommentTransitionAnalyzer`'s hash logic, keeping the old analyzer behind a flag for A/B comparison on the oracle.

### Phase 4: Extend to the other trackers
1. **Method** restoration (report Algorithm 3): use class-level comment runs, triggered in `MethodTrackerImpl` when a method is classified as added.
2. Variables and attributes: run the same detector over the enclosing method body or class body.
3. Detect uncommenting combined with refactorings (rename, move, extract), using the virtual model in steps 4–5 as well.

### Phase 5: Evaluation and upstreaming
1. Measure on the oracle (`experiments/tracking-accuracy/...`): precision and recall of the new change types, the impact on existing histories (there should be none for unrelated elements), and runtime overhead (gated vs. ungated).
2. Mine real cases. Search histories of the oracle projects (checkstyle, jgit, pmd, …) for commits that delete comment runs which parse as code, to measure how often this happens and to collect natural test cases.
3. Prepare small, focused PRs to `jodavimehran/code-tracker`: the tests, the detector, the mode switch, and the trackers, each separately. Discuss option D (RM-level support) with the maintainers.

---

## 7. Open questions for the author and maintainers

1. Should a body change *while code is commented out* be part of the element's history, or hidden?
2. When code is first written as a comment and later uncommented (S7), should the origin be "introduced (as comment)" at the earlier commit, or "introduced" at the uncomment commit?
3. Should these be first-class change types (`COMMENTED_OUT` / `UNCOMMENTED`) or flags on `BODY_CHANGE`? This affects oracle compatibility and the accuracy numbers reported in the papers.
4. What runtime overhead is acceptable? This decides how aggressive the gating in phase 3 has to be.

---

## 8. Phase results

Per-phase reports live next to this file: [phase-0.md](phase-0.md), [phase-1.md](phase-1.md), …. Design decisions are recorded as ADRs in [../adr/](../adr/).
