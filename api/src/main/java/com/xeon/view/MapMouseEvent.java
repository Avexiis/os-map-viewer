package com.xeon.view;

import com.xeon.model.Tile;

import java.awt.*;
import java.awt.event.MouseEvent;

public final class MapMouseEvent {
    private final MapView mapPanel;
    private final MouseEvent mouseEvent;
    private final Tile tile;
    private final int regionId;

    public MapMouseEvent(MapView mapPanel, MouseEvent mouseEvent, Tile tile, int regionId) {
        this.mapPanel = mapPanel;
        this.mouseEvent = mouseEvent;
        this.tile = tile;
        this.regionId = regionId;
    }

    public MapView mapPanel() {
        return mapPanel;
    }

    public MouseEvent source() {
        return mouseEvent;
    }

    public Point point() {
        return mouseEvent.getPoint();
    }

    public Tile tile() {
        return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
    }

    public int regionId() {
        return regionId;
    }

    public boolean isAdditiveSelection() {
        return mouseEvent.isControlDown() || mouseEvent.isMetaDown();
    }

    public boolean isShiftDown() {
        return mouseEvent.isShiftDown();
    }
}
