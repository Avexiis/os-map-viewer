package com.xeon.view;

import com.xeon.model.Tile;

import java.awt.*;
import java.util.List;

public final class MapRenderContext {
    private final MapView mapPanel;

    public MapRenderContext(MapView mapPanel) {
        this.mapPanel = mapPanel;
    }

    public int plane() {
        return mapPanel.getPlane();
    }

    public double zoom() {
        return mapPanel.getZoom();
    }

    public double effectiveZoom() {
        return mapPanel.getEffectiveZoom();
    }

    public Rectangle tileToRect(Tile tile) {
        return mapPanel.tileToRect(tile);
    }

    public Rectangle regionToRect(int regionId) {
        return mapPanel.regionToRect(regionId);
    }

    public List<Integer> visibleRegionIds(Rectangle visibleMap) {
        return mapPanel.visibleRegionIds(visibleMap);
    }

    public Tile hoverTile() {
        return mapPanel.getHoverTile();
    }

    public int hoverRegionId() {
        return mapPanel.getHoverRegionId();
    }

    public boolean regionSelectionActive() {
        return mapPanel.isRegionSelectionActive();
    }
}
