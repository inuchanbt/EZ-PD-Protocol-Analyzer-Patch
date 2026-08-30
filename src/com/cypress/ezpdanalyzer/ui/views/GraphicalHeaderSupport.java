package com.cypress.ezpdanalyzer.ui.views;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

/** Arranges Graphical's live-coordinate groups as X, Y, ΔX, ΔY. */
public final class GraphicalHeaderSupport {
    private GraphicalHeaderSupport() {
    }

    public static void configure(GraphSelectorComposite selector, Group group) {
        if (selector == null || group == null || group.isDisposed()) {
            return;
        }

        Label x = findLabel(group, "X:");
        Label y = findLabel(group, "Y:");
        Label deltaX = findLabel(group, "ΔX:");
        Label deltaY = findLabel(group, "ΔY:");
        Control[] controls = group.getChildren();
        if (controls.length < 2 || x == null || y == null || deltaX == null || deltaY == null) {
            return;
        }

        // Controls are placed immediately after the X-scale combo.  Moving a
        // pair below the previous pair preserves the associated value label.
        Control previous = controls[1];
        previous = moveBelow(x, previous);
        previous = moveBelow(selector.getLblX_Value(), previous);
        previous = moveBelow(y, previous);
        previous = moveBelow(selector.getLblY_Value(), previous);
        previous = moveBelow(deltaX, previous);
        previous = moveBelow(selector.getLblX1_Value(), previous);
        previous = moveBelow(deltaY, previous);
        moveBelow(selector.getLblY1_Value(), previous);
        setInitialUnit(selector.getLblX1_Value(), "m");
        setInitialUnit(selector.getLblY1_Value(), "\u00B5");
        group.layout(true, true);
    }

    private static Control moveBelow(Control control, Control previous) {
        if (control != null && !control.isDisposed()) {
            control.moveBelow(previous);
        }
        return control;
    }

    private static Label findLabel(Group group, String text) {
        for (Control control : group.getChildren()) {
            if (control instanceof Label && text.equals(((Label) control).getText())) {
                return (Label) control;
            }
        }
        return null;
    }

    private static void setInitialUnit(Label label, String unit) {
        if (label != null && !label.isDisposed() && "0".equals(label.getText())) {
            label.setText("0" + unit);
        }
    }
}
