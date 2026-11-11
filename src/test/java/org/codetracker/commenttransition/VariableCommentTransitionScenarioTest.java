package org.codetracker.commenttransition;

import static org.codetracker.commenttransition.ScenarioAssertions.COMMENTED_OUT;
import static org.codetracker.commenttransition.ScenarioAssertions.INTRODUCED;
import static org.codetracker.commenttransition.ScenarioAssertions.UNCOMMENTED;
import static org.codetracker.commenttransition.ScenarioAssertions.expected;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.codetracker.api.CodeTracker;
import org.codetracker.api.VariableTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Specification tests for comment-out / uncomment detection in <em>variable</em> histories
 * (docs/comment-transition/phase-4c.md). Same conventions as {@link CommentTransitionScenarioTest}:
 * <pre>mvn test -Dtest=VariableCommentTransitionScenarioTest -Dct.spec=true</pre>
 * A variable's history records declaration changes (rename, type, modifiers), not initializer edits,
 * so the active period uses a rename (B) and a type change (C).
 */
@EnabledIfSystemProperty(named = "ct.spec", matches = "true")
public class VariableCommentTransitionScenarioTest {

    private static final String RENAME = "rename";
    private static final String TYPE_CHANGE = "type change";

    // ---------------------------------------------------------------- sources

    /** {@code run(int x)} with the given body lines. */
    private static String src(String... bodyLines) {
        StringBuilder sb = new StringBuilder("package demo;\n\npublic class Sample {\n\n    public int run(int x) {\n");
        for (String line : bodyLines)
            sb.append("        ").append(line).append("\n");
        return sb.append("    }\n}\n").toString();
    }

    /** A, B, C: the variable is introduced as {@code extra}, renamed to {@code bonus}, then its type changes. */
    private static FixtureRepo activeHistory(String name) throws Exception {
        return FixtureRepo.create(name)
                .commit("A", src("int result = x;", "int extra = x;", "return result;"))
                .commit("B", src("int result = x;", "int bonus = x;", "return result;"))
                .commit("C", src("int result = x;", "long bonus = x;", "return result;"));
    }

    // ---------------------------------------------------------------- scenarios

    /** Report Fig. 9 for a variable: commented out (D), method edited while commented (E), restored (F). */
    @Test
    public void v1_variableRestored_methodEditedWhileCommented() throws Exception {
        try (FixtureRepo repo = activeHistory("v1")
                .commit("D", src("int result = x;", "// long bonus = x;", "return result;"))
                .commit("E", src("int result = x + 1;", "// long bonus = x;", "return result;"))
                .commit("F", src("int result = x + 1;", "long bonus = x;", "return result;"))) {
            verify(repo, expected("F", UNCOMMENTED, "D", COMMENTED_OUT, "C", TYPE_CHANGE, "B", RENAME, "A", INTRODUCED));
        }
    }

    /** The variable is commented out with a block comment. */
    @Test
    public void v2_variableRestoredFromBlockComment() throws Exception {
        try (FixtureRepo repo = activeHistory("v2")
                .commit("D", src("int result = x;", "/* long bonus = x; */", "return result;"))
                .commit("F", src("int result = x;", "long bonus = x;", "return result;"))) {
            verify(repo, expected("F", UNCOMMENTED, "D", COMMENTED_OUT, "C", TYPE_CHANGE, "B", RENAME, "A", INTRODUCED));
        }
    }

    /** The type is changed while the variable is uncommented. */
    @Test
    public void v3_variableRestoredWithTypeEdit() throws Exception {
        try (FixtureRepo repo = activeHistory("v3")
                .commit("D", src("int result = x;", "// long bonus = x;", "return result;"))
                .commit("F", src("int result = x;", "double bonus = x;", "return result;"))) {
            verify(repo, expected("F", UNCOMMENTED + "|" + TYPE_CHANGE, "D", COMMENTED_OUT, "C", TYPE_CHANGE, "B", RENAME, "A", INTRODUCED));
        }
    }

    /** The variable is first written as a comment (B) and uncommented later (C): only the origin invariants. */
    @Test
    public void v4_variableFirstWrittenAsComment() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("v4")
                .commit("A", src("int result = x;", "return result;"))
                .commit("B", src("int result = x;", "// int bonus = x;", "return result;"))
                .commit("C", src("int result = x;", "int bonus = x;", "return result;"))) {
            Map<String, List<String>> actual = track(repo);
            ScenarioAssertions.print("v4", actual);
            ScenarioAssertions.assertSingleOrigin(actual);
            assertTrue(actual.containsKey("C") || actual.containsKey("B"), "history must reach the uncomment commit");
        }
    }

    /** Negative control: an unrelated commented-out variable is deleted in the commit that adds {@code bonus}. */
    @Test
    public void v5_unrelatedCommentedVariableDeletedWhileVariableAdded() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("v5")
                .commit("A", src("int result = x;", "// String other = \"x\";", "return result;"))
                .commit("B", src("int result = x;", "int bonus = x;", "return result;"))) {
            verify(repo, expected("B", INTRODUCED));
        }
    }

    // ---------------------------------------------------------------- harness

    private static Map<String, List<String>> track(FixtureRepo repo) throws Exception {
        String content = repo.lastContent();
        VariableTracker tracker = CodeTracker.variableTracker()
                .repository(repo.repository())
                .filePath(FixtureRepo.FILE_PATH)
                .startCommitId(repo.headSha())
                .methodName("run")
                .methodDeclarationLineNumber(FixtureRepo.lineOf(content, "public int run(", 1))
                .variableName("bonus")
                .variableDeclarationLineNumber(FixtureRepo.lineOf(content, " bonus = ", 1))
                .build();
        return ScenarioAssertions.historyOf(tracker.track(), repo);
    }

    private static void verify(FixtureRepo repo, Map<String, List<String>> expected) throws Exception {
        Map<String, List<String>> actual = track(repo);
        ScenarioAssertions.check(Thread.currentThread().getStackTrace()[2].getMethodName(), expected, actual);
    }
}
