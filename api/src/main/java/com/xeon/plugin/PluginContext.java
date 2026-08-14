package com.xeon.plugin;

import com.xeon.config.ConfigManager;
import com.xeon.config.PluginConfig;
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
}
