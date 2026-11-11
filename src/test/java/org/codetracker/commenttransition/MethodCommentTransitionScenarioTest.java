package org.codetracker.commenttransition;

import static org.codetracker.commenttransition.ScenarioAssertions.BODY_CHANGE;
import static org.codetracker.commenttransition.ScenarioAssertions.COMMENTED_OUT;
import static org.codetracker.commenttransition.ScenarioAssertions.INTRODUCED;
import static org.codetracker.commenttransition.ScenarioAssertions.UNCOMMENTED;
import static org.codetracker.commenttransition.ScenarioAssertions.expected;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.codetracker.api.CodeTracker;
import org.codetracker.api.MethodTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Specification tests for comment-out / uncomment detection in <em>method</em> histories
 * (report <em>Enhancing CodeTracker Accuracy</em>, Algorithm 3; docs/comment-transition/phase-4a.md).
 * Same conventions as {@link CommentTransitionScenarioTest}:
 * <pre>mvn test -Dtest=MethodCommentTransitionScenarioTest -Dct.spec=true</pre>
 */
@EnabledIfSystemProperty(named = "ct.spec", matches = "true")
public class MethodCommentTransitionScenarioTest {

    // ---------------------------------------------------------------- sources

    private static String src(List<String> members) {
        StringBuilder sb = new StringBuilder("package demo;\n\npublic class Sample {\n\n    private int count;\n\n");
        for (String line : members)
            sb.append(line.isEmpty() ? "" : "    " + line).append("\n");
        return sb.append("}\n").toString();
    }

    @SafeVarargs
    private static List<String> members(List<String>... parts) {
        List<String> all = new ArrayList<>();
        for (List<String> part : parts)
            all.addAll(part);
        return all;
    }

    private static List<String> lineCommented(List<String> lines) {
        return lines.stream().map(l -> "// " + l).collect(Collectors.toList());
    }

    private static List<String> blockCommented(List<String> lines) {
        return members(Arrays.asList("/*"), lines, Arrays.asList("*/"));
    }

    private static final List<String> HELPER = Arrays.asList("public void helper() {", "    count = 0;", "}", "");
    private static final List<String> HELPER_EDITED = Arrays.asList("public void helper() {", "    count = 1;", "}", "");
    private static final List<String> RUN_A = Arrays.asList("public int run(int x) {", "    return x;", "}");
    private static final List<String> RUN_B = Arrays.asList("public int run(int x) {", "    return x * 2;", "}");
    private static final List<String> RUN_C = Arrays.asList("public int run(int x) {", "    count++;", "    return x * 2;", "}");

    /** A, B, C: the method is introduced and then changed twice while active. */
    private static FixtureRepo activeHistory(String name) throws Exception {
        return FixtureRepo.create(name)
                .commit("A", src(members(HELPER, RUN_A)))
                .commit("B", src(members(HELPER, RUN_B)))
                .commit("C", src(members(HELPER, RUN_C)));
    }

    // ---------------------------------------------------------------- scenarios

    /** Report Fig. 9 for a method: commented out (D), class edited while commented (E), restored (F). */
    @Test
    public void m1_methodRestored_classEditedWhileCommented() throws Exception {
        try (FixtureRepo repo = activeHistory("m1")
                .commit("D", src(members(HELPER, lineCommented(RUN_C))))
                .commit("E", src(members(HELPER_EDITED, lineCommented(RUN_C))))
                .commit("F", src(members(HELPER_EDITED, RUN_C)))) {
            verify(repo, expected("F", UNCOMMENTED, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** The method is commented out with a block comment. */
    @Test
    public void m2_methodRestoredFromBlockComment() throws Exception {
        try (FixtureRepo repo = activeHistory("m2")
                .commit("D", src(members(HELPER, blockCommented(RUN_C))))
                .commit("F", src(members(HELPER, RUN_C)))) {
            verify(repo, expected("F", UNCOMMENTED, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** The body is edited while the method is uncommented. */
    @Test
    public void m3_methodRestoredWithBodyEdit() throws Exception {
        try (FixtureRepo repo = activeHistory("m3")
                .commit("D", src(members(HELPER, lineCommented(RUN_C))))
                .commit("F", src(members(HELPER, Arrays.asList("public int run(int x) {", "    count++;", "    return x * 3;", "}"))))) {
            verify(repo, expected("F", UNCOMMENTED + "|" + BODY_CHANGE, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** The method is first written as a comment (B) and uncommented later (C): only the origin invariants. */
    @Test
    public void m4_methodFirstWrittenAsComment() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("m4")
                .commit("A", src(HELPER))
                .commit("B", src(members(HELPER, lineCommented(RUN_A))))
                .commit("C", src(members(HELPER, RUN_A)))) {
            Map<String, List<String>> actual = track(repo);
            ScenarioAssertions.print("m4", actual);
            ScenarioAssertions.assertSingleOrigin(actual);
            assertTrue(actual.containsKey("C") || actual.containsKey("B"), "history must reach the uncomment commit");
        }
    }

    /** Negative control: an unrelated commented-out method is deleted in the commit that adds a new method. */
    @Test
    public void m5_unrelatedCommentedMethodDeletedWhileMethodAdded() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("m5")
                .commit("A", src(members(HELPER, lineCommented(Arrays.asList("public int other(int y) {", "    return y + 1;", "}")))))
                .commit("B", src(members(HELPER, RUN_A)))) {
            verify(repo, expected("B", INTRODUCED));
        }
    }

    // ---------------------------------------------------------------- harness

    private static Map<String, List<String>> track(FixtureRepo repo) throws Exception {
        MethodTracker tracker = CodeTracker.methodTracker()
                .repository(repo.repository())
                .filePath(FixtureRepo.FILE_PATH)
                .startCommitId(repo.headSha())
                .methodName("run")
                .methodDeclarationLineNumber(FixtureRepo.lineOf(repo.lastContent(), "public int run(", 1))
                .build();
        return ScenarioAssertions.historyOf(tracker.track(), repo);
    }

    private static void verify(FixtureRepo repo, Map<String, List<String>> expected) throws Exception {
        Map<String, List<String>> actual = track(repo);
        ScenarioAssertions.check(Thread.currentThread().getStackTrace()[2].getMethodName(), expected, actual);
    }
}
