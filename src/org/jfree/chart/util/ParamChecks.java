package org.jfree.chart.util;

/**
 * Compatibility facade for the legacy bundled SWT bridge.
 *
 * JFreeChart 1.5.6 exposes the same null validation through Args.
 */
public final class ParamChecks {
    private ParamChecks() {
    }

    public static void nullNotPermitted(Object value, String name) {
        Args.nullNotPermitted(value, name);
    }
}
