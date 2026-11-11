package org.codetracker.commenttransition;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.codetracker.api.BlockTracker;
import org.codetracker.api.CodeTracker;
import org.codetracker.change.Change;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import gr.uom.java.xmi.LocationInfo.CodeElementType;

/**
 * Specification tests for comment-out / uncomment detection in block histories
 * (see docs/comment-transition-detection.md, Phase 0).
 * <p>
 * Each scenario builds a local Git repository commit by commit (labels A, B, C, ...),
 * tracks one block from the last commit, and compares the history with the
 * <em>target</em> behaviour. Several scenarios are expected to fail until the
 * comment-transition work is finished, so the class only runs when enabled:
 * <pre>mvn test -Dtest=CommentTransitionScenarioTest -Dct.spec=true</pre>
 * Change types are matched by lower-case keywords contained in {@link Change.Type#getTitle()},
 * so the tests do not depend on the final enum names.
 */
@EnabledIfSystemProperty(named = "ct.spec", matches = "true")
public class CommentTransitionScenarioTest {

    private static final String INTRODUCED = "introduced";
    private static final String BODY_CHANGE = "body change";
    private static final String COMMENTED_OUT = "commented out";
    private static final String UNCOMMENTED = "uncommented";

    // ---------------------------------------------------------------- sources

    private static String src(String... bodyLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("package demo;\n\npublic class Sample {\n\n    private int count;\n\n    public int run(int x) {\n");
        for (String line : bodyLines)
            sb.append(line.isEmpty() ? "" : "        " + line).append("\n");
        sb.append("    }\n}\n");
        return sb.toString();
    }

    private static final String[] A_BODY = {"int result = 0;", "if (x > 0) {", "    result = x;", "}", "return result;"};
    private static final String[] B_BODY = {"int result = 0;", "if (x > 0) {", "    result = x * 2;", "}", "return result;"};
    private static final String[] C_BODY = {"int result = 0;", "if (x > 0) {", "    result = x * 2;", "    count++;", "}", "return result;"};
    private static final String[] D_LINE_COMMENTED = {"int result = 0;", "// if (x > 0) {", "//     result = x * 2;", "//     count++;", "// }", "return result;"};

    /** A, B, C: the block is introduced and then changed twice while active. */
    private static FixtureRepo activeHistory(String name) throws Exception {
        return FixtureRepo.create(name)
                .commit("A", src(A_BODY))
                .commit("B", src(B_BODY))
                .commit("C", src(C_BODY));
    }

    // ---------------------------------------------------------------- scenarios

    /** Report Fig. 9: block commented out (D), method edited while commented (E), block restored (F). */
    @Test
    public void s1_wholeBlockRestored_methodEditedWhileCommented() throws Exception {
        try (FixtureRepo repo = activeHistory("s1")
                .commit("D", src(D_LINE_COMMENTED))
                .commit("E", src("int result = -1;", "// if (x > 0) {", "//     result = x * 2;", "//     count++;", "// }", "return result;"))
                .commit("F", src("int result = -1;", "if (x > 0) {", "    result = x * 2;", "    count++;", "}", "return result;"))) {
            verify(repo, "if (x > 0) {", expected(
                    "F", UNCOMMENTED, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** Same as S1 but nothing else changes while the block is commented out (method body at F equals C). */
    @Test
    public void s2_wholeBlockRestored_noOtherEdit() throws Exception {
        try (FixtureRepo repo = activeHistory("s2")
                .commit("D", src(D_LINE_COMMENTED))
                .commit("F", src(C_BODY))) {
            verify(repo, "if (x > 0) {", expected(
                    "F", UNCOMMENTED, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** One statement inside the tracked block is commented out (D) and restored (F). */
    @Test
    public void s3_statementInsideBlockRestored() throws Exception {
        try (FixtureRepo repo = activeHistory("s3")
                .commit("D", src("int result = 0;", "if (x > 0) {", "    result = x * 2;", "    // count++;", "}", "return result;"))
                .commit("E", src("int result = -1;", "if (x > 0) {", "    result = x * 2;", "    // count++;", "}", "return result;"))
                .commit("F", src("int result = -1;", "if (x > 0) {", "    result = x * 2;", "    count++;", "}", "return result;"))) {
            verify(repo, "if (x > 0) {", expected(
                    "F", UNCOMMENTED, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** Commented-out code uses compact formatting; the restored code is formatted normally. */
    @Test
    public void s4_restoredWithDifferentFormatting() throws Exception {
        try (FixtureRepo repo = activeHistory("s4")
                .commit("D", src("int result = 0;", "//if(x>0){", "//    result=x*2;", "//    count++;", "//}", "return result;"))
                .commit("F", src(C_BODY))) {
            verify(repo, "if (x > 0) {", expected(
                    "F", UNCOMMENTED, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** The condition is edited while the block is uncommented. */
    @Test
    public void s5_restoredWithEdit() throws Exception {
        try (FixtureRepo repo = activeHistory("s5")
                .commit("D", src(D_LINE_COMMENTED))
                .commit("F", src("int result = 0;", "if (x >= 0) {", "    result = x * 2;", "    count++;", "}", "return result;"))) {
            verify(repo, "if (x >= 0) {", expected(
                    "F", UNCOMMENTED, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** The block is commented out with a block comment instead of line comments. */
    @Test
    public void s6_blockCommentRestored() throws Exception {
        try (FixtureRepo repo = activeHistory("s6")
                .commit("D", src("int result = 0;", "/*", "if (x > 0) {", "    result = x * 2;", "    count++;", "}", "*/", "return result;"))
                .commit("F", src(C_BODY))) {
            verify(repo, "if (x > 0) {", expected(
                    "F", UNCOMMENTED, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /**
     * The block is first written as a comment (B) and uncommented later (C); it was never active before.
     * Where the origin belongs is an open question (doc, section 7, question 2), so only the
     * invariants are checked: the history has exactly one origin, at its oldest entry.
     */
    @Test
    public void s7_firstWrittenAsComment() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("s7")
                .commit("A", src("int result = 0;", "return result;"))
                .commit("B", src("int result = 0;", "// if (x > 0) {", "//     result = x;", "// }", "return result;"))
                .commit("C", src("int result = 0;", "if (x > 0) {", "    result = x;", "}", "return result;"))) {
            Map<String, List<String>> actual = track(repo, "if (x > 0) {");
            print("s7", actual);
            assertSingleOrigin(actual);
            assertTrue(actual.containsKey("C") || actual.containsKey("B"), "history must reach the uncomment commit");
        }
    }

    /** Negative control: a prose comment is removed together with a real edit. No comment transition. */
    @Test
    public void s8_proseCommentRemovedWithRealEdit() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("s8")
                .commit("A", src("int result = 0;", "if (x > 0) {", "    // TODO handle negatives", "    result = x;", "}", "return result;"))
                .commit("B", src(B_BODY))) {
            verify(repo, "if (x > 0) {", expected("B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** Negative control: the deleted comment belongs to a sibling block, not to the tracked block. */
    @Test
    public void s9_uncommentInSiblingBlockIsOutOfScope() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("s9")
                .commit("A", src("int result = 0;", "if (x > 0) {", "    result = x;", "}",
                        "if (x < 0) {", "    // count++;", "    result = -x;", "}", "return result;"))
                .commit("B", src("int result = 0;", "if (x > 0) {", "    result = x;", "    count++;", "}",
                        "if (x < 0) {", "    result = -x;", "}", "return result;"))) {
            verify(repo, "if (x > 0) {", expected("B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** A statement is uncommented in the same commit as a real edit: both must be reported. */
    @Test
    public void s10_uncommentTogetherWithRealEdit() throws Exception {
        try (FixtureRepo repo = activeHistory("s10")
                .commit("D", src("int result = 0;", "if (x > 0) {", "    result = x * 2;", "    // count++;", "}", "return result;"))
                .commit("F", src("int result = 0;", "if (x > 0) {", "    result = x * 3;", "    count++;", "}", "return result;"))) {
            verify(repo, "if (x > 0) {", expected(
                    "F", UNCOMMENTED + "|" + BODY_CHANGE, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /** A statement inside the block is edited while the whole block is uncommented (Phase 3). */
    @Test
    public void s11_restoredWithBodyEdit() throws Exception {
        try (FixtureRepo repo = activeHistory("s11")
                .commit("D", src(D_LINE_COMMENTED))
                .commit("F", src("int result = 0;", "if (x > 0) {", "    result = x * 3;", "    count++;", "}", "return result;"))) {
            verify(repo, "if (x > 0) {", expected(
                    "F", UNCOMMENTED + "|" + BODY_CHANGE, "D", COMMENTED_OUT, "C", BODY_CHANGE, "B", BODY_CHANGE, "A", INTRODUCED));
        }
    }

    /**
     * Negative control (Phase 3): an unrelated commented-out loop is deleted in the same commit that
     * introduces a new block. The new block must not be reported as uncommented.
     */
    @Test
    public void s12_unrelatedCommentedCodeDeletedWhileBlockAdded() throws Exception {
        try (FixtureRepo repo = FixtureRepo.create("s12")
                .commit("A", src("int result = 0;", "// for (int i = 0; i < x; i++) {", "//     count += i;", "// }", "return result;"))
                .commit("B", src("int result = 0;", "if (x > 0) {", "    result = x * 2;", "}", "return result;"))) {
            verify(repo, "if (x > 0) {", expected("B", INTRODUCED));
        }
    }

    // ---------------------------------------------------------------- harness

    private static Map<String, List<String>> expected(String... labelKeywordPairs) {
        return ScenarioAssertions.expected(labelKeywordPairs);
    }

    private static Map<String, List<String>> track(FixtureRepo repo, String blockMarker) throws Exception {
        String content = repo.lastContent();
        int methodLine = FixtureRepo.lineOf(content, "public int run(", 1);
        int blockStart = FixtureRepo.lineOf(content, blockMarker, methodLine);
        int blockEnd = FixtureRepo.closingLineOf(content, blockStart);
        BlockTracker tracker = CodeTracker.blockTracker()
                .repository(repo.repository())
                .filePath(FixtureRepo.FILE_PATH)
                .startCommitId(repo.headSha())
                .methodName("run")
                .methodDeclarationLineNumber(methodLine)
                .codeElementType(CodeElementType.IF_STATEMENT)
                .blockStartLineNumber(blockStart)
                .blockEndLineNumber(blockEnd)
                .build();
        return ScenarioAssertions.historyOf(tracker.track(), repo);
    }

    private static void verify(FixtureRepo repo, String blockMarker, Map<String, List<String>> expected) throws Exception {
        Map<String, List<String>> actual = track(repo, blockMarker);
        ScenarioAssertions.check(Thread.currentThread().getStackTrace()[2].getMethodName(), expected, actual);
    }

    private static void assertSingleOrigin(Map<String, List<String>> actual) {
        ScenarioAssertions.assertSingleOrigin(actual);
    }

    private static void print(String scenario, Map<String, List<String>> actual) {
        ScenarioAssertions.print(scenario, actual);
    }
}
