package org.codetracker.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.graph.EndpointPair;
import gr.uom.java.xmi.LocationInfo;
import org.codetracker.GitLog;
import org.codetracker.HistoryImpl;
import org.codetracker.api.CodeElement;
import org.codetracker.api.CodeTracker;
import org.codetracker.api.History;
import org.codetracker.experiment.oracle.history.AbstractHistoryInfo;
import org.codetracker.experiment.oracle.history.AttributeHistoryInfo;
import org.codetracker.experiment.oracle.history.BlockHistoryInfo;
import org.codetracker.experiment.oracle.history.ChangeHistory;
import org.codetracker.experiment.oracle.history.MethodHistoryInfo;
import org.codetracker.experiment.oracle.history.VariableHistoryInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Builds the offline inputs an oracle history needs, from a local clone instead of the GitHub API.
 * <p>
 * For one oracle history JSON it
 * <ol>
 *   <li>tracks the element with the git-based tracker on the local clone and writes the detected changes
 *       to {@code target/oracle-detected/<kind>/<history>.detected.json} (or {@code -Dct.out}), in oracle format,
 *       for manual validation;</li>
 *   <li>writes the commit-log map the {@code *WithLocalFiles} trackers read
 *       ({@code src/test/resources/<kind>/<repo>-<Class>-<name>.json});</li>
 *   <li>writes the {@code oracle/commits} cache entries (changed-file info plus file contents before and after)
 *       for every commit in those logs that is not cached yet, in the format of
 *       {@code GitHistoryRefactoringMinerImpl.populateWithGitHubAPIAndSaveFiles}.</li>
 * </ol>
 * Modes (run with the working directory at the CodeTracker root; {@code <kind>} is block, method, variable or attribute):
 * <ul>
 *   <li>{@code <kind> <localClone> <history.json>...}: steps 1-3 above;</li>
 *   <li>{@code check <kind> <localClone> <history.json>...}: validation aid, see {@link #check};</li>
 *   <li>{@code finalize <kind> <localClone> <history.json> [verdict.json]}: writes the validated expected changes;</li>
 *   <li>{@code score <kind> <history.json>...}: TP/FP/FN of the offline tracker, as the oracle tests compute them;</li>
 *   <li>{@code cache <localClone> <repoName> <commitLog.json>...}: rebuilds the cache entries a commit-log file needs;</li>
 *   <li>{@code verify <localClone> <repoName> <commit>}: regenerates one existing cache entry into a temporary
 *       folder and compares it with the cached one (checks that this class matches the GitHub-API format).</li>
 * </ul>
 * The workflow is described in {@code docs/comment-transition/oracle/README.md}.
 */
public class LocalOracleBuilder {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String CACHE = System.getProperty("user.dir") + "/oracle/commits";

    public static void main(String[] args) throws Exception {
        if (args[0].equals("verify")) {
            verify(args[1], args[2], args[3]);
            return;
        }
        if (args[0].equals("check")) {
            try (Repository repo = new FileRepositoryBuilder().setGitDir(new File(args[2], ".git")).build()) {
                for (int i = 3; i < args.length; i++)
                    check(args[1], repo, new File(args[i]));
            }
            return;
        }
        if (args[0].equals("cache")) {
            // Rebuilds the oracle/commits entries from committed commit-log files only (no tracking, no network).
            try (Repository repo = new FileRepositoryBuilder().setGitDir(new File(args[1], ".git")).build()) {
                int written = 0;
                for (int i = 3; i < args.length; i++) {
                    Map<String, List<String>> logMap = new ObjectMapper().readValue(new File(args[i]), GitLog.class).getCommitLogMap();
                    Set<String> commits = new LinkedHashSet<>();
                    logMap.values().forEach(commits::addAll);
                    for (String commit : commits)
                        if (writeCacheEntry(repo, args[2], commit, new File(CACHE), false)) written++;
                }
                System.out.println("new cache entries=" + written);
            }
            return;
        }
        if (args[0].equals("score")) {
            for (int i = 2; i < args.length; i++)
                System.out.println(score(args[1], new File(args[i])));
            return;
        }
        if (args[0].equals("finalize")) {
            try (Repository repo = new FileRepositoryBuilder().setGitDir(new File(args[2], ".git")).build()) {
                finalizeHistory(args[1], repo, new File(args[3]), args.length > 4 ? new File(args[4]) : null);
            }
            return;
        }
        String kind = args[0];
        try (Repository repo = new FileRepositoryBuilder().setGitDir(new File(args[1], ".git")).build()) {
            for (int i = 2; i < args.length; i++) {
                try {
                    build(kind, repo, new File(args[i]));
                } catch (Exception e) {
                    System.out.println(args[i] + ": FAILED " + e);
                    e.printStackTrace(System.out);
                }
            }
        }
    }

    static void build(String kind, Repository repo, File historyFile) throws Exception {
        AbstractHistoryInfo info = MAPPER.readValue(historyFile, historyClass(kind));
        History<? extends CodeElement> history = track(kind, repo, info);
        HistoryImpl<? extends CodeElement> impl = (HistoryImpl<? extends CodeElement>) history;

        List<ChangeHistory> detected = new ArrayList<>(OracleTest.processHistory(impl).values());
        detected.sort(Comparator.comparingLong(ChangeHistory::getCommitTime).reversed()
                .thenComparing(ChangeHistory::getChangeType));
        File detectedFile = new File(System.getProperty("ct.out", "target/oracle-detected"),
                kind + "/" + historyFile.getName().replaceAll("\\.json$", "") + ".detected.json");
        detectedFile.getParentFile().mkdirs();
        String startElementName = null;
        if (impl.getGraph() != null) {
            for (CodeElement node : impl.getGraph().getNodeList()) {
                if (node.getVersion().getId().equals(info.getStartCommitId())
                        && ((org.codetracker.api.Graph) impl.getGraph()).successors(node).isEmpty())
                    startElementName = node.getName();
            }
        }
        Map<String, Object> detectedOut = new LinkedHashMap<>();
        detectedOut.put("startElementName", startElementName);
        detectedOut.put("changes", detected);
        MAPPER.writeValue(detectedFile, detectedOut);
        System.out.println(historyFile.getName() + ": " + detected.size() + " changes -> " + detectedFile);
        for (ChangeHistory c : detected)
            System.out.println("  " + c.getCommitId().substring(0, 10) + " " + c.getChangeType() + "  " + c.getElementNameAfter());

        // Commit-log map: every version the tracker may restart from, plus the start commit.
        Map<String, String> versionToPath = new LinkedHashMap<>();
        versionToPath.put(info.getStartCommitId(), info.getFilePath());
        if (impl.getGraph() != null) {
            for (EndpointPair<? extends CodeElement> edge : impl.getGraph().getEdges()) {
                versionToPath.putIfAbsent(edge.source().getVersion().getId(), edge.source().getFilePath());
                versionToPath.putIfAbsent(edge.target().getVersion().getId(), edge.target().getFilePath());
            }
        }
        String repoName = repoName(info.getRepositoryWebURL());
        String className = info.getFilePath().substring(info.getFilePath().lastIndexOf('/') + 1).replaceAll("\\.java$", "");
        File logFile = new File(System.getProperty("user.dir") + "/src/test/resources/" + kind + "/"
                + repoName + "-" + className + "-" + elementName(kind, info) + ".json");
        Map<String, List<String>> logMap = new LinkedHashMap<>();
        if (logFile.exists())
            logMap.putAll(new ObjectMapper().readValue(logFile, GitLog.class).getCommitLogMap());
        Set<String> commits = new LinkedHashSet<>();
        try (Git git = new Git(repo)) {
            for (Map.Entry<String, String> e : versionToPath.entrySet()) {
                if (e.getKey().equals("0")) continue;
                List<String> log = StreamSupport.stream(git.log().add(repo.resolve(e.getKey())).addPath(e.getValue())
                                .setRevFilter(RevFilter.ALL).call().spliterator(), false)
                        .map(c -> c.getId().getName()).collect(Collectors.toList());
                logMap.putIfAbsent(e.getKey(), log);
            }
        }
        for (List<String> log : logMap.values()) commits.addAll(log);
        new ObjectMapper().writeValue(logFile, new GitLog(logMap));
        int written = 0;
        for (String commit : commits)
            if (writeCacheEntry(repo, repoName, commit, new File(CACHE), false)) written++;
        System.out.println("  log keys=" + logMap.size() + ", commits=" + commits.size() + ", new cache entries=" + written);
    }

    /**
     * Writes the validated expected changes into the oracle history JSON: the detected changes, minus the ones the
     * verdict file rejects ({@code "drop": ["<commit prefix>:<change type>", ...]}), plus the ones it adds
     * ({@code "add": [ChangeHistory, ...]}, where parentCommitId and commitTime are filled from git when missing).
     * Also fills the element keys from the tracked start element.
     */
    @SuppressWarnings("unchecked")
    static void finalizeHistory(String kind, Repository repo, File historyFile, File verdictFile) throws Exception {
        ObjectMapper plain = new ObjectMapper();
        File detectedFile = new File(System.getProperty("ct.out", "target/oracle-detected"),
                kind + "/" + historyFile.getName().replaceAll("\\.json$", "") + ".detected.json");
        Map<String, Object> detected = plain.readValue(detectedFile, Map.class);
        List<ChangeHistory> changes = new ArrayList<>();
        for (Object o : (List<Object>) detected.get("changes"))
            changes.add(plain.convertValue(o, ChangeHistory.class));
        if (verdictFile != null) {
            Map<String, Object> verdict = plain.readValue(verdictFile, Map.class);
            for (Object d : (List<Object>) verdict.getOrDefault("drop", Collections.emptyList())) {
                String[] parts = d.toString().split(":", 2);
                boolean removed = changes.removeIf(c -> c.getCommitId().startsWith(parts[0]) && c.getChangeType().equals(parts[1]));
                if (!removed) throw new IllegalStateException("verdict drops a change that was not detected: " + d);
            }
            for (Object a : (List<Object>) verdict.getOrDefault("add", Collections.emptyList())) {
                Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) a);
                m.remove("rationale");
                ChangeHistory c = plain.convertValue(m, ChangeHistory.class);
                try (RevWalk walk = new RevWalk(repo)) {
                    RevCommit rc = walk.parseCommit(repo.resolve(c.getCommitId()));
                    c.setCommitId(rc.getName());
                    if (c.getParentCommitId() == null) c.setParentCommitId(rc.getParentCount() > 0 ? rc.getParent(0).getName() : "0");
                    if (c.getCommitTime() == 0) c.setCommitTime(rc.getCommitTime());
                }
                changes.add(c);
            }
        }
        changes.sort(Comparator.comparingLong(ChangeHistory::getCommitTime).reversed().thenComparing(ChangeHistory::getChangeType));
        Map<String, Object> spec = plain.readValue(historyFile, LinkedHashMap.class);
        String start = (String) detected.get("startElementName");
        if (start != null) {
            String function = start.contains("$") ? start.substring(0, start.indexOf('$')) : start;
            switch (kind) {
                case "block": spec.put("functionKey", function); spec.put("blockKey", start); break;
                case "variable": spec.put("functionKey", function); spec.put("variableKey", start); break;
                case "method": spec.put("functionKey", start); break;
                case "attribute": spec.put("attributeKey", start); break;
            }
        }
        List<Object> expected = new ArrayList<>();
        for (ChangeHistory c : changes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("parentCommitId", c.getParentCommitId());
            m.put("commitId", c.getCommitId());
            m.put("commitTime", c.getCommitTime());
            m.put("changeType", c.getChangeType());
            m.put("elementFileBefore", c.getElementFileBefore());
            m.put("elementNameBefore", c.getElementNameBefore());
            m.put("elementFileAfter", c.getElementFileAfter());
            m.put("elementNameAfter", c.getElementNameAfter());
            if (c.getComment() != null) m.put("comment", c.getComment());
            expected.add(m);
        }
        spec.put("expectedChanges", expected);
        MAPPER.writeValue(historyFile, spec);
        System.out.println(historyFile.getName() + ": " + expected.size() + " expected changes written");
    }

    /**
     * Runs the {@code *WithLocalFiles} tracker exactly as the oracle tests do and returns
     * {@code "<file name>, TP, FP, FN"}, the line format of {@code <oracle>-expected.txt}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String score(String kind, File historyFile) throws Exception {
        AbstractHistoryInfo info = MAPPER.readValue(historyFile, historyClass(kind));
        History<? extends CodeElement> history;
        switch (kind) {
            case "block": history = BlockOracleTest.blockTracker((BlockHistoryInfo) info, info.getRepositoryWebURL()); break;
            case "method": history = MethodOracleTest.methodTracker((MethodHistoryInfo) info, info.getRepositoryWebURL()); break;
            case "variable": history = VariableOracleTest.variableTracker((VariableHistoryInfo) info, info.getRepositoryWebURL()); break;
            case "attribute": history = AttributeOracleTest.attributeTracker((AttributeHistoryInfo) info, info.getRepositoryWebURL()); break;
            default: throw new IllegalArgumentException(kind);
        }
        Map<String, ChangeHistory> expected = OracleTest.oracle(info.getExpectedChanges());
        Map<String, ChangeHistory> detected = OracleTest.processHistory((HistoryImpl) history);
        int tp = 0;
        for (String key : expected.keySet()) if (detected.containsKey(key)) tp++;
        int fp = detected.size() - tp, fn = expected.size() - tp;
        return historyFile.getName() + ", " + tp + ", " + fp + ", " + fn;
    }

    private static final java.util.regex.Pattern RANGE = java.util.regex.Pattern.compile("\\((\\d+)(?:-(\\d+))?\\)$");

    /**
     * Validation aid. For every detected change it prints the element text that differs between the parent and the
     * commit (from the line ranges in the element names). Then it flags every other commit in the file logs whose
     * changed lines contain a line of the element (any tracked version): candidates for missed changes, to inspect
     * by hand. Nothing here decides; it only collects evidence.
     */
    @SuppressWarnings("unchecked")
    static void check(String kind, Repository repo, File historyFile) throws Exception {
        ObjectMapper plain = new ObjectMapper();
        File detectedFile = new File(System.getProperty("ct.out", "target/oracle-detected"),
                kind + "/" + historyFile.getName().replaceAll("\\.json$", "") + ".detected.json");
        Map<String, Object> detected = plain.readValue(detectedFile, Map.class);
        List<ChangeHistory> changes = new ArrayList<>();
        for (Object o : (List<Object>) detected.get("changes"))
            changes.add(plain.convertValue(o, ChangeHistory.class));
        System.out.println("##### " + historyFile.getName());
        Set<String> elementLines = new HashSet<>();
        Set<String> reported = new HashSet<>();
        Set<String> paths = new LinkedHashSet<>();
        for (ChangeHistory c : changes) {
            reported.add(c.getCommitId());
            paths.add(c.getElementFileAfter());
            paths.add(c.getElementFileBefore());
            List<String> before = elementText(repo, c.getParentCommitId(), c.getElementFileBefore(), c.getElementNameBefore());
            List<String> after = elementText(repo, c.getCommitId(), c.getElementFileAfter(), c.getElementNameAfter());
            for (List<String> t : Arrays.asList(before, after))
                for (String l : t) if (meaningful(l)) elementLines.add(norm(l));
            System.out.println("== " + c.getCommitId().substring(0, 10) + " " + c.getChangeType() + "  "
                    + range(c.getElementNameBefore()) + " -> " + range(c.getElementNameAfter()));
            if (c.getChangeType().equals("introduced")) {
                System.out.println("   first line: " + (after.isEmpty() ? "?" : after.get(0).strip()));
                continue;
            }
            List<String> nb = before.stream().map(LocalOracleBuilder::norm).collect(Collectors.toList());
            List<String> na = after.stream().map(LocalOracleBuilder::norm).collect(Collectors.toList());
            int shown = 0;
            for (String l : before) if (!na.contains(norm(l)) && shown++ < 6) System.out.println("   - " + l.strip());
            for (String l : after) if (!nb.contains(norm(l)) && shown++ < 12) System.out.println("   + " + l.strip());
            if (shown == 0) System.out.println("   (same text modulo whitespace)");
        }
        String start = ((AbstractHistoryInfo) plain.readValue(historyFile, historyClass(kind))).getStartCommitId();
        paths.remove(null);
        try (Git git = new Git(repo)) {
            for (String path : paths) {
                for (RevCommit rc : git.log().add(repo.resolve(start)).addPath(path).call()) {
                    if (reported.contains(rc.getName()) || rc.getParentCount() == 0) continue;
                    List<String> hits = new ArrayList<>();
                    for (String l : changedLines(repo, rc, path))
                        if (meaningful(l) && elementLines.contains(norm(l)) && hits.size() < 4) hits.add(l.strip());
                    if (!hits.isEmpty())
                        System.out.println("?? " + rc.getName().substring(0, 10) + " (" + path.substring(path.lastIndexOf('/') + 1) + ") touches element lines: " + hits);
                }
            }
        }
    }

    private static String range(String name) {
        if (name == null) return "?";
        java.util.regex.Matcher m = RANGE.matcher(name);
        return m.find() ? m.group(0) : "?";
    }

    private static boolean meaningful(String l) {
        String n = norm(l);
        return n.length() > 3 && !n.equals("}else{") && !n.equals("return;") && !n.equals("break;") && !n.startsWith("//");
    }

    private static String norm(String l) {
        return l.replaceFirst("^\\s*//+", "").replaceAll("\\s+", "");
    }

    static List<String> elementText(Repository repo, String commit, String path, String name) throws Exception {
        if (commit == null || path == null || name == null || commit.equals("0")) return Collections.emptyList();
        java.util.regex.Matcher m = RANGE.matcher(name);
        if (!m.find()) return Collections.emptyList();
        int s = Integer.parseInt(m.group(1)), e = m.group(2) != null ? Integer.parseInt(m.group(2)) : s;
        String content = fileAt(repo, commit, path);
        if (content == null) return Collections.emptyList();
        String[] lines = content.split("\r?\n", -1);
        return Arrays.asList(lines).subList(Math.min(s - 1, lines.length), Math.min(e, lines.length));
    }

    static String fileAt(Repository repo, String commit, String path) throws Exception {
        try (RevWalk walk = new RevWalk(repo); TreeWalk tw = TreeWalk.forPath(repo, path, walk.parseCommit(repo.resolve(commit)).getTree())) {
            if (tw == null) return null;
            return new String(repo.open(tw.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<String> changedLines(Repository repo, RevCommit rc, String path) throws Exception {
        String before = fileAt(repo, rc.getParent(0).getName(), path);
        String after = fileAt(repo, rc.getName(), path);
        List<String> a = before == null ? Collections.emptyList() : Arrays.asList(before.split("\r?\n"));
        List<String> b = after == null ? Collections.emptyList() : Arrays.asList(after.split("\r?\n"));
        org.eclipse.jgit.diff.RawText ra = new org.eclipse.jgit.diff.RawText(String.join("\n", a).getBytes(StandardCharsets.UTF_8));
        org.eclipse.jgit.diff.RawText rb = new org.eclipse.jgit.diff.RawText(String.join("\n", b).getBytes(StandardCharsets.UTF_8));
        List<String> out = new ArrayList<>();
        for (org.eclipse.jgit.diff.Edit ed : org.eclipse.jgit.diff.DiffAlgorithm.getAlgorithm(org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
                .diff(org.eclipse.jgit.diff.RawTextComparator.DEFAULT, ra, rb)) {
            for (int i = ed.getBeginA(); i < ed.getEndA(); i++) out.add(a.get(i));
            for (int i = ed.getBeginB(); i < ed.getEndB(); i++) out.add(b.get(i));
        }
        return out;
    }

    static History<? extends CodeElement> track(String kind, Repository repo, AbstractHistoryInfo info) throws Exception {
        switch (kind) {
            case "block": {
                BlockHistoryInfo h = (BlockHistoryInfo) info;
                return CodeTracker.blockTracker().repository(repo).filePath(h.getFilePath()).startCommitId(h.getStartCommitId())
                        .methodName(h.getFunctionName()).methodDeclarationLineNumber(h.getFunctionStartLine())
                        .codeElementType(LocationInfo.CodeElementType.valueOf(h.getBlockType()))
                        .blockStartLineNumber(h.getBlockStartLine()).blockEndLineNumber(h.getBlockEndLine())
                        .build().track();
            }
            case "method": {
                MethodHistoryInfo h = (MethodHistoryInfo) info;
                return CodeTracker.methodTracker().repository(repo).filePath(h.getFilePath()).startCommitId(h.getStartCommitId())
                        .methodName(h.getFunctionName()).methodDeclarationLineNumber(h.getFunctionStartLine())
                        .build().track();
            }
            case "variable": {
                VariableHistoryInfo h = (VariableHistoryInfo) info;
                return CodeTracker.variableTracker().repository(repo).filePath(h.getFilePath()).startCommitId(h.getStartCommitId())
                        .methodName(h.getFunctionName()).methodDeclarationLineNumber(h.getFunctionStartLine())
                        .variableName(h.getVariableName()).variableDeclarationLineNumber(h.getVariableStartLine())
                        .build().track();
            }
            case "attribute": {
                AttributeHistoryInfo h = (AttributeHistoryInfo) info;
                return CodeTracker.attributeTracker().repository(repo).filePath(h.getFilePath()).startCommitId(h.getStartCommitId())
                        .attributeName(h.getAttributeName()).attributeDeclarationLineNumber(h.getAttributeDeclarationLine())
                        .build().track();
            }
            default:
                throw new IllegalArgumentException(kind);
        }
    }

    static Class<? extends AbstractHistoryInfo> historyClass(String kind) {
        switch (kind) {
            case "block": return BlockHistoryInfo.class;
            case "method": return MethodHistoryInfo.class;
            case "variable": return VariableHistoryInfo.class;
            case "attribute": return AttributeHistoryInfo.class;
            default: throw new IllegalArgumentException(kind);
        }
    }

    static String elementName(String kind, AbstractHistoryInfo info) {
        if (kind.equals("attribute")) return ((AttributeHistoryInfo) info).getAttributeName();
        if (kind.equals("block")) return ((BlockHistoryInfo) info).getFunctionName();
        if (kind.equals("variable")) return ((VariableHistoryInfo) info).getFunctionName();
        return ((MethodHistoryInfo) info).getFunctionName();
    }

    static String repoName(String cloneURL) {
        return cloneURL.substring(cloneURL.lastIndexOf('/') + 1, cloneURL.lastIndexOf('.'));
    }

    /** Writes {@code <repo>-<commit>.json} and the changed .java files before/after; returns false if already cached. */
    static boolean writeCacheEntry(Repository repo, String repoName, String commitId, File root, boolean overwrite) throws Exception {
        File json = new File(root, repoName + "-" + commitId + ".json");
        if (json.exists() && !overwrite) return false;
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(repo.resolve(commitId));
            if (commit.getParentCount() == 0) return false;
            RevCommit parent = walk.parseCommit(commit.getParent(0));
            List<DiffEntry> entries;
            try (TreeWalk tw = new TreeWalk(repo)) {
                tw.setRecursive(true);
                tw.addTree(parent.getTree());
                tw.addTree(commit.getTree());
                RenameDetector rd = new RenameDetector(repo);
                rd.addAll(DiffEntry.scan(tw));
                entries = rd.compute();
            }
            List<String> filesBefore = new ArrayList<>(), filesCurrent = new ArrayList<>();
            Map<String, String> renamed = new LinkedHashMap<>();
            for (DiffEntry e : entries) {
                switch (e.getChangeType()) {
                    case MODIFY:
                        if (!e.getNewPath().endsWith(".java")) break;
                        filesBefore.add(e.getOldPath());
                        filesCurrent.add(e.getNewPath());
                        save(repo, e.getOldId().toObjectId(), root, repoName + "-" + parent.getName(), e.getOldPath());
                        save(repo, e.getNewId().toObjectId(), root, repoName + "-" + commitId, e.getNewPath());
                        break;
                    case ADD:
                    case COPY:
                        if (!e.getNewPath().endsWith(".java")) break;
                        filesCurrent.add(e.getNewPath());
                        save(repo, e.getNewId().toObjectId(), root, repoName + "-" + commitId, e.getNewPath());
                        break;
                    case DELETE:
                        if (!e.getOldPath().endsWith(".java")) break;
                        filesBefore.add(e.getOldPath());
                        save(repo, e.getOldId().toObjectId(), root, repoName + "-" + parent.getName(), e.getOldPath());
                        break;
                    case RENAME:
                        if (!e.getOldPath().endsWith(".java") && !e.getNewPath().endsWith(".java")) break;
                        filesBefore.add(e.getOldPath());
                        filesCurrent.add(e.getNewPath());
                        renamed.put(e.getOldPath(), e.getNewPath());
                        save(repo, e.getOldId().toObjectId(), root, repoName + "-" + parent.getName(), e.getOldPath());
                        save(repo, e.getNewId().toObjectId(), root, repoName + "-" + commitId, e.getNewPath());
                        break;
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("parentCommitId", parent.getName());
            out.put("currentCommitId", commit.getName());
            out.put("filesBefore", filesBefore);
            out.put("filesCurrent", filesCurrent);
            out.put("renamedFilesHint", renamed);
            out.put("repositoryDirectoriesBefore", directories(repo, parent));
            out.put("repositoryDirectoriesCurrent", directories(repo, commit));
            out.put("commitTime", commit.getCommitTime());
            out.put("authoredTime", commit.getAuthorIdent().getWhen().getTime() / 1000);
            out.put("commitAuthorName", commit.getAuthorIdent().getName());
            json.getParentFile().mkdirs();
            new ObjectMapper().writeValue(json, out);
            return true;
        }
    }

    private static void save(Repository repo, ObjectId blob, File root, String folder, String path) throws Exception {
        File f = new File(new File(root, folder), path);
        if (f.exists()) return;
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), repo.open(blob).getBytes());
    }

    /** Every directory that (transitively) contains a .java file. */
    static Set<String> directories(Repository repo, RevCommit commit) throws Exception {
        Set<String> dirs = new LinkedHashSet<>();
        try (TreeWalk tw = new TreeWalk(repo)) {
            tw.setRecursive(true);
            tw.addTree(commit.getTree());
            while (tw.next()) {
                String p = tw.getPathString();
                if (!p.endsWith(".java")) continue;
                int idx = p.lastIndexOf('/');
                while (idx > 0) {
                    p = p.substring(0, idx);
                    if (!dirs.add(p)) break;
                    idx = p.lastIndexOf('/');
                }
            }
        }
        return dirs;
    }

    static void verify(String clone, String repoName, String commitId) throws Exception {
        File tmp = Files.createTempDirectory("cache-verify").toFile();
        try (Repository repo = new FileRepositoryBuilder().setGitDir(new File(clone, ".git")).build()) {
            writeCacheEntry(repo, repoName, commitId, tmp, true);
        }
        ObjectMapper m = new ObjectMapper();
        Map<?, ?> mine = m.readValue(new File(tmp, repoName + "-" + commitId + ".json"), Map.class);
        Map<?, ?> cached = m.readValue(new File(CACHE, repoName + "-" + commitId + ".json"), Map.class);
        for (Object key : cached.keySet()) {
            Object a = cached.get(key), b = mine.get(key);
            boolean same = a instanceof Collection && b instanceof Collection
                    ? new HashSet<>((Collection<?>) a).equals(new HashSet<>((Collection<?>) b))
                    : Objects.equals(String.valueOf(a), String.valueOf(b));
            System.out.println((same ? "same " : "DIFF ") + key + (same ? "" : "\n   cached=" + abbreviate(a) + "\n   mine  =" + abbreviate(b)));
            if (!same && a instanceof Collection && b instanceof Collection) {
                Set<Object> onlyCached = new TreeSet<>((Collection<?>) a); onlyCached.removeAll((Collection<?>) b);
                Set<Object> onlyMine = new TreeSet<>((Collection<?>) b); onlyMine.removeAll((Collection<?>) a);
                System.out.println("   only cached: " + abbreviate(onlyCached) + "\n   only mine: " + abbreviate(onlyMine));
            }
        }
        for (String side : new String[]{"filesCurrent"}) {
            for (Object f : (List<?>) cached.get(side)) {
                String p = f.toString();
                String a = new String(Files.readAllBytes(new File(CACHE, repoName + "-" + commitId + "/" + p).toPath()), StandardCharsets.UTF_8);
                String b = new String(Files.readAllBytes(new File(tmp, repoName + "-" + commitId + "/" + p).toPath()), StandardCharsets.UTF_8);
                System.out.println((a.equals(b) ? "same content " : "DIFF content ") + p);
            }
        }
    }

    private static String abbreviate(Object o) {
        String s = String.valueOf(o);
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
