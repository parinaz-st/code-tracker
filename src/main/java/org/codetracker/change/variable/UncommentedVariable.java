package org.codetracker.change.variable;

public class UncommentedVariable extends VariableChange {
    public UncommentedVariable() {
        super(Type.UNCOMMENTED_VARIABLE);
    }

    @Override
    public String toString() {
        return "Variable Change, Uncommented Variable";
    }

}
