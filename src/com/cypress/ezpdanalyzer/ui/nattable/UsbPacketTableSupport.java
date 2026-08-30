package com.cypress.ezpdanalyzer.ui.nattable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.data.convert.DefaultDisplayConverter;
import org.eclipse.nebula.widgets.nattable.edit.EditConfigAttributes;
import org.eclipse.nebula.widgets.nattable.edit.editor.IComboBoxDataProvider;
import org.eclipse.nebula.widgets.nattable.filterrow.TextMatchingMode;
import org.eclipse.nebula.widgets.nattable.filterrow.combobox.FilterNatCombo;
import org.eclipse.nebula.widgets.nattable.filterrow.combobox.FilterRowComboBoxCellEditor;
import org.eclipse.nebula.widgets.nattable.filterrow.config.FilterRowConfigAttributes;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.layer.LabelStack;
import org.eclipse.nebula.widgets.nattable.layer.cell.ILayerCell;
import org.eclipse.nebula.widgets.nattable.painter.cell.TextPainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.decorator.CustomLineBorderDecorator;
import org.eclipse.nebula.widgets.nattable.style.CellStyleAttributes;
import org.eclipse.nebula.widgets.nattable.style.ConfigAttribute;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.nebula.widgets.nattable.style.HorizontalAlignmentEnum;
import org.eclipse.nebula.widgets.nattable.style.IDisplayModeOrdering;
import org.eclipse.nebula.widgets.nattable.style.IStyle;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.widget.NatCombo;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.LabelProvider;

import com.cypress.ezpdanalyzer.ui.model.USBPacketData;
import com.cypress.ezpdanalyzer.ui.util.DataManager;

/** v1.0p rendering, persistence, and filtering support for USB PD Messages. */
public final class UsbPacketTableSupport {
    public static final String RIGHT_ALIGNED = "EZPD_RIGHT_ALIGNED";
    private static final String PREFERENCE_NODE = "com.cypress.ezpdanalyzer.ui";
    private static final String WIDTH_KEY_PREFIX = "usbPacketTable.columnWidth.";
    private static final int COLUMN_COUNT = 14;
    private static final int MIN_SAVED_WIDTH = 12;
    private static final String FILTER_COLUMN_PREFIX = "FILTER_COLUMN_";
    private static final String HIDE_SELECTED = "Hide selected values";
    private static final int[] MULTI_FILTER_COLUMNS = { 0, 1, 2, 3, 4, 5, 6, 7 };

    private UsbPacketTableSupport() {
    }

    public static void configure(IConfigRegistry registry) {
        // Kept for compatibility with v1.0g/v1.0h and for painters that read
        // the attribute directly.
        registry.registerConfigAttribute(
            CellStyleAttributes.HORIZONTAL_ALIGNMENT,
            HorizontalAlignmentEnum.RIGHT,
            DisplayMode.NORMAL,
            RIGHT_ALIGNED
        );
        // TextPainter obtains its effective alignment from CELL_STYLE. This
        // painter wraps that effective style and overrides only its horizontal
        // alignment, preserving row colors, fonts, and all other attributes.
        registry.registerConfigAttribute(
            CellConfigAttributes.CELL_PAINTER,
            new CustomLineBorderDecorator(new RightAlignedTextPainter()),
            DisplayMode.NORMAL,
            RIGHT_ALIGNED
        );
        // The row-header data cells contain the initial # column. Give them
        // the same right-aligned painter without affecting its # header cell.
        registry.registerConfigAttribute(
            CellConfigAttributes.CELL_PAINTER,
            new CustomLineBorderDecorator(new RightAlignedTextPainter()),
            DisplayMode.NORMAL,
            GridRegion.ROW_HEADER
        );
        configureMultiSelectFilters(registry);
    }

    public static void addRightAlignedColumnLabel(LabelStack labels, int columnIndex) {
        switch (columnIndex) {
            case 3:  // Msg ID
            case 6:  // Obj Count
            case 7:  // Rev (for example, v3)
            case 8:  // Duration (us)
            case 9:  // Delta (us)
            case 10: // Vbus (mV)
            case 12: // Start Time (us)
            case 13: // End Time (us)
                labels.addLabel(RIGHT_ALIGNED);
                break;
            default:
                break;
        }
    }

    /** Keeps the table header consistent with the graph's VBUS spelling. */
    public static String normalizeVbusHeader(String header) {
        return "Vbus(mV)".equals(header) ? "VBUS(mV)" : header;
    }

    /**
     * Restores user-resized USB PD Messages columns, then records a new set of
     * widths whenever the user releases the mouse after a column drag.  The
     * listener is deliberately attached to NatTable rather than a particular
     * resize event class, which keeps this compatible with the bundled
     * NatTable version used by the analyzer.
     */
    public static void installColumnWidthPersistence(
            NatTable table, CustomBodyLayerStack bodyLayerStack) {
        configureMultiSelectFilters(table.getConfigRegistry());
        final DataLayer dataLayer = bodyLayerStack.getBodyDataLayer();
        restoreColumnWidths(dataLayer);
        table.addListener(SWT.MouseUp, new Listener() {
            @Override
            public void handleEvent(Event event) {
                saveColumnWidths(dataLayer);
            }
        });
    }

    /**
     * Replaces Infineon's single-value combo filters with checkbox based
     * multi-select filters.  A separate exclusion-mode checkbox switches the
     * selected values from an include list to an exclude list.  Continuous
     * numeric columns are intentionally left as text filters so their
     * comparison operators keep working.
     */
    public static void configureMultiSelectFilters(IConfigRegistry registry) {
        IComboBoxDataProvider provider = new UsbPacketMultiFilterDataProvider();
        DefaultDisplayConverter cellConverter = new MultiFilterCellConverter();
        DefaultDisplayConverter matcherConverter = new MultiFilterMatcherConverter();

        for (int column : MULTI_FILTER_COLUMNS) {
            String label = FILTER_COLUMN_PREFIX + column;
            FilterRowComboBoxCellEditor editor =
                new EnglishFilterRowComboBoxCellEditor(provider, 14);
            editor.setShowDropdownFilter(true);
            editor.setMultiselectTextBracket("", "");
            editor.setMultiselectValueSeparator(" | ");

            registry.registerConfigAttribute(
                EditConfigAttributes.CELL_EDITOR,
                editor,
                DisplayMode.NORMAL,
                label
            );
            registry.registerConfigAttribute(
                CellConfigAttributes.DISPLAY_CONVERTER,
                cellConverter,
                DisplayMode.NORMAL,
                label
            );
            registry.registerConfigAttribute(
                FilterRowConfigAttributes.FILTER_DISPLAY_CONVERTER,
                matcherConverter,
                DisplayMode.NORMAL,
                label
            );
            registry.registerConfigAttribute(
                FilterRowConfigAttributes.TEXT_MATCHING_MODE,
                TextMatchingMode.REGULAR_EXPRESSION,
                DisplayMode.NORMAL,
                label
            );
        }
    }

    /**
     * NatTable 1.5 localizes its built-in Select All row from the Windows
     * locale.  The Analyzer UI is English, so keep this one filter menu in
     * English without changing NatTable's global locale or resource bundle.
     */
    private static final class EnglishFilterRowComboBoxCellEditor
            extends FilterRowComboBoxCellEditor {
        private EnglishFilterNatCombo activeCombo;

        EnglishFilterRowComboBoxCellEditor(
                IComboBoxDataProvider provider, int maxVisibleItems) {
            super(provider, maxVisibleItems);
        }

        @Override
        public NatCombo createEditorControl(Composite parent) {
            final EnglishFilterNatCombo combo;
            final int style = 42; // Same SWT style used by NatTable 1.5.
            if (iconImage == null) {
                combo = new EnglishFilterNatCombo(
                    parent, cellStyle, maxVisibleItems, style, showDropdownFilter
                );
            } else {
                combo = new EnglishFilterNatCombo(
                    parent, cellStyle, maxVisibleItems, style,
                    iconImage, showDropdownFilter
                );
            }
            activeCombo = combo;
            combo.setCursor(new Cursor(Display.getDefault(), SWT.CURSOR_HAND));
            combo.setMultiselectValueSeparator(multiselectValueSeparator);
            combo.setMultiselectTextBracket(
                multiselectTextPrefix, multiselectTextSuffix
            );
            addNatComboListener(combo);
            combo.addCheckStateListener(event -> {
                if (event.getChecked()) {
                    setCanonicalValue("SELECT_ALL");
                }
                commit(SelectionLayer.MoveDirectionEnum.NONE, !isMultiselect());
            });
            combo.setHideModeListener(selected -> {
                commit(SelectionLayer.MoveDirectionEnum.NONE, false);
            });
            return combo;
        }

        @Override
        public Object getCanonicalValue() {
            Object canonical = super.getCanonicalValue();
            if (activeCombo == null || !activeCombo.isHideModeSelected()) {
                return canonical;
            }

            // ComboBoxCellEditor normally reconstructs its canonical value
            // only from the indices selected in NatCombo's ordinary value
            // table.  Exclusion mode lives in an independent viewer, so add
            // its sentinel explicitly before the filter row is committed.
            List<Object> combined = new ArrayList<Object>();
            if (canonical instanceof Collection<?>) {
                combined.addAll((Collection<?>) canonical);
            } else if (canonical != null) {
                combined.add(canonical);
            }
            combined.add(HIDE_SELECTED);
            return combined;
        }
    }

    /**
     * Keeps NatTable's built-in Select All row limited to real filter values,
     * and places exclusion mode in a separate checkbox-table row.  Treating the
     * exclusion switch as a normal value causes Select All to toggle it and
     * also makes NatTable report misleading checked/grayed states.
     */
    private static final class EnglishFilterNatCombo extends FilterNatCombo {
        private CheckboxTableViewer hideSelectedItemViewer;
        private Label filterModeSeparator;
        private boolean hideModeSelected;
        private HideModeListener hideModeListener;

        EnglishFilterNatCombo(
                Composite parent, IStyle style, int maxVisibleItems,
                int swtStyle, boolean showDropdownFilter) {
            super(parent, style, maxVisibleItems, swtStyle, showDropdownFilter);
        }

        EnglishFilterNatCombo(
                Composite parent, IStyle style, int maxVisibleItems,
                int swtStyle, org.eclipse.swt.graphics.Image image,
                boolean showDropdownFilter) {
            super(parent, style, maxVisibleItems, swtStyle, image, showDropdownFilter);
        }

        @Override
        protected void createDropdownControl(int style) {
            super.createDropdownControl(style);
            installEnglishSelectAllInput();
            createHideSelectedViewer(style);
        }

        @Override
        protected void setDropdownSelection(String[] selection) {
            super.setDropdownSelection(selection);
            installEnglishSelectAllInput();
        }

        @Override
        public void setSelection(String[] selection) {
            boolean hide = containsHideMode(selection);
            super.setSelection(withoutHideMode(selection));
            hideModeSelected = hide;
            if (hideSelectedItemViewer != null &&
                    !hideSelectedItemViewer.getTable().isDisposed()) {
                hideSelectedItemViewer.refresh();
                updateTextControl(false);
            }
        }

        @Override
        protected String[] getTransformedSelection() {
            String[] selected = super.getTransformedSelection();
            if (!isHideModeSelected()) {
                return selected;
            }
            String[] combined = new String[selected.length + 1];
            System.arraycopy(selected, 0, combined, 0, selected.length);
            combined[selected.length] = HIDE_SELECTED;
            return combined;
        }

        @Override
        protected void calculateBounds() {
            super.calculateBounds();
            if (dropdownShell != null && !dropdownShell.isDisposed() &&
                    hideSelectedItemViewer != null &&
                    !hideSelectedItemViewer.getTable().isDisposed()) {
                Point current = dropdownShell.getSize();
                // FormLayout gives the independent checkbox table its full
                // preferred height, including the top and bottom border.  If
                // only getItemHeight() is added to the shell, the value table
                // is left a few pixels short and SWT drops its final row (for
                // example 7, v3, or VBUS_DN) from the visible area.
                int height = hideSelectedItemViewer.getTable().computeSize(
                    SWT.DEFAULT, SWT.DEFAULT, true
                ).y;
                if (filterModeSeparator != null && !filterModeSeparator.isDisposed()) {
                    height += filterModeSeparator.computeSize(
                        SWT.DEFAULT, SWT.DEFAULT, true
                    ).y;
                }

                TableColumn hideColumn =
                    hideSelectedItemViewer.getTable().getColumn(0);
                hideColumn.pack();
                int columnWidth = hideColumn.getWidth();
                if (dropdownTable.getColumnCount() > 0) {
                    columnWidth = Math.max(
                        columnWidth, dropdownTable.getColumn(0).getWidth()
                    );
                    dropdownTable.getColumn(0).setWidth(columnWidth);
                }
                if (selectAllItemViewer.getTable().getColumnCount() > 0) {
                    selectAllItemViewer.getTable().getColumn(0).setWidth(columnWidth);
                }
                hideColumn.setWidth(columnWidth);
                dropdownShell.setSize(
                    Math.max(current.x, columnWidth), current.y + height
                );
            }
        }

        void setHideModeListener(HideModeListener listener) {
            this.hideModeListener = listener;
        }

        private void installEnglishSelectAllInput() {
            if (selectAllItemViewer != null &&
                    !selectAllItemViewer.getTable().isDisposed()) {
                // Replace the viewer input, not only the rendered TableItem.
                // NatTable refreshes this viewer after every value toggle.
                selectAllItemViewer.setInput(Collections.singletonList("Select all"));
                selectAllItemViewer.setCheckStateProvider(new ICheckStateProvider() {
                    @Override
                    public boolean isChecked(Object element) {
                        int itemCount = dropdownTable.getItemCount();
                        return itemCount > 0 &&
                            dropdownTable.getSelectionCount() == itemCount;
                    }

                    @Override
                    public boolean isGrayed(Object element) {
                        // A partial selection is represented by the checked
                        // value rows themselves.  Do not show a misleading
                        // tri-state mark on Select All.
                        return false;
                    }
                });
                selectAllItemViewer.refresh();
            }
        }

        private void createHideSelectedViewer(int style) {
            hideSelectedItemViewer = CheckboxTableViewer.newCheckList(
                dropdownShell,
                (style & ~(SWT.H_SCROLL | SWT.V_SCROLL)) |
                    SWT.NO_SCROLL | SWT.CHECK |
                    HorizontalAlignmentEnum.getSWTStyle(cellStyle) | SWT.SINGLE
            );
            new TableColumn(hideSelectedItemViewer.getTable(), SWT.NONE);
            hideSelectedItemViewer.setContentProvider(
                ArrayContentProvider.getInstance()
            );
            hideSelectedItemViewer.setLabelProvider(new LabelProvider());
            hideSelectedItemViewer.setInput(
                Collections.singletonList(HIDE_SELECTED)
            );
            hideSelectedItemViewer.getTable().setBackground(
                cellStyle.getAttributeValue(CellStyleAttributes.BACKGROUND_COLOR)
            );
            hideSelectedItemViewer.getTable().setForeground(
                cellStyle.getAttributeValue(CellStyleAttributes.FOREGROUND_COLOR)
            );
            hideSelectedItemViewer.getTable().setFont(
                cellStyle.getAttributeValue(CellStyleAttributes.FONT)
            );
            hideSelectedItemViewer.setCheckStateProvider(new ICheckStateProvider() {
                @Override
                public boolean isChecked(Object element) {
                    return hideModeSelected;
                }

                @Override
                public boolean isGrayed(Object element) {
                    return false;
                }
            });

            FormData hideData = new FormData();
            hideData.top = new FormAttachment(selectAllItemViewer.getControl(), 0, SWT.BOTTOM);
            hideData.left = new FormAttachment(0);
            hideData.right = new FormAttachment(100);
            hideSelectedItemViewer.getControl().setLayoutData(hideData);

            filterModeSeparator = new Label(
                dropdownShell, SWT.SEPARATOR | SWT.HORIZONTAL
            );
            FormData separatorData = new FormData();
            separatorData.top = new FormAttachment(
                hideSelectedItemViewer.getControl(), 0, SWT.BOTTOM
            );
            separatorData.left = new FormAttachment(0);
            separatorData.right = new FormAttachment(100);
            filterModeSeparator.setLayoutData(separatorData);

            FormData tableData = (FormData) dropdownTable.getLayoutData();
            tableData.top = new FormAttachment(
                filterModeSeparator, 0, SWT.BOTTOM
            );
            dropdownTable.setLayoutData(tableData);

            hideSelectedItemViewer.getTable().addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    // NatCombo otherwise closes its shell when focus leaves
                    // one of the two built-in tables.
                    showDropdownControl();
                }
            });
            hideSelectedItemViewer.addCheckStateListener(event -> {
                hideModeSelected = event.getChecked();
                hideSelectedItemViewer.refresh();
                updateTextControl(false);
                showDropdownControl();
                if (hideModeListener != null) {
                    hideModeListener.changed(hideModeSelected);
                }
            });
        }

        private boolean isHideModeSelected() {
            return hideModeSelected;
        }

        private static boolean containsHideMode(String[] selection) {
            if (selection != null) {
                for (String value : selection) {
                    if (HIDE_SELECTED.equals(value)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static String[] withoutHideMode(String[] selection) {
            if (selection == null || selection.length == 0) {
                return selection;
            }
            List<String> values = new ArrayList<String>(selection.length);
            for (String value : selection) {
                if (!HIDE_SELECTED.equals(value)) {
                    values.add(value);
                }
            }
            return values.toArray(new String[values.size()]);
        }
    }

    private interface HideModeListener {
        void changed(boolean selected);
    }

    /** Uses the unfiltered capture/import list, so choices remain stable. */
    private static final class UsbPacketMultiFilterDataProvider
            implements IComboBoxDataProvider {
        @Override
        public List<?> getValues(int columnIndex, int rowIndex) {
            Set<String> distinct = new HashSet<String>();
            List<USBPacketData> packets = DataManager.getInstance().getUsbPacketDatas();
            if (packets != null) {
                for (USBPacketData packet : packets) {
                    String value = getColumnValue(packet, columnIndex);
                    if (value != null && !value.isEmpty()) {
                        distinct.add(value);
                    }
                }
            }

            // These columns have small, fixed domains.  Keeping them available
            // before capture also makes the filter behavior predictable.
            if (columnIndex == 3 || columnIndex == 6) {
                for (int value = 0; value <= 7; value++) {
                    distinct.add(Integer.toString(value));
                }
            } else if (columnIndex == 7) {
                distinct.add("v1");
                distinct.add("v2");
                distinct.add("v3");
            }

            List<String> values = new ArrayList<String>(distinct);
            if (columnIndex == 3 || columnIndex == 6) {
                Collections.sort(values, new NumericTextComparator());
            } else {
                Collections.sort(values, String.CASE_INSENSITIVE_ORDER);
            }
            return values;
        }

        private static String getColumnValue(USBPacketData packet, int column) {
            switch (column) {
                case 0: return packet.getOk();
                case 1: return packet.getSop();
                case 2: return packet.getMsg();
                case 3: return packet.getId();
                case 4: return packet.getdRole();
                case 5: return packet.getpRole();
                case 6: return packet.getCount();
                case 7: return packet.getRev();
                default: return null;
            }
        }
    }

    private static final class NumericTextComparator implements Comparator<String> {
        @Override
        public int compare(String left, String right) {
            try {
                return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
            } catch (NumberFormatException ignored) {
                return left.compareToIgnoreCase(right);
            }
        }
    }

    /** Friendly text shown inside the filter-row cell and dropdown. */
    private static final class MultiFilterCellConverter
            extends DefaultDisplayConverter {
        @Override
        public Object canonicalToDisplayValue(Object canonicalValue) {
            if (!(canonicalValue instanceof Collection<?>)) {
                return canonicalValue == null ? "" : canonicalValue.toString();
            }
            Collection<?> selected = (Collection<?>) canonicalValue;
            boolean hide = selected.contains(HIDE_SELECTED);
            StringBuilder display = new StringBuilder(hide ? "Hide: " : "");
            for (Object value : selected) {
                if (HIDE_SELECTED.equals(value)) {
                    continue;
                }
                if (display.length() > (hide ? 6 : 0)) {
                    display.append(" | ");
                }
                display.append(value);
            }
            return display.toString();
        }

        @Override
        public Object displayToCanonicalValue(Object displayValue) {
            return displayValue;
        }
    }

    /** Converts the checked values to one exact include/exclude regex. */
    private static final class MultiFilterMatcherConverter
            extends DefaultDisplayConverter {
        @Override
        public Object canonicalToDisplayValue(Object canonicalValue) {
            if (!(canonicalValue instanceof Collection<?>)) {
                return canonicalValue == null ? "" : canonicalValue.toString();
            }
            Collection<?> selected = (Collection<?>) canonicalValue;
            boolean hide = selected.contains(HIDE_SELECTED);
            StringBuilder alternatives = new StringBuilder();
            for (Object value : selected) {
                if (HIDE_SELECTED.equals(value)) {
                    continue;
                }
                if (alternatives.length() > 0) {
                    alternatives.append('|');
                }
                alternatives.append(Pattern.quote(value.toString()));
            }
            if (alternatives.length() == 0) {
                return "";
            }
            if (hide) {
                return "^(?!(?:" + alternatives + ")$).*$";
            }
            return "^(?:" + alternatives + ")$";
        }

        @Override
        public Object displayToCanonicalValue(Object displayValue) {
            return displayValue;
        }
    }

    private static void restoreColumnWidths(DataLayer dataLayer) {
        IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(PREFERENCE_NODE);
        for (int column = 0; column < COLUMN_COUNT; column++) {
            int width = preferences.getInt(WIDTH_KEY_PREFIX + column, -1);
            if (width >= MIN_SAVED_WIDTH) {
                dataLayer.setColumnWidthByPosition(column, width);
            }
        }
    }

    private static void saveColumnWidths(DataLayer dataLayer) {
        IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(PREFERENCE_NODE);
        for (int column = 0; column < COLUMN_COUNT; column++) {
            preferences.putInt(
                WIDTH_KEY_PREFIX + column,
                dataLayer.getColumnWidthByPosition(column)
            );
        }
        try {
            preferences.flush();
        } catch (org.osgi.service.prefs.BackingStoreException ignored) {
            // A failed preference write must never disturb capture or the UI.
        }
    }

    private static final class RightAlignedTextPainter extends TextPainter {
        RightAlignedTextPainter() {
            // TextPainter adds spacing after calculating right alignment, so
            // a negative value moves the right-aligned glyphs left, creating
            // a 4px inset instead of pushing them beyond the grid boundary.
            super(false, true, -4);
        }

        @Override
        public void paintCell(
                ILayerCell cell, GC gc, Rectangle bounds, IConfigRegistry registry) {
            super.paintCell(cell, gc, bounds, new RightAlignedRegistry(registry));
        }
    }

    private static final class RightAlignedRegistry implements IConfigRegistry {
        private final IConfigRegistry delegate;

        RightAlignedRegistry(IConfigRegistry delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T getConfigAttribute(
                ConfigAttribute<T> attribute, String displayMode, String... configLabels) {
            return forceRight(attribute, delegate.getConfigAttribute(attribute, displayMode, configLabels));
        }

        @Override
        public <T> T getConfigAttribute(
                ConfigAttribute<T> attribute, String displayMode, List<String> configLabels) {
            return forceRight(attribute, delegate.getConfigAttribute(attribute, displayMode, configLabels));
        }

        @Override
        public <T> T getSpecificConfigAttribute(
                ConfigAttribute<T> attribute, String displayMode, String configLabel) {
            return forceRight(attribute, delegate.getSpecificConfigAttribute(attribute, displayMode, configLabel));
        }

        @SuppressWarnings("unchecked")
        private <T> T forceRight(ConfigAttribute<T> attribute, T value) {
            if (attribute == CellConfigAttributes.CELL_STYLE && value instanceof IStyle) {
                return (T) new RightAlignedStyle((IStyle) value);
            }
            return value;
        }

        @Override
        public <T> void registerConfigAttribute(ConfigAttribute<T> attribute, T value) {
            delegate.registerConfigAttribute(attribute, value);
        }

        @Override
        public <T> void registerConfigAttribute(
                ConfigAttribute<T> attribute, T value, String displayMode) {
            delegate.registerConfigAttribute(attribute, value, displayMode);
        }

        @Override
        public <T> void registerConfigAttribute(
                ConfigAttribute<T> attribute, T value, String displayMode, String configLabel) {
            delegate.registerConfigAttribute(attribute, value, displayMode, configLabel);
        }

        @Override
        public <T> void unregisterConfigAttribute(ConfigAttribute<T> attribute) {
            delegate.unregisterConfigAttribute(attribute);
        }

        @Override
        public <T> void unregisterConfigAttribute(ConfigAttribute<T> attribute, String displayMode) {
            delegate.unregisterConfigAttribute(attribute, displayMode);
        }

        @Override
        public <T> void unregisterConfigAttribute(
                ConfigAttribute<T> attribute, String displayMode, String configLabel) {
            delegate.unregisterConfigAttribute(attribute, displayMode, configLabel);
        }

        @Override
        public IDisplayModeOrdering getDisplayModeOrdering() {
            return delegate.getDisplayModeOrdering();
        }
    }

    private static final class RightAlignedStyle implements IStyle {
        private final IStyle delegate;

        RightAlignedStyle(IStyle delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getAttributeValue(ConfigAttribute<T> attribute) {
            if (attribute == CellStyleAttributes.HORIZONTAL_ALIGNMENT) {
                return (T) HorizontalAlignmentEnum.RIGHT;
            }
            return delegate.getAttributeValue(attribute);
        }

        @Override
        public <T> void setAttributeValue(ConfigAttribute<T> attribute, T value) {
            delegate.setAttributeValue(attribute, value);
        }
    }
}
