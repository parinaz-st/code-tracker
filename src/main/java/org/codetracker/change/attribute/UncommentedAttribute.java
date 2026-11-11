package org.codetracker.change.attribute;

public class UncommentedAttribute extends AttributeChange {
    public UncommentedAttribute() {
        super(Type.UNCOMMENTED_ATTRIBUTE);
    }

    @Override
    public String toString() {
        return "Attribute Change, Uncommented Attribute";
    }

}
