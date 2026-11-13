import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Compares two directories of surefire XML reports from the oracle tests.
 * Usage: java CompareOracle.java <baseReportsDir> <finalReportsDir>
 * For each test case: status (pass / fail / error) and the TP/FP/FN numbers found in the failure message.
 */
public class CompareOracle {

    record Result(String status, String detail) {}

    static Map<String, Result> load(File dir) throws Exception {
        Map<String, Result> results = new TreeMap<>();
        File[] files = dir.listFiles((d, n) -> n.startsWith("TEST-") && n.endsWith(".xml"));
        if (files == null)
            return results;
        for (File f : files) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
            NodeList cases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element c = (Element) cases.item(i);
                String cls = c.getAttribute("classname");
                String name = c.getAttribute("name");
                String key = cls.substring(cls.lastIndexOf('.') + 1) + " | " + name.replaceFirst("^\\[?\\d+\\]?:?\\s*", "");
                String status = "pass";
                String detail = "";
                NodeList fail = c.getElementsByTagName("failure");
                NodeList err = c.getElementsByTagName("error");
                if (fail.getLength() > 0) {
                    status = "fail";
                    detail = summarize(((Element) fail.item(0)).getAttribute("message") + " " + fail.item(0).getTextContent());
                }
                else if (err.getLength() > 0) {
                    status = "error";
                    String type = ((Element) err.item(0)).getAttribute("type");
                    detail = type + ": " + ((Element) err.item(0)).getAttribute("message");
                    detail = detail.length() > 160 ? detail.substring(0, 160) : detail;
                }
                results.put(key, new Result(status, detail));
            }
        }
        return results;
    }

    /** Extracts "Should have X ... but has Y" pairs for TP / FP / FN from an assertAll failure text. */
    static String summarize(String text) {
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("Should have (\\d+) according to \\S+ (True Positives|False Positives|False Negatives),\\s+but has (\\d+)").matcher(text);
        while (m.find()) {
            String kind = m.group(2).startsWith("True") ? "TP" : m.group(2).startsWith("False P") ? "FP" : "FN";
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(kind).append(" expected ").append(m.group(1)).append(" got ").append(m.group(3));
        }
        return sb.length() > 0 ? sb.toString() : text.replaceAll("\\s+", " ").substring(0, Math.min(160, text.replaceAll("\\s+", " ").length()));
    }

    public static void main(String[] args) throws Exception {
        Map<String, Result> base = load(new File(args[0]));
        Map<String, Result> fin = load(new File(args[1]));
        Map<String, int[]> perClass = new TreeMap<>(); // base pass, base total, final pass, final total, changed
        for (String key : new java.util.TreeSet<String>() {{ addAll(base.keySet()); addAll(fin.keySet()); }}) {
            String cls = key.substring(0, key.indexOf(" | "));
            int[] counts = perClass.computeIfAbsent(cls, k -> new int[5]);
            Result b = base.get(key), f = fin.get(key);
            if (b != null) { counts[1]++; if (b.status.equals("pass")) counts[0]++; }
            if (f != null) { counts[3]++; if (f.status.equals("pass")) counts[2]++; }
            boolean changed = b == null || f == null || !b.status.equals(f.status) || !b.detail.equals(f.detail);
            if (changed) {
                counts[4]++;
                System.out.println("CHANGED " + key);
                System.out.println("    base : " + (b == null ? "missing" : b.status + " " + b.detail));
                System.out.println("    final: " + (f == null ? "missing" : f.status + " " + f.detail));
            }
        }
        System.out.println();
        System.out.printf("%-22s %16s %16s %8s%n", "oracle", "baseline pass", "final pass", "changed");
        for (Map.Entry<String, int[]> e : perClass.entrySet()) {
            int[] c = e.getValue();
            System.out.printf("%-22s %8d / %-5d %8d / %-5d %8d%n", e.getKey(), c[0], c[1], c[2], c[3], c[4]);
        }
    }
}
