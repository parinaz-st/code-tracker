package org.codetracker.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.codetracker.change.Change;
import org.codetracker.element.Block;

import gr.uom.java.xmi.LocationInfo;
import gr.uom.java.xmi.UMLComment;
import gr.uom.java.xmi.VariableDeclarationContainer;
import gr.uom.java.xmi.decomposition.AbstractCodeFragment;
import gr.uom.java.xmi.decomposition.AbstractCodeMapping;
import gr.uom.java.xmi.decomposition.CompositeStatementObject;
import gr.uom.java.xmi.decomposition.CompositeStatementObjectMapping;
import gr.uom.java.xmi.decomposition.LeafMapping;
import gr.uom.java.xmi.decomposition.StatementObject;
import gr.uom.java.xmi.decomposition.UMLOperationBodyMapper;

/**
 * Detects code that was commented out or uncommented between two versions of a method body.
 * <p>
 * Code and comment text are compared by {@link #codeHash(String)}, which ignores all whitespace
 * (ADR 0006), so formatting differences between the commented and the active form do not matter.
 */
public class CommentTransitionAnalyzer {

    /** A line that starts a block statement. The word boundary keeps {@code double}, {@code format(}, {@code ifPresent(} out. */
    private static final Pattern BLOCK_KEYWORD = Pattern.compile(
            "^(if|else|for|while|do|try|catch|finally|switch|synchronized|static)\\b");

    /**
     * Lines that are too common to count as evidence of uncommented code on their own
     * (whitespace already removed). They still count when checking the body of an uncommented block.
     */
    private static final Pattern TRIVIAL_CODE_LINE = Pattern.compile(
            "^(\\{|\\}|\\};|\\)|\\);|return;|break;|continue;|returnnull;|returntrue;|returnfalse;|return0;|"
                    + "\\}?else\\{?|try\\{|\\}?finally\\{|default:)$");

    private CommentTransitionAnalyzer() {
    }

    /**
     * Returns the comment transitions that explain part of a matched block's body change:
     * {@link Change.Type#UNCOMMENTED_STATEMENT} and/or {@link Change.Type#COMMENTED_OUT_STATEMENT}.
     */
    public static Set<Change.Type> detectCommentTransitionsInsideMatchedBlock(UMLOperationBodyMapper umlOperationBodyMapper, Block blockBefore, Block blockAfter) {
        Set<Change.Type> changeTypes = new LinkedHashSet<>();
        List<UMLComment> deletedComments = umlOperationBodyMapper.getCommentListDiff().getDeletedComments();
        List<UMLComment> addedComments = umlOperationBodyMapper.getCommentListDiff().getAddedComments();
        if (!deletedComments.isEmpty()) {
            List<UMLComment> inScopeDeletedComments = removeOutOfScopeComments(deletedComments, blockBefore);
            Map<String, UMLComment> deletedCommentsHash = generateCommentTextHashMap(inScopeDeletedComments, false);
            if (isUncommentedCodeDetected(deletedCommentsHash, umlOperationBodyMapper, blockAfter)) {
                changeTypes.add(Change.Type.UNCOMMENTED_STATEMENT);
            }
        }
        if (!addedComments.isEmpty()) {
            List<UMLComment> inScopeAddedComments = removeOutOfScopeComments(addedComments, blockAfter);
            Map<String, UMLComment> addedCommentsHash = generateCommentTextHashMap(inScopeAddedComments, false);
            if (isCommentedOutCodeDetected(addedCommentsHash, umlOperationBodyMapper, blockBefore)) {
                changeTypes.add(Change.Type.COMMENTED_OUT_STATEMENT);
            }
        }
        return changeTypes;
    }

    private static List<UMLComment> removeOutOfScopeComments(List<UMLComment> commentList, Block block) {
        List<UMLComment> inScopeComments = new ArrayList<>();
        for (UMLComment comment : commentList) {
            if (comment.getLocationInfo().getStartLine() > block.getLocation().getStartLine() &&
                    comment.getLocationInfo().getEndLine() < block.getLocation().getEndLine()) {
                inScopeComments.add(comment);
            }
        }
        return inScopeComments;
    }

    private static boolean isUncommentedCodeDetected(Map<String, UMLComment> deletedCommentsHash, UMLOperationBodyMapper umlOperationBodyMapper, Block blockAfter) {
        if (deletedCommentsHash.isEmpty())
            return false;
        // unmatched leaf statements of the block after, found in deleted comments
        for (AbstractCodeFragment abstractCodeFragment : umlOperationBodyMapper.getNonMappedLeavesT2()) {
            if (blockAfter.getComposite().getLeaves().contains(abstractCodeFragment)) {
                if (deletedCommentsHash.containsKey(codeHash(abstractCodeFragment.getString())))
                    return true;
            }
        }
        // unmatched composite statements of the block after, found in deleted comments
        for (CompositeStatementObject compositeStatementObject : umlOperationBodyMapper.getNonMappedInnerNodesT2()) {
            if (((CompositeStatementObject) blockAfter.getComposite()).contains(compositeStatementObject)) {
                if (deletedCommentsHash.containsKey(codeHash(compositeStatementObject.getActualSignature())))
                    return true;
            }
        }
        // RefactoringMiner may map textually similar but different statements when one side is actually
        // an uncommented statement, so leaf mappings with replacements are checked too.
        for (AbstractCodeMapping mapping : umlOperationBodyMapper.getMappings()) {
            if (!(mapping instanceof LeafMapping) || mapping.getReplacements().isEmpty())
                continue;
            if (!isMappingBelongToTrackedBlock(mapping, blockAfter, true))
                continue;
            if (deletedCommentsHash.containsKey(codeHash(mapping.getFragment2().getString())))
                return true;
        }
        return false;
    }

    private static boolean isCommentedOutCodeDetected(Map<String, UMLComment> addedCommentsHash, UMLOperationBodyMapper umlOperationBodyMapper, Block blockBefore) {
        if (addedCommentsHash.isEmpty())
            return false;
        // unmatched leaf statements of the block before, found in added comments
        for (AbstractCodeFragment abstractCodeFragment : umlOperationBodyMapper.getNonMappedLeavesT1()) {
            if (blockBefore.getComposite().getLeaves().contains(abstractCodeFragment)) {
                if (addedCommentsHash.containsKey(codeHash(abstractCodeFragment.getString())))
                    return true;
            }
        }
        // unmatched composite statements of the block before, found in added comments
        for (CompositeStatementObject compositeStatementObject : umlOperationBodyMapper.getNonMappedInnerNodesT1()) {
            if (((CompositeStatementObject) blockBefore.getComposite()).contains(compositeStatementObject)) {
                if (!isBracketAndBelongsToStandAloneBlock(compositeStatementObject))
                    continue;
                if (addedCommentsHash.containsKey(codeHash(compositeStatementObject.getActualSignature())))
                    return true;
            }
        }
        // see isUncommentedCodeDetected
        for (AbstractCodeMapping mapping : umlOperationBodyMapper.getMappings()) {
            if (!(mapping instanceof LeafMapping) || mapping.getReplacements().isEmpty())
                continue;
            if (!isMappingBelongToTrackedBlock(mapping, blockBefore, false))
                continue;
            if (addedCommentsHash.containsKey(codeHash(mapping.getFragment1().getString())))
                return true;
        }
        return false;
    }

    /**
     * Checks that every statement inside an uncommented block is either mapped or present in the
     * deleted comments, i.e. the block body did not change while it was uncommented.
     */
    public static boolean isBlockBodyUnchangedDuringUncomment(UMLOperationBodyMapper umlOperationBodyMapper, CompositeStatementObject uncommentedBlock, Map<String, UMLComment> deletedComments, Block blockAfter) {
        Set<String> mappedFragments = new HashSet<>();
        for (AbstractCodeMapping mapping : umlOperationBodyMapper.getMappings()) {
            if (!isMappingBelongToTrackedBlock(mapping, blockAfter, true))
                continue;
            if (mapping instanceof CompositeStatementObjectMapping) {
                CompositeStatementObject composite = (CompositeStatementObject) mapping.getFragment2();
                if (!isBracketAndBelongsToStandAloneBlock(composite))
                    continue;
                mappedFragments.add(codeHash(composite.getActualSignature()));
            }
            else if (mapping instanceof LeafMapping && mapping.getFragment2() instanceof StatementObject) {
                mappedFragments.add(codeHash(mapping.getFragment2().getString()));
            }
        }
        for (CompositeStatementObject inner : uncommentedBlock.getInnerNodes()) {
            if (inner.getActualSignature().equals(uncommentedBlock.getActualSignature()))
                continue;
            if (!isBracketAndBelongsToStandAloneBlock(inner))
                continue;
            if (!existsAsMappedOrComment(codeHash(inner.getActualSignature()), mappedFragments, deletedComments))
                return false;
        }
        for (AbstractCodeFragment leaf : uncommentedBlock.getLeaves()) {
            if (!existsAsMappedOrComment(codeHash(((StatementObject) leaf).getActualSignature()), mappedFragments, deletedComments))
                return false;
        }
        return true;
    }

    /**
     * Finds the active version of {@code target} in {@code operation}: a composite statement of the same
     * kind with the same signature (whitespace ignored). A candidate with an identical body is preferred;
     * otherwise the first signature match is returned. Returns {@code null} if there is none (ADR 0008).
     */
    public static CompositeStatementObject findActiveBlock(VariableDeclarationContainer operation, CompositeStatementObject target) {
        if (operation.getBody() == null)
            return null;
        String signatureHash = codeHash(target.getActualSignature());
        String bodyHash = bodyHash(target);
        CompositeStatementObject signatureMatch = null;
        for (CompositeStatementObject candidate : operation.getBody().getCompositeStatement().getInnerNodes()) {
            if (candidate.getLocationInfo().getCodeElementType() != target.getLocationInfo().getCodeElementType())
                continue;
            if (!codeHash(candidate.getActualSignature()).equals(signatureHash))
                continue;
            if (bodyHash(candidate).equals(bodyHash))
                return candidate;
            if (signatureMatch == null)
                signatureMatch = candidate;
        }
        return signatureMatch;
    }

    /** Whether the comments of {@code operation} contain the signature of {@code target} as commented-out code. */
    public static boolean containsCommentedBlock(VariableDeclarationContainer operation, CompositeStatementObject target) {
        return generateUncommentedBlockSignatureHashMap(operation.getComments()).containsKey(codeHash(target.getActualSignature()));
    }

    private static String bodyHash(CompositeStatementObject composite) {
        return codeHash(String.join("", composite.stringRepresentation()));
    }

    public static boolean isBracketAndBelongsToStandAloneBlock(CompositeStatementObject compositeStatementObject) { // heuristic
        return compositeStatementObject.getLocationInfo().getCodeElementType() != LocationInfo.CodeElementType.BLOCK
                || compositeStatementObject.getParent().getActualSignature().equals("{");
    }

    private static boolean isMappingBelongToTrackedBlock(AbstractCodeMapping mapping, Block block, boolean isAfter) {
        if (mapping instanceof CompositeStatementObjectMapping) {
            CompositeStatementObject composite = (CompositeStatementObject) mapping.getFragment2();
            return ((CompositeStatementObject) block.getComposite()).contains(composite);
        }
        AbstractCodeFragment fragment = isAfter ? mapping.getFragment2() : mapping.getFragment1();
        if (mapping instanceof LeafMapping && fragment instanceof StatementObject)
            return block.getComposite().getLeaves().contains(fragment);
        return true;
    }

    private static boolean existsAsMappedOrComment(String hash, Set<String> mappedFragments, Map<String, UMLComment> commentsHash) {
        return mappedFragments.contains(hash) || commentsHash.containsKey(hash);
    }

    // ------------------------------------------------------------------ text normalization

    /** Removes all whitespace, so {@code if(x>0){} and {@code if (x > 0) {} compare equal (ADR 0006). */
    public static String normalizeCode(String code) {
        return code.replaceAll("\\s+", "");
    }

    /** SHA-512 of {@link #normalizeCode(String)}; the key used for every code/comment comparison. */
    public static String codeHash(String code) {
        return Util.getSHA512(normalizeCode(code));
    }

    public static String normalizeCommentText(String commentText) {
        commentText = commentText.trim();
        if (commentText.startsWith("//")) {
            commentText = commentText.substring(2);
        }
        if (commentText.startsWith("/*")) {
            commentText = commentText.substring(2);
        }
        if (commentText.endsWith("*/")) {
            commentText = commentText.substring(0, commentText.length() - 2);
        }
        commentText = commentText.trim();
        if (commentText.startsWith("*")) {
            commentText = commentText.substring(1);
        }
        return commentText.trim();
    }

    /**
     * Maps {@link #codeHash(String)} of every comment line (and of the whole text of each block comment)
     * to its comment.
     *
     * @param includeTrivialLines whether lines such as {@code return;} or {@code }} are included; they are
     *                            too common to be evidence on their own (ADR 0006)
     */
    public static Map<String, UMLComment> generateCommentTextHashMap(List<UMLComment> commentList, boolean includeTrivialLines) {
        Map<String, UMLComment> commentTextHashMap = new HashMap<>();
        for (UMLComment comment : commentList) {
            String text = normalizeCommentText(comment.getText());
            if (comment.getFullText().startsWith("/*")) {
                commentTextHashMap.put(codeHash(text), comment);
            }
            for (String line : text.split("\\R")) {
                line = normalizeCommentText(line);
                if (line.isEmpty())
                    continue;
                if (!includeTrivialLines && isTrivialCodeLine(line))
                    continue;
                commentTextHashMap.put(codeHash(line), comment);
            }
        }
        return commentTextHashMap;
    }

    /** Maps {@link #codeHash(String)} of every block signature reconstructed from the comments to the signature. */
    public static Map<String, String> generateUncommentedBlockSignatureHashMap(List<UMLComment> commentList) {
        Map<String, String> blockSignatureHashMap = new HashMap<>();
        for (String signature : extractBlockSignaturesFromComments(commentList)) {
            blockSignatureHashMap.put(codeHash(signature), signature);
        }
        return blockSignatureHashMap;
    }

    public static List<String> extractBlockSignaturesFromComments(List<UMLComment> comments) {
        List<String> signatures = new ArrayList<>();
        for (int i = 0; i < comments.size(); i++) {
            UMLComment comment = comments.get(i);
            String text = normalizeCommentText(comment.getText());
            if (text.isEmpty())
                continue;
            if (isLeafStatement(text))
                continue;
            // complete one-line signature
            if (isCompleteBlockSignature(text)) {
                signatures.add(text);
                continue;
            }
            if (comment.getFullText().startsWith("/*")) {
                signatures.addAll(extractFromBlockComment(text));
            }
            else if (comment.getFullText().startsWith("//")) {
                if (!hasBlockKeyword(text))
                    continue;
                SingleLineExtractionResult singleLineExtractionResult = extractFromSingleLineComments(comments, i, text);
                signatures.addAll(singleLineExtractionResult.getSignatures());
                i = singleLineExtractionResult.getLastConsumedIndex();
            }
        }
        return signatures;
    }

    static boolean hasBlockKeyword(String text) {
        // TODO: add support for standalone blocks {}
        return BLOCK_KEYWORD.matcher(text.trim()).find();
    }

    static boolean isLeafStatement(String text) {
        return !hasBlockKeyword(text) && text.trim().endsWith(";");
    }

    static boolean isTrivialCodeLine(String text) {
        return TRIVIAL_CODE_LINE.matcher(normalizeCode(text)).matches();
    }

    private static boolean isCompleteBlockSignature(String text) {
        return hasBlockKeyword(text) && signatureEnds(text);
    }

    private static boolean signatureEnds(String text) {
        return text.trim().endsWith("{");
    }

    private static boolean areConsecutiveComments(UMLComment current, UMLComment next) {
        return next.getLocationInfo().getStartLine() == current.getLocationInfo().getEndLine() + 1;
    }

    private static List<String> extractFromBlockComment(String commentText) {
        List<String> signatures = new ArrayList<>();
        String[] lines = commentText.split("\\R");
        for (int j = 0; j < lines.length; j++) {
            if (isLeafStatement(lines[j]))
                continue;
            if (!hasBlockKeyword(lines[j]))
                continue;
            StringBuilder signatureBuilder = new StringBuilder(lines[j]);
            int currentIndex = j + 1;
            while (!signatureEnds(signatureBuilder.toString()) && currentIndex < lines.length) {
                String nextLine = lines[currentIndex];
                if (isLeafStatement(nextLine)) {
                    if (signatureBuilder.toString().trim().endsWith(")")) {
                        signatures.add(signatureBuilder.toString());
                    }
                    break;
                }
                // another block signature starts
                if (hasBlockKeyword(nextLine))
                    break;
                signatureBuilder.append("\n").append(nextLine);
                currentIndex++;
            }
            if (signatureEnds(signatureBuilder.toString())) {
                signatures.add(signatureBuilder.toString());
            }
            j = currentIndex - 1;
        }
        return signatures;
    }

    private static SingleLineExtractionResult extractFromSingleLineComments(List<UMLComment> comments, int index, String commentText) {
        int lastConsumedIndex = index;
        List<String> signatures = new ArrayList<>();
        StringBuilder signatureBuilder = new StringBuilder(commentText);
        int currentIndex = index;
        while (!signatureEnds(signatureBuilder.toString()) && currentIndex + 1 < comments.size()) {
            UMLComment current = comments.get(currentIndex);
            UMLComment next = comments.get(currentIndex + 1);
            if (!areConsecutiveComments(current, next))
                break;
            String nextText = normalizeCommentText(next.getText());
            if (isLeafStatement(nextText)) {
                if (signatureBuilder.toString().trim().endsWith(")")) {
                    signatures.add(signatureBuilder.toString());
                }
                break;
            }
            // another block signature starts
            if (hasBlockKeyword(nextText))
                break;
            signatureBuilder.append("\n").append(nextText);
            currentIndex++;
        }
        if (signatureEnds(signatureBuilder.toString())) {
            signatures.add(signatureBuilder.toString());
            lastConsumedIndex = currentIndex;
        }
        return new SingleLineExtractionResult(signatures, lastConsumedIndex);
    }

    private static class SingleLineExtractionResult {
        private final List<String> signatures;
        private final int lastConsumedIndex;

        SingleLineExtractionResult(List<String> signatures, int lastConsumedIndex) {
            this.signatures = signatures;
            this.lastConsumedIndex = lastConsumedIndex;
        }

        List<String> getSignatures() {
            return signatures;
        }

        int getLastConsumedIndex() {
            return lastConsumedIndex;
        }
    }
}
