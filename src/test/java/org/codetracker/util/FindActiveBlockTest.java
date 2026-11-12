package org.codetracker.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import gr.uom.java.xmi.LocationInfo;
import gr.uom.java.xmi.UMLModel;
import gr.uom.java.xmi.UMLOperation;
import gr.uom.java.xmi.decomposition.CompositeStatementObject;

/**
 * {@link CommentTransitionAnalyzer#findActiveBlock}: a block with a generic signature ({@code try}) must not be
 * matched with an unrelated block of the same kind (ADR 0014; elasticsearch InternalTestCluster).
 */
public class FindActiveBlockTest {

    private static UMLOperation operation(String body) throws Exception {
        String source = "package demo;\npublic class Sample {\n    void run() {\n" + body + "\n    }\n}\n";
        UMLModel model = GitHistoryRefactoringMinerImpl.createModel(Collections.singletonMap("src/demo/Sample.java", source), new LinkedHashSet<>());
        return model.getClassList().get(0).getOperations().get(0);
    }

    private static CompositeStatementObject lastTry(UMLOperation operation) {
        CompositeStatementObject found = null;
        for (CompositeStatementObject node : operation.getBody().getCompositeStatement().getInnerNodes())
            if (node.getLocationInfo().getCodeElementType() == LocationInfo.CodeElementType.TRY_STATEMENT)
                found = node;
        return found;
    }

    private static final String TRACKED = "try { checkAccounting(name); assertZero(breaker); } catch (Exception e) { throw new AssertionError(e); }";

    @Test
    public void unrelatedTryIsNotTheActiveVersion() throws Exception {
        CompositeStatementObject target = lastTry(operation(TRACKED));
        UMLOperation older = operation("try { close(node); } catch (Exception e) { log(e); }");
        assertNull(CommentTransitionAnalyzer.findActiveBlock(older, target));
    }

    @Test
    public void editedTryWithSharedStatementsIsTheActiveVersion() throws Exception {
        CompositeStatementObject target = lastTry(operation(TRACKED));
        UMLOperation older = operation("try { close(node); } catch (Exception e) { log(e); }\n"
                + "try { checkAccounting(name); assertZero(breaker); report(); } catch (Exception e) { throw new AssertionError(e); }");
        CompositeStatementObject match = CommentTransitionAnalyzer.findActiveBlock(older, target);
        assertNotNull(match);
        assertTrue(match.getString().contains("checkAccounting") || match.getLeaves().toString().contains("checkAccounting"));
    }
}
