package com.xeon.util;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Enumeration;

public class Ui {
    public static void installGlobalFont(String name, int size) {
        try {
            Font f = new Font(name, Font.PLAIN, size);
            Enumeration<Object> keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                Object val = UIManager.get(key);
                if (val instanceof Font) UIManager.put(key, f);
            }
        } catch (Exception ignore) {}
    }

    public static void info(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void warn(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    public static void error(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static File chooseJson(Component parent, String title) {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle(title);
        if (ch.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) return ch.getSelectedFile();
        return null;
    }

    public static File saveJson(Component parent, String title) {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle(title);
        if (ch.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) return ch.getSelectedFile();
        return null;
    }
}
