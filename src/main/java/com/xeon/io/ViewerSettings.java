package com.xeon.io;

import com.xeon.config.ConfigManager;

import java.awt.Color;

public final class ViewerSettings {
    public static final int MEMORY_BUDGET_UNLIMITED_MB = -1;

    private static final String CORE = ConfigManager.CORE_NAMESPACE;
    private static final String KEY_PLUGINS_ENABLED = "plugins.enabledOnStartup";
    private static final String KEY_BACKGROUND = "map.background";
    private static final String KEY_JUMP_TO_LAST_REGION = "map.jumpToLastRegionOnStart";
    private static final String KEY_LAST_REGION_ID = "map.last.regionId";
    private static final String KEY_LAST_PLANE = "map.last.plane";
    private static final String KEY_LAST_ZOOM = "map.last.zoom";
    private static final String KEY_MEMORY_BUDGET_MB = "map.memoryBudgetMb";
    private static final String DEFAULT_BACKGROUND = "#000000";
    private static final int DEFAULT_MEMORY_BUDGET_MB = 512;

    private final ConfigManager configManager;

    private ViewerSettings(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public static ViewerSettings load(ConfigManager configManager) {
        if (configManager == null) {
            throw new IllegalArgumentException("configManager must not be null");
        }
        return new ViewerSettings(configManager);
    }

    public boolean pluginsEnabledOnStartup() {
        return configManager.getBoolean(CORE, KEY_PLUGINS_ENABLED, false);
    }

    public void setPluginsEnabledOnStartup(boolean value) {
        configManager.setBoolean(CORE, KEY_PLUGINS_ENABLED, value);
    }

    public Color mapBackgroundColor() {
        return parseColor(configManager.getString(CORE, KEY_BACKGROUND, DEFAULT_BACKGROUND), Color.BLACK);
    }

    public void setMapBackgroundColor(Color color) {
        if (color == null) {
            return;
        }
        configManager.setString(CORE, KEY_BACKGROUND, colorToHex(color));
    }

    public boolean jumpToLastRegionOnStart() {
        return configManager.getBoolean(CORE, KEY_JUMP_TO_LAST_REGION, false);
    }

    public void setJumpToLastRegionOnStart(boolean value) {
        configManager.setBoolean(CORE, KEY_JUMP_TO_LAST_REGION, value);
    }

    public int lastRegionId() {
        return configManager.getInt(CORE, KEY_LAST_REGION_ID, -1);
    }

    public int lastPlane() {
        return configManager.getInt(CORE, KEY_LAST_PLANE, 0);
    }

    public double lastZoom() {
        return configManager.getDouble(CORE, KEY_LAST_ZOOM, 1.0);
    }

    public void setLastView(int regionId, int plane, double zoom) {
        if (regionId < 0) {
            return;
        }
        configManager.setInt(CORE, KEY_LAST_REGION_ID, regionId);
        configManager.setInt(CORE, KEY_LAST_PLANE, Math.max(0, plane));
        configManager.setDouble(CORE, KEY_LAST_ZOOM, Math.max(1.0, zoom));
    }

    public int memoryBudgetMb() {
        int value = configManager.getInt(CORE, KEY_MEMORY_BUDGET_MB, DEFAULT_MEMORY_BUDGET_MB);
        if (value == MEMORY_BUDGET_UNLIMITED_MB) {
            return MEMORY_BUDGET_UNLIMITED_MB;
        }
        return Math.max(DEFAULT_MEMORY_BUDGET_MB, value);
    }

    public void setMemoryBudgetMb(int value) {
        configManager.setInt(CORE, KEY_MEMORY_BUDGET_MB,
                value == MEMORY_BUDGET_UNLIMITED_MB ? MEMORY_BUDGET_UNLIMITED_MB : Math.max(DEFAULT_MEMORY_BUDGET_MB, value));
    }

    private static String colorToHex(Color color) {
        return String.format("#%06X", color.getRGB() & 0x00FFFFFF);
    }

    private static String normalizeColor(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}")) {
            return fallback;
        }
        return "#" + value.toUpperCase();
    }

    private static Color parseColor(String raw, Color fallback) {
        String normalized = normalizeColor(raw, null);
        if (normalized == null) {
            return fallback;
        }
        return new Color(Integer.parseInt(normalized.substring(1), 16));
    }
}
