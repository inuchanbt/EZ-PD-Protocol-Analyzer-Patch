package com.cypress.ezpdanalyzer.ui.views;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class TerminationsFixed extends Terminations {
    private Button setButton;
    private Button clearButton;

    public void createPartControl(Composite parent) {
        super.createPartControl(parent);

        List<Button> buttons = new ArrayList<Button>();
        collect(parent, buttons);
        for (Button b : buttons) {
            if ("Set".equals(b.getText())) setButton = b;
            if ("Clear".equals(b.getText())) clearButton = b;
        }

        // One initial vendor connection-state check only.
        super.setFocus();

        if (clearButton != null && !clearButton.isDisposed()) clearButton.setEnabled(true);
        AdvancedFeatureUsbSync.registerTerminations(setButton);
    }

    public void setFocus() {
        // No focus-driven USB access after creation.
        if (clearButton != null && !clearButton.isDisposed()) clearButton.setEnabled(true);
    }

    private static void collect(Composite c, List<Button> buttons) {
        for (Control child : c.getChildren()) {
            if (child instanceof Button) buttons.add((Button)child);
            if (child instanceof Composite) collect((Composite)child, buttons);
        }
    }
}
