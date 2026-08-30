package com.cypress.ezpdanalyzer.ui.jfreechart;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.axis.TickType;
import org.jfree.chart.axis.ValueTick;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;

public final class DomainAlignedNumberAxis extends NumberAxis {
    private static final long serialVersionUID = 1L;

    /*
     * The first visible BOTTOM major tick stays at its stock position while
     * every other numbered tick uses the shared downward label offset.
     */
    // The final visual baseline is 4px higher than the preceding layout.
    // Keeping the first-tick compensation at -8px preserves that same shift
    // for every visible bottom-axis number.
    private static final float ALL_TICKS_Y_CORRECTION = 4.0f;
    private static final float FIRST_TICK_Y_CORRECTION = -8.0f;

    private transient double firstMajorValue = Double.NaN;

    public DomainAlignedNumberAxis(String label) {
        super(label);
    }

    void setAutoRangeQuiet(boolean auto) {
        super.setAutoRange(auto, false);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List refreshTicks(Graphics2D g2, AxisState state,
            Rectangle2D dataArea, RectangleEdge edge) {

        List original = super.refreshTicks(g2, state, dataArea, edge);

        firstMajorValue = Double.NaN;

        if (original == null
                || edge != RectangleEdge.BOTTOM
                || isVerticalTickLabels()) {
            return original;
        }

        List rebuilt = new ArrayList(original.size());

        for (Object item : original) {
            if (item instanceof NumberTick) {
                NumberTick tick = (NumberTick) item;

                if (tick.getTickType() == TickType.MAJOR) {

                    if (Double.isNaN(firstMajorValue)) {
                        firstMajorValue = tick.getValue();
                    }

                    rebuilt.add(new NumberTick(
                            TickType.MAJOR,
                            tick.getValue(),
                            tick.getText(),
                            TextAnchor.TOP_CENTER,
                            TextAnchor.TOP_CENTER,
                            0.0));

                    continue;
                }
            }

            rebuilt.add(item);
        }

        return rebuilt;
    }

    @Override
    protected float[] calculateAnchorPoint(ValueTick tick,
            double cursor,
            Rectangle2D dataArea,
            RectangleEdge edge) {

        float[] p = super.calculateAnchorPoint(
                tick, cursor, dataArea, edge);

        if (edge == RectangleEdge.BOTTOM && tick.getTickType() == TickType.MAJOR) {
            p[1] += ALL_TICKS_Y_CORRECTION;

            if (Double.isNaN(firstMajorValue)) {
                return p;
            }

            double tolerance = Math.max(
                    1.0e-9,
                    Math.ulp(firstMajorValue) * 8.0);

            if (Math.abs(tick.getValue() - firstMajorValue) <= tolerance) {
                p[1] += FIRST_TICK_Y_CORRECTION;
            }
        }

        return p;
    }
}
