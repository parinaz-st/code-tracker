package org.codetracker.util;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;

public class GitRepository implements IRepository {
    private final Repository repository;

    public GitRepository(Repository repository) {
        this.repository = repository;
    }

    protected RevCommit getRevCommit(String commitId) {
        RevCommit revCommit = null;
        try {
            revCommit = repository.parseCommit(ObjectId.fromString(commitId));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return revCommit;
    }

    @Override
    public String getParentId(String commitId) {
        RevCommit revCommit = getRevCommit(commitId);
        if (revCommit.getParentCount() > 0)
            return revCommit.getParent(0).getId().getName();
        return "0";
    }

    @Override
    public long getCommitTime(String commitId) {
        if ("0".equals(commitId))
            return 0;
        return getRevCommit(commitId).getCommitTime();
    }
}
