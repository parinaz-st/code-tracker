package org.codetracker;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.codetracker.api.Version;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import gr.uom.java.xmi.UMLModel;

/**
 * Reads one file at a given commit, independently of where the commits come from (a Git repository,
 * or the cached commits used by the {@code *WithLocalFiles} trackers). Comment-transition detection
 * uses it to load older versions and to build "virtual" versions with commented-out code uncommented
 * (ADR 0009).
 */
public interface CommitFileSource {

    /** One file at one commit. */
    final class FileSnapshot {
        private final String filePath;
        private final String content;
        private final Set<String> repositoryDirectories;

        public FileSnapshot(String filePath, String content, Set<String> repositoryDirectories) {
            this.filePath = filePath;
            this.content = content;
            this.repositoryDirectories = repositoryDirectories;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getContent() {
            return content;
        }

        /** Builds a partial model of this file, optionally with different content. */
        public UMLModel toModel(String replacementContent) throws Exception {
            UMLModel model = GitHistoryRefactoringMinerImpl.createModel(
                    Collections.singletonMap(filePath, replacementContent), new LinkedHashSet<>(repositoryDirectories));
            model.setPartial(true);
            return model;
        }

        public UMLModel toModel() throws Exception {
            return toModel(content);
        }
    }

    Version version(String commitId) throws Exception;

    /** The file as it is at {@code commitId}, or {@code null} if it does not exist or is not available. */
    FileSnapshot fileAt(String commitId, String filePath) throws Exception;

    /** The file as it is in the (first) parent of {@code commitId}, or {@code null}. */
    FileSnapshot fileBefore(String commitId, String filePath) throws Exception;
}
