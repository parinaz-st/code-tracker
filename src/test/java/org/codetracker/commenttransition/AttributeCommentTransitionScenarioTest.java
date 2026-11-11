package org.codetracker.commenttransition;

import static org.codetracker.commenttransition.ScenarioAssertions.COMMENTED_OUT;
import static org.codetracker.commenttransition.ScenarioAssertions.INTRODUCED;
import static org.codetracker.commenttransition.ScenarioAssertions.UNCOMMENTED;
import static org.codetracker.commenttransition.ScenarioAssertions.expected;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.codetracker.api.AttributeTracker;
import org.codetracker.api.CodeTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Specification tests for comment-out / uncomment detection in <em>attribute</em> histories
 * (docs/comment-transition/phase-4b.md). Same conventions as {@link CommentTransitionScenarioTest}:
 * <pre>mvn test -Dtest=AttributeCommentTransitionScenarioTest -Dct.spec=true</pre>
 */
@EnabledIfSystemProperty(named = "ct.spec", matches = "true")
public class AttributeCommentTransitionScenarioTest {

    private static final String INITIALIZER_CHANGE = "initializer change";

    // ---------------------------------------------------------------- sources

    private static final String RUN = "    public int run(int x) {\n        return x;\n    }\n";
    private static final String RUN_EDITED = "    public int run(int x) {\n        return x * 2;\n    }\n";

    /** A class with the given field lines (already indented by the caller as class members) and a method. */
    private static String src(String run, String... fieldLines) {
        StringBuilder sb = new StringBuilder("package demo;\n\npublic class Sample {\n\n");
        for (String line : fieldLines)
            sb.append("    ").append(line).append("\n");
        return sb.append("\n").append(run).append("}\n").toString();
    }

    /** A, B, C: the field is introduced and its initializer changed twice while active. */
    private static FixtureRepo activeHistory(String name) throws Exception {
        return FixtureRepo.create(name)
                .commit("A", src(RUN, "private int limit = 10;"))
                .commit("B", src(RUN, "private int limit = 20;"))
                .commit("C", src(RUN, "private int limit = 30;"));
    }

    // ---------------------------------------------------------------- scenarios

    /** Report Fig. 9 for a field: commented out (D), method edited while commented (E), restored (F). */
    @Test
    public void f1_fieldRestored_classEditedWhileCommented() throws Exception {
        try (FixtureRepo repo = activeHistory("f1")
                .commit("D", src(RUN, "// private int limit = 30;"))
                .commit("E", src(RUN_EDITED, "// private int limit = 30;"))
                .commit("F", src(RUN_EDITED, "private int limit = 30;"))) {
            verify(repo, expected("F", UNCOMMENTED, "D", COMMENTED_OUT, "C", INITIALIZER_CHANGE, "B", INITIALIZER_CHANGE, "A", INTRODUCED));
        }
    }

    /** The field is commented out with a block comment. */
    @Test
    public void f2_fieldRestoredFromBlockComment() throws Exception {
        try (FixtureRepo repo = activeHistory("f2")
                .commit("D", src(RUN, "/* private int limit = 30; */"))
                .commit("F", src(RUN, "private int limit = 30;"))) {
            verify(repo, expected("F", UNCOMMENTED, "D", COMMENTED_OUT, "C", INITIALIZER_CHANGE, "B", INITIALIZER_CHANGE, "A", INTRODUCED));
        }
    }

    /** The initializer is edited while the field is uncommented. */
    @Test
    public void f3_fieldRestoredWithInitializerEdit() throws Exception {
        try (FixtureRepo repo = activeHistory("f3")
                .commit("D", src(RUN, "// private int limit = 30;"))
                .commit("F", src(RUN, "private int limit = 40;"))) {
            verify(repo, expected("F", UNCOMMENTED + "|" + INITIALIZER_CHANGE, "D", COMMENTED_OUT, "C", INITIALIZER_CHANGE, "B", INITIALIZER_CHANGE, "A", INTRODUCED));
        }
    }

    /** The field is first written as a comment (B) and uncommented later (C): only the origin invariants. */
    @Test
    public void f4_fieldFirstWrittenAsComment() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("f4")
                .commit("A", src(RUN))
                .commit("B", src(RUN, "// private int limit = 10;"))
                .commit("C", src(RUN, "private int limit = 10;"))) {
            Map<String, List<String>> actual = track(repo);
            ScenarioAssertions.print("f4", actual);
            ScenarioAssertions.assertSingleOrigin(actual);
            assertTrue(actual.containsKey("C") || actual.containsKey("B"), "history must reach the uncomment commit");
        }
    }

    /** Negative control: an unrelated commented-out field is deleted in the commit that adds {@code limit}. */
    @Test
    public void f5_unrelatedCommentedFieldDeletedWhileFieldAdded() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("f5")
                .commit("A", src(RUN, "// private int other = 1;"))
                .commit("B", src(RUN, "private int limit = 10;"))) {
            verify(repo, expected("B", INTRODUCED));
        }
    }

    // ---------------------------------------------------------------- harness

    private static Map<String, List<String>> track(FixtureRepo repo) throws Exception {
        AttributeTracker tracker = CodeTracker.attributeTracker()
                .repository(repo.repository())
                .filePath(FixtureRepo.FILE_PATH)
                .startCommitId(repo.headSha())
                .attributeName("limit")
                .attributeDeclarationLineNumber(FixtureRepo.lineOf(repo.lastContent(), "int limit", 1))
                .build();
        return ScenarioAssertions.historyOf(tracker.track(), repo);
    }

    private static void verify(FixtureRepo repo, Map<String, List<String>> expected) throws Exception {
        Map<String, List<String>> actual = track(repo);
        ScenarioAssertions.check(Thread.currentThread().getStackTrace()[2].getMethodName(), expected, actual);
    }
}
