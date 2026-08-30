package com.cypress.ezpdanalyzer.ui.jfreechart;

import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleInsets;

/**
 * Raises only the legend text by four pixels.  The top/bottom padding still
 * totals four pixels, so the coloured legend squares retain their position.
 */
public final class LegendTextBaselineSupport {
    private static final RectangleInsets TEXT_UP_4PX =
            new RectangleInsets(-2.0, 2.0, 6.0, 2.0);

    private LegendTextBaselineSupport() {
    }

    public static void adjust(LegendTitle legend) {
        if (legend != null) {
            legend.setItemLabelPadding(TEXT_UP_4PX);
        }
    }
}
