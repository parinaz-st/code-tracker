package org.codetracker.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import gr.uom.java.xmi.UMLComment;

/**
 * Turns commented-out code in a source file back into code, without moving anything: comment markers
 * are replaced by the same number of spaces, so every offset and line number stays valid (ADR 0009).
 * <p>
 * Comments are grouped into runs (consecutive {@code //} lines, or one {@code /* *}{@code /} comment;
 * Javadoc is skipped). A run is uncommented only if the result parses as Java statements without
 * errors, so prose comments are left alone. If a run of {@code //} lines with code-like lines does not
 * parse, up to {@value #MAX_TRIMMED_LINES} prose lines are trimmed at each end and note text after the last
 * code token of a line (such as {@code } NOCOMMIT fix this}) is blanked, and the result is parsed again
 * (ADR 0014). Trimmed lines stay comments.
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

    /** What kind of code a comment run must be to count as commented-out code. */
    public enum Kind {
        /** Statements inside a method body (blocks, variables). */
        STATEMENTS,
        /** Members of a class body: methods, fields, initializers (methods, attributes). */
        CLASS_BODY_DECLARATIONS
    }

    /** Uncomments every run of {@code comments} (all from the file {@code content}) that is valid Java statements. */
    public static Result uncomment(String content, List<UMLComment> comments) {
        return uncomment(content, comments, Kind.STATEMENTS);
    }

    /** How many prose lines may be trimmed at each end of a line-comment run (ADR 0014). */
    private static final int MAX_TRIMMED_LINES = 3;

    /** Words that continue code after a closing brace, so the text after the brace is not a marker. */
    private static final Set<String> CONTINUATION_KEYWORDS = new LinkedHashSet<>(Arrays.asList(
            "else", "catch", "finally", "while", "return", "throw", "new", "instanceof", "case", "default"));

    /** Text after the last code token of a line that is a note, not code: words and punctuation only. */
    private static final Pattern MARKER = Pattern.compile("^\\s+([A-Za-z]\\w*)[^;{}()=\\[\\]\"']*$");

    /** Uncomments every run of {@code comments} (all from the file {@code content}) that is valid code of the given kind. */
    public static Result uncomment(String content, List<UMLComment> comments, Kind kind) {
        char[] chars = content.toCharArray();
        Set<Integer> uncommentedLines = new LinkedHashSet<>();
        for (List<UMLComment> run : groupIntoRuns(comments)) {
            List<UMLComment> kept = run;
            char[] accepted = tryRun(chars, run, kind, false);
            // Real commented-out code often carries a prose note above or below it, or a marker such as
            // "} NOCOMMIT fix this" after it (ADR 0014). Retry without them, but only for runs with code-like lines.
            if (accepted == null && isLineCommentRun(run) && hasCodeLikeLine(chars, run)) {
                attempts:
                for (int removed = 0; removed <= 2 * MAX_TRIMMED_LINES; removed++) {
                    for (int head = Math.max(0, removed - MAX_TRIMMED_LINES); head <= Math.min(removed, MAX_TRIMMED_LINES); head++) {
                        int tail = removed - head;
                        if (head + tail >= run.size() || !onlyProse(chars, run.subList(0, head)) || !onlyProse(chars, run.subList(run.size() - tail, run.size())))
                            continue;
                        List<UMLComment> sub = run.subList(head, run.size() - tail);
                        for (boolean cut : new boolean[]{false, true}) {
                            if (removed == 0 && !cut)
                                continue; // the whole run was already tried
                            char[] candidate = tryRun(chars, sub, kind, cut);
                            if (candidate != null) {
                                accepted = candidate;
                                kept = sub;
                                break attempts;
                            }
                        }
                    }
                }
            }
            if (accepted != null) {
                chars = accepted;
                for (int line = kept.get(0).getLocationInfo().getStartLine(); line <= kept.get(kept.size() - 1).getLocationInfo().getEndLine(); line++)
                    uncommentedLines.add(line);
            }
        }
        return new Result(new String(chars), uncommentedLines);
    }

    /**
     * Uncomments {@code run} in a copy of {@code chars} and returns it if the run is valid code of the given kind,
     * otherwise {@code null}. With {@code cutMarkers}, note text after the last code token of each line is blanked;
     * if that changes nothing, the attempt is skipped.
     */
    private static char[] tryRun(char[] chars, List<UMLComment> run, Kind kind, boolean cutMarkers) {
        char[] candidate = chars.clone();
        boolean cutSomething = false;
        for (UMLComment comment : run) {
            stripMarkers(candidate, comment);
            if (cutMarkers)
                cutSomething |= cutMarker(candidate, comment);
        }
        if (cutMarkers && !cutSomething)
            return null;
        int start = run.get(0).getLocationInfo().getStartOffset();
        int end = run.get(run.size() - 1).getLocationInfo().getEndOffset();
        if (start < 0 || end > candidate.length || start >= end)
            return null;
        return isJava(new String(candidate, start, end - start), kind) ? candidate : null;
    }

    private static boolean isLineCommentRun(List<UMLComment> run) {
        return run.get(0).getFullText().startsWith("//");
    }

    /** Whether some line of the run, uncommented, ends like a statement or a block boundary. */
    private static boolean hasCodeLikeLine(char[] chars, List<UMLComment> run) {
        for (UMLComment comment : run) {
            String text = commentBody(chars, comment).trim();
            int lastCode = lastCodeToken(text);
            if (lastCode >= 0 && (lastCode == text.length() - 1 || MARKER.matcher(text.substring(lastCode + 1)).matches()))
                return true;
        }
        return false;
    }

    /**
     * Whether every comment in {@code lines} is a note rather than code: it does not end like a statement or a
     * block boundary. Only such lines may be trimmed, so a run is never cut into a parseable code fragment.
     */
    private static boolean onlyProse(char[] chars, List<UMLComment> lines) {
        for (UMLComment comment : lines) {
            String text = commentBody(chars, comment).trim();
            int lastCode = lastCodeToken(text);
            if (lastCode >= 0 && (lastCode == text.length() - 1 || MARKER.matcher(text.substring(lastCode + 1)).matches()))
                return false;
        }
        return true;
    }

    /** Blanks the note text after the last code token of a {@code //} comment, in place. Returns whether it did. */
    private static boolean cutMarker(char[] chars, UMLComment comment) {
        int start = comment.getLocationInfo().getStartOffset() + 2;
        int end = comment.getLocationInfo().getEndOffset();
        if (start < 2 || end > chars.length || start >= end)
            return false;
        String body = new String(chars, start, end - start);
        int lastCode = lastCodeToken(body);
        if (lastCode < 0 || lastCode == body.length() - 1)
            return false;
        Matcher m = MARKER.matcher(body.substring(lastCode + 1));
        if (!m.matches() || CONTINUATION_KEYWORDS.contains(m.group(1)))
            return false;
        for (int i = start + lastCode + 1; i < end; i++)
            if (chars[i] != '\n' && chars[i] != '\r')
                chars[i] = ' ';
        return true;
    }

    private static int lastCodeToken(String text) {
        return Math.max(text.lastIndexOf(';'), Math.max(text.lastIndexOf('{'), text.lastIndexOf('}')));
    }

    private static String commentBody(char[] chars, UMLComment comment) {
        int start = comment.getLocationInfo().getStartOffset();
        int end = comment.getLocationInfo().getEndOffset();
        if (start < 0 || end > chars.length || end - start < 2)
            return "";
        return new String(chars, start + 2, end - start - 2);
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
        return isJava(text, Kind.STATEMENTS);
    }

    /** Whether {@code text} parses as one or more Java class members (methods, fields, ...) without syntax errors. */
    static boolean isJavaClassBodyDeclarations(String text) {
        return isJava(text, Kind.CLASS_BODY_DECLARATIONS);
    }

    private static boolean isJava(String text, Kind kind) {
        if (text.trim().isEmpty())
            return false;
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(kind == Kind.STATEMENTS ? ASTParser.K_STATEMENTS : ASTParser.K_CLASS_BODY_DECLARATIONS);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.latestSupportedJavaVersion(), options);
        parser.setCompilerOptions(options);
        parser.setSource(text.toCharArray());
        ASTNode node = parser.createAST(null);
        if (kind == Kind.STATEMENTS && (!(node instanceof Block) || ((Block) node).statements().isEmpty()))
            return false;
        if (kind == Kind.CLASS_BODY_DECLARATIONS && (!(node instanceof TypeDeclaration) || ((TypeDeclaration) node).bodyDeclarations().isEmpty()))
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
