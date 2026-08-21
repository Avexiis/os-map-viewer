package com.xeon.plugin;

import com.xeon.config.ConfigManager;
import com.xeon.config.PluginConfig;
import com.xeon.model.Tile;
import com.xeon.view.MapView;

import javax.swing.*;
import java.awt.*;

public interface PluginContext
{
	JFrame frame();

	MapView mapPanel();

	void setStatus(String message);

	void promptLoadPluginJar();

	ConfigManager configManager();

	PluginConfig config();

	default Window owner()
	{
		return frame();
	}

	default boolean is3DViewerActive()
	{
		return false;
	}

	default Tile centerTile()
	{
		MapView map = mapPanel();
		return map == null ? null : map.getCenterTile();
	}

	default void focusTile(Tile tile, Double targetZoom)
	{
		MapView map = mapPanel();
		if (map != null)
		{
			map.focusTile(tile, targetZoom);
		}
	}

	default void repaintVisible()
	{
		MapView map = mapPanel();
		if (map != null)
		{
			map.repaintVisible();
		}
	}

	default void invoke3DRenderLater(Runnable task)
	{
		if (task != null)
		{
			task.run();
		}
	}
}
