package com.xeon.view3d;

import com.xeon.model.Tile;

import java.awt.Color;

public record Map3DTileOverlay(
	Tile tile,
	Color fillColor,
	Color outlineColor,
	String label
)
{
	public Map3DTileOverlay
	{
		tile = copy(tile);
		fillColor = fillColor == null ? new Color(0x55FFFFFF, true) : fillColor;
		outlineColor = outlineColor == null ? new Color(0xFFFFFFFF, true) : outlineColor;
		label = label == null ? "" : label;
	}

	private static Tile copy(Tile tile)
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}
}
