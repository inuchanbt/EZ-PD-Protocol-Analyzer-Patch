package com.cypress.ezpdanalyzer.ui.jfreechart;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Stable post-capture graph data for the community patch.
 *
 * The vendor STOP path performs its own final-buffer processing after live
 * capture has already stopped.  Physical testing showed that the waveform
 * currently painted on screen survives, while later packet-row selection can
 * lose the backing GraphData and move only the magenta Start/End markers.
 *
 * v0.7 therefore snapshots the final LIVE GraphData immediately before the
 * vendor final-buffer handoff and keeps that independent snapshot for UI
 * navigation after STOP.
 *
 * Important:
 * - Vendor lists are NEVER cleared or rewritten by this helper.
 * - Vendor STOP/finalization remains untouched.
 * - The snapshot is used only for a stopped LIVE capture.
 * - Imported/file/old-data paths keep using the vendor-selected data source.
 */
public final class GraphStopSupport {

    private static volatile Object registeredChart;
    private static volatile ArrayList<Object> stoppedLiveSnapshot;

    private GraphStopSupport() {
    }

    /** Register the CyXYLineChart instance created by the Graphical view. */
    public static void registerChart(Object chart) {
        registeredChart = chart;
    }

    /**
     * Snapshot the final live graph immediately before vendor STOP
     * finalization begins.
     */
    public static synchronized void captureBeforeStopFlush(Object dataManager) {
        stoppedLiveSnapshot = null;

        try {
            if (dataManager == null) {
                return;
            }

            Method getPrimary =
                dataManager.getClass().getMethod("getPrimaryGraphDatas");
            Object value = getPrimary.invoke(dataManager);

            if (!(value instanceof List<?>)) {
                return;
            }

            List<?> source = (List<?>) value;
            ArrayList<Object> snapshot =
                new ArrayList<Object>(source.size());
            snapshot.addAll(source);

            stoppedLiveSnapshot = snapshot;
        } catch (Throwable ignored) {
            stoppedLiveSnapshot = null;
        }
    }

    /**
     * The vendor final-buffer method has been invoked.  Do not mutate any
     * vendor list here; just repaint once.  refreshGraph() is patched to use
     * chooseGraphData(), so this repaint already uses our independent snapshot
     * when the DataManager is in stopped-live state.
     */
    public static void restoreAfterStopFlush(Object dataManager) {
        Object chart = registeredChart;

        try {
            if (chart != null) {
                Method refresh =
                    chart.getClass().getMethod("refreshGraph");
                refresh.invoke(chart);
            }
        } catch (Throwable ignored) {
            // STOP must remain fail-safe even if the view has already closed.
        }
    }

    /**
     * Select the backing data list for Graph UI operations.
     *
     * Use the independent snapshot only when:
     *   isStopped == true
     *   isFileData == false
     *   isOldData  == false
     *
     * Thus opening a saved/imported capture continues through the stock
     * ScopeData/file-data path and can never accidentally show an earlier
     * LIVE snapshot.
     */
    public static List<?> chooseGraphData(
            Object dataManager,
            List<?> stockData) {

        ArrayList<Object> snapshot = stoppedLiveSnapshot;
        if (snapshot == null || snapshot.isEmpty() || dataManager == null) {
            return stockData;
        }

        try {
            Method isStopped =
                dataManager.getClass().getMethod("isStopped");
            Method isFileData =
                dataManager.getClass().getMethod("isFileData");
            Method isOldData =
                dataManager.getClass().getMethod("isOldData");

            boolean stopped =
                ((Boolean) isStopped.invoke(dataManager)).booleanValue();
            boolean fileData =
                ((Boolean) isFileData.invoke(dataManager)).booleanValue();
            boolean oldData =
                ((Boolean) isOldData.invoke(dataManager)).booleanValue();

            if (stopped && !fileData && !oldData) {
                return snapshot;
            }
        } catch (Throwable ignored) {
            // On any incompatible state, preserve the vendor-selected list.
        }

        return stockData;
    }

    /**
     * Return the effective GraphData size for stock scrollRight().
     *
     * v0.7 already makes displayGraph()/refreshGraph() use the independent
     * stopped-LIVE snapshot. Stock scrollRight(), however, performs its
     * boundary test against DataManager.getGraphDatas().size() directly.
     * Reuse the same stopped-LIVE selection policy here, returning only the
     * integer size so the vendor's index/update logic stays untouched.
     */
    public static int graphDataSizeForScrollRight(
            Object dataManager,
            List<?> stockData) {

        List<?> selected = chooseGraphData(dataManager, stockData);
        return selected == null ? 0 : selected.size();
    }

    /** Visible to javap/tests; useful for diagnostics without exposing data. */
    public static int getStoppedLiveSnapshotSize() {
        ArrayList<Object> snapshot = stoppedLiveSnapshot;
        return snapshot == null ? 0 : snapshot.size();
    }
}
