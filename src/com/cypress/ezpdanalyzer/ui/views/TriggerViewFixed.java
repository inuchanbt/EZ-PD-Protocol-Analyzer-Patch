package com.cypress.ezpdanalyzer.ui.views;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import com.cypress.ezpdanalyzer.ui.usb.PDUtils;
import com.cypress.ezpdanalyzer.ui.util.DataManager;
import com.cypress.ezpdanalyzer.ui.util.EZPDUtil;

public class TriggerViewFixed extends TriggerView {
    private Combo classCombo;
    private Combo typeCombo;
    private Combo sopCombo;
    private Button setButton;
    private Button clearButton;
    private Button startSnoButton;
    private Button endSnoButton;
    private Button sopButton;
    private Button msgTypeButton;
    private Button countButton;
    private Button msgIdButton;
    private Text startSnoText;
    private Text endSnoText;
    private Text countText;
    private Text msgIdText;

    // The vendor Set handler sends Combo.getSelectionIndex() directly to the
    // device.  This mapping keeps that on-wire index intact after reserved
    // entries have been removed from the visible Combo.
    private int[] visibleTypeIndices = new int[0];

    public void createPartControl(Composite parent) {
        super.createPartControl(parent);

        List<Combo> combos = new ArrayList<Combo>();
        List<Button> buttons = new ArrayList<Button>();
        List<Text> texts = new ArrayList<Text>();
        collect(parent, combos, buttons, texts);

        for (Combo c : combos) {
            String[] items = c.getItems();
            if (items.length == 3 && "CONTROL".equals(items[0]) && "DATA".equals(items[1])) {
                classCombo = c;
            } else if (items.length == 32 && "C_RSVD0".equals(items[0])) {
                typeCombo = c;
            }
        }

        for (Button b : buttons) {
            if ("Set".equals(b.getText())) setButton = b;
            if ("Clear".equals(b.getText())) clearButton = b;
            if ("Start Sno".equals(b.getText())) startSnoButton = b;
            if ("End Sno".equals(b.getText())) endSnoButton = b;
            if ("SOP".equals(b.getText())) sopButton = b;
            if ("Msg Type".equals(b.getText())) msgTypeButton = b;
            if ("Count".equals(b.getText())) countButton = b;
            if ("Msg ID".equals(b.getText())) msgIdButton = b;
        }

        // TriggerView creates its Text controls in this fixed order.
        if (texts.size() >= 4) {
            startSnoText = texts.get(0);
            endSnoText = texts.get(1);
            countText = texts.get(2);
            msgIdText = texts.get(3);
        }
        if (combos.size() >= 1) sopCombo = combos.get(0);

        installDictionaryFix();
        installMappedSet();
        installRealClear();

        // One initial vendor connection-state check only.
        super.setFocus();

        if (clearButton != null && !clearButton.isDisposed()) clearButton.setEnabled(true);
        AdvancedFeatureUsbSync.registerTrigger(setButton);
    }

    public void setFocus() {
        // No focus-driven USB access after creation.
        synchronizeMessageTypes();
        if (clearButton != null && !clearButton.isDisposed()) clearButton.setEnabled(true);
    }

    private void installDictionaryFix() {
        if (classCombo == null || typeCombo == null) return;

        int ci = classCombo.getSelectionIndex();
        int ti = typeCombo.getSelectionIndex();
        if (ci < 0 || ci > 2) ci = 0;

        classCombo.setItems(PDUtils.MSG_CLASS);
        classCombo.select(ci);
        updateTypes(ci, ti);

        classCombo.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                updateTypes(classCombo.getSelectionIndex(), typeCombo.getSelectionIndex());
            }
        });
    }

    private void synchronizeMessageTypes() {
        if (classCombo == null || typeCombo == null || classCombo.isDisposed() || typeCombo.isDisposed()) return;
        int ci = classCombo.getSelectionIndex();
        int ti = typeCombo.getSelectionIndex();
        if (ci < 0 || ci > 2) {
            ci = 0;
            classCombo.select(0);
        }
        updateTypes(ci, ti);
    }

    private void updateTypes(int ci, int preferred) {
        if (typeCombo == null || typeCombo.isDisposed()) return;
        String[] source;
        switch (ci) {
            case 1: source = PDUtils.DATA_MSG_TYPE; break;
            case 2: source = PDUtils.EXTD_MSG_TYPE; break;
            default: source = PDUtils.CTRL_MSG_TYPE; break;
        }

        int rawPreferred = rawTypeIndex(preferred);
        List<String> labels = new ArrayList<String>();
        List<Integer> indices = new ArrayList<Integer>();
        for (int i = 0; i < source.length; i++) {
            if (!isReservedMessageType(ci, source[i])) {
                labels.add(source[i]);
                indices.add(Integer.valueOf(i));
            }
        }

        String[] items = labels.toArray(new String[labels.size()]);
        visibleTypeIndices = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            visibleTypeIndices[i] = indices.get(i).intValue();
        }
        typeCombo.setItems(items);
        int visible = visibleIndexForRawType(rawPreferred);
        if (visible >= 0) typeCombo.select(visible);
        else if (items.length > 0) typeCombo.select(0);
    }

    private boolean isReservedMessageType(int messageClass, String name) {
        if (messageClass == 0) {
            return "C_RSVD0".equals(name) || name.matches("C_RSVD(2[5-9]|3[0-1])");
        }
        if (messageClass == 1) {
            return "D_RSVD0".equals(name) ||
                "D_RSVD13".equals(name) || "D_RSVD14".equals(name) ||
                name.matches("D_RSVD(1[6-9]|2[0-9]|3[0-1])");
        }
        return "E_RSVD0".equals(name) ||
            name.matches("E_RSVD(1[9]|2[0-9]|3[0-1])");
    }

    private int rawTypeIndex(int visibleIndex) {
        if (visibleIndex >= 0 && visibleIndex < visibleTypeIndices.length) {
            return visibleTypeIndices[visibleIndex];
        }
        return visibleIndex;
    }

    private int visibleIndexForRawType(int rawIndex) {
        for (int i = 0; i < visibleTypeIndices.length; i++) {
            if (visibleTypeIndices[i] == rawIndex) return i;
        }
        return -1;
    }

    private void installMappedSet() {
        if (setButton == null) return;

        // Replace the vendor listener: it treats the displayed index as the
        // PD message-type number, which is no longer true after filtering.
        for (Listener listener : setButton.getListeners(SWT.Selection)) {
            setButton.removeListener(SWT.Selection, listener);
        }
        setButton.addListener(SWT.Selection, new Listener() {
            public void handleEvent(org.eclipse.swt.widgets.Event event) {
                sendMappedTrigger();
            }
        });
    }

    private void sendMappedTrigger() {
        try {
            byte[] cmd = new byte[64];
            cmd[0] = 0x02; // CMD_TRIGGER
            cmd[4] = selected(startSnoButton) ? (byte) 1 : 0;
            cmd[5] = selected(endSnoButton) ? (byte) 1 : 0;
            cmd[6] = selected(sopButton) ? (byte) 1 : 0;
            cmd[7] = selected(msgTypeButton) ? (byte) 1 : 0;
            cmd[8] = selected(countButton) ? (byte) 1 : 0;
            cmd[9] = selected(msgIdButton) ? (byte) 1 : 0;
            cmd[10] = (byte) rawTypeIndex(typeCombo.getSelectionIndex());
            cmd[11] = (byte) classCombo.getSelectionIndex();
            cmd[12] = (byte) sopCombo.getSelectionIndex();
            cmd[13] = (byte) Integer.parseInt(countText.getText());
            cmd[14] = (byte) Integer.parseInt(msgIdText.getText());
            putUint32(cmd, 16, Long.parseLong(startSnoText.getText()));
            putUint32(cmd, 20, Long.parseLong(endSnoText.getText()));

            if (DataManager.getInstance().getUsbTransfer() == null) return;
            if (DataManager.getInstance().getUsbTransfer().setTigger(cmd)) {
                EZPDUtil.addDeviceStatus(EZPDUtil.getActiveViewSite(), "Trigger Set Success");
            } else {
                EZPDUtil.addDeviceStatus(EZPDUtil.getActiveViewSite(),
                    "Trigger Set Failed. Please check settings.");
                System.err.println("[TriggerViewFixed] Hardware trigger set transfer failed.");
            }
        } catch (Throwable t) {
            System.err.println("[TriggerViewFixed] Hardware trigger set unavailable: " + t);
        }
    }

    private static boolean selected(Button button) {
        return button != null && !button.isDisposed() && button.getSelection();
    }

    private static void putUint32(byte[] target, int offset, long value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        target[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private void installRealClear() {
        if (clearButton == null) return;
        clearButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                sendHardwareTriggerClear();
                if (classCombo != null && !classCombo.isDisposed()) {
                    classCombo.select(0);
                    updateTypes(0, 1); // GOODCRC keeps its original raw value: 1.
                }
            }
        });
    }

    private void sendHardwareTriggerClear() {
        try {
            byte[] cmd = new byte[64];
            cmd[0] = 0x02; // CMD_TRIGGER
            // bytes 4..9 remain zero: disable all six trigger criteria.
            if (DataManager.getInstance().getUsbTransfer() == null) return;
            if (!DataManager.getInstance().getUsbTransfer().setTigger(cmd)) {
                System.err.println("[TriggerViewFixed] Hardware trigger clear transfer failed.");
            }
        } catch (Throwable t) {
            System.err.println("[TriggerViewFixed] Hardware trigger clear unavailable: " + t);
        }
    }

    private static void collect(Composite c, List<Combo> combos, List<Button> buttons,
            List<Text> texts) {
        for (Control child : c.getChildren()) {
            if (child instanceof Combo) combos.add((Combo)child);
            if (child instanceof Button) buttons.add((Button)child);
            if (child instanceof Text) texts.add((Text)child);
            if (child instanceof Composite) collect((Composite)child, combos, buttons, texts);
        }
    }
}
