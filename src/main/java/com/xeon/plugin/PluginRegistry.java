/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.

 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.xeon.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public final class PluginRegistry
{
	private PluginRegistry()
	{
	}

	public static List<MapViewerPlugin> load(MapViewerPlugin... builtIns)
	{
		List<MapViewerPlugin> plugins = new ArrayList<>();
		if (builtIns != null)
		{
			for (MapViewerPlugin plugin : builtIns)
			{
				if (plugin != null)
				{
					plugins.add(plugin);
				}
			}
		}

		ServiceLoader<MapViewerPlugin> loader = ServiceLoader.load(MapViewerPlugin.class);
		for (MapViewerPlugin plugin : loader)
		{
			if (plugin != null && plugins.stream().noneMatch(p -> p.id().equals(plugin.id())))
			{
				plugins.add(plugin);
			}
		}
		return plugins;
	}

	public static List<MapViewerPlugin> loadFromJar(File jarFile) throws Exception
	{
		if (jarFile == null || !jarFile.isFile())
		{
			throw new IllegalArgumentException("Plugin jar does not exist.");
		}
		URLClassLoader loader = new URLClassLoader(
			new URL[]{jarFile.toURI().toURL()},
			PluginRegistry.class.getClassLoader()
		);
		List<MapViewerPlugin> plugins = new ArrayList<>();
		ServiceLoader<MapViewerPlugin> serviceLoader = ServiceLoader.load(MapViewerPlugin.class, loader);
		for (MapViewerPlugin plugin : serviceLoader)
		{
			if (plugin != null)
			{
				plugins.add(plugin);
			}
		}
		if (plugins.isEmpty())
		{
			throw new IllegalArgumentException("No MapViewerPlugin service was found in " + jarFile.getName());
		}
		return plugins;
	}
}
