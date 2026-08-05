package com.xeon.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public final class PluginRegistry {
    private PluginRegistry() {
    }

    public static List<MapViewerPlugin> load(MapViewerPlugin... builtIns) {
        List<MapViewerPlugin> plugins = new ArrayList<>();
        if (builtIns != null) {
            for (MapViewerPlugin plugin : builtIns) {
                if (plugin != null) {
                    plugins.add(plugin);
                }
            }
        }

        ServiceLoader<MapViewerPlugin> loader = ServiceLoader.load(MapViewerPlugin.class);
        for (MapViewerPlugin plugin : loader) {
            if (plugin != null && plugins.stream().noneMatch(p -> p.id().equals(plugin.id()))) {
                plugins.add(plugin);
            }
        }
        return plugins;
    }

    public static List<MapViewerPlugin> loadFromJar(File jarFile) throws Exception {
        if (jarFile == null || !jarFile.isFile()) {
            throw new IllegalArgumentException("Plugin jar does not exist.");
        }
        URLClassLoader loader = new URLClassLoader(
                new URL[]{jarFile.toURI().toURL()},
                PluginRegistry.class.getClassLoader()
        );
        List<MapViewerPlugin> plugins = new ArrayList<>();
        ServiceLoader<MapViewerPlugin> serviceLoader = ServiceLoader.load(MapViewerPlugin.class, loader);
        for (MapViewerPlugin plugin : serviceLoader) {
            if (plugin != null) {
                plugins.add(plugin);
            }
        }
        if (plugins.isEmpty()) {
            throw new IllegalArgumentException("No MapViewerPlugin service was found in " + jarFile.getName());
        }
        return plugins;
    }
}
