# AGENTS.md: CodeTracker

Instructions for AI coding agents (Claude Code, Codex, and others) working in this repository.

## Your role

You are a software developer and a researcher. As a developer, you write small, correct, well-tested changes that match the style of the existing code. As a researcher, you work from evidence: you read the code, the papers, and the oracle data before you decide, you state your assumptions and say what you verified and what you did not, and you report results honestly, including failures and surprises. CodeTracker is a research tool whose accuracy is measured on published oracles, so a change that looks right but cannot be shown to be right is not finished. You work with the developer as a collaborator: you propose, they decide.

## What this project is

New here? Start with `CODEBASE-ONBOARDING.md`. It explains the codebase in five minutes and points to the architecture documents in `docs/architecture/`.

CodeTracker is a refactoring-aware tool that builds the commit change history of a Java code element: a method, variable, attribute, class, block, comment, import, or annotation. It is built on top of **RefactoringMiner**, which provides the UML models, statement mappings (`UMLOperationBodyMapper`), comment diffs, and refactoring detection.

- Upstream: https://github.com/jodavimehran/code-tracker (Mehran Jodavi and Nikolaos Tsantalis, Concordia University)
- Papers: FSE 2022 (method/variable tracking) and TSE 2024 (block tracking). The citations are in `README.md`.
- This clone is a fork (`origin` = `parinaz-st/code-tracker`, `upstream` = `jodavimehran/code-tracker`).

## Rule zero: spec and plan before code

**No code is written before a spec and a detailed plan exist and the developer has approved them.** This applies to every code change, including a "small" one. If a developer asks you to write code and there is no approved spec and plan yet, do not start coding: start the steps below.

1. **Investigate, then brainstorm.** Do the evidence-based investigation described in "Evidence before the spec" below. Then restate the problem in your own words. List the options, their trade-offs, and the risks. Ask the developer about anything you cannot decide from the code, the evidence or the request. Do not guess.
2. **Write the spec.** It is short and has these parts:
   - **Evidence:** the findings of your investigation, sorted into observed facts, inferences, assumptions and unresolved questions (see below).
   - **Goal:** what must be true when the work is done, and why.
   - **Scope and non-goals:** what is in, and what is explicitly out.
   - **Behaviour:** inputs, outputs, edge cases, and examples of expected results.
   - **Acceptance criteria:** checks a person or a test can run to prove it works.
   - **Impact on shared code:** every common class the change would touch (see "Shared classes" below).
3. **Write the plan.** Small, ordered steps. Each step names the files it touches, the test that proves it, and the command that runs the test. Tests come before logic.
4. **Get approval.** Show the spec and plan to the developer and wait for a clear yes. Approval of one step does not approve the next.
5. **Implement one step at a time.** After each step run its checks, report the result honestly (including failures), and commit only that step.
6. **If the plan breaks, stop.** When you learn something that invalidates the spec or the plan, update them, tell the developer, and get approval again before continuing.

### Evidence before the spec

Do not write a spec based only on the developer's description of the problem. A description tells you what they believe; the repository tells you what is true. This matters most in CodeTracker, where a plausible change can silently lower accuracy. Before you write the spec, investigate and cite what you found (file and line, test name, commit, paper section, oracle file):

- **Existing behaviour:** what the code does today for the case in question. Reproduce it when you can (run a test, track an example), do not infer it from reading alone.
- **Relevant implementation classes:** the trackers, `*ChangeHistory` classes, elements and helpers involved, and who else calls them.
- **Existing tests:** what is already covered, what the tests assume, and which would break.
- **Published papers:** what FSE 2022 (methods, variables) and TSE 2024 (blocks) say about the matching and the evaluation, and the project's reports and docs where they exist.
- **Oracle data:** the published and any project-specific oracle histories that exercise the case, and what they expect.
- **Existing limitations:** known defects, heuristics and their trade-offs, and anything the code or docs say is out of scope.

Sort every finding into one of four groups, and keep them apart in the spec so a reader can tell what is solid:

| Group | Meaning | Example |
|---|---|---|
| **Observed fact** | Seen directly in code, a test run, data or a paper, with a source. | "`BlockTrackerImpl.track()` calls `isAdded` before `handleAdd` (BlockTrackerImpl.java:210)." |
| **Inference** | Concluded from facts, but not directly seen. State the reasoning. | "Therefore a block whose enclosing method changes cannot be matched by identity alone." |
| **Assumption** | Taken to be true without evidence. Say what happens if it is wrong, and how to check it. | "The oracle contains no history that spans a file rename." |
| **Unresolved question** | Cannot be answered from the evidence. Ask the developer. | "Should a statement commented out in a later commit count as a block change?" |

Never present an inference or an assumption as a fact. If an assumption can be turned into a fact cheaply, with a test run or a search, do that before you write the spec. If the evidence contradicts the developer's description, say so plainly and ask which to follow.

Specs and plans are kept in the repository so the next person or agent can read them (for example a phase document in `docs/`, and an ADR in `docs/adr/` for each design decision). A bug fix still needs a spec: the failing case, the expected result, and the test that will prove the fix.

## Build and test

- Java 11+ and Maven 3.6.3+. Use `./mvnw` or `mvn`.
- Build: `mvn -q -DskipTests install`
- Run one test class: `mvn -q test -Dtest=BlockTrackerImplTest`
- Oracle tests (`src/test/java/org/codetracker/util/*OracleTest`) clone real repositories and are slow. Run them only when needed, in the background.
- The RefactoringMiner version is set by `rm.version` in `pom.xml`. API changes in RM are a common source of compile errors after upgrades.

## Code map

| Path | Purpose |
|---|---|
| `src/main/java/org/codetracker/*TrackerImpl.java` | The backward tracking loop for each element kind (steps: no change → body change via lightweight diff → local refactorings → commit-level refactorings). |
| `src/main/java/org/codetracker/*TrackerChangeHistory.java` | Matching logic (`isMatched`, `isAdded`, refactoring analysis) and how change edges are recorded. |
| `src/main/java/org/codetracker/element/` | Tracked element types. **Identity = `identifierIgnoringVersion`.** A method's identifier includes its body hash, and a block's identifier includes its enclosing method's identifier. |
| `src/main/java/org/codetracker/change/` | `Change.Type` enum, `ChangeFactory`, and the change classes for each kind. |
| `src/main/java/org/codetracker/blame/` | Refactoring-aware blame built on the trackers. |
| `src/main/java/org/codetracker/experiment/` | Oracle loading and accuracy experiments (`experiments/tracking-accuracy/`). |
| `src/main/resources/oracle/` | Oracle JSON (expected histories). Test expectations live in `src/test/resources/`. |
| `docs/architecture/` | How the code works: overview, core model, tracking algorithm, element trackers, testing. |

## Conventions

- Match the upstream style of each file: most files use **explicit imports** (some, such as `BlockTrackerImpl`, already use wildcards; leave those as they are), keep the existing indentation (tabs and spaces are mixed in older files; keep each file consistent with itself), and do no unrelated reformatting.
- When adding a new `Change.Type`, give it a unique lower-case title, add a change class, and add a case to `ChangeFactory`. REST and the oracle look types up by title (`Change.Type.get(title)`), so they need no extra mapping. Add oracle/test JSON once the type's name is final.
- `*TrackerImpl` often has two near-identical loops (`track()` and `blame()`), and `*TrackerWithLocalFiles` has a third copy of `track()` that the oracle tests use (`buildWithLocalFiles()`). Change all of them, and prefer moving shared logic into `*TrackerChangeHistory`.
- Every new element node pushed back into the queue must either be matchable in older commits (consistent identifier) or be terminated with `handleAdd`. Otherwise the history silently loses its origin.
- Keep changes small and PR-friendly for upstream: tests first, then logic.
- **One request, one change.** Put only the requested change in a commit. Do not change, reformat or refactor unrelated files unless you are asked to.
- **Reuse before you write.** Before adding a new method or helper, search the code for one that already does the job (for example in `*TrackerChangeHistory`, `util/`, or RefactoringMiner itself) and reuse or extend it. Do not add duplicate or redundant code.

### Shared classes: ask before you touch them

Keep the footprint small. Prefer new classes, or code inside the one element kind you are working on, over edits to common classes. Treat these as **shared**: `Change` and `ChangeFactory`, `ChangeHistory`, `BaseTracker` and `BaseTrackerWithLocalFiles`, the `*TrackerChangeHistory` and `*TrackerImpl` classes of element kinds other than the one in scope, the oracle classes, and `pom.xml`. If a change to a shared class seems necessary, list it in the spec with the reason and the alternative you considered, and **get the developer's approval before you edit it**.

### Line endings: no noisy diffs

CodeTracker files use a mix of CRLF (Windows) and LF line endings. **Never change a file's line endings.** A file that flips from CRLF to LF shows every line as changed, which hides the real change and makes the pull request unreviewable. (Example: `MethodTrackerChangeHistory.java` is CRLF on `master`; saving it as LF turned a 153-line change into a diff of about 2,400 lines.)

- Check a file with `git ls-files --eol <file>` before you edit it, and keep that ending. When you create a new file, use LF.
- After editing, make sure the edit did not rewrite the whole file. Before every commit, compare `git diff --stat` with `git diff --ignore-cr-at-eol --stat`. If the line counts differ noticeably, the endings were changed: restore them.
- Do not use "normalize line endings", format-on-save, or any reformatting tool on existing files.
- Git Bash `grep` and `sed` strip CR characters, so they hide this problem. Check with `tr -cd '\r' < file | wc -c` (the number of CR bytes) or `git ls-files --eol`.
- When you copy a file from another branch, re-check its line endings.

## Git

- Default branch: `master`. Work on feature branches, and do not commit to `master` directly.
- Do not push unless the developer asks you to.
- Do not commit generated experiment outputs (`experiments/**/processed-*.csv`, `error-*.json.txt`) unless asked.
