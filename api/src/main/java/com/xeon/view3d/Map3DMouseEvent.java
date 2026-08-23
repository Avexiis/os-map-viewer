package com.xeon.view3d;

import com.xeon.model.Tile;

public record Map3DMouseEvent(
	Tile tile,
	int button,
	boolean popupTrigger,
	boolean additiveSelection,
	boolean shiftDown
)
{
	public Map3DMouseEvent
	{
		tile = copy(tile);
	}

	private static Tile copy(Tile tile)
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}
}
