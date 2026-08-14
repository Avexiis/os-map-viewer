package com.xeon.view;

import com.xeon.model.Tile;

import java.awt.*;
import java.awt.geom.Area;
import java.util.Collection;
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

    public Shape tileArea(Collection<Tile> tiles) {
        Area area = new Area();
        if (tiles == null || tiles.isEmpty()) {
            return area;
        }
        for (Tile tile : tiles) {
            if (tile == null) {
                continue;
            }
            Rectangle rect = tileToRect(tile);
            if (rect != null && !rect.isEmpty()) {
                area.add(new Area(rect));
            }
        }
        return area;
    }

    public Shape tileArea(Rectangle tileBounds, int plane, TilePredicate predicate) {
        Area area = new Area();
        if (tileBounds == null || tileBounds.width <= 0 || tileBounds.height <= 0 || predicate == null) {
            return area;
        }

        int endX = safeEnd(tileBounds.x, tileBounds.width);
        int endY = safeEnd(tileBounds.y, tileBounds.height);
        for (int y = tileBounds.y; y < endY; y++) {
            int runStart = Integer.MIN_VALUE;
            for (int x = tileBounds.x; x < endX; x++) {
                if (predicate.test(x, y, plane)) {
                    if (runStart == Integer.MIN_VALUE) {
                        runStart = x;
                    }
                    continue;
                }
                if (runStart != Integer.MIN_VALUE) {
                    addRun(area, runStart, x - 1, y, plane);
                    runStart = Integer.MIN_VALUE;
                }
            }
            if (runStart != Integer.MIN_VALUE) {
                addRun(area, runStart, endX - 1, y, plane);
            }
        }
        return area;
    }

    public Rectangle regionToRect(int regionId) {
        return mapPanel.regionToRect(regionId);
    }

    public Rectangle regionTileBounds(int regionId) {
        return mapPanel.regionTileBounds(regionId);
    }

    public Rectangle tileBounds(Rectangle mapRect) {
        return mapPanel.tileBounds(mapRect);
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

    public int hoveredRegionId() {
        return mapPanel.getHoveredRegionId();
    }

    public int selectedRegionId() {
        return mapPanel.getSelectedRegionId();
    }

    public Rectangle visibleViewRect() {
        return mapPanel.visibleViewRect();
    }

    public boolean regionSelectionActive() {
        return mapPanel.isRegionSelectionActive();
    }

    private void addRun(Area area, int startX, int endX, int y, int plane) {
        Rectangle first = tileToRect(new Tile(startX, y, plane));
        Rectangle last = tileToRect(new Tile(endX, y, plane));
        if (first == null || last == null || first.isEmpty() || last.isEmpty()) {
            return;
        }
        int left = Math.min(first.x, last.x);
        int top = Math.min(first.y, last.y);
        int right = Math.max(first.x + first.width, last.x + last.width);
        int bottom = Math.max(first.y + first.height, last.y + last.height);
        if (right > left && bottom > top) {
            area.add(new Area(new Rectangle(left, top, right - left, bottom - top)));
        }
    }

    private static int safeEnd(int start, int length) {
        long end = (long) start + length;
        if (end > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (end < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) end;
    }
}
