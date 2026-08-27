package com.xeon.view3d;

import com.xeon.model.Tile;

import java.awt.Color;

public record Map3DObjectOverlay(
	Tile tile,
	int objectId,
	Color fillColor,
	Color outlineColor,
	String label
)
{
	public Map3DObjectOverlay(Tile tile, int objectId, Color fillColor, Color outlineColor)
	{
		this(tile, objectId, fillColor, outlineColor, "");
	}

	public Map3DObjectOverlay
	{
		tile = copy(tile);
		fillColor = fillColor == null ? new Color(0x3300FF00, true) : fillColor;
		outlineColor = outlineColor == null ? Color.GREEN : outlineColor;
		label = label == null ? "" : label;
	}

	private static Tile copy(Tile tile)
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}
}
