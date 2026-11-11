package org.codetracker.change.method;

public class UncommentedMethod extends MethodChange {
    public UncommentedMethod() {
        super(Type.UNCOMMENTED_METHOD);
    }

    @Override
    public String toString() {
        return "Method Change, Uncommented Method";
    }

}
