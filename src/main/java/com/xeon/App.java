package com.xeon;

import com.xeon.controller.MapViewerController;
import com.xeon.util.Ui;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;

public class App {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignore) {
            }
        }

        EventQueue.invokeLater(() -> {
            Ui.installGlobalFont("Segoe UI", 13);
            try {
                new MapViewerController().show();
            } catch (Exception ex) {
                Ui.error("Failed to start: " + ex.getMessage());
            }
        });
    }
}
