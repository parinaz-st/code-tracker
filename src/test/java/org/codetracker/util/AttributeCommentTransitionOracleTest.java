package org.codetracker.util;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;

import org.codetracker.experiment.oracle.AttributeOracle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Attribute histories from real projects in which a field was commented out and/or restored
 * ({@code oracle/attribute/comment-transition}, docs/comment-transition/oracle/README.md).
 * Opt-in, because the commit cache entries are built locally from a clone:
 * <pre>mvn test -Dtest=AttributeCommentTransitionOracleTest -Dct.oracle=true</pre>
 */
@EnabledIfSystemProperty(named = "ct.oracle", matches = "true")
public class AttributeCommentTransitionOracleTest extends OracleTest {
	private static final String EXPECTED = System.getProperty("user.dir") + "/src/test/resources/attribute/";

	public static Stream<Arguments> testProvider() throws IOException {
		return getArgumentsStream(Collections.singletonList(AttributeOracle.commentTransition()), EXPECTED, AttributeOracleTest::attributeTracker);
	}
}
