package com.cypress.ezpdanalyzer.ui.jfreechart;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.TickType;
import org.jfree.chart.axis.ValueTick;
import org.jfree.chart.ui.RectangleEdge;

/**
 * Keeps the lowest visible vertical tick label at its stock position while
 * every other numbered tick uses the shared downward label offset.
 */
public class FirstMajorTickNumberAxis extends NumberAxis {
    private static final long serialVersionUID = 1L;
    // The final visual baseline is 4px higher than the preceding layout.
    // Keeping the first-tick compensation at -8px preserves that same shift
    // for every visible vertical-axis number.
    private static final float ALL_TICKS_Y_CORRECTION = 4.0f;
    private static final float FIRST_TICK_Y_CORRECTION = -8.0f;

    private transient double firstMajorValue = Double.NaN;

    public FirstMajorTickNumberAxis(String label) {
        super(label);
    }

    void setAutoRangeQuiet(boolean auto) {
        super.setAutoRange(auto, false);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List refreshTicks(Graphics2D g2, AxisState state,
            Rectangle2D dataArea, RectangleEdge edge) {
        List ticks = super.refreshTicks(g2, state, dataArea, edge);
        firstMajorValue = Double.NaN;

        if (ticks == null || isVerticalTickLabels()
                || (edge != RectangleEdge.LEFT && edge != RectangleEdge.RIGHT)) {
            return ticks;
        }

        for (Object item : ticks) {
            if (item instanceof ValueTick) {
                ValueTick tick = (ValueTick) item;
                if (tick.getTickType() == TickType.MAJOR) {
                    firstMajorValue = tick.getValue();
                    break;
                }
            }
        }
        return ticks;
    }

    @Override
    protected float[] calculateAnchorPoint(ValueTick tick, double cursor,
            Rectangle2D dataArea, RectangleEdge edge) {
        float[] point = super.calculateAnchorPoint(tick, cursor, dataArea, edge);
        if (tick != null && tick.getTickType() == TickType.MAJOR
                && !isVerticalTickLabels()
                && (edge == RectangleEdge.LEFT || edge == RectangleEdge.RIGHT)) {
            point[1] += ALL_TICKS_Y_CORRECTION;
            if (Double.isNaN(firstMajorValue)) {
                return point;
            }
            double tolerance = Math.max(1.0e-9, Math.ulp(firstMajorValue) * 8.0);
            if (Math.abs(tick.getValue() - firstMajorValue) <= tolerance) {
                point[1] += FIRST_TICK_Y_CORRECTION;
            }
        }
        return point;
    }
}
