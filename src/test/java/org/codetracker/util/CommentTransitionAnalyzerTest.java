package org.codetracker.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Unit tests for the text helpers of {@link CommentTransitionAnalyzer} (ADR 0006). */
public class CommentTransitionAnalyzerTest {

    @ParameterizedTest
    @ValueSource(strings = {"if (x > 0) {", "if(x>0){", "else {", "for (int i = 0; i < n; i++) {", "while (true) {",
            "do {", "try {", "catch (Exception e) {", "finally {", "switch (k) {", "synchronized (lock) {", "static {"})
    public void blockKeywordIsDetected(String line) {
        assertTrue(CommentTransitionAnalyzer.hasBlockKeyword(line), line);
    }

    @ParameterizedTest
    @ValueSource(strings = {"double x = 1;", "format(value);", "forEach(item -> use(item));", "doSomething();",
            "tryLock();", "ifPresent(v -> use(v));", "elseValue = 2;", "statics.add(x);", "whileCount++;", "switchMode();"})
    public void identifierStartingWithKeywordIsNotABlock(String line) {
        assertFalse(CommentTransitionAnalyzer.hasBlockKeyword(line), line);
        assertTrue(CommentTransitionAnalyzer.isLeafStatement(line), line);
    }

    @ParameterizedTest
    @ValueSource(strings = {"}", "{", "return;", "break;", "continue;", "return null;", "return true;", "return  false ;",
            "} else {", "else", "try {", "finally {", "default:"})
    public void trivialLinesAreRecognized(String line) {
        assertTrue(CommentTransitionAnalyzer.isTrivialCodeLine(line), line);
    }

    @ParameterizedTest
    @ValueSource(strings = {"count++;", "return result;", "result = x * 2;", "break label;", "if (x > 0) {", "TODO handle negatives"})
    public void meaningfulLinesAreNotTrivial(String line) {
        assertFalse(CommentTransitionAnalyzer.isTrivialCodeLine(line), line);
    }

    @Test
    public void codeHashIgnoresWhitespace() {
        assertEquals(CommentTransitionAnalyzer.codeHash("if (x > 0) {"), CommentTransitionAnalyzer.codeHash("if(x>0){"));
        assertEquals(CommentTransitionAnalyzer.codeHash("result = x * 2;\n"), CommentTransitionAnalyzer.codeHash("  result=x*2;"));
    }

    @Test
    public void commentMarkersAreStripped() {
        assertEquals("count++;", CommentTransitionAnalyzer.normalizeCommentText("//   count++;"));
        assertEquals("count++;", CommentTransitionAnalyzer.normalizeCommentText("/* count++; */"));
        assertEquals("count++;", CommentTransitionAnalyzer.normalizeCommentText(" * count++;"));
    }
}
