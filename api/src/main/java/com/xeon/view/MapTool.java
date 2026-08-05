package com.xeon.view;

public interface MapTool {
    MapTool NONE = new MapTool() {
    };

    default boolean mouseClicked(MapMouseEvent event) {
        return false;
    }

    default void mouseMoved(MapMouseEvent event) {
    }

    default boolean arrowStep(int dx, int dy) {
        return false;
    }

    default void visibleAreaChanged() {
    }

    default boolean supportsRegionSelection() {
        return false;
    }
}
