package com.cypress.ezpdanalyzer.ui.jfreechart;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;

/**
 * Installs DomainAlignedNumberAxis immediately after ChartFactory creates the
 * chart and BEFORE DataManager/CyXYLineChart caches any axis reference.
 * This is intentionally different from the unsafe v1.0a late replacement.
 */
public final class DomainAxisSupport {
    private DomainAxisSupport() {}

    public static JFreeChart install(JFreeChart chart) {
        if (chart == null) {
            return null;
        }
        XYPlot plot = chart.getXYPlot();
        if (plot == null) {
            return chart;
        }
        ValueAxis axis = plot.getDomainAxis(0);
        if (axis instanceof DomainAlignedNumberAxis) {
            return chart;
        }
        if (!(axis instanceof NumberAxis)) {
            return chart;
        }

        NumberAxis src = (NumberAxis) axis;
        DomainAlignedNumberAxis dst = new DomainAlignedNumberAxis(src.getLabel());

        copyAxisSettings(src, dst);

        Range range = src.getRange();
        boolean autoRange = src.isAutoRange();

        // No CY4500 code has cached the old axis yet at this injection point.
        plot.setDomainAxis(0, dst, false);
        dst.setRange(range, false, false);
        dst.setAutoRangeQuiet(autoRange);
        installLeftRangeAxis(plot);
        return chart;
    }

    private static void installLeftRangeAxis(XYPlot plot) {
        ValueAxis axis = plot.getRangeAxis(0);
        if (axis instanceof FirstMajorTickNumberAxis || !(axis instanceof NumberAxis)) {
            return;
        }
        NumberAxis src = (NumberAxis) axis;
        FirstMajorTickNumberAxis dst = new FirstMajorTickNumberAxis(src.getLabel());
        copyAxisSettings(src, dst);
        Range range = src.getRange();
        boolean autoRange = src.isAutoRange();
        plot.setRangeAxis(0, dst, false);
        dst.setRange(range, false, false);
        dst.setAutoRangeQuiet(autoRange);
    }

    private static void copyAxisSettings(NumberAxis src, NumberAxis dst) {
        dst.setLabelFont(src.getLabelFont());
        dst.setLabelPaint(src.getLabelPaint());
        dst.setLabelInsets(src.getLabelInsets());
        dst.setLabelAngle(src.getLabelAngle());
        dst.setTickLabelFont(src.getTickLabelFont());
        dst.setTickLabelPaint(src.getTickLabelPaint());
        dst.setTickLabelInsets(src.getTickLabelInsets());
        dst.setTickLabelsVisible(src.isTickLabelsVisible());
        dst.setTickMarksVisible(src.isTickMarksVisible());
        dst.setAxisLineVisible(src.isAxisLineVisible());
        dst.setAxisLinePaint(src.getAxisLinePaint());
        dst.setAxisLineStroke(src.getAxisLineStroke());
        dst.setTickMarkPaint(src.getTickMarkPaint());
        dst.setTickMarkStroke(src.getTickMarkStroke());
        dst.setTickMarkInsideLength(src.getTickMarkInsideLength());
        dst.setTickMarkOutsideLength(src.getTickMarkOutsideLength());
        dst.setVisible(src.isVisible());
        dst.setFixedDimension(src.getFixedDimension());
        dst.setInverted(src.isInverted());
        dst.setLowerMargin(src.getLowerMargin());
        dst.setUpperMargin(src.getUpperMargin());
        dst.setAutoRangeMinimumSize(src.getAutoRangeMinimumSize());
        dst.setDefaultAutoRange(src.getDefaultAutoRange());
        dst.setFixedAutoRange(src.getFixedAutoRange());
        dst.setVerticalTickLabels(src.isVerticalTickLabels());
        dst.setMinorTickCount(src.getMinorTickCount());
        dst.setMinorTickMarksVisible(src.isMinorTickMarksVisible());
        dst.setMinorTickMarkInsideLength(src.getMinorTickMarkInsideLength());
        dst.setMinorTickMarkOutsideLength(src.getMinorTickMarkOutsideLength());
        dst.setPositiveArrowVisible(src.isPositiveArrowVisible());
        dst.setNegativeArrowVisible(src.isNegativeArrowVisible());
        dst.setAutoRangeIncludesZero(src.getAutoRangeIncludesZero());
        dst.setAutoRangeStickyZero(src.getAutoRangeStickyZero());
        dst.setRangeType(src.getRangeType());
        dst.setStandardTickUnits(src.getStandardTickUnits());
        dst.setAutoTickUnitSelection(src.isAutoTickUnitSelection(), false);
        if (!src.isAutoTickUnitSelection()) {
            dst.setTickUnit(src.getTickUnit(), false, false);
        }
        dst.setNumberFormatOverride(src.getNumberFormatOverride());
    }
}
