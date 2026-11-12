package org.codetracker.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import gr.uom.java.xmi.UMLComment;
import gr.uom.java.xmi.UMLModel;

/** Unit tests for {@link CommentedCodeUncommenter} (ADR 0009). */
public class CommentedCodeUncommenterTest {

    private static final String PATH = "src/main/java/demo/Sample.java";

    private static String method(String... bodyLines) {
        StringBuilder sb = new StringBuilder("package demo;\npublic class Sample {\n    public int run(int x) {\n");
        for (String line : bodyLines)
            sb.append("        ").append(line).append("\n");
        return sb.append("    }\n}\n").toString();
    }

    private static List<UMLComment> comments(String source) throws Exception {
        UMLModel model = GitHistoryRefactoringMinerImpl.createModel(Collections.singletonMap(PATH, source), new LinkedHashSet<>());
        return model.getClassList().get(0).getOperations().get(0).getComments();
    }

    @ParameterizedTest
    @ValueSource(strings = {"count++;", "return;", "if (x > 0) {\n result = x * 2;\n}", "for (int i = 0; i < x; i++) {\n total += i;\n}",
            "int y = foo(1, 2);", "list.forEach(v -> use(v));"})
    public void codeParsesAsStatements(String text) {
        assertTrue(CommentedCodeUncommenter.isJavaStatements(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TODO handle negatives", "see the docs for details.", "if (x > 0) {", "}", "", "fixes #123 (see issue)",
            "result = x * 2"})
    public void proseAndFragmentsDoNotParse(String text) {
        assertFalse(CommentedCodeUncommenter.isJavaStatements(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"public int run(int x) {\n return x;\n}", "private int count = 0;", "void a() {}\nvoid b() {}",
            "@Override\npublic String toString() {\n return \"x\";\n}", "static {\n init();\n}"})
    public void membersParseAsClassBodyDeclarations(String text) {
        assertTrue(CommentedCodeUncommenter.isJavaClassBodyDeclarations(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TODO remove after release", "count++;", "public int run(int x) {", "return x;", "see Sample#run(int)"})
    public void statementsAndProseAreNotClassBodyDeclarations(String text) {
        assertFalse(CommentedCodeUncommenter.isJavaClassBodyDeclarations(text), text);
    }

    @Test
    public void commentedMethodIsUncommentedAsClassMember() throws Exception {
        String source = "package demo;\npublic class Sample {\n    // public int run(int x) {\n    //     return x;\n    // }\n}\n";
        UMLModel model = GitHistoryRefactoringMinerImpl.createModel(Collections.singletonMap(PATH, source), new LinkedHashSet<>());
        List<UMLComment> classComments = model.getClassList().get(0).getComments();
        CommentedCodeUncommenter.Result asMembers = CommentedCodeUncommenter.uncomment(source, classComments, CommentedCodeUncommenter.Kind.CLASS_BODY_DECLARATIONS);
        assertEquals(new HashSet<>(Arrays.asList(3, 4, 5)), asMembers.getUncommentedLines());
        assertTrue(asMembers.getContent().contains("   public int run(int x) {"));
        CommentedCodeUncommenter.Result asStatements = CommentedCodeUncommenter.uncomment(source, classComments);
        assertTrue(asStatements.isEmpty(), "a method declaration is not a statement");
    }

    @Test
    public void lineCommentRunIsUncommentedInPlace() throws Exception {
        String source = method("int result = 0;", "// if (x > 0) {", "//     result = x * 2;", "// }", "return result;");
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, comments(source));
        String expected = method("int result = 0;", "   if (x > 0) {", "       result = x * 2;", "   }", "return result;");
        assertEquals(expected, result.getContent());
        assertEquals(source.length(), result.getContent().length(), "offsets must not move");
        assertEquals(new HashSet<>(Arrays.asList(5, 6, 7)), result.getUncommentedLines());
    }

    @Test
    public void blockCommentIsUncommentedInPlace() throws Exception {
        String source = method("int result = 0;", "/*", "for (int i = 0; i < x; i++) {", "    result += i;", "}", "*/", "return result;");
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, comments(source));
        assertTrue(result.getContent().contains("for (int i = 0; i < x; i++) {"));
        assertFalse(result.getContent().contains("/*"));
        assertFalse(result.getContent().contains("*/"));
        assertEquals(source.length(), result.getContent().length(), "offsets must not move");
    }

    @Test
    public void proseAndPartialRunsAreLeftAlone() throws Exception {
        String source = method("int result = 0;", "// TODO handle negatives", "", "// if (x > 0) {", "result = x;", "// }", "return result;");
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, comments(source));
        assertEquals(source, result.getContent());
        assertTrue(result.isEmpty());
    }

    private static List<UMLComment> classComments(String source) throws Exception {
        UMLModel model = GitHistoryRefactoringMinerImpl.createModel(Collections.singletonMap(PATH, source), new LinkedHashSet<>());
        return model.getClassList().get(0).getComments();
    }

    /** elasticsearch JsonSourceFieldMapper (3a55998): a prose note sits right above the commented method. */
    @Test
    public void proseLineAboveCommentedMethodIsLeftAsComment() throws Exception {
        String source = "package demo;\npublic class Sample {\n"
                + "    // source is always enabled for now\n"
                + "//    public Sample enabled(boolean enabled) {\n"
                + "//        this.enabled = enabled;\n"
                + "//        return this;\n"
                + "//    }\n"
                + "}\n";
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, classComments(source), CommentedCodeUncommenter.Kind.CLASS_BODY_DECLARATIONS);
        assertEquals(new HashSet<>(Arrays.asList(4, 5, 6, 7)), result.getUncommentedLines());
        assertTrue(result.getContent().contains("// source is always enabled for now"), "the prose line stays a comment");
        assertTrue(result.getContent().contains("      public Sample enabled(boolean enabled) {"));
        assertEquals(source.length(), result.getContent().length(), "offsets must not move");
    }

    /** elasticsearch FunctionScoreIT (ab01ec5): a marker follows the closing brace inside the comment. */
    @Test
    public void markerTextAfterCodeIsCut() throws Exception {
        String source = "package demo;\npublic class Sample {\n"
                + "//    public void testWeight() {\n"
                + "//        check(2);\n"
                + "//    } NOCOMMIT fix this\n"
                + "}\n";
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, classComments(source), CommentedCodeUncommenter.Kind.CLASS_BODY_DECLARATIONS);
        assertEquals(new HashSet<>(Arrays.asList(3, 4, 5)), result.getUncommentedLines());
        assertFalse(result.getContent().contains("NOCOMMIT"), "the marker is blanked in the uncommented copy");
        assertEquals(source.length(), result.getContent().length(), "offsets must not move");
    }

    /** elasticsearch TransportTwoNodesSearchIT (ab01ec5): a question-like marker after the closing brace. */
    @Test
    public void markerWithPunctuationIsCut() throws Exception {
        String source = "package demo;\npublic class Sample {\n"
                + "//    public void testFailed() {\n"
                + "//        fail();\n"
                + "//    } NORELEASE can this still be tested? if so, how?\n"
                + "}\n";
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, classComments(source), CommentedCodeUncommenter.Kind.CLASS_BODY_DECLARATIONS);
        assertEquals(new HashSet<>(Arrays.asList(3, 4, 5)), result.getUncommentedLines());
    }

    /** elasticsearch InternalTestCluster (4bec333): a prose note above a commented try statement. */
    @Test
    public void proseLineAboveCommentedBlockIsLeftAsComment() throws Exception {
        String source = method("int result = 0;",
                "// Mute this assertion until we have a new Lucene snapshot",
                "// try {",
                "//     result = check(x);",
                "// } catch (Exception e) {",
                "//     throw new AssertionError(e);",
                "// }",
                "return result;");
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, comments(source));
        assertEquals(new HashSet<>(Arrays.asList(6, 7, 8, 9, 10)), result.getUncommentedLines());
        assertTrue(result.getContent().contains("// Mute this assertion"));
    }

    /** Trailing prose after the commented code is trimmed the same way. */
    @Test
    public void proseLineBelowCommentedBlockIsLeftAsComment() throws Exception {
        String source = method("int result = 0;", "// if (x > 0) {", "//     result = x * 2;", "// }", "// re-enable when the fix is released", "return result;");
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, comments(source));
        assertEquals(new HashSet<>(Arrays.asList(5, 6, 7)), result.getUncommentedLines());
    }

    /** Prose-only runs, even with code-like words, stay comments. */
    @Test
    public void proseOnlyRunsStayComments() throws Exception {
        String source = method("int result = 0;", "// we return early here; see the issue {123}", "// because the cache is not ready", "return result;");
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, comments(source));
        assertTrue(result.isEmpty());
        assertEquals(source, result.getContent());
    }

    /** "} else" continues code: it must not be cut as if it were a marker. */
    @Test
    public void keywordAfterBraceIsNotAMarker() throws Exception {
        String source = method("int result = 0;", "// if (x > 0) {", "//     result = 1;", "// } else", "//     result = 2;", "return result;");
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, comments(source));
        assertTrue(result.getContent().contains("} else"), "else is kept");
        assertEquals(new HashSet<>(Arrays.asList(5, 6, 7, 8)), result.getUncommentedLines());
    }

    @Test
    public void javadocIsSkipped() throws Exception {
        String source = "package demo;\npublic class Sample {\n    public int run(int x) {\n        /** count++; */\n        return x;\n    }\n}\n";
        CommentedCodeUncommenter.Result result = CommentedCodeUncommenter.uncomment(source, comments(source));
        assertEquals(source, result.getContent());
    }
}
