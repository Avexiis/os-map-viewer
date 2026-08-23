# Built-In Ground Markers Plugin

<img width="1918" height="1045" alt="image" src="https://github.com/user-attachments/assets/47b5ba6d-db59-449e-be0b-1d578bd1a868" />

The built-in `Ground Markers` plugin can view, create, import, and export ground marker JSON for RuneLite-style marker packs.

It does not write to RuneLite or HDOS config files. It only reads files you allow it to read, then lets you export marker JSON.

## Enable The Plugin

1. Open the hamburger menu.
2. Click `Plugins...`.
3. Check `Ground Markers`.

When enabled, the plugin adds:

- A marker list on the left.
- A marker editor on the right.
- A Ground Markers button in the left rail.

## Import Markers

Click the Ground Markers button in the left rail.

The plugin menu has these import options:

- `Import from RuneLite`
- `Import from HDOS`
- `Import JSON file`

For RuneLite and HDOS, the app asks before it searches common config locations. If it cannot find a file, or if you choose not to search, it lets you pick the config or profile file yourself.

The importer supports RuneLite config files, HDOS profile files, and JSON files that use the RuneLite ground marker format.

## Create Or Edit Markers

With the Ground Markers plugin enabled:

- Click a tile to select it.
- Right-click a tile and choose `Add marker` to create one quickly.
- Right-click an existing marker to select it, set its label, set its color, or delete it.
- Use the right panel to set the label, color, and alpha.
- Click `Add Marker` to create a marker.
- Select an existing marker, edit the fields, then click `Update Marker`.
- Click `Delete Marker` to remove the selected marker.

Hold `Ctrl` while clicking tiles to select more than one tile. Then click `Add Marker(s)` to add markers to all selected tiles.

The marker list on the left has three tabs:

- `All`: every marker in the current project.
- `Visible Area`: markers in the visible map area and current plane.
- `New Tiles`: markers created in this app session that were not imported from RuneLite or HDOS config files.

Clicking a marker in the list zooms in and centers it on the map. In 3D mode, it warps the camera to that marker.

## Ground Markers In 3D
<img width="1919" height="1044" alt="image" src="https://github.com/user-attachments/assets/a6ca0583-6f87-4199-bd40-fcd099e71564" />

Ground markers work in 3D mode when the plugin is active.

- Markers in loaded 3D regions draw as tile overlays.
- Marker color, alpha, plane, and label are respected.
- Markers are not drawn in 3D regions that have not loaded yet.
- The minimap and floating world map draw markers the same way the 2D map does.
- Right-click a tile in the 3D scene to add or edit a marker.

RuneLite, HDOS, and JSON imports use the saved marker location data, so imported markers appear in both 2D and 3D.

## Region Selection For Export

Hold `Shift` to enter region selection mode.

- The map shows a dark region overlay while `Shift` is held.
- Click a region to select it for export.
- Click more regions while holding `Shift` to select more than one.
- Selected regions stay highlighted after you release `Shift`.
- Click normally on the map to clear the selected regions.

Region selection is only used for export.

## Export Markers

Click the Ground Markers button in the left rail, then open `Export`.

Export options:

- `Current region to clipboard`
- `All markers to clipboard`
- `Selected regions to clipboard`
- `Current region to file`
- `All markers to file`
- `Selected regions to file`

Clipboard exports can be pasted into RuneLite or HDOS. File exports save JSON that RuneLite or HDOS can import.

## Import Markers Into RuneLite Or HDOS

After you copy marker JSON to your clipboard:

1. Log into the game.
2. Right-click the world map icon below the minimap.
3. Click **Import Ground Markers**.
4. RuneLite or HDOS will import the markers from your clipboard.
