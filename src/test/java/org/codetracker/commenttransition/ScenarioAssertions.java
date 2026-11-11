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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.codetracker.api.CodeElement;
import org.codetracker.api.Edge;
import org.codetracker.api.Graph;
import org.codetracker.api.History;
import org.codetracker.change.Change;
import org.junit.jupiter.api.function.Executable;

/**
 * Shared harness for the comment-transition scenario tests (ADR 0002). Histories are reduced to
 * commit label → lower-case change titles, and compared with expected keywords.
 */
final class ScenarioAssertions {

    static final String INTRODUCED = "introduced";
    static final String BODY_CHANGE = "body change";
    static final String COMMENTED_OUT = "commented out";
    static final String UNCOMMENTED = "uncommented";

    private ScenarioAssertions() {
    }

    /**
     * Expected history as label → keywords. Keywords for one commit are separated by '|';
     * each must be contained in the title of some change recorded at that commit.
     */
    static Map<String, List<String>> expected(String... labelKeywordPairs) {
        Map<String, List<String>> map = new TreeMap<>();
        for (int i = 0; i < labelKeywordPairs.length; i += 2)
            map.put(labelKeywordPairs[i], Arrays.asList(labelKeywordPairs[i + 1].split("\\|")));
        return map;
    }

    /** Reduces a history to label → change titles, after checking that its graph is one chain. */
    static <T extends CodeElement> Map<String, List<String>> historyOf(History<T> history, FixtureRepo repo) {
        Map<String, List<String>> actual = new TreeMap<>();
        for (History.HistoryInfo<T> info : history.getHistoryInfoList()) {
            List<String> titles = actual.computeIfAbsent(repo.label(info.getCommitId()), k -> new ArrayList<>());
            for (Change change : info.getChangeList())
                titles.add(change.getType().getTitle().toLowerCase(Locale.ROOT));
        }
        assertSingleChain(history.getGraph(), actual);
        return actual;
    }

    /**
     * The history graph must be one connected chain: exactly one root (the origin), exactly one sink
     * (the tracked element), and every node reachable from the root. A cycle or a disconnected piece fails.
     */
    static <T extends CodeElement> void assertSingleChain(Graph<T, Edge> graph, Map<String, List<String>> actual) {
        Set<T> nodes = graph.getNodeList();
        List<T> roots = nodes.stream().filter(n -> graph.predecessors(n).isEmpty()).collect(Collectors.toList());
        List<T> sinks = nodes.stream().filter(n -> graph.successors(n).isEmpty()).collect(Collectors.toList());
        assertEquals(1, roots.size(), "history graph must have exactly one root; roots " + roots + "; history " + actual);
        assertEquals(1, sinks.size(), "history graph must have exactly one sink; sinks " + sinks + "; history " + actual);
        Set<T> reached = new HashSet<>();
        Deque<T> toVisit = new ArrayDeque<>(roots);
        while (!toVisit.isEmpty()) {
            T node = toVisit.poll();
            if (reached.add(node))
                toVisit.addAll(graph.successors(node));
        }
        assertEquals(nodes.size(), reached.size(), "every node must be reachable from the root; history " + actual);
    }

    /** Compares an actual history with the expected keywords and the origin invariants. */
    static void check(String scenario, Map<String, List<String>> expected, Map<String, List<String>> actual) {
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

    static void assertSingleOrigin(Map<String, List<String>> actual) {
        Map<String, Long> introducedCount = actual.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().stream().filter(t -> t.contains(INTRODUCED)).count(), (a, b) -> a, LinkedHashMap::new));
        long total = introducedCount.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(1, total, "history must have exactly one 'introduced' change; actual " + actual);
        String oldest = ((TreeMap<String, List<String>>) actual).firstKey();
        assertTrue(introducedCount.getOrDefault(oldest, 0L) == 1, "'introduced' must be the oldest entry; actual " + actual);
    }

    static boolean containsKeyword(List<String> titles, String keyword) {
        if (titles == null)
            return false;
        Predicate<String> matches = keyword.equals(COMMENTED_OUT)
                ? t -> t.contains(COMMENTED_OUT) && !t.contains(UNCOMMENTED)
                : t -> t.contains(keyword);
        return titles.stream().anyMatch(matches);
    }

    static void print(String scenario, Map<String, List<String>> actual) {
        System.out.println("[comment-transition] " + scenario + " -> " + actual);
    }
}
