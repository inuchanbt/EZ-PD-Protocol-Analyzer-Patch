package com.cypress.ezpdanalyzer.ui.jfreechart;

import java.awt.Color;
import java.lang.reflect.Method;

/** Removes only the fill behind Start/End marker labels. */
public final class MarkerLabelBackgroundSupport {
    private MarkerLabelBackgroundSupport() {
    }

    public static void clear(Object marker) {
        if (marker == null) {
            return;
        }
        try {
            Method setBackground = marker.getClass().getMethod(
                "setLabelBackgroundColor", Color.class
            );
            setBackground.invoke(marker, new Color(0, 0, 0, 0));
        } catch (Throwable ignored) {
            // A cosmetic failure must never affect graph selection.
        }
    }
}
