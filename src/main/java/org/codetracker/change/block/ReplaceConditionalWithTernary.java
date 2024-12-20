package org.codetracker.change.block;

import org.refactoringminer.api.Refactoring;

public class ReplaceConditionalWithTernary extends BlockChange {
    private final Refactoring refactoring;

    public ReplaceConditionalWithTernary(Refactoring refactoring) {
        super(Type.REPLACE_CONDITIONAL_WITH_TERNARY);
        this.refactoring = refactoring;
    }

    public Refactoring getReplaceConditionalWithTernaryRefactoring() {
        return refactoring;
    }

    @Override
    public String toString() {
        return refactoring.toString();
    }
}
