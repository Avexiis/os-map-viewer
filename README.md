# OS Map Viewer
<img width="1917" height="1048" alt="image" src="https://github.com/user-attachments/assets/5558f01c-c0bd-4244-af9a-2ea187ec7c10" />

OS Map Viewer is a desktop map viewer for the Old School RuneScape world map.

The core app is only a map viewer. Extra tools are added through plugins. The app includes built-in plugins for RuneLite/HDOS ground markers and experimental shortest-path routing.

## Experimental branch
This branch is the experimental branch of OS Map Viewer. It contains features that may be removed, changed, or never make it to the stable main branch. To run the experimental branch, you must download the release file named `OSMapViewer_EXPERIMENTAL.jar`.
- This version will use the same stored configuration as the main branch, but it will add to it if you use the different features. This will not affect the main release's operation.

### Experimental features include:
- Map printer that uses the official RuneLite cache library to read your installed game cache for the purpose of printing a new map after game updates
- Map binary stored in the user home directory to support the map printer. This branch will also automatically replace the map binary when a new version is run for the first time.
- Map binary backup. This branch will validate that a new printed map is non-corrupt before using it. It will roll the binary back to the last version if found to be corrupt. 
- Experimental shortest-path plugin adapted from the RuneLite `shortest-path` plugin data and pathfinder core.

## Requirements

- Java 17 or newer
- Gradle, or the included `gradlew` script

## Main Features

- View the bundled OSRS world map.
- Pan and zoom the map.
- Change map planes.
- Search for map areas by name.
- Jump to a region ID, region coordinates, or world tile.
- Show or hide grid lines.
- Show region IDs or region coordinates over the map.
- Show or hide atlas text and map icons.
- Show tooltips for supported map icon locations.
- Lock the map so it cannot be moved or zoomed by mistake.
- Change the map background color.
- Save your last viewed region and zoom level.
- Enable, disable, and load plugins.

## Basic Map Use

- Drag the map to pan.
- Use the mouse wheel to zoom.
- Hover a tile to see the tile selector.
- Hover supported map icons to see their location tooltip when `Map Icons` and `Icon Tooltips` are enabled.
- Use the floating `Map Controls` panel in the top right for grid, plane, jump, and lock options.
- Click the arrow button on the `Map Controls` panel to hide or show it.

## Area Search

The search box is at the bottom of the app.

- Type at least 3 letters.
- Matching map areas appear in a dropdown.
- Press `Tab` to fill the best match.
- Press `Enter` to jump to the selected match.
- You can also click a result in the dropdown.

Jumping to an area zooms in and centers it in the viewer.

## Options Menu

The hamburger button in the left rail opens the main options menu.

From this menu you can:

- Open `Plugins...`
- Load a plugin JAR file.
- Change the map background color.
- Turn `Jump to last region on start` on or off.
- Change the map memory budget.

The memory budget controls how much RAM the map cache can use. The app offers presets from `512 MB` to `5 GB`, plus `Unlimited`. Unlimited mode shows a warning before it is enabled.

## Plugins

Plugins are disabled by default. Only one plugin can be active at a time.

When no plugin is active, OS Map Viewer only shows the map viewer and core map controls. A small message tells you how to select a plugin.

To enable a plugin:

1. Click the hamburger button in the left rail.
2. Click `Plugins...`.
3. Select the plugin you want to enable.
4. Close the plugin window.

Selecting a plugin disables any currently active plugin.

To start with the selected plugin enabled next time:

1. Open `Plugins...`.
2. Check `Start selected plugin on launch`.

To load an external plugin:

1. Click the hamburger button.
2. Click `Load plugin JAR...`.
3. Choose a compatible plugin JAR file.

The active plugin adds a button to the left rail. Click the plugin button to open its plugin menu.

## Built-In Shortest Path Plugin (Experimental Only)
<img width="1917" height="1045" alt="image" src="https://github.com/user-attachments/assets/0e9a7b01-2123-4e77-941d-b408baeb330e" />

The built-in `Shortest Path` plugin can draw a route from one tile to another over the viewer map.

It is adapted from the RuneLite plugin hub `shortest-path` plugin for use on a 2D only map.
It can also use public OSRS WikiSync profile data for account-aware skill and quest filtering.

### Enable The Plugin

1. Open the hamburger menu.
2. Click `Plugins...`.
3. Check `Shortest Path`.

When enabled, the plugin adds:

- A shortest-path panel on the right.
- A WikiSync username field and `Look up` button.
- A searchable teleport item list for enabling or disabling item teleports.
- Route import/export, color, collision-map, and transport-type controls.
- A Shortest Path button in the left rail.
- A map overlay for the calculated path and a left route-step table.

### Create A Route

With the Shortest Path plugin enabled:

- The first map click sets the start tile.
- `Shift`-click after setting a start tile appends a route step.
- A later non-shift map click sets the final end tile.
- Use the plugin menu to target to the map center.

Solid lines are walked path segments. Dashed lines are transport or teleport jumps.

The left `Route Steps` table lists the current start, steps, and end. Click a row to center the map on that route point. Hovering a table row or route marker shows the route point coordinates in a tooltip.

Right-click a route marker to remove it. Removing the end point promotes the previous step to the new end; if the previous point is only the start, the route is cleared.

If a start, step, or end tile is not accessible, the plugin clears that point and leaves the other route points in place instead of drawing to the nearest pathable tile.

The right panel includes a `Teleport Items` list with `Filter` and `Sort` controls. Enabled items are highlighted green. Click `+` to enable a disabled item, or `-` to disable an enabled item. `Default` keeps the built-in order; the other sort modes group enabled or disabled items first. Item choices are saved in the plugin config as disabled items, so new supported items remain enabled by default. Use `Avoid item teleports` to exclude all item teleports without changing the saved item list.

The `Use...` checkboxes mirror RuneLite shortest-path transport category toggles, including agility shortcuts, boats, canoes, ships, fairy rings, gnome gliders, hot air balloons, magic carpets, magic mushtrees, minecarts, quetzals, spirit trees, teleport levers, teleport portals, teleport spells, Home Teleport spells, teleport minigames, and wilderness obelisks. Toggling these options recalculates the route.

Use `Draw collision map` to show movement-blocking collision edges at readable zoom levels. The color controls change walk lines, transport lines, start markers, step markers, end markers, and collision-map lines. `Recalculate` forces a fresh route calculation.

Use `Export` to save the current route as JSON with `start`, ordered `steps`, and optional `target` tile objects. Use `Import` to load that JSON back into the viewer.

The route can draw across the full logical atlas range, including northern regions that the in-game vanilla world map does not show, such as quest areas and undergrounds.

### WikiSync Profile Lookup

To apply account-specific data, type a RuneScape username in the Shortest Path panel and click `Look up`, which is labeled with the RuneLite icon.

After a successful lookup, the app asks if you want to store the profile. If you choose to store it, OS Map Viewer saves the username plus the subset of WikiSync data used by the shortest-path viewer: fetched timestamp, profile type, skill levels, and completed quest names. It does not store unrelated public sections such as diaries, music, combat achievements, or collection log data. The next time the Shortest Path plugin loads, it refreshes that username from WikiSync and replaces the stored profile.

### Routing Limits

OS Map Viewer is not connected to the RuneLite client, so it cannot know live account state such as inventory, equipment, bank contents, planted spirit trees, spellbook, or current cooldowns.

With a WikiSync profile loaded, the route filters transports by satisfied skill and quest requirements. Without a WikiSync profile, the plugin uses broad offline toggles for transports, teleports, wilderness avoidance, and POH links.

## Built-In Ground Markers Plugin
<img width="1918" height="1045" alt="image" src="https://github.com/user-attachments/assets/47b5ba6d-db59-449e-be0b-1d578bd1a868" />

The built-in `Ground Markers` plugin can view, create, import, and export ground marker JSON for RuneLite-style marker packs.

It does not write to RuneLite or HDOS config files. It only reads files you allow it to read, then lets you export marker JSON.

### Enable The Plugin

1. Open the hamburger menu.
2. Click `Plugins...`.
3. Check `Ground Markers`.

When enabled, the plugin adds:

- A marker list on the left.
- A marker editor on the right.
- A Ground Markers button in the left rail.

### Import Markers

Click the Ground Markers button in the left rail.

The plugin menu has these import options:

- `Import from RuneLite`
- `Import from HDOS`
- `Import JSON file`

For RuneLite and HDOS, the app asks before it searches common config locations. If it cannot find a file, or if you choose not to search, it lets you pick the config or profile file yourself.

The importer supports RuneLite config files, HDOS profile files, and JSON files that use the RuneLite ground marker format.

### Create Or Edit Markers

With the Ground Markers plugin enabled:

- Click a tile to select it.
- Use the right panel to set the label, color, and alpha.
- Click `Add Marker` to create a marker.
- Select an existing marker, edit the fields, then click `Update Marker`.
- Click `Delete Marker` to remove the selected marker.

Hold `Ctrl` while clicking tiles to select more than one tile. Then click `Add Marker(s)` to add markers to all selected tiles.

The marker list on the left has three tabs:

- `All`: every marker in the current project.
- `Visible Area`: markers in the visible map area and current plane.
- `New Tiles`: markers created in this app session that were not imported from RuneLite or HDOS config files.

Clicking a marker in the list zooms in and centers it on the map.

### Region Selection For Export

Hold `Shift` to enter region selection mode.

- The map shows a dark region overlay while `Shift` is held.
- Click a region to select it for export.
- Click more regions while holding `Shift` to select more than one.
- Selected regions stay highlighted after you release `Shift`.
- Click normally on the map to clear the selected regions.

Region selection is only used for export.

### Export Markers

Click the Ground Markers button in the left rail, then open `Export`.

Export options:

- `Current region to clipboard`
- `All markers to clipboard`
- `Selected regions to clipboard`
- `Current region to file`
- `All markers to file`
- `Selected regions to file`

Clipboard exports can be pasted into RuneLite or HDOS. File exports save JSON that RuneLite or HDOS can import.

### Import Markers Into RuneLite Or HDOS

After you copy marker JSON to your clipboard:

1. Log into the game.
2. Right-click the world map icon below the minimap.
3. Click **Import Ground Markers**.
4. RuneLite or HDOS will import the markers from your clipboard.

## Saved Settings

OS Map Viewer saves settings here:

```text
user.home/os-map-viewer/config.json
```

Saved settings include:

- Whether plugins should start enabled.
- Map background color.
- Last viewed region, plane, and zoom.
- Whether to jump to the last region on start.
- Map memory budget.
- Plugin settings.

## Notes For Plugin Developers

The plugin API document is at:

```text
docs/PLUGIN_API.md
```

Build the API JAR with:

```bash
./gradlew buildPluginApi
```

The API lets plugins do things like:

- Add a button to the left rail.
- Add panels to the left or right side of the app.
- Draw overlays on the map.
- Show tooltips for map tiles.
- Handle map clicks and tile selection.
- Search bundled map area metadata.
- Save plugin settings in the shared config manager.

Plugins are loaded from JAR files with Java `ServiceLoader`. See `docs/PLUGIN_API.md` for the exact setup.

## RuneLite Cache Dependency

The experimental map printer uses a shaded RuneLite cache JAR that is bundled with this repository:

```text
lib/runelite-cache/cache-<version>-SNAPSHOT-shaded.jar
```

This file is not published by RuneLite and was created in a local fork of the RuneLite repository. It is simply the RuneLite Cache .JAR file, but with its dependencies included. You can accomplish this by adding a shadowJar task to the cache module in a local fork of RuneLite.

## Third-Party Attributions

- This branch includes and uses the RuneLite cache module.

- The experimental map printer contains code derived from RuneLite cache tooling.

- The Shortest Path plugin for this application includes data and adapted pathfinding/collision map code from the RuneLite hub plugin `shortest-path` of the same name.

- Shortest Path for RuneLite can be found at https://runelite.net/plugin-hub/show/shortest-path

- Map icon tooltips include location data adapted from RuneLite's core world map plugin.

RuneLite and its Shortest Path hub plugin are licensed under the BSD 2-Clause License:

```text
BSD 2-Clause License

Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
Shortest Path does not list a year or author in their license, it is maintained by GitHub user **Skretzo**: https://github.com/Skretzo/shortest-path
##
#### This project was developed on Linux!
