package com.xeon.view3d;

import com.xeon.model.Tile;

import java.awt.Color;

public record Map3DMarker(
	Tile tile,
	Color color,
	String label
)
{
	public Map3DMarker
	{
		tile = copy(tile);
		color = color == null ? Color.WHITE : color;
		label = label == null ? "" : label;
	}

	private static Tile copy(Tile tile)
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}
}
