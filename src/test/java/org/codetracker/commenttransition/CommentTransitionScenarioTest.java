package org.codetracker.commenttransition;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.codetracker.api.BlockTracker;
import org.codetracker.api.CodeTracker;
import org.codetracker.api.Edge;
import org.codetracker.api.Graph;
import org.codetracker.api.History;
import org.codetracker.change.Change;
import org.codetracker.element.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

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

    // ---------------------------------------------------------------- harness

    /**
     * Expected history as label → keywords. Keywords for one commit are separated by '|';
     * each must be contained in the title of some change recorded at that commit.
     */
    private static Map<String, List<String>> expected(String... labelKeywordPairs) {
        Map<String, List<String>> map = new TreeMap<>();
        for (int i = 0; i < labelKeywordPairs.length; i += 2)
            map.put(labelKeywordPairs[i], Arrays.asList(labelKeywordPairs[i + 1].split("\\|")));
        return map;
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
        History<Block> history = tracker.track();
        Map<String, List<String>> actual = new TreeMap<>();
        for (History.HistoryInfo<Block> info : history.getHistoryInfoList()) {
            List<String> titles = actual.computeIfAbsent(repo.label(info.getCommitId()), k -> new ArrayList<>());
            for (Change change : info.getChangeList())
                titles.add(change.getType().getTitle().toLowerCase(Locale.ROOT));
        }
        assertSingleChain(history.getGraph(), actual);
        return actual;
    }

    /**
     * The history graph must be one connected chain: exactly one root (the origin), exactly one sink
     * (the tracked block), and every node reachable from the root. A cycle or a disconnected piece fails.
     */
    private static void assertSingleChain(Graph<Block, Edge> graph, Map<String, List<String>> actual) {
        Set<Block> nodes = graph.getNodeList();
        List<Block> roots = nodes.stream().filter(n -> graph.predecessors(n).isEmpty()).collect(Collectors.toList());
        List<Block> sinks = nodes.stream().filter(n -> graph.successors(n).isEmpty()).collect(Collectors.toList());
        assertEquals(1, roots.size(), "history graph must have exactly one root; roots " + roots + "; history " + actual);
        assertEquals(1, sinks.size(), "history graph must have exactly one sink; sinks " + sinks + "; history " + actual);
        Set<Block> reached = new HashSet<>();
        Deque<Block> toVisit = new ArrayDeque<>(roots);
        while (!toVisit.isEmpty()) {
            Block node = toVisit.poll();
            if (reached.add(node))
                toVisit.addAll(graph.successors(node));
        }
        assertEquals(nodes.size(), reached.size(), "every node must be reachable from the root; history " + actual);
    }

    private static void verify(FixtureRepo repo, String blockMarker, Map<String, List<String>> expected) throws Exception {
        Map<String, List<String>> actual = track(repo, blockMarker);
        String scenario = Thread.currentThread().getStackTrace()[2].getMethodName();
        print(scenario, actual);
        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertEquals(expected.keySet(), actual.keySet(),
                "commits in history; expected " + expected + " but was " + actual));
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            for (String keyword : entry.getValue()) {
                checks.add(() -> assertTrue(containsKeyword(actual.get(entry.getKey()), keyword),
                        "commit " + entry.getKey() + " should have a '" + keyword + "' change; actual " + actual));
            }
        }
        boolean expectsComments = expected.values().stream().flatMap(List::stream)
                .anyMatch(k -> k.equals(COMMENTED_OUT) || k.equals(UNCOMMENTED));
        if (!expectsComments) {
            checks.add(() -> assertTrue(actual.values().stream().flatMap(List::stream).noneMatch(t -> t.contains("comment")),
                    "no comment transition expected; actual " + actual));
        }
        checks.add(() -> assertSingleOrigin(actual));
        assertAll(scenario, checks);
    }

    private static void assertSingleOrigin(Map<String, List<String>> actual) {
        Map<String, Long> introducedCount = actual.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().stream().filter(t -> t.contains(INTRODUCED)).count(), (a, b) -> a, LinkedHashMap::new));
        long total = introducedCount.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(1, total, "history must have exactly one 'introduced' change; actual " + actual);
        String oldest = ((TreeMap<String, List<String>>) actual).firstKey();
        assertTrue(introducedCount.getOrDefault(oldest, 0L) == 1, "'introduced' must be the oldest entry; actual " + actual);
    }

    private static boolean containsKeyword(List<String> titles, String keyword) {
        if (titles == null)
            return false;
        Function<String, Boolean> matches = keyword.equals(COMMENTED_OUT)
                ? t -> t.contains(COMMENTED_OUT) && !t.contains(UNCOMMENTED)
                : t -> t.contains(keyword);
        return titles.stream().anyMatch(matches::apply);
    }

    private static void print(String scenario, Map<String, List<String>> actual) {
        System.out.println("[comment-transition] " + scenario + " -> " + actual);
    }
}
