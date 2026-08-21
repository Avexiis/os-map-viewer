package com.xeon.view3d;

import com.xeon.model.Tile;

import java.util.List;

public interface Map3DLayer
{
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
