import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Mines a local git clone for real comment transitions: Java code that a commit turns into
 * line comments ("comment out") and code that a commit turns back from line comments into code
 * ("uncomment"). Pairs the two events on the same file so each pair is a candidate oracle history.
 *
 * Usage: java MineCommentTransitions.java <repoDir> [minLines=3] > candidates.tsv
 *
 * Detection, per commit and file (from `git log -p -U0 --no-merges`):
 *   comment out: removed code lines reappear as added `//` lines (multiset match, whitespace-free);
 *   uncomment:   removed `//` lines reappear as added code lines.
 * Lines that are too trivial (`}`, `{`, `else`, empty) do not count toward the threshold.
 */
public class MineCommentTransitions {

    record Event(String commit, int order, String file, String kind, List<String> lines) {}

    public static void main(String[] args) throws Exception {
        File repo = new File(args[0]);
        int minLines = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        Process p = new ProcessBuilder("git", "-C", repo.getPath(), "log", "-p", "-U0", "--no-merges",
                "--no-renames", "--format=@@@COMMIT %H", "--", "*.java")
                .redirectErrorStream(false).start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        List<Event> events = new ArrayList<>();
        String commit = null, file = null;
        int order = 0;
        List<String> removed = new ArrayList<>(), added = new ArrayList<>();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("@@@COMMIT ")) {
                flush(events, commit, order, file, removed, added, minLines);
                commit = line.substring(10).trim();
                order++;
                file = null;
            } else if (line.startsWith("diff --git ")) {
                flush(events, commit, order, file, removed, added, minLines);
                file = null;
            } else if (line.startsWith("+++ ")) {
                String f = line.substring(4);
                if (f.startsWith("b/")) file = f.substring(2);
            } else if (line.startsWith("--- ") && file == null) {
                // old path; ignored
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                removed.add(line.substring(1));
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                added.add(line.substring(1));
            }
        }
        flush(events, commit, order, file, removed, added, minLines);
        p.waitFor();

        // git log is newest first: a larger order is older.
        Map<String, List<Event>> byFile = new HashMap<>();
        for (Event e : events) byFile.computeIfAbsent(e.file, k -> new ArrayList<>()).add(e);
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println("file\tcommentOutCommit\tuncommentCommit\tsharedLines\tsample");
        int pairs = 0;
        for (Map.Entry<String, List<Event>> en : byFile.entrySet()) {
            List<Event> list = en.getValue();
            for (Event out1 : list) {
                if (!out1.kind.equals("OUT")) continue;
                for (Event in : list) {
                    if (!in.kind.equals("IN") || in.order >= out1.order) continue; // uncomment must be newer
                    List<String> shared = intersect(out1.lines, in.lines);
                    if (shared.size() >= minLines) {
                        pairs++;
                        out.println(en.getKey() + "\t" + out1.commit + "\t" + in.commit + "\t" + shared.size() + "\t"
                                + String.join(" | ", shared.subList(0, Math.min(4, shared.size()))));
                    }
                }
            }
        }
        System.err.println("events=" + events.size() + " pairs=" + pairs);
        PrintStream ev = new PrintStream(new FileOutputStream(args.length > 2 ? args[2] : "events.tsv"), true, StandardCharsets.UTF_8);
        for (Event e : events)
            ev.println(e.kind + "\t" + e.commit + "\t" + e.file + "\t" + e.lines.size() + "\t"
                    + String.join(" | ", e.lines.subList(0, Math.min(3, e.lines.size()))));
        ev.close();
    }

    static void flush(List<Event> events, String commit, int order, String file,
                      List<String> removed, List<String> added, int minLines) {
        if (commit != null && file != null && file.endsWith(".java")) {
            List<String> remCode = new ArrayList<>(), remCmt = new ArrayList<>();
            List<String> addCode = new ArrayList<>(), addCmt = new ArrayList<>();
            split(removed, remCode, remCmt);
            split(added, addCode, addCmt);
            List<String> out = intersect(remCode, addCmt);
            if (meaningful(out) >= minLines) events.add(new Event(commit, order, file, "OUT", out));
            List<String> in = intersect(remCmt, addCode);
            if (meaningful(in) >= minLines) events.add(new Event(commit, order, file, "IN", in));
        }
        removed.clear();
        added.clear();
    }

    static void split(List<String> lines, List<String> code, List<String> commented) {
        for (String l : lines) {
            String t = l.strip();
            if (t.startsWith("//")) {
                String c = norm(t.replaceFirst("^/+", ""));
                if (!c.isEmpty()) commented.add(c);
            } else if (!t.isEmpty() && !t.startsWith("*") && !t.startsWith("/*")) {
                code.add(norm(t));
            }
        }
    }

    static String norm(String s) { return s.replaceAll("\\s+", ""); }

    static int meaningful(List<String> lines) {
        int n = 0;
        for (String l : lines)
            if (l.length() > 2 && !l.equals("else") && !l.equals("}else{") && !l.equals("});")) n++;
        return n;
    }

    static List<String> intersect(List<String> a, List<String> b) {
        Map<String, Integer> counts = new HashMap<>();
        for (String s : b) counts.merge(s, 1, Integer::sum);
        List<String> res = new ArrayList<>();
        for (String s : a) {
            Integer c = counts.get(s);
            if (c != null && c > 0) { res.add(s); counts.put(s, c - 1); }
        }
        return res;
    }
}
