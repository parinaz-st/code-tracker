package org.codetracker.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import gr.uom.java.xmi.UMLComment;
import gr.uom.java.xmi.UMLModel;

/**
 * {@link CommentTransitionAnalyzer#deletedCommentRuns}: the whole run of a restored commented method is returned,
 * even when one of its lines also occurs in another commented method that stays (ADR 0014; elasticsearch SearchFieldsIT).
 */
public class DeletedCommentRunsTest {

    private static List<UMLComment> classComments(String source) throws Exception {
        UMLModel model = GitHistoryRefactoringMinerImpl.createModel(Collections.singletonMap("src/demo/Sample.java", source), new LinkedHashSet<>());
        return model.getClassList().get(0).getComments();
    }

    private static Set<Integer> lines(List<UMLComment> comments) {
        Set<Integer> lines = new TreeSet<>();
        for (UMLComment c : comments)
            lines.add(c.getLocationInfo().getStartLine());
        return lines;
    }

    @Test
    public void sharedClosingLineStaysWithItsRun() throws Exception {
        String parent = "package demo;\npublic class Sample {\n"
                + "//    public void testA() {\n"
                + "//        a();\n"
                + "//    } NOCOMMIT fix this\n"
                + "\n"
                + "//    public void testB() {\n"
                + "//        b();\n"
                + "//    } NOCOMMIT fix this\n"
                + "}\n";
        String child = "package demo;\npublic class Sample {\n"
                + "    public void testA() {\n"
                + "        a();\n"
                + "    }\n"
                + "\n"
                + "//    public void testB() {\n"
                + "//        b();\n"
                + "//    } NOCOMMIT fix this\n"
                + "}\n";
        List<UMLComment> runs = CommentTransitionAnalyzer.deletedCommentRuns(classComments(parent), classComments(child));
        // The whole run of testA is returned. Text alone cannot tell which of the two identical closing lines was
        // removed, so the run of testB may be returned too; that is harmless, because the restored method is then
        // found by its own signature.
        assertTrue(lines(runs).containsAll(Arrays.asList(3, 4, 5)), lines(runs).toString());
    }

    @Test
    public void untouchedRunsAreNotReturned() throws Exception {
        String parent = "package demo;\npublic class Sample {\n"
                + "//    public void testA() {\n"
                + "//        a();\n"
                + "//    }\n"
                + "\n"
                + "//    public void testB() {\n"
                + "//        b();\n"
                + "//    }\n"
                + "}\n";
        String child = "package demo;\npublic class Sample {\n"
                + "    public void testA() {\n"
                + "        a();\n"
                + "    }\n"
                + "\n"
                + "//    public void testB() {\n"
                + "//        b();\n"
                + "//    }\n"
                + "}\n";
        List<UMLComment> runs = CommentTransitionAnalyzer.deletedCommentRuns(classComments(parent), classComments(child));
        assertEquals(new TreeSet<>(Arrays.asList(3, 4, 5)), lines(runs));
    }
}
