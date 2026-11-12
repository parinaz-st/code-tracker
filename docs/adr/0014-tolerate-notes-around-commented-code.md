# 0014. Tolerate notes around commented-out code

- Status: Proposed
- Date: 2026-11-12
- Phase: 7
- Related: [0009](0009-virtual-uncomment-with-refactoringminer-mapping.md) (the uncommenter), [0013](0013-real-project-comment-transition-oracle.md) (the real-project oracle that found the problem)

## Context

`CommentedCodeUncommenter` turns commented-out code back into code, keeping all offsets. It groups consecutive `//` lines into one run, and uncomments a run only if the *whole run* parses as Java of the expected kind (statements, or class members). This keeps prose comments untouched. Every detection path goes through it: uncommented blocks, methods, attributes and variables, and the commented-state search of all four.

The real-project oracle (ADR 0013) showed that developers rarely comment code out without a note. In 8 of the 14 elasticsearch histories the restoration was missed for this reason alone:

- **A prose line above or below the code**, in the same run of `//` lines:
  - `// source is always enabled for now`
  - `// this test requires tests to run with -Djava.locale.providers=COMPAT in order to work`
  - `// Mute this assertion until we have a new Lucene snapshot with …`
  - `// do this once LUCENE-7685 is addressed`
  - `// NORELEASE These need to be tested in TemplateQueryBuilderTests`
- **A marker after the code on the same line**: `//    } NOCOMMIT fix this`, and `//    } NORELEASE can this still be tested? if so, how?`

Each one makes the run unparseable, so the element was reported as `introduced` and its whole earlier history was lost. The synthetic scenarios never contained such notes.

## Decision

When a run of `//` lines does not parse and contains at least one code-like line, retry before giving up:

1. **Code-like line:** after the comment marker, the line ends with `;`, `{` or `}`, or ends with such a token followed by a marker (rule 2).
2. **Marker cut:** text after the last `;`, `{` or `}` of a line is blanked if it is a note. A note starts with a word and has no code punctuation (`;{}()=[]` or quotes). It is never cut when the word continues code (`else`, `catch`, `finally`, `while`, `return`, `throw`, `new`, `instanceof`, `case`, `default`).
3. **Trimming:** up to 3 lines may be dropped at each end of the run, but only lines that are not code-like. A run is therefore never cut into a parseable fragment. For example, `return x;` taken out of a commented method is not uncommented as a statement.
4. **Order of attempts:** fewest lines dropped first, each without and then with the marker cut. The first variant that parses cleanly wins.
5. Trimmed lines stay comments, and blanked markers are replaced by spaces. Offsets and line numbers stay valid.

### Two related fixes found while measuring this decision

Once these restorations were detected, two more weaknesses showed up on the same real histories. They are part of this decision because they only become reachable through it.

6. **Deleted comments are aligned, not counted.** `deletedComments` compared the comment texts of parent and child as a multiset. When two commented methods end with the same line (`//    } NOCOMMIT fix this` in elasticsearch `SearchFieldsIT`), the line that remains was credited to the *first* occurrence, so the restored method lost its closing line. It is now a longest-common-subsequence alignment in file order, like a diff.
7. **Whole runs are passed to the uncommenter** (`deletedCommentRuns`, used by the method and attribute detection). A restoring commit can move part of the commented text into a new comment. In elasticsearch `JavaJodaTimeDuellingTests`, the extra assertions became a new commented test. The deleted lines alone then do not form a parseable run, so every comment of a run that lost at least one line is passed on. Over-inclusion is harmless, because the restored element is still found by its own signature.
8. **The commented-state search no longer accepts any block with the same signature.** `findActiveBlock` used to fall back to the first block of the same kind and signature. Generic signatures such as `try` or `else` then matched unrelated blocks. In elasticsearch `InternalTestCluster`, the search followed a different `try` block back to 2013. A signature-only match must now share at least half of the target's leaf statements, and the best match wins.

## Consequences

- Restorations with a note above or a marker after the code are detected. The effect on the real-project oracle is reported in `docs/comment-transition/phase-7.md`.
- A run is still accepted only if the result parses without errors, so prose-only comments stay comments (unit tests `proseOnlyRunsStayComments`, `keywordAfterBraceIsNotAMarker`).
- **Cost:** at most 2·(4·4) − 1 = 31 extra parses for a failing run, and only for runs with a code-like line. Prose runs without `;{}` endings are not retried.
- **Remaining limits:**
  - notes in the *middle* of commented code are not handled;
  - notes longer than 3 lines are not trimmed;
  - `/* … */` comments are not trimmed (one block comment is one run);
  - a code-like note (one ending in `;`, `{` or `}`) cannot be trimmed.
- Making it possible to reach older versions exposed a missing `commits == null` guard in the offline block, method and variable trackers. The attribute tracker already had it. The guard was added; the git-based trackers are not affected.
