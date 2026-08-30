package com.cypress.ezpdanalyzer.ui.views;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

/** Persists user-resized Details and Payload column widths. */
public final class ViewColumnWidthPersistence {
    private static final String PREFERENCE_NODE = "com.cypress.ezpdanalyzer.ui";
    private static final int MIN_SAVED_WIDTH = 12;

    private ViewColumnWidthPersistence() {
    }

    public static void installDetails(Tree tree) {
        if (tree == null || tree.isDisposed()) {
            return;
        }
        final TreeColumn[] columns = tree.getColumns();
        restoreTreeColumns(columns, "details.columnWidth.");
        for (TreeColumn column : columns) {
            column.addListener(SWT.Resize, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    saveTreeColumns(columns, "details.columnWidth.");
                }
            });
        }
    }

    public static void installPayload(Table table) {
        if (table == null || table.isDisposed()) {
            return;
        }
        final TableColumn[] columns = table.getColumns();
        restoreTableColumns(columns, "payload.columnWidth.");
        for (TableColumn column : columns) {
            column.addListener(SWT.Resize, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    saveTableColumns(columns, "payload.columnWidth.");
                }
            });
        }
    }

    private static void restoreTreeColumns(TreeColumn[] columns, String prefix) {
        IEclipsePreferences preferences = preferences();
        for (int index = 0; index < columns.length; index++) {
            int width = preferences.getInt(prefix + index, -1);
            if (width >= MIN_SAVED_WIDTH) {
                columns[index].setWidth(width);
            }
        }
    }

    private static void restoreTableColumns(TableColumn[] columns, String prefix) {
        IEclipsePreferences preferences = preferences();
        for (int index = 0; index < columns.length; index++) {
            int width = preferences.getInt(prefix + index, -1);
            if (width >= MIN_SAVED_WIDTH) {
                columns[index].setWidth(width);
            }
        }
    }

    private static void saveTreeColumns(TreeColumn[] columns, String prefix) {
        IEclipsePreferences preferences = preferences();
        for (int index = 0; index < columns.length; index++) {
            preferences.putInt(prefix + index, columns[index].getWidth());
        }
        flush(preferences);
    }

    private static void saveTableColumns(TableColumn[] columns, String prefix) {
        IEclipsePreferences preferences = preferences();
        for (int index = 0; index < columns.length; index++) {
            preferences.putInt(prefix + index, columns[index].getWidth());
        }
        flush(preferences);
    }

    private static IEclipsePreferences preferences() {
        return InstanceScope.INSTANCE.getNode(PREFERENCE_NODE);
    }

    private static void flush(IEclipsePreferences preferences) {
        try {
            preferences.flush();
        } catch (org.osgi.service.prefs.BackingStoreException ignored) {
            // Preference I/O must not affect packet decoding or the UI.
        }
    }
}
