package com.xeon.util;

import javax.swing.*;
import javax.swing.text.*;
import java.text.ParseException;

public class NumberField extends JFormattedTextField {
    public NumberField(int columns) {
        super(new NumberFormatter());
        setColumns(columns);
    }

    public Integer getInt(Integer def) {
        try { commitEdit(); } catch (ParseException ignore) {}
        Object v = getValue();
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(getText().trim()); } catch (Exception e) { return def; }
    }

    public void setInt(Integer v) {
        if (v == null) setText(""); else setValue(v);
    }

    private static class NumberFormatter extends DefaultFormatter {
        @Override public Object stringToValue(String string) throws ParseException {
            if (string == null || string.trim().isEmpty()) return null;
            try { return Integer.parseInt(string.trim()); } catch (NumberFormatException e) {
                throw new ParseException("Not a number", 0);
            }
        }
        @Override public String valueToString(Object value) throws ParseException {
            if (value == null) return "";
            return String.valueOf(value);
        }
    }
}
