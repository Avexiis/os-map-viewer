package com.xeon.view3d;

import com.xeon.model.Tile;

import java.awt.Color;
import java.util.List;

public interface Map3DLayer
{
	default boolean entityPickingEnabled()
	{
		return false;
	}

	default Color objectHoverOutlineColor()
	{
		return null;
	}

	default List<Map3DTextSegment> entityHoverText(Map3DEntity.Kind kind, int id)
	{
		return List.of();
	}

	default boolean tileHoverSelectorVisible(boolean objectHovered)
	{
		return true;
	}

	default boolean entityClicked(Map3DEntity entity, Map3DMouseEvent event)
	{
		return false;
	}

	default List<Map3DTileAction> entityActions(Map3DEntity entity)
	{
		return List.of();
	}

	default Map3DOverlay overlay(Map3DRenderContext context)
	{
		return Map3DOverlay.empty();
	}

	default List<Map3DTileAction> tileActions(Map3DMouseEvent event)
	{
		return List.of();
	}

	default Tile clickWarpTarget(Map3DMouseEvent event)
	{
		return null;
	}

	default List<Map3DControlHint> controlHints()
	{
		return List.of();
	}
}
