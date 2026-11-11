# 0013. A real-project oracle for comment transitions

- Status: Proposed
- Date: 2026-11-12
- Phase: 6
- Related: [0007](0007-oracle-regression-runs-once-at-the-end.md) (oracle regression), [0005](0005-comment-transitions-complement-body-change.md) (statement-level types), [0008](0008-commented-state-tracking-by-direct-search.md) to [0012](0012-commented-out-variables.md) (detection)

## Context

The scenario tests (Phases 0–4) are synthetic repositories written for this work. The published CodeTracker evaluations (FSE 2022 for methods and variables, TSE 2024 for blocks) use oracles instead: real histories from real projects, each change hand-validated. The published oracles contain no comment transitions, so they can only show that the new code does not break old results. They cannot measure the new feature.

Three practical constraints shape an oracle for it:

1. The oracle tests run on the `*WithLocalFiles` trackers, which read `oracle/commits` (the `EmpiricalSEConcordia/oracle` submodule). That cache was filled through the GitHub API, which needs a token and is rate-limited.
2. `OracleTest` keeps expected TP/FP/FN in static maps keyed by history **file name**, shared by all oracle test classes in a JVM.
3. Commit-log files (`src/test/resources/<kind>/<repo>-<Class>-<method>.json`) are named by class and method, so a new history can share one with a published history.

## Decision

1. **A separate oracle set** named `comment-transition` for each element kind (`oracle/<kind>/comment-transition/`, expected values in `src/test/resources/<kind>/comment-transition-expected.txt`), loaded by new opt-in test classes (`-Dct.oracle=true`). `training` and `test` stay exactly as published, and `*Oracle.all()` does not include the new set.
2. **File names end in `-ct`**, so they never collide with published names in the shared static maps.
3. **The cache is built from a local clone** by `LocalOracleBuilder` (test scope). It writes the same JSON as `populateWithGitHubAPIAndSaveFiles`, except that file contents keep the repository's own line endings. This was checked against existing javaparser entries: all ten metadata fields equal, and file contents equal modulo CRLF.
4. **Shared commit-log files are only extended**, never changed: existing keys keep their lists. Every published history that uses a changed log file was re-scored and gives its published TP/FP/FN.
5. **Ground truth comes from git, not from the tool.** The tool's output is only the starting point. Each reported change is checked against the element text before and after, and every other commit that touches the element's lines is inspected (as the papers combined several tools' outputs and validated the union by hand). The reasons for every correction are kept in `docs/comment-transition/oracle/verdicts/`.
6. **Labelling conventions** for cases the change types do not settle on their own:
   - An element first written as a comment is `introduced` at the commit where it becomes code, together with the `uncommented …` change (scenario M4).
   - When the commit that comments an element out also edits the commented copy, the edits are labelled at that commit (`expression change`, `body change`) next to `commented out …`, because that is where they happened.
   - `commented out statement` / `uncommented statement` apply to any statement inside a block, at any depth, as `CommentTransitionAnalyzer` defines them.
   - Whitespace-only edits are not changes.

## Consequences

- The new feature is measured on real code, with the same TP/FP/FN machinery as the papers.
- Whoever runs these tests needs the cache entries. They are not in the submodule, but `LocalOracleBuilder cache` rebuilds them from a clone and the committed commit-log files, with no network.
- The labels that depend on convention 6 are marked in their verdict files. If the author decides otherwise, only those histories change.
- Adding the cache entries upstream would mean a pull request to `EmpiricalSEConcordia/oracle`.
