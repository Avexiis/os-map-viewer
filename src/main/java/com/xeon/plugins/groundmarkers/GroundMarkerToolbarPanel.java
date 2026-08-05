package com.xeon.plugins.groundmarkers;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

public final class GroundMarkerToolbarPanel extends JPopupMenu {
    public enum ExportAction {
        CURRENT_TO_CLIPBOARD("Current region to clipboard"),
        ALL_TO_CLIPBOARD("All markers to clipboard"),
        SELECTED_TO_CLIPBOARD("Selected regions to clipboard"),
        CURRENT_TO_FILE("Current region to file"),
        ALL_TO_FILE("All markers to file"),
        SELECTED_TO_FILE("Selected regions to file");

        private final String label;

        ExportAction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public final Event<ExportAction> onExport = new Event<>();

    private Consumer<ActionEvent> onImportRuneLite;
    private Consumer<ActionEvent> onImportHdos;
    private Consumer<ActionEvent> onImportJson;

    public GroundMarkerToolbarPanel() {
        buildMenu();
    }

    public void setOnImportRuneLite(Consumer<ActionEvent> onImportRuneLite) {
        this.onImportRuneLite = onImportRuneLite;
    }

    public void setOnImportHdos(Consumer<ActionEvent> onImportHdos) {
        this.onImportHdos = onImportHdos;
    }

    public void setOnImportJson(Consumer<ActionEvent> onImportJson) {
        this.onImportJson = onImportJson;
    }

    private void buildMenu() {
        JMenuItem importRuneLite = new JMenuItem("Import from RuneLite");
        importRuneLite.addActionListener(e -> {
            if (onImportRuneLite != null) {
                onImportRuneLite.accept(e);
            }
        });
        JMenuItem importHdos = new JMenuItem("Import from HDOS");
        importHdos.addActionListener(e -> {
            if (onImportHdos != null) {
                onImportHdos.accept(e);
            }
        });
        JMenuItem importJson = new JMenuItem("Import JSON file");
        importJson.addActionListener(e -> {
            if (onImportJson != null) {
                onImportJson.accept(e);
            }
        });
        add(importRuneLite);
        add(importHdos);
        add(importJson);
        addSeparator();

        JMenu exportMenu = new JMenu("Export");
        for (ExportAction action : ExportAction.values()) {
            JMenuItem item = new JMenuItem(action.label());
            item.addActionListener(e -> onExport.emit(action));
            exportMenu.add(item);
        }
        add(exportMenu);
    }

    public static final class Event<T> {
        private final java.util.List<Consumer<T>> listeners = new java.util.ArrayList<>();

        public void addListener(Consumer<T> listener) {
            listeners.add(listener);
        }

        public void emit(T value) {
            for (Consumer<T> listener : listeners) {
                listener.accept(value);
            }
        }
    }
}
