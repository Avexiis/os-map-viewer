package com.xeon.plugin;

import com.xeon.model.Tile;

import javax.swing.*;

public interface MapViewerPlugin
{
	String id();

	String displayName();

	void install(PluginContext context);

	default void uninstall()
	{
	}

	default void afterShow()
	{
	}

	default Icon icon()
	{
		return null;
	}

	default JPopupMenu actionMenu()
	{
		return null;
	}

	default JComponent leftComponent()
	{
		return null;
	}

	default JComponent rightComponent()
	{
		return null;
	}

	default void planeChanged(int plane)
	{
	}

	default void tileFocused(Tile tile)
	{
	}

	default void viewer3DModeChanged(boolean active)
	{
	}
}
