package com.xeon.view3d;

import com.xeon.model.Tile;

import java.awt.Color;

public record Map3DLabel(
	Tile tile,
	String text,
	Color color,
	Tile warpTarget
)
{
	public Map3DLabel
	{
		tile = copy(tile);
		text = text == null ? "" : text;
		color = color == null ? Color.WHITE : color;
		warpTarget = copy(warpTarget);
	}

	private static Tile copy(Tile tile)
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}
}
