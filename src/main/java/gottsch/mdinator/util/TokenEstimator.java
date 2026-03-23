package gottsch.mdinator.util;

/**
 * Rough token-count estimator suitable for budget warnings.
 *
 * <p>Claude and GPT-family models average roughly 3.5–4 characters per token
 * for English prose and source code.  We use 3.7 as a conservative estimate
 * to avoid false "within budget" signals.
 */
public final class TokenEstimator {

    private static final double CHARS_PER_TOKEN = 3.7;

    private TokenEstimator() {}

    /** Estimate the number of tokens in {@code text}. */
    public static long estimate(String text) {
        if (text == null || text.isEmpty()) return 0L;
        return Math.round(text.length() / CHARS_PER_TOKEN);
    }

    /** Estimate tokens from a raw byte count (assumes UTF-8, mostly ASCII). */
    public static long estimateFromBytes(long byteCount) {
        return Math.round(byteCount / CHARS_PER_TOKEN);
    }
}
