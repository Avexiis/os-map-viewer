package com.xeon.view;

import com.xeon.model.MapArea;
import com.xeon.model.Tile;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.IntConsumer;

public interface MapView
{
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

	default int getHoveredRegionId()
	{
		return getHoverRegionId();
	}

	default void addHoveredRegionChangeListener(RegionChangeListener listener)
	{
	}

	default void removeHoveredRegionChangeListener(RegionChangeListener listener)
	{
	}

	default int getSelectedRegionId()
	{
		return -1;
	}

	default void setSelectedRegionId(int regionId)
	{
	}

	default void clearSelectedRegionId()
	{
		setSelectedRegionId(-1);
	}

	default void addSelectedRegionChangeListener(RegionChangeListener listener)
	{
	}

	default void removeSelectedRegionChangeListener(RegionChangeListener listener)
	{
	}

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

	default Rectangle visibleViewRect()
	{
		return new Rectangle();
	}

	Tile pointToTile(Point point);

	int regionIdFor(Tile tile);

	Rectangle tileToRect(Tile tile);

	Rectangle regionToRect(int regionId);

	default Rectangle regionTileBounds(int regionId)
	{
		return new Rectangle();
	}

	default Rectangle tileBounds(Rectangle mapRect)
	{
		return new Rectangle();
	}

	List<Integer> visibleRegionIds(Rectangle visibleMap);

	List<Integer> currentVisibleRegionIds();

	void repaintVisible();

	void repaintTile(Tile tile);

	void repaintRegion(int regionId);
}
