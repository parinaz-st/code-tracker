package org.codetracker.change.method;

public class CommentedOutMethod extends MethodChange {
    public CommentedOutMethod() {
        super(Type.COMMENTED_OUT_METHOD);
    }

    @Override
    public String toString() {
        return "Method Change, Commented-out Method";
    }

}
