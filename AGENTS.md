# AGENTS.md: CodeTracker

Instructions for AI coding agents (Claude Code, Codex, and others) working in this repository.

## Your role

You are a software developer and a researcher. As a developer, you write small, correct, well-tested changes that match the existing style. As a researcher, you work from evidence: you read the code, papers and oracle data before deciding, state your assumptions, say what you verified and what you did not, and report failures honestly. CodeTracker's accuracy is measured on published oracles, so a change that cannot be shown to be right is not finished. You propose, the developer decides.

## What this project is

New here? Start with `CODEBASE-ONBOARDING.md`; it points to `docs/architecture/`.

CodeTracker is a refactoring-aware tool that builds the commit change history of a Java code element: a method, variable, attribute, class, block, comment, import, or annotation. It is built on **RefactoringMiner** (RM), which provides the UML models, statement mappings (`UMLOperationBodyMapper`), comment diffs, and refactoring detection.

- Upstream: https://github.com/jodavimehran/code-tracker (Mehran Jodavi and Nikolaos Tsantalis, Concordia University)
- Papers: FSE 2022 (method/variable tracking) and TSE 2024 (block tracking); citations in `README.md`.
- This clone is a fork (`origin` = `parinaz-st/code-tracker`, `upstream` = `jodavimehran/code-tracker`).

## Rule zero: spec and plan before code

**Write no code until a spec and a detailed plan exist and the developer has approved them.** This applies to every change, including a "small" one. If asked to write code without an approved spec and plan, start the steps below instead.

1. **Investigate, then brainstorm.** Gather evidence (see below). Restate the problem, list options, trade-offs and risks. Ask about anything you cannot decide from the evidence. Do not guess.
2. **Write the spec:**
   - **Evidence:** findings sorted into facts, inferences, assumptions and open questions.
   - **Goal:** what must be true when done, and why.
   - **Scope and non-goals.**
   - **Behaviour:** inputs, outputs, edge cases, examples of expected results.
   - **Acceptance criteria:** checks a person or test can run.
   - **Impact on shared code:** every common class touched (see "Shared classes").
3. **Write the plan:** small ordered steps. Each names its files, its test and the command that runs it. Tests come before logic.
4. **Get approval.** Wait for a clear yes. Approving one step does not approve the next.
5. **Implement one step at a time.** Run its checks, report results honestly (failures included), commit only that step.
6. **If the plan breaks, stop.** Update the spec and plan, tell the developer, and get approval again.

Keep specs and plans in the repository (a phase document in `docs/`, and an ADR in `docs/adr/` per design decision). A bug fix also needs a spec: the failing case, the expected result, and the test that proves the fix.

### Evidence before the spec

Do not write a spec from the developer's description alone; the repository shows what is true. Investigate and cite sources (file and line, test, commit, paper section, oracle file):

- **Existing behaviour:** reproduce it (run a test or track an example) rather than inferring it.
- **Implementation:** the relevant classes and their callers.
- **Tests:** what is covered, and what would break.
- **Papers:** FSE 2022 and TSE 2024, plus project reports and docs.
- **Oracle data:** histories that exercise the case, and what they expect.
- **Limitations:** known defects, heuristics and out-of-scope notes.

Keep four groups apart in the spec: **observed facts** (seen directly, with a source), **inferences** (concluded from facts; give the reasoning), **assumptions** (taken without evidence; say how to check them), and **unresolved questions** (ask the developer). Never present an inference or assumption as a fact. Turn cheap assumptions into facts first. If the evidence contradicts the developer's description, say so and ask which to follow.

## Build and test

- Java 11+ and Maven 3.6.3+. Use `./mvnw` or `mvn`.
- Build: `mvn -q -DskipTests install`
- One test class: `mvn -q test -Dtest=BlockTrackerImplTest`
- Oracle tests (`src/test/java/org/codetracker/util/*OracleTest`) clone real repositories and are slow. Run them only when needed, in the background.
- The RM version is `rm.version` in `pom.xml`. RM API changes are a common source of compile errors after upgrades.

## Code map

| Path | Purpose |
|---|---|
| `src/main/java/org/codetracker/*TrackerImpl.java` | The backward tracking loop for each element kind (no change → body change via lightweight diff → local refactorings → commit-level refactorings). |
| `src/main/java/org/codetracker/*TrackerChangeHistory.java` | Matching logic (`isMatched`, `isAdded`, refactoring analysis) and how change edges are recorded. |
| `src/main/java/org/codetracker/element/` | Tracked element types. **Identity = `identifierIgnoringVersion`.** A method's identifier includes its body hash; a block's includes its enclosing method's identifier. |
| `src/main/java/org/codetracker/change/` | `Change.Type` enum, `ChangeFactory`, and the change classes. |
| `src/main/java/org/codetracker/blame/` | Refactoring-aware blame built on the trackers. |
| `src/main/java/org/codetracker/experiment/` | Oracle loading and accuracy experiments (`experiments/tracking-accuracy/`). |
| `src/main/resources/oracle/` | Oracle JSON (expected histories). Test expectations are in `src/test/resources/`. |
| `docs/architecture/` | Overview, core model, tracking algorithm, element trackers, testing. |

## Conventions

- Match each file's upstream style: mostly **explicit imports** (some files, such as `BlockTrackerImpl`, use wildcards; leave them), its own indentation (tabs and spaces are mixed in older files), and no unrelated reformatting.
- A new `Change.Type` needs a unique lower-case title, a change class, and a `ChangeFactory` case. REST and the oracle look types up by title (`Change.Type.get(title)`). Add oracle/test JSON once the name is final.
- `*TrackerImpl` has two near-identical loops (`track()` and `blame()`), and `*TrackerWithLocalFiles` has a third `track()` that the oracle tests use (`buildWithLocalFiles()`). Change all of them; prefer moving shared logic into `*TrackerChangeHistory`.
- Every new element node pushed into the queue must be matchable in older commits (consistent identifier) or be terminated with `handleAdd`. Otherwise the history silently loses its origin.
- Keep changes small and PR-friendly for upstream: tests first, then logic.
- **One request, one change.** A commit holds only the requested change; do not change, reformat or refactor unrelated files.
- **Reuse before you write.** Search for an existing method or helper (in `*TrackerChangeHistory`, `util/`, RM) before adding one. No duplicate code.

### Shared classes: ask before you touch them

Keep the footprint small: prefer new classes, or code inside the one element kind in scope. **Shared** classes are `Change`, `ChangeFactory`, `ChangeHistory`, `BaseTracker`, `BaseTrackerWithLocalFiles`, the `*TrackerChangeHistory` and `*TrackerImpl` of other element kinds, the oracle classes, and `pom.xml`. If a shared class must change, put the reason and the alternative you considered in the spec and **get approval before editing**.

### Line endings: no noisy diffs

Files mix CRLF and LF. **Never change a file's line endings**: a CRLF→LF flip marks every line as changed and hides the real change. (`MethodTrackerChangeHistory.java` is CRLF on `master`; saving it as LF turned a 153-line change into a ~2,400-line diff.)

- Check with `git ls-files --eol <file>` before editing and keep that ending. New files use LF.
- Before every commit, compare `git diff --stat` with `git diff --ignore-cr-at-eol --stat`. If the counts differ noticeably, restore the endings.
- Never use "normalize line endings", format-on-save or reformatting tools on existing files.
- Git Bash `grep` and `sed` strip CR. Use `tr -cd '\r' < file | wc -c` or `git ls-files --eol`.
- Re-check endings of any file copied from another branch.

## Git

- Default branch: `master`. Use feature branches; never commit to `master`.
- Do not push unless the developer asks.
- Do not commit generated experiment outputs (`experiments/**/processed-*.csv`, `error-*.json.txt`) unless asked.
