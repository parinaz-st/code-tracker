package org.codetracker.change.block;

public class CommentedOutBlock extends BlockChange {
    public CommentedOutBlock() {
        super(Type.COMMENTED_OUT_BLOCK);
    }

    @Override
    public String toString() {
        return "Block Change, Commented-out Block";
    }

}
