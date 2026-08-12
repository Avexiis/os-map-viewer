package com.xeon.plugins.shortestpath;

import javax.swing.*;

final class ShortestPathMenu extends JPopupMenu {
    private Runnable onCenterStart;
    private Runnable onCenterTarget;
    private Runnable onRecalculate;
    private Runnable onImportRoute;
    private Runnable onExportRoute;
    private Runnable onClear;

    ShortestPathMenu() {
        JMenuItem centerStart = new JMenuItem("Set start to center");
        JMenuItem centerTarget = new JMenuItem("Set target to center");
        JMenuItem recalculate = new JMenuItem("Recalculate");
        JMenuItem importRoute = new JMenuItem("Import route...");
        JMenuItem exportRoute = new JMenuItem("Export route...");
        JMenuItem clear = new JMenuItem("Clear");

        centerStart.addActionListener(e -> {
            if (onCenterStart != null) {
                onCenterStart.run();
            }
        });
        centerTarget.addActionListener(e -> {
            if (onCenterTarget != null) {
                onCenterTarget.run();
            }
        });
        recalculate.addActionListener(e -> {
            if (onRecalculate != null) {
                onRecalculate.run();
            }
        });
        importRoute.addActionListener(e -> {
            if (onImportRoute != null) {
                onImportRoute.run();
            }
        });
        exportRoute.addActionListener(e -> {
            if (onExportRoute != null) {
                onExportRoute.run();
            }
        });
        clear.addActionListener(e -> {
            if (onClear != null) {
                onClear.run();
            }
        });

        add(centerStart);
        add(centerTarget);
        addSeparator();
        add(recalculate);
        add(importRoute);
        add(exportRoute);
        add(clear);
    }

    void setOnCenterStart(Runnable onCenterStart) {
        this.onCenterStart = onCenterStart;
    }

    void setOnCenterTarget(Runnable onCenterTarget) {
        this.onCenterTarget = onCenterTarget;
    }

    void setOnRecalculate(Runnable onRecalculate) {
        this.onRecalculate = onRecalculate;
    }

    void setOnImportRoute(Runnable onImportRoute) {
        this.onImportRoute = onImportRoute;
    }

    void setOnExportRoute(Runnable onExportRoute) {
        this.onExportRoute = onExportRoute;
    }

    void setOnClear(Runnable onClear) {
        this.onClear = onClear;
    }
}
