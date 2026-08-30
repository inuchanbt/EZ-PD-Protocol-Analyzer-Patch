package com.cypress.ezpdanalyzer.ui.jfreechart;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

/** Small, non-destructive graph-label cosmetics. */
public final class GraphUiCosmeticSupport {

    private GraphUiCosmeticSupport() {
    }

    /**
     * Make the two right-side numeric columns visually right-aligned.
     *
     * JFreeChart's normal RIGHT-edge vertical-axis tick anchor starts every
     * label at the axis, so "0" and "48,000" are left-aligned.  A NumberAxis
     * has no public per-edge text-anchor setter, therefore we retain the stock
     * tick calculation and only install a NumberFormat that left-pads short
     * labels with U+2007 FIGURE SPACE (digit-width whitespace).
     *
     * Axis 2 = VBUS, axis 3 = AMP in this utility.
     */
    public static void configureChart(Object chart) {
        try {
            if (chart == null) {
                return;
            }

            Method getXYPlot = chart.getClass().getMethod("getXYPlot");
            Object plot = getXYPlot.invoke(chart);
            if (plot == null) {
                return;
            }

            Method getRangeAxis =
                plot.getClass().getMethod("getRangeAxis", int.class);

            configureRightAxis(getRangeAxis.invoke(plot, 2), 6);
            configureRightAxis(getRangeAxis.invoke(plot, 3), 6);
        } catch (Throwable ignored) {
            // Cosmetic only; never interfere with graph operation.
        }
    }

    private static void configureRightAxis(Object axis, int width) {
        try {
            if (axis == null) {
                return;
            }

            Method setFormat = axis.getClass().getMethod(
                "setNumberFormatOverride",
                NumberFormat.class
            );
            setFormat.invoke(axis, new FigureSpaceNumberFormat(width));
        } catch (Throwable ignored) {
            // Leave the stock label format in place on any incompatibility.
        }
    }

    /** Fixed visual width without changing tick locations or axis range. */
    private static final class FigureSpaceNumberFormat extends NumberFormat {
        private static final long serialVersionUID = 1L;
        private static final char FIGURE_SPACE = '\u2007';

        private final int width;
        private final DecimalFormat delegate;

        FigureSpaceNumberFormat(int width) {
            this.width = width;
            DecimalFormatSymbols symbols =
                DecimalFormatSymbols.getInstance();
            this.delegate = new DecimalFormat("#,##0.###", symbols);
            this.delegate.setGroupingUsed(true);
        }

        @Override
        public StringBuffer format(
                double number,
                StringBuffer toAppendTo,
                FieldPosition pos) {
            return appendPadded(delegate.format(number), toAppendTo);
        }

        @Override
        public StringBuffer format(
                long number,
                StringBuffer toAppendTo,
                FieldPosition pos) {
            return appendPadded(delegate.format(number), toAppendTo);
        }

        @Override
        public Number parse(String source, ParsePosition parsePosition) {
            // Accept our own visual padding if parsing is ever requested.
            int start = parsePosition.getIndex();
            while (start < source.length() &&
                   source.charAt(start) == FIGURE_SPACE) {
                start++;
            }
            parsePosition.setIndex(start);
            return delegate.parse(source, parsePosition);
        }

        private StringBuffer appendPadded(
                String text,
                StringBuffer out) {
            for (int i = text.length(); i < width; i++) {
                out.append(FIGURE_SPACE);
            }
            out.append(text);
            return out;
        }
    }
}
