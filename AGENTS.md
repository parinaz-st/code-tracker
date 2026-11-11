# AGENTS.md: CodeTracker

Instructions for AI coding agents (Claude Code, Codex, and others) working in this repository.

## What this project is

CodeTracker is a refactoring-aware tool that builds the commit change history of a Java code element: a method, variable, attribute, class, block, comment, import, or annotation. It is built on top of **RefactoringMiner**, which provides the UML models, statement mappings (`UMLOperationBodyMapper`), comment diffs, and refactoring detection.

- Upstream: https://github.com/jodavimehran/code-tracker (Mehran Jodavi and Nikolaos Tsantalis, Concordia University)
- Papers: FSE 2022 (method/variable tracking) and TSE 2024 (block tracking). The citations are in `README.md`.
- This clone is a fork (`origin` = `parinaz-st/code-tracker`, `upstream` = `jodavimehran/code-tracker`).

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
| `docs/` | Design notes. See `docs/comment-transition/` for the comment-out/uncomment work. |

## Conventions

- Match the upstream style: **explicit imports, no wildcards**, existing indentation (tabs and spaces are mixed in older files; keep each file consistent with itself), and no unrelated reformatting.
- When adding a new `Change.Type`, also update `ChangeFactory`, the REST mapping (`rest/changeHistory`), and the oracle vocabulary (`experiment/oracle/history`). Add oracle/test JSON for it as well.
- `*TrackerImpl` often has two near-identical loops (`track()` and `blame()`). Change both.
- Every new element node pushed back into the queue must either be matchable in older commits (consistent identifier) or be terminated with `handleAdd`. Otherwise the history silently loses its origin.
- Keep changes small and PR-friendly for upstream: tests first, then logic.

## Ongoing work in this fork

- `feature/CmtTransitionDetectionBlock`: detects code that was commented out or uncommented in block histories. Its analysis, known defects, and plan are in `docs/comment-transition/`.
- Other report items (dependency regex for Extract Class, multi-variable declaration splits, SHA-512 body hash) are described in the author's report *Enhancing CodeTracker Accuracy* (April 2026).

## Git

- Default branch: `master`. Work on feature branches, and do not commit to `master` directly.
- Do not commit generated experiment outputs (`experiments/**/processed-*.csv`, `error-*.json.txt`) unless asked.
