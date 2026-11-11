package org.codetracker.util;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;

import org.codetracker.experiment.oracle.MethodOracle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Method histories from real projects in which the method was commented out and/or restored
 * ({@code oracle/method/comment-transition}, docs/comment-transition/oracle/README.md).
 * Opt-in, because the commit cache entries are built locally from a clone:
 * <pre>mvn test -Dtest=MethodCommentTransitionOracleTest -Dct.oracle=true</pre>
 */
@EnabledIfSystemProperty(named = "ct.oracle", matches = "true")
public class MethodCommentTransitionOracleTest extends OracleTest {
	private static final String EXPECTED = System.getProperty("user.dir") + "/src/test/resources/method/";

	public static Stream<Arguments> testProvider() throws IOException {
		return getArgumentsStream(Collections.singletonList(MethodOracle.commentTransition()), EXPECTED, MethodOracleTest::methodTracker);
	}
}
