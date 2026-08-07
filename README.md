# OS Map Viewer

OS Map Viewer is a desktop map viewer for the Old School RuneScape world map.

The core app is only a map viewer. Extra tools are added through plugins. The app includes one built-in plugin for RuneLite and HDOS ground markers.

## Experimental branch
This branch is the experimental branch of OS Map Viewer. It contains features that may be removed, changed, or never make it to the stable main branch. To run the experimental branch, you must build it from source.
You can do this by:
- Clicking the green **Code** button, and copying the URL in the text box.
- Open IntelliJ IDEA and press "New Project from Version Control"
- Paste the link in and select a path for it to download into
- Press **Clone**
- Let the project load and allow Gradle to sync
- Once it stops loading, click the elephant button on the right side of IntelliJ
- Click the arrow next to **os-map-viewer** if it is not already expanded
- Click the arrow next to **Tasks**
- Click the arrow next to **shadow**
- Double click **shadowJar** and allow the task to run
- Open `build/libs` and you will see `OSMapViewer.jar`

### Experimental features include:
- Map printer that uses the official RuneLite cache library to read your installed game cache for the purpose of printing a new map after game updates
- Map binary stored in the user home directory to support printer. This branch will also automatically replace the map binary when a new version is run for the first time.
- Map binary backup. This branch will validate that a new printed map is non-corrupt before using it. It will roll the binary back to the last version if found to be corrupt. 

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
- Lock the map so it cannot be moved or zoomed by mistake.
- Change the map background color.
- Save your last viewed region and zoom level.
- Enable, disable, and load plugins.

## Basic Map Use

- Drag the map to pan.
- Use the mouse wheel to zoom.
- Hover a tile to see the tile selector.
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

Plugins are disabled by default.

When no plugins are enabled, OS Map Viewer only shows the map viewer and core map controls. A small message tells you how to enable plugins.

To enable plugins:

1. Click the hamburger button in the left rail.
2. Click `Plugins...`.
3. Check the plugin you want to enable.
4. Close the plugin window.

To start with plugins enabled next time:

1. Open `Plugins...`.
2. Check `Start with plugins enabled`.

To load an external plugin:

1. Click the hamburger button.
2. Click `Load plugin JAR...`.
3. Choose a compatible plugin JAR file.

Enabled plugins add buttons to the left rail. Click a plugin button to open its plugin menu.

## Built-In Ground Markers Plugin

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
