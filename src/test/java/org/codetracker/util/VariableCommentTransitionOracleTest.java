package org.codetracker.util;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;

import org.codetracker.experiment.oracle.VariableOracle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Variable histories from real projects around commented-out and restored code
 * ({@code oracle/variable/comment-transition}, docs/comment-transition/oracle/README.md).
 * Opt-in, because the commit cache entries are built locally from a clone:
 * <pre>mvn test -Dtest=VariableCommentTransitionOracleTest -Dct.oracle=true</pre>
 */
@EnabledIfSystemProperty(named = "ct.oracle", matches = "true")
public class VariableCommentTransitionOracleTest extends OracleTest {
	private static final String EXPECTED = System.getProperty("user.dir") + "/src/test/resources/variable/";

	public static Stream<Arguments> testProvider() throws IOException {
		return getArgumentsStream(Collections.singletonList(VariableOracle.commentTransition()), EXPECTED, VariableOracleTest::variableTracker);
	}
}
