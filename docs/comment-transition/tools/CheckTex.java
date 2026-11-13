import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Static sanity checks for a LaTeX file: braces, environments, algorithmic blocks, refs/cites, non-ASCII. */
public class CheckTex {
    public static void main(String[] args) throws Exception {
        Path tex = Path.of(args[0]);
        List<String> lines = Files.readAllLines(tex, StandardCharsets.UTF_8);
        int problems = 0;

        // braces and environments, line by line (comments stripped, escaped braces ignored)
        Deque<int[]> braces = new ArrayDeque<>();
        Deque<String> envs = new ArrayDeque<>();
        Deque<Integer> envLines = new ArrayDeque<>();
        Pattern env = Pattern.compile("\\\\(begin|end)\\{([^}]+)\\}");
        for (int n = 0; n < lines.size(); n++) {
            String line = stripComment(lines.get(n));
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\\') { i++; continue; }          // skip escaped char, e.g. \{ \} \%
                if (c == '{') braces.push(new int[]{n + 1, i + 1});
                else if (c == '}') {
                    if (braces.isEmpty()) { System.out.println("unmatched } at line " + (n + 1)); problems++; }
                    else braces.pop();
                }
            }
            Matcher m = env.matcher(line);
            while (m.find()) {
                if (m.group(1).equals("begin")) { envs.push(m.group(2)); envLines.push(n + 1); }
                else if (envs.isEmpty() || !envs.peek().equals(m.group(2))) {
                    System.out.println("\\end{" + m.group(2) + "} at line " + (n + 1) + " does not close " + (envs.isEmpty() ? "anything" : envs.peek() + " (line " + envLines.peek() + ")"));
                    problems++;
                } else { envs.pop(); envLines.pop(); }
            }
            for (int i = 0; i < lines.get(n).length(); i++) {
                if (lines.get(n).charAt(i) > 127) { System.out.println("non-ASCII at line " + (n + 1) + ": '" + lines.get(n).charAt(i) + "'"); problems++; break; }
            }
        }
        for (int[] b : braces) { System.out.println("unclosed { at line " + b[0] + " col " + b[1]); problems++; }
        for (String e : envs) { System.out.println("unclosed environment " + e); problems++; }

        String all = String.join("\n", lines.stream().map(CheckTex::stripComment).toList());
        // algorithmic block balance
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("If", "EndIf"); pairs.put("For", "EndFor"); pairs.put("Procedure", "EndProcedure");
        for (Map.Entry<String, String> p : pairs.entrySet()) {
            int open = count(all, "\\\\" + p.getKey() + "\\{"), close = count(all, "\\\\" + p.getValue() + "(?![A-Za-z])");
            System.out.println(p.getKey() + "/" + p.getValue() + ": " + open + "/" + close);
            if (open != close) problems++;
        }
        // refs and cites
        Set<String> labels = collect(all, "\\\\label\\{([^}]+)\\}");
        Set<String> bib = collect(all, "\\\\bibitem\\{([^}]+)\\}");
        for (String r : collect(all, "\\\\ref\\{([^}]+)\\}"))
            if (!labels.contains(r)) { System.out.println("missing \\label{" + r + "}"); problems++; }
        Set<String> cites = new TreeSet<>();
        for (String c : collect(all, "\\\\cite\\{([^}]+)\\}")) for (String k : c.split(",")) cites.add(k.trim());
        for (String c : cites) if (!bib.contains(c)) { System.out.println("missing \\bibitem{" + c + "}"); problems++; }
        System.out.println("labels=" + labels.size() + " refs ok, bibitems=" + bib.size() + " cites=" + cites.size());
        System.out.println(problems == 0 ? "OK: no problems found" : problems + " problem(s)");
    }

    static String stripComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '\\') { i++; continue; }
            if (line.charAt(i) == '%') return line.substring(0, i);
        }
        return line;
    }

    static int count(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    static Set<String> collect(String s, String regex) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile(regex).matcher(s);
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
