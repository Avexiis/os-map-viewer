package com.xeon.view;

import com.xeon.model.MapArea;
import com.xeon.model.Tile;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.IntConsumer;

public interface MapView {
    void addLayer(MapLayer layer);

    void removeLayer(MapLayer layer);

    void setActiveTool(MapTool activeTool);

    void clearActiveTool(MapTool tool);

    void addPlaneChangeListener(IntConsumer listener);

    void removePlaneChangeListener(IntConsumer listener);

    void addVisibleAreaChangeListener(Runnable listener);

    void removeVisibleAreaChangeListener(Runnable listener);

    void setShowGrid(boolean value);

    void setShowRegionIds(boolean value);

    void setMapLocked(boolean value);

    boolean isMapLocked();

    Color getMapBackgroundColor();

    void setMapBackgroundColor(Color color);

    void setPlane(int z);

    int getPlane();

    int getPlaneCount();

    double getZoom();

    double getEffectiveZoom();

    Tile getHoverTile();

    int getHoverRegionId();

    boolean isRegionSelectionActive();

    int getTotalWidthTiles();

    int getTotalHeightTiles();

    void focusTile(Tile tile, Double targetZoom);

    void focusRegion(int regionId, int plane, Double targetZoom);

    List<MapArea> searchMapAreas(String query, int limit);

    List<MapArea> mapAreasAt(Tile tile);

    MapArea nearestMapArea(Tile tile, int maxDistanceTiles);

    Tile getCenterTile();

    int getCenterRegionId();

    Tile pointToTile(Point point);

    int regionIdFor(Tile tile);

    Rectangle tileToRect(Tile tile);

    Rectangle regionToRect(int regionId);

    List<Integer> visibleRegionIds(Rectangle visibleMap);

    List<Integer> currentVisibleRegionIds();

    void repaintVisible();

    void repaintTile(Tile tile);

    void repaintRegion(int regionId);
}
