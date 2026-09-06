package com.xeon.view3d;

import com.xeon.model.Tile;

import java.util.List;

public interface Map3DLayer
{
	/** Enables entity picking while this layer is active. */
	default boolean entityPickingEnabled()
	{
		return false;
	}

	/** Null disables the hovered-object outline. */
	default java.awt.Color objectHoverOutlineColor()
	{
		return null;
	}

	/** Controls the core tile hover overlay while an entity is under the pointer. */
	default boolean tileHoverSelectorVisible(boolean entityHovered)
	{
		return true;
	}

	/** Handles a picked entity click. Return true when the event was consumed. */
	default boolean entityClicked(Map3DEntity entity, Map3DMouseEvent event)
	{
		return false;
	}

	/** Called on Swing's event thread, independently of whether a terrain tile was hit. */
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
