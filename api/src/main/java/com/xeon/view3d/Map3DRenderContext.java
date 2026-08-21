package com.xeon.view3d;

import com.xeon.model.Tile;

import java.util.List;

public record Map3DRenderContext(
	Tile cameraTile,
	List<Integer> visibleRegionIds
)
{
	public Map3DRenderContext
	{
		cameraTile = copy(cameraTile);
		visibleRegionIds = visibleRegionIds == null ? List.of() : List.copyOf(visibleRegionIds);
	}

	private static Tile copy(Tile tile)
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}
}
