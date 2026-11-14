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
2. **Write the spec.** The spec is a short document that says *what* will be built, not *how*. It answers these questions:
   - **Evidence:** What did you find out about the current code, tests, papers and oracle data? List it in the four groups from "Evidence before the spec" (facts, inferences, assumptions, open questions).
   - **Goal:** What problem are we solving, what will be different when it is solved, and why does it matter?
   - **Scope and non-goals:** What will this change cover? What will it deliberately *not* cover, even though it is related?
   - **Behaviour:** What should the code do? Give the input, the expected output, the edge cases, and at least one concrete example (for instance: "a block commented out in commit X is reported as `commented out block`").
   - **Acceptance criteria:** How will we know it is done? Each criterion is a check that a person or a test can run and that clearly passes or fails.
   - **Impact on shared code:** Which common classes (see "Shared classes") would have to change? If none, say "none".
3. **Write the plan.** The plan says *how* the spec will be built: a list of small steps in the order they will be done. A step should be small enough to finish, check and commit on its own. For each step, write down:
   - **What it does,** in one sentence.
   - **Which files it creates or changes.** Mark any shared class (see "Shared classes").
   - **Which test proves it works.** The test is written first and must fail before the code is written (see TDD).
   - **The exact command that runs that test,** for example `mvn -q test -Dtest=BlockTrackerImplTest`.

   Order the steps so that each one builds on the previous ones and the code compiles and the existing tests pass after every step.
4. **Get approval.** Wait for a clear yes. Approving one step does not approve the next.
5. **Implement one step at a time.** Run its checks, report results honestly (failures included), commit only that step.
6. **If the plan breaks, stop.** Update the spec and plan, tell the developer, and get approval again.

Keep specs and plans in the repository so the next person or agent can read them: one document per feature or fix in `docs/` (spec and plan together), and one ADR (architecture decision record) in `docs/adr/` for each design decision, saying what was decided and why. A bug fix also needs a spec: the failing case, the expected result, and the test that proves the fix.

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

## Test-Driven Development (TDD)

TDD is the default for all behavioural code changes. Follow Red-Green-Refactor:

1. **RED:** write a test that expresses the behaviour in the approved spec.
2. **Run it and confirm it fails for the intended reason.** A compilation error or broken test setup is not a meaningful failure.
3. **GREEN:** make the smallest production-code change that passes it.
4. **REFACTOR:** improve the code without changing its observable behaviour, then run the relevant tests again.

Rules:

- Derive tests from the approved spec and acceptance criteria, not from the existing code. Do not change production code first and then write tests that confirm what it already does.
- Do not weaken assertions, delete failing tests or change expected results just to get a green build. When a test fails, find the cause before changing the code or the test.
- Existing tests stay green unless a behaviour change was specified and approved.
- Use focused unit tests for isolated logic, integration tests for component interactions, and oracle tests for historical tracking accuracy.
- Do not claim TDD was followed unless the failing test was actually run before the production change.
- If TDD is impractical for a task, explain why and get approval for another way to verify it.
- A passing suite is necessary but does not by itself prove correctness.

## Research integrity and oracle protection

CodeTracker is a research artifact. Its papers, tracking algorithms, oracle data and experiment results are the scientific basis of the project, not just test resources. Passing conventional tests shows that the code works as written; it does not show that the research results are still valid. Keep the two apart.

- **Read the paper first.** Before changing an algorithm or behaviour that a published paper describes, read the relevant part of that paper.
- **Do not "fix" what you do not understand.** An implementation detail is not wrong just because another implementation looks cleaner. It may exist because of a case the evaluation covers.
- **Protect oracle and experiment data.** Do not change oracle data, expected histories or experimental datasets to make a new implementation pass. This needs the developer's explicit approval and a written justification.
- **Investigate every oracle failure.** Decide which of these four it is before you act: a regression in your change, an existing limitation of the tracker, an incorrect oracle entry, or an intentional behaviour change that was specified and approved.
- **Never hide inconvenient results.** Do not silently drop, skip or weaken an experiment because its result is bad. Report it.
- **Keep it reproducible.** Report the dataset, the test configuration, the commands you ran and the relevant results, so someone else can repeat them.
- **Separate measurements from opinions.** Say clearly what was measured, what is an assumption, and what is your interpretation.
- **No accuracy claim without evidence.** Do not claim an improvement unless you ran the relevant experiments and compared them with the established baseline.
- **State the research impact.** If a change alters the algorithm or its behaviour, explain what it could mean for the published research and the existing evaluation results.

**Example.** You change the method-matching logic and 8 historical cases in the oracle now fail. Do not edit the tests or loosen the matching conditions until the build is green. Ask instead:

1. What did the oracle expect for these histories?
2. Why do these 8 cases fail?
3. Did the tracker become less accurate overall?
4. Did the change reveal an existing defect, in the tracker or in the oracle?
5. Does the new behaviour affect any claim made in the papers?

Report the answers to the developer, then decide together what to do.

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
