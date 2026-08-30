package com.cypress.ezpdanalyzer.ui.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/** Display-only formatting for the two numeric Payload columns. */
public final class PayloadTableSupport {
    private static final int BYTE_INDEX_RIGHT_PADDING = 12;
    private static final int VALUE_RIGHT_PADDING = 6;
    private static final String FIRST_COLUMN_RENDERER_KEY =
            PayloadTableSupport.class.getName() + ".firstColumnRenderer";

    private PayloadTableSupport() {
    }

    public static void configure(Table table) {
        if (table == null || table.isDisposed()) {
            return;
        }
        TableColumn[] columns = table.getColumns();
        // Keep both native table headers left-aligned. Data cells are rendered
        // separately below so both numeric columns can be right-aligned.
        for (TableColumn column : columns) {
            column.setAlignment(SWT.LEFT);
        }
        installFirstColumnRenderer(table);
    }

    /**
     * Keeps the native headers left-aligned, while right-aligning only data cells.
     * Headers are not TableItems, so they are deliberately untouched.
     */
    private static void installFirstColumnRenderer(final Table table) {
        if (Boolean.TRUE.equals(table.getData(FIRST_COLUMN_RENDERER_KEY))) {
            return;
        }
        table.setData(FIRST_COLUMN_RENDERER_KEY, Boolean.TRUE);

        table.addListener(SWT.EraseItem, new Listener() {
            @Override
            public void handleEvent(Event event) {
                if (event.index == 0 || event.index == 1) {
                    // Suppress only SWT's default left-aligned data text painting.
                    event.detail &= ~SWT.FOREGROUND;
                }
            }
        });
        table.addListener(SWT.PaintItem, new Listener() {
            @Override
            public void handleEvent(Event event) {
                if ((event.index != 0 && event.index != 1)
                        || !(event.item instanceof TableItem)) {
                    return;
                }
                String text = ((TableItem) event.item).getText(event.index);
                Point size = event.gc.textExtent(text);
                // On native Windows table columns, PaintItem can report a zero
                // event.width. Use the actual TableColumn width instead.
                int cellWidth = table.getColumn(event.index).getWidth();
                int padding = event.index == 0 ? BYTE_INDEX_RIGHT_PADDING : VALUE_RIGHT_PADDING;
                int x = event.x + Math.max(0, cellWidth - size.x - padding);
                int y = event.y + Math.max(0, (event.height - size.y) / 2);
                event.gc.drawText(text, x, y, true);
            }
        });
    }
}
