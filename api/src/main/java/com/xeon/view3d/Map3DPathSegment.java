package com.xeon.view3d;

import com.xeon.model.Tile;

import java.awt.Color;

public record Map3DPathSegment(
	Tile start,
	Tile end,
	Color color,
	boolean dashed
)
{
	public Map3DPathSegment
	{
		start = copy(start);
		end = copy(end);
		color = color == null ? Color.WHITE : color;
	}

	private static Tile copy(Tile tile)
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}
}
