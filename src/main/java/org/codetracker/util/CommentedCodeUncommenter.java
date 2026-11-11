package org.codetracker.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;

import gr.uom.java.xmi.UMLComment;

/**
 * Turns commented-out code in a source file back into code, without moving anything: comment markers
 * are replaced by the same number of spaces, so every offset and line number stays valid (ADR 0009).
 * <p>
 * Comments are grouped into runs (consecutive {@code //} lines, or one {@code /* *}{@code /} comment;
 * Javadoc is skipped). A run is uncommented only if the result parses as Java statements without
 * errors, so prose comments are left alone.
 */
public final class CommentedCodeUncommenter {

    /** The file content with commented-out code uncommented, and the lines that were uncommented. */
    public static final class Result {
        private final String content;
        private final Set<Integer> uncommentedLines;

        Result(String content, Set<Integer> uncommentedLines) {
            this.content = content;
            this.uncommentedLines = Collections.unmodifiableSet(uncommentedLines);
        }

        public String getContent() {
            return content;
        }

        public Set<Integer> getUncommentedLines() {
            return uncommentedLines;
        }

        public boolean isEmpty() {
            return uncommentedLines.isEmpty();
        }
    }

    private CommentedCodeUncommenter() {
    }

    /** Uncomments every run of {@code comments} (all from the file {@code content}) that is valid Java code. */
    public static Result uncomment(String content, List<UMLComment> comments) {
        char[] chars = content.toCharArray();
        Set<Integer> uncommentedLines = new LinkedHashSet<>();
        for (List<UMLComment> run : groupIntoRuns(comments)) {
            char[] candidate = chars.clone();
            for (UMLComment comment : run)
                stripMarkers(candidate, comment);
            int start = run.get(0).getLocationInfo().getStartOffset();
            int end = run.get(run.size() - 1).getLocationInfo().getEndOffset();
            if (start < 0 || end > candidate.length || start >= end)
                continue;
            if (isJavaStatements(new String(candidate, start, end - start))) {
                chars = candidate;
                for (int line = run.get(0).getLocationInfo().getStartLine(); line <= run.get(run.size() - 1).getLocationInfo().getEndLine(); line++)
                    uncommentedLines.add(line);
            }
        }
        return new Result(new String(chars), uncommentedLines);
    }

    static List<List<UMLComment>> groupIntoRuns(List<UMLComment> comments) {
        List<UMLComment> sorted = new ArrayList<>(comments);
        sorted.sort(Comparator.comparingInt(c -> c.getLocationInfo().getStartOffset()));
        List<List<UMLComment>> runs = new ArrayList<>();
        List<UMLComment> current = null;
        for (UMLComment comment : sorted) {
            String fullText = comment.getFullText();
            if (fullText.startsWith("/**"))
                continue;
            boolean lineComment = fullText.startsWith("//");
            if (lineComment && current != null && current.get(0).getFullText().startsWith("//")
                    && comment.getLocationInfo().getStartLine() == current.get(current.size() - 1).getLocationInfo().getEndLine() + 1) {
                current.add(comment);
                continue;
            }
            current = new ArrayList<>();
            current.add(comment);
            runs.add(current);
            if (!lineComment)
                current = null;
        }
        return runs;
    }

    /** Replaces the comment markers of {@code comment} in {@code chars} by spaces, in place. */
    private static void stripMarkers(char[] chars, UMLComment comment) {
        int start = comment.getLocationInfo().getStartOffset();
        int end = comment.getLocationInfo().getEndOffset();
        if (start < 0 || end > chars.length || end - start < 2)
            return;
        if (chars[start] == '/' && chars[start + 1] == '/') {
            chars[start] = ' ';
            chars[start + 1] = ' ';
            return;
        }
        if (chars[start] == '/' && chars[start + 1] == '*' && end - start >= 4 && chars[end - 2] == '*' && chars[end - 1] == '/') {
            chars[start] = ' ';
            chars[start + 1] = ' ';
            chars[end - 2] = ' ';
            chars[end - 1] = ' ';
            // a leading '*' on a continuation line is decoration, not code
            boolean lineStart = false;
            for (int i = start + 2; i < end - 2; i++) {
                char c = chars[i];
                if (c == '\n' || c == '\r') {
                    lineStart = true;
                }
                else if (lineStart && c == '*' && (i + 1 >= end - 2 || chars[i + 1] != '=')) {
                    chars[i] = ' ';
                    lineStart = false;
                }
                else if (!Character.isWhitespace(c)) {
                    lineStart = false;
                }
            }
        }
    }

    /** Whether {@code text} parses as one or more Java statements without syntax errors. */
    static boolean isJavaStatements(String text) {
        if (text.trim().isEmpty())
            return false;
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_STATEMENTS);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.latestSupportedJavaVersion(), options);
        parser.setCompilerOptions(options);
        parser.setSource(text.toCharArray());
        ASTNode node = parser.createAST(null);
        if (!(node instanceof Block) || ((Block) node).statements().isEmpty())
            return false;
        boolean[] broken = {false};
        node.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode child) {
                if ((child.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0)
                    broken[0] = true;
            }
        });
        return !broken[0];
    }
}
