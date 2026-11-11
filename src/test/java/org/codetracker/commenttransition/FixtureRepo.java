package org.codetracker.commenttransition;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Builds a throwaway local Git repository with one Java file whose content is replaced
 * commit by commit. Each commit gets a label (A, B, C, ...) so tests can refer to commits
 * by label instead of by SHA.
 */
final class FixtureRepo implements AutoCloseable {
    static final String FILE_PATH = "src/main/java/demo/Sample.java";
    private static final long BASE_EPOCH_MILLIS = 1577836800000L; // 2020-01-01T00:00:00Z

    private final Path dir;
    private final Git git;
    private final Map<String, String> shaByLabel = new LinkedHashMap<>();
    private final Map<String, String> labelBySha = new LinkedHashMap<>();
    private String lastContent;
    private int commitIndex;

    private FixtureRepo(Path dir, Git git) {
        this.dir = dir;
        this.git = git;
    }

    static FixtureRepo create(String name) throws Exception {
        Path dir = Files.createTempDirectory("ct-fixture-" + name + "-");
        Git git = Git.init().setDirectory(dir.toFile()).setInitialBranch("master").call();
        git.getRepository().getConfig().setBoolean("core", null, "autocrlf", false);
        git.getRepository().getConfig().save();
        return new FixtureRepo(dir, git);
    }

    /** Writes the file with the given content and commits it under the given label. */
    FixtureRepo commit(String label, String content) throws Exception {
        File file = dir.resolve(FILE_PATH).toFile();
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        git.add().addFilepattern(FILE_PATH).call();
        PersonIdent ident = new PersonIdent("fixture", "fixture@example.com",
                new Date(BASE_EPOCH_MILLIS + commitIndex++ * 86_400_000L), TimeZone.getTimeZone("UTC"));
        RevCommit commit = git.commit().setMessage(label).setAuthor(ident).setCommitter(ident).call();
        shaByLabel.put(label, commit.getName());
        labelBySha.put(commit.getName(), label);
        lastContent = content;
        return this;
    }

    Repository repository() {
        return git.getRepository();
    }

    String sha(String label) {
        return shaByLabel.get(label);
    }

    String label(String sha) {
        return labelBySha.getOrDefault(sha, sha == null ? "null" : sha.substring(0, Math.min(7, sha.length())));
    }

    String headSha() {
        return sha(lastLabel());
    }

    String lastLabel() {
        String last = null;
        for (String label : shaByLabel.keySet())
            last = label;
        return last;
    }

    String lastContent() {
        return lastContent;
    }

    /** 1-based line number of the first line at or after {@code fromLine} that contains {@code text}. */
    static int lineOf(String content, String text, int fromLine) {
        String[] lines = content.split("\n", -1);
        for (int i = Math.max(0, fromLine - 1); i < lines.length; i++) {
            if (lines[i].contains(text))
                return i + 1;
        }
        throw new IllegalArgumentException("Text not found: " + text);
    }

    /** 1-based line number of the closing brace that matches the first '{' on {@code openLine}. */
    static int closingLineOf(String content, int openLine) {
        String[] lines = content.split("\n", -1);
        int depth = 0;
        boolean opened = false;
        for (int i = openLine - 1; i < lines.length; i++) {
            for (char c : lines[i].toCharArray()) {
                if (c == '{') {
                    depth++;
                    opened = true;
                } else if (c == '}') {
                    depth--;
                }
            }
            if (opened && depth == 0)
                return i + 1;
        }
        throw new IllegalArgumentException("No matching brace for line " + openLine);
    }

    @Override
    public void close() throws IOException {
        git.close();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
