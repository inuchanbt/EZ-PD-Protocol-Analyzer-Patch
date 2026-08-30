package org.jfree.chart;

import java.io.File;
import java.io.IOException;

/**
 * Compatibility facade for the legacy bundled SWT bridge.
 *
 * JFreeChart 1.5.6 renamed this legacy static entry point to ChartUtils.
 * The Analyzer's existing SWT 1.0.17 bridge still invokes ChartUtilities,
 * so retain the one method it needs without altering the bridge itself.
 */
public abstract class ChartUtilities {
    private ChartUtilities() {
    }

    public static void saveChartAsPNG(File file, JFreeChart chart, int width, int height)
            throws IOException {
        ChartUtils.saveChartAsPNG(file, chart, width, height);
    }
}
