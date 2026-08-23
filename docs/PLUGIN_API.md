# Plugin API

OS Map Viewer has a small core and optional plugins. The core owns the map viewer, map controls, plugin loading, and persistent config. Plugins add features such as sidebars, map overlays, map click tools, and import/export actions.

The bundled Ground Markers feature is also a plugin, so external plugins should use the same API instead of changing the core viewer.

## API JAR

Plugin authors should compile against the API module, not the full application.

Build the plugin API JAR with either command:

```bash
./gradlew :api:jar
./gradlew buildPluginApi
```

The JAR is written here:

```text
api/build/libs/os-map-viewer-api.jar
```

Use that JAR as a compile-time dependency for plugins. The JAR includes the small API surface and Gson for compile-time convenience. The released OS Map Viewer application bundles those classes, so plugin JARs should not shade or bundle `os-map-viewer-api.jar`.

In Gradle, a plugin project can reference it like this:

```kotlin
dependencies {
    compileOnly(files("path/to/os-map-viewer-api.jar"))
}
```

## Quick Start

A plugin is a Java class that implements `com.xeon.plugin.MapViewerPlugin`.

```java
package com.example;

import com.xeon.plugin.MapViewerPlugin;
import com.xeon.plugin.PluginContext;

import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public final class MyMapPlugin implements MapViewerPlugin {
    private final JLabel sidebar = new JLabel("My plugin");
    private final JPopupMenu actions = new JPopupMenu();

    @Override
    public String id() {
        return "my-map-plugin";
    }

    @Override
    public String displayName() {
        return "My Plugin";
    }

    @Override
    public void install(PluginContext context) {
        actions.removeAll();

        JMenuItem refresh = new JMenuItem("Refresh");
        refresh.addActionListener(e -> context.setStatus("Refreshed"));
        actions.add(refresh);

        boolean enabled = context.config().getBoolean("enabled", true);
        context.setStatus(enabled ? "My plugin loaded" : "My plugin loaded disabled");
    }

    @Override
    public JPopupMenu actionMenu() {
        return actions;
    }

    @Override
    public JComponent rightComponent() {
        return sidebar;
    }
}
```

The class must have a public no-argument constructor. If you do not define a constructor, Java creates one for you.

## Loading Plugins

Plugins are loaded from the top-left menu with `Load plugin JAR...`. Installed plugins can be selected or disabled from `Plugins...`.
Only one plugin can be active at a time; enabling a plugin disables the previously active plugin.

OS Map Viewer uses Java `ServiceLoader`, so the plugin JAR must include this file:

```text
META-INF/services/com.xeon.plugin.MapViewerPlugin
```

That file contains one plugin class name per line:

```text
com.example.MyMapPlugin
```

Build plugins for Java 17 bytecode or lower.

## Bundled Ground Markers Plugin

The Ground Markers feature that ships with OS Map Viewer is a normal plugin. It is a useful example of how a plugin can provide sidebars, map drawing, map input, imports, and exports without adding those features to the core viewer.

Its import menu can read RuneLite and HDOS ground markers from Java properties files. It asks before searching common locations and does not scan those folders on startup. If nothing is found, or if the user chooses not to search, it opens a file chooser so the user can pick the profile/config file directly.

The plugin checks normal client folders plus Bolt Launcher folders. Bolt sets client home data under:

```text
Linux native:  ${XDG_DATA_HOME:-$HOME/.local/share}/bolt-launcher
Linux Flatpak: $HOME/.var/app/com.adamcake.Bolt/data/bolt-launcher
Windows:       %APPDATA%/bolt-launcher/data
```

For Bolt installs, RuneLite markers are searched under `.runelite`, and HDOS profiles are searched under `hdos/profiles2`. HDOS profile files often look like `default-676169001900.properties`; the plugin shows that as `default (676169001900)` so similar profile names can still be distinguished.

## Where Plugin UI Goes

Plugins can provide UI in three places:

- `actionMenu()` returns a `JPopupMenu` opened from the plugin button on the left rail.
- `leftComponent()` returns a Swing component shown in the left sidebar.
- `rightComponent()` returns a Swing component shown in the right sidebar.

Plugins can also return an icon from `icon()`. If `icon()` returns `null`, OS Map Viewer draws a small letter icon from `displayName()`.

## Plugin Lifecycle

`MapViewerPlugin` has three required methods:

- `id()` returns a stable unique id, such as `ground-markers` or `my-map-plugin`.
- `displayName()` returns the user-facing name.
- `install(PluginContext context)` is called when the plugin is enabled.

Optional methods:

- `uninstall()` is called when the plugin is disabled or the app closes.
- `afterShow()` is called after the main window becomes visible. Plugins loaded from a JAR at runtime receive this after installation.
- `icon()` returns the left-rail icon.
- `actionMenu()` returns the plugin menu.
- `leftComponent()` returns left sidebar UI.
- `rightComponent()` returns right sidebar UI.
- `planeChanged(int plane)` is called when the map plane changes.
- `tileFocused(Tile tile)` is called when the shared jump control focuses a tile.

## Plugin Context

`PluginContext` is passed to `install`. Keep it if your plugin needs to talk to the app later.

Available methods:

- `frame()` returns the main `JFrame`, useful as the parent for dialogs.
- `owner()` returns the same window as a generic `Window`.
- `mapPanel()` gives access to the public `MapView` API for the map viewer.
- `is3DViewerActive()` reports whether the 3D viewer is currently open.
- `centerTile()` returns the 2D map center or the 3D camera tile.
- `focusTile(tile, zoom)` focuses the 2D map or warps the 3D camera, depending on the active mode.
- `repaintVisible()` repaints 2D plugin layers and refreshes 3D plugin overlays.
- `invoke3DRenderLater(task)` queues work for the 3D render path when the 3D viewer is active.
- `setStatus(String message)` writes to the status bar.
- `promptLoadPluginJar()` opens the core plugin loader.
- `config()` returns persistent settings scoped to this plugin id.
- `configManager()` returns the full config manager and shared Gson instance.

## MapView

`context.mapPanel()` returns `MapView`, which is the public plugin-facing map API. It is not the concrete Swing panel.

Common methods:

- `addLayer(layer)` and `removeLayer(layer)` register map drawing.
- `setActiveTool(tool)` and `clearActiveTool(tool)` register map input.
- `focusTile(tile, zoom)` moves the viewport to a tile.
- `focusRegion(regionId, plane, zoom)` moves the viewport to a region.
- `getPlane()` and `setPlane(plane)` read or change the active plane.
- `getZoom()` reads the current map zoom.
- `getHoverTile()`, `getHoverRegionId()`, and `getHoveredRegionId()` read the current hover target.
- `addHoveredRegionChangeListener(listener)` and `removeHoveredRegionChangeListener(listener)` subscribe to hovered-region changes.
- `getSelectedRegionId()`, `setSelectedRegionId(regionId)`, and `clearSelectedRegionId()` read or update the core selected region.
- `addSelectedRegionChangeListener(listener)` and `removeSelectedRegionChangeListener(listener)` subscribe to selected-region changes.
- `tileToRect(tile)` and `regionToRect(regionId)` convert map locations into drawing rectangles.
- `regionTileBounds(regionId)` returns the 64x64 world-tile bounds for a map region.
- `tileBounds(mapRect)` returns the world-tile bounds covered by a map-coordinate rectangle.
- `visibleViewRect()` returns the current viewport rectangle in component coordinates, useful for fixed-position overlays.
- `currentVisibleRegionIds()` returns the visible regions.
- `repaintTile(tile)`, `repaintRegion(regionId)`, and `repaintVisible()` request redraws.

## 3D Plugin API

Plugins can support the 3D viewer by implementing `com.xeon.view3d.Map3DLayer` in addition to the normal 2D interfaces.

```java
public final class MyPlugin implements MapViewerPlugin, MapLayer, MapTool, Map3DLayer {
    @Override
    public Map3DOverlay overlay(Map3DRenderContext context) {
        return Map3DOverlay.empty();
    }
}
```

`Map3DLayer` methods:

- `overlay(context)` returns immutable data for in-world 3D drawing.
- `tileActions(event)` returns right-click context menu actions for a 3D tile.
- `clickWarpTarget(event)` can return a tile for simple left-click 3D warp actions.
- `controlHints()` returns rows shown in the 3D `View Controls` overlay.

`Map3DRenderContext` includes:

- `cameraTile()`: the current 3D camera tile.
- `visibleRegionIds()`: regions that are loaded in the 3D scene. Plugins should use this to avoid drawing overlays for unloaded regions.

`Map3DOverlay` can contain:

- `Map3DPathSegment`: terrain-height-aware line segments. Dashed segments are represented in 3D as directional transport arrows.
- `Map3DMarker`: tile outline markers.
- `Map3DLabel`: billboarded text labels, optionally with a warp target.
- `Map3DTileOverlay`: filled tile overlays with an outline and optional label.

Keep `overlay(context)` data-only and fast. If a Swing event changes plugin state, call `context.repaintVisible()` or `context.invoke3DRenderLater(...)` so the 3D renderer refreshes safely.

## Map Area Metadata

The bundled v3 atlas includes metadata for map labels and icons. Core exposes that metadata through `com.xeon.model.MapArea`, so plugins can use area names without parsing the atlas file themselves.

Useful methods:

```java
List<MapArea> matches = context.mapPanel().searchMapAreas("varrock", 20);
List<MapArea> exact = context.mapPanel().mapAreasAt(tile);
MapArea nearest = context.mapPanel().nearestMapArea(tile, 32);
```

- `searchMapAreas(query, limit)` searches by area name, raw label text, object name, source, area id, and coordinates. Queries shorter than three characters return no results.
- `mapAreasAt(tile)` returns entries exactly on that world tile and plane.
- `nearestMapArea(tile, maxDistanceTiles)` returns the closest entry on the same plane within the requested tile radius, or `null`.

`MapArea` includes:

- `displayName()` for user-facing text.
- `rawName()` and `objectName()` for the original atlas metadata when present.
- `source()` for where the entry came from, such as labels or map icons.
- `areaId()` when the atlas metadata has one, otherwise `-1`.
- `worldX()`, `worldY()`, `plane()`, and `tile()` for map position.
- `searchText()` and `dropdownLabel()` for simple plugin UI.

## Persistent Config

OS Map Viewer stores all persistent settings in one JSON file:

```text
user.home/os-map-viewer/config.json
```

The top-level JSON object is split into named sections. Core settings use `core`. A plugin should use its stable plugin id. You normally do not need to pass the plugin id yourself, because `context.config()` is already scoped to the current plugin.

Example file:

```json
{
  "core": {
    "plugins.enabledOnStartup": false,
    "plugins.activePluginId": "my-map-plugin",
    "map.background": "#000000",
    "map.jumpToLastRegionOnStart": false,
    "map.last.regionId": 5536,
    "map.last.plane": 0,
    "map.last.zoom": 2.0,
    "map.memoryBudgetMb": 512
  },
  "my-map-plugin": {
    "showOverlay": true,
    "maxResults": 50,
    "lastRegionId": 5536
  }
}
```

For simple settings, use the typed helper methods:

```java
boolean enabled = context.config().getBoolean("enabled", true);
int maxResults = context.config().getInt("maxResults", 50);
double opacity = context.config().getDouble("opacity", 0.75);
String color = context.config().getString("color", "#FF00FF00");

context.config().setBoolean("enabled", enabled);
context.config().setInt("maxResults", maxResults);
context.config().setDouble("opacity", opacity);
context.config().setString("color", color);
```

For grouped settings, create a simple Java class with fields and let the app's shared Gson instance read and write it:

```java
public final class MyPluginSettings {
    public int lastRegionId = -1;
    public String markerColor = "#FF00FF00";
    public boolean showLabels = true;
}

MyPluginSettings settings = context.config().getObject(
        "settings",
        MyPluginSettings.class,
        new MyPluginSettings()
);

settings.lastRegionId = 5536;
context.config().setObject("settings", settings);
```

You do not need to bundle Gson in your plugin for this normal config usage. OS Map Viewer owns the Gson instance and the config file.

Useful config methods:

- `contains(key)` checks whether a setting exists.
- `keys()` returns all keys in your plugin's config section.
- `remove(key)` deletes a setting.
- `getObject(key, MySettings.class, defaultValue)` reads a simple Java object.
- `setObject(key, value)` writes a simple Java object. Passing `null` removes the key.
- `getElement(key)` and `setElement(key, jsonElement)` are available if you intentionally want to work with Gson JSON values.

Every write saves the JSON file. Keep plugin ids and setting keys stable between releases so user settings continue to load.

Core uses `map.memoryBudgetMb` for the map cache preset. Valid values are `512`, `1024`, `2048`, `3072`, `4096`, `5120`, or `-1` for unlimited.

## Drawing On The Map

Use `MapLayer` when a plugin needs to draw over the map or provide map tooltips.

```java
import com.xeon.plugin.MapViewerPlugin;
import com.xeon.plugin.PluginContext;
import com.xeon.view.MapLayer;
import com.xeon.view.MapRenderContext;

import java.awt.Graphics2D;
import java.awt.Rectangle;

public final class OverlayPlugin implements MapViewerPlugin, MapLayer {
    private PluginContext context;

    @Override
    public String id() {
        return "overlay-plugin";
    }

    @Override
    public String displayName() {
        return "Overlay Plugin";
    }

    @Override
    public void install(PluginContext context) {
        this.context = context;
        context.mapPanel().addLayer(this);
    }

    @Override
    public void uninstall() {
        if (context != null) {
            context.mapPanel().removeLayer(this);
        }
    }

    @Override
    public void paint(MapRenderContext context, Graphics2D g, Rectangle visibleMap) {
        // Draw in map coordinates. Helpers such as context.tileToRect(tile)
        // and context.regionToRect(regionId) align drawing to the map.
    }
}
```

`MapRenderContext` exposes the active plane, zoom, hovered tile, hovered region, selected region, visible region ids, viewport rectangle, and conversion helpers.
For dense tile-grid overlays, use `context.tileArea(tileBounds, plane, predicate)` or `context.tileArea(tiles)` to build one merged `Shape` from many tiles, then fill or draw that shape once:

```java
int regionId = context.hoveredRegionId();
Rectangle bounds = context.regionTileBounds(regionId);
Shape blocked = context.tileArea(bounds, context.plane(), (x, y, z) -> isBlocked(x, y, z));
g.fill(blocked);
```

Core map overlays, such as region id text, can draw after plugin overlays.

## Handling Map Input

Use `MapTool` when a plugin needs map clicks, hover events, arrow-key stepping, or visible-area notifications.

```java
import com.xeon.plugin.MapViewerPlugin;
import com.xeon.plugin.PluginContext;
import com.xeon.view.MapMouseEvent;
import com.xeon.view.MapTool;

public final class ClickPlugin implements MapViewerPlugin, MapTool {
    private PluginContext context;

    @Override
    public String id() {
        return "click-plugin";
    }

    @Override
    public String displayName() {
        return "Click Plugin";
    }

    @Override
    public void install(PluginContext context) {
        this.context = context;
        context.mapPanel().setActiveTool(this);
    }

    @Override
    public void uninstall() {
        if (context != null) {
            context.mapPanel().clearActiveTool(this);
        }
    }

    @Override
    public boolean mouseClicked(MapMouseEvent event) {
        if (event.tile() == null) {
            return false;
        }
        context.setStatus("Clicked tile " + event.tile().x + ", " + event.tile().y);
        return true;
    }
}
```

Only one `MapTool` is active at a time. A plugin can still draw with `MapLayer` without being the active tool.

Popup-trigger and right-click mouse events are delivered through `mouseClicked(MapMouseEvent)`, so tools can open context menus from `event.source()`.

`MapMouseEvent` includes:

- `tile()` for the clicked or hovered world tile.
- `regionId()` for the region under the mouse.
- `isShiftDown()` for Shift state.
- `isAdditiveSelection()` for Ctrl or Command multi-selection.
- `source()` for the original Swing mouse event.

Return `true` from `supportsRegionSelection()` only if your plugin wants Shift to enter region-selection mode. Plugins that do not opt in will not trigger the dark region-selection overlay. A region-selection tool can persist the clicked region through `event.mapPanel().setSelectedRegionId(event.regionId())`.

## Map Coordinates

`Tile` uses world coordinates and plane:

```java
new Tile(worldX, worldY, plane)
```

The bundled map range is fixed in `com.xeon.io.Paths`:

```text
MIN_RX = 15
MIN_RY = 19
MAX_RX = 65
MAX_RY = 196
REGION_TILE_SIZE = 64
```

RuneScape region ids use this packed shape:

```java
int regionId = (regionX << 8) | regionY;
```

## Packaging Checklist

1. Compile against `api/build/libs/os-map-viewer-api.jar`.
2. Implement `MapViewerPlugin`.
3. Add `META-INF/services/com.xeon.plugin.MapViewerPlugin`.
4. Build a JAR containing your plugin classes and any plugin-only dependencies.
5. Open OS Map Viewer and choose `Load plugin JAR...`.

Plugins should not write directly to RuneLite or HDOS config files unless a plugin is explicitly designed for that and clearly tells the user. The bundled ground marker plugin reads client config files and exports JSON instead of writing back to those clients.
