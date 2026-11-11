package org.codetracker.change.attribute;

public class CommentedOutAttribute extends AttributeChange {
    public CommentedOutAttribute() {
        super(Type.COMMENTED_OUT_ATTRIBUTE);
    }

    @Override
    public String toString() {
        return "Attribute Change, Commented-out Attribute";
    }

}
