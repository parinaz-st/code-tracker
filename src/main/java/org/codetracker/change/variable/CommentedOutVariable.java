package org.codetracker.change.variable;

public class CommentedOutVariable extends VariableChange {
    public CommentedOutVariable() {
        super(Type.COMMENTED_OUT_VARIABLE);
    }

    @Override
    public String toString() {
        return "Variable Change, Commented-out Variable";
    }

}
