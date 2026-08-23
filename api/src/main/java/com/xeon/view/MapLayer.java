package com.xeon.view;

import com.xeon.model.Tile;

import java.awt.*;

public interface MapLayer
{
	void paint(MapRenderContext context, Graphics2D g, Rectangle visibleMap);

	default String tooltipText(MapRenderContext context, Tile tile, int regionId)
	{
		return null;
	}
}
