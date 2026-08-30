package com.cypress.ezpdanalyzer.ui.jfreechart;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.axis.TickType;
import org.jfree.chart.axis.ValueTick;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;

/**
 * v1.0b cosmetic-only right axis.
 *
 * This class deliberately keeps the v0.9 class name so the already-patched
 * CyXYLineChart continues to create exactly the same axis object at exactly
 * the same time.  No late axis replacement is performed.
 */
public final class RightAlignedNumberAxis extends FirstMajorTickNumberAxis {
    private static final long serialVersionUID = 1L;

    private transient float rightLabelWidth;

    public RightAlignedNumberAxis(String label) {
        super(label);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List refreshTicks(Graphics2D g2, AxisState state,
            Rectangle2D dataArea, RectangleEdge edge) {

        List original = super.refreshTicks(g2, state, dataArea, edge);
        rightLabelWidth = 0.0f;

        if (g2 == null || original == null || edge != RectangleEdge.RIGHT
                || isVerticalTickLabels()) {
            return original;
        }

        FontMetrics fm = g2.getFontMetrics(getTickLabelFont());
        int maxWidth = 0;
        for (Object item : original) {
            if (item instanceof ValueTick) {
                ValueTick tick = (ValueTick) item;
                if (tick.getTickType() == TickType.MAJOR) {
                    String text = cleanLeadingPadding(tick.getText());
                    if (text != null) {
                        maxWidth = Math.max(maxWidth, fm.stringWidth(text));
                    }
                }
            }
        }
        rightLabelWidth = maxWidth;

        boolean fractionalScale = false;
        for (Object item : original) {
            if (item instanceof ValueTick &&
                    ((ValueTick) item).getTickType() == TickType.MAJOR &&
                    ((ValueTick) item).getText().indexOf('.') >= 0) {
                fractionalScale = true;
                break;
            }
        }
        DecimalFormat fractionalFormat = fractionalScale
                ? new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance())
                : null;

        List rebuilt = new ArrayList(original.size());
        for (Object item : original) {
            if (item instanceof NumberTick) {
                NumberTick tick = (NumberTick) item;
                if (tick.getTickType() == TickType.MAJOR) {
                    rebuilt.add(new NumberTick(
                            TickType.MAJOR,
                            tick.getValue(),
                            fractionalFormat == null
                                    ? cleanLeadingPadding(tick.getText())
                                    : fractionalFormat.format(tick.getValue()),
                            TextAnchor.CENTER_RIGHT,
                            TextAnchor.CENTER_RIGHT,
                            0.0));
                    continue;
                }
            }
            rebuilt.add(item);
        }
        return rebuilt;
    }

    @Override
    protected float[] calculateAnchorPoint(ValueTick tick, double cursor,
            Rectangle2D dataArea, RectangleEdge edge) {

        float[] p = super.calculateAnchorPoint(tick, cursor, dataArea, edge);
        if (tick != null && tick.getTickType() == TickType.MAJOR
                && edge == RectangleEdge.RIGHT && !isVerticalTickLabels()) {
            // Stock RIGHT anchor is the left edge of the reserved label strip.
            // CENTER_RIGHT at base + maxWidth makes all visible right edges equal.
            p[0] += rightLabelWidth;
        }
        return p;
    }

    private static String cleanLeadingPadding(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)
                    || c == '\u00A0' || c == '\u2007' || c == '\u2009'
                    || c == '\u200A' || c == '\u202F') {
                i++;
            } else {
                break;
            }
        }
        return text.substring(i);
    }
}
