package com.cypress.ezpdanalyzer.ui.jfreechart;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Local support for the community Graph navigation repair.
 *
 * This class intentionally uses only JDK types at compile time. At runtime it
 * calls the public IData/JFreeChart APIs through reflection. That keeps this
 * helper independent of Infineon/Cypress nested library JARs during patching.
 */
public final class GraphNavigationSupport {

    private static final String IDATA_CLASS =
        "com.cypress.ezpdanalyzer.ui.model.IData";

    private static final String VALUE_AXIS_CLASS =
        "org.jfree.chart.axis.ValueAxis";

    private GraphNavigationSupport() {
    }

    /**
     * Snap an arbitrary PD packet timestamp to the nearest real graph sample.
     *
     * Stock displayGraph() searches fixed 1000-sample pages by requiring the
     * packet timestamp to fall between the first and last sample timestamp of
     * a page. A PD packet can legitimately fall into the tiny gap between two
     * graph samples/pages and then stock code finds no page at all.
     *
     * Returning an actual sample timestamp guarantees that the stock page
     * search can map the selection to one of its pages.
     */
    public static long snapSelectionTime(List<?> data, long selectedTime) {
        try {
            if (data == null || data.isEmpty()) {
                return selectedTime;
            }

            Class<?> iData = Class.forName(IDATA_CLASS);
            Method getTimeStamp = iData.getMethod("getTimeStamp");

            int lo = 0;
            int hi = data.size() - 1;

            long first = timeAt(data, lo, getTimeStamp);
            long last = timeAt(data, hi, getTimeStamp);

            if (selectedTime <= first) {
                return first;
            }
            if (selectedTime >= last) {
                return last;
            }

            while (lo <= hi) {
                int mid = lo + ((hi - lo) >>> 1);
                long t = timeAt(data, mid, getTimeStamp);

                if (t == selectedTime) {
                    return t;
                } else if (t < selectedTime) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }

            // hi = previous sample, lo = next sample.
            long prev = timeAt(data, Math.max(0, hi), getTimeStamp);
            long next = timeAt(
                data,
                Math.min(data.size() - 1, lo),
                getTimeStamp
            );

            long dPrev = distance(selectedTime, prev);
            long dNext = distance(next, selectedTime);

            // Prefer the earlier sample on an exact tie.
            return dPrev <= dNext ? prev : next;

        } catch (Throwable ignored) {
            // Fail-safe: preserve stock behavior instead of breaking the UI.
            return selectedTime;
        }
    }

    /**
     * Reset stale/manual Y-axis zoom after a packet selection and ensure the
     * selected packet markers remain inside the X domain if the packet fell
     * between graph samples.
     */
    public static void normalizeAfterSelection(
            Object chart,
            Object usbPacket) {

        try {
            if (chart == null) {
                return;
            }

            Method getXYPlot =
                chart.getClass().getMethod("getXYPlot");
            Object plot = getXYPlot.invoke(chart);
            if (plot == null) {
                return;
            }

            Class<?> plotClass = plot.getClass();
            Class<?> valueAxisClass = Class.forName(VALUE_AXIS_CLASS);

            Method setAutoRange =
                valueAxisClass.getMethod("setAutoRange", boolean.class);

            Method getRangeAxis =
                plotClass.getMethod("getRangeAxis", int.class);

            // Axis 0 = CC1/CC2, axis 2 = VBUS, axis 3 = AMP.
            int[] axes = {0, 2, 3};
            for (int index : axes) {
                Object axis = getRangeAxis.invoke(plot, index);
                if (axis != null) {
                    setAutoRange.invoke(axis, true);
                }
            }

            if (usbPacket == null) {
                return;
            }

            Method getDomainAxis =
                plotClass.getMethod("getDomainAxis");
            Object domainAxis = getDomainAxis.invoke(plot);
            if (domainAxis == null) {
                return;
            }

            Method getLowerBound =
                valueAxisClass.getMethod("getLowerBound");
            Method getUpperBound =
                valueAxisClass.getMethod("getUpperBound");
            Method setLowerBound =
                valueAxisClass.getMethod("setLowerBound", double.class);
            Method setUpperBound =
                valueAxisClass.getMethod("setUpperBound", double.class);

            Method getsTime =
                usbPacket.getClass().getMethod("getsTime");
            Method geteTime =
                usbPacket.getClass().getMethod("geteTime");

            double start = Double.parseDouble(
                String.valueOf(getsTime.invoke(usbPacket))
            );
            double end = Double.parseDouble(
                String.valueOf(geteTime.invoke(usbPacket))
            );

            double lower =
                ((Number) getLowerBound.invoke(domainAxis)).doubleValue();
            double upper =
                ((Number) getUpperBound.invoke(domainAxis)).doubleValue();

            if (start < lower) {
                setLowerBound.invoke(domainAxis, start);
            }
            if (end > upper) {
                setUpperBound.invoke(domainAxis, end);
            }

        } catch (Throwable ignored) {
            // Selection must never take down the whole analyzer UI.
        }
    }

    private static long timeAt(
            List<?> data,
            int index,
            Method getTimeStamp) throws Exception {

        Object value =
            getTimeStamp.invoke(data.get(index));

        return ((Number) value).longValue();
    }

    private static long distance(long high, long low) {
        // The analyzer timestamps are non-negative and ordered, but use a
        // saturation-safe subtraction anyway.
        if (high < low) {
            long tmp = high;
            high = low;
            low = tmp;
        }

        long d = high - low;
        return d < 0 ? Long.MAX_VALUE : d;
    }
}
