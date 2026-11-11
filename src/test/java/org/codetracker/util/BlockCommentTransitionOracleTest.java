package org.codetracker.util;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Stream;

import org.codetracker.experiment.oracle.BlockOracle;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Block histories from real projects in which code was commented out and/or restored
 * ({@code oracle/block/comment-transition}, docs/comment-transition/oracle/README.md).
 * Opt-in, because the commit cache entries are built locally from a clone:
 * <pre>mvn test -Dtest=BlockCommentTransitionOracleTest -Dct.oracle=true</pre>
 */
@EnabledIfSystemProperty(named = "ct.oracle", matches = "true")
public class BlockCommentTransitionOracleTest extends OracleTest {
	private static final String EXPECTED = System.getProperty("user.dir") + "/src/test/resources/block/";

	public static Stream<Arguments> testProvider() throws IOException {
		return getArgumentsStream(Collections.singletonList(BlockOracle.commentTransition()), EXPECTED, BlockOracleTest::blockTracker);
	}
}
