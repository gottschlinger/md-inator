package gottsch.mdinator;

import gottsch.mdinator.util.TokenEstimator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEstimatorTest {

    @Test
    void emptyStringIsZero() {
        assertThat(TokenEstimator.estimate("")).isEqualTo(0L);
        assertThat(TokenEstimator.estimate(null)).isEqualTo(0L);
    }

    @Test
    void roughEstimateIsReasonable() {
        // ~100 chars of typical source code → roughly 25–30 tokens
        String src = "public class App { public static void main(String[] args) { System.out.println(\"hello\"); } }";
        long tokens = TokenEstimator.estimate(src);
        assertThat(tokens).isBetween(20L, 40L);
    }

    @Test
    void byteEstimateMatchesStringEstimate() {
        String src = "Hello, world! This is a test.";
        long fromString = TokenEstimator.estimate(src);
        long fromBytes  = TokenEstimator.estimateFromBytes(src.getBytes().length);
        // Should be within 1 token of each other for ASCII
        assertThat(Math.abs(fromString - fromBytes)).isLessThanOrEqualTo(1L);
    }
}
