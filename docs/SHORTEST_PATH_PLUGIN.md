# Built-In Shortest Path Plugin

<img width="1917" height="1045" alt="image" src="https://github.com/user-attachments/assets/0e9a7b01-2123-4e77-941d-b408baeb330e" />

The built-in `Shortest Path` plugin can draw a route from one tile to another over the viewer map.

It is adapted from the RuneLite plugin hub `shortest-path` plugin for use in the 2D map and 3D viewer.
It can also use public OSRS WikiSync profile data for account-aware skill and quest filtering.

## Enable The Plugin

1. Open the hamburger menu.
2. Click `Plugins...`.
3. Check `Shortest Path`.

When enabled, the plugin adds:

- A shortest-path panel on the right.
- A searchable teleport item list for enabling or disabling item teleports.
- Route import/export, color, collision-map, and transport-type controls.
- A Shortest Path button in the left rail.
- A map overlay for the calculated path and a left route-step table.
- 3D route overlays when the 3D viewer is open.

## Create A Route

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

## 3D Viewer
<img width="1918" height="1046" alt="image" src="https://github.com/user-attachments/assets/73b73dbd-4a7a-4ade-bf62-b37e9e85a5b1" />

With Shortest Path enabled in 3D mode:

- Right-click a tile in the 3D scene to set the start, add a step, remove a route point, or set the end.
- Right-click a tile in the floating world map to use the same route actions.
- Double-click a tile in the floating world map to warp the 3D camera there.
- Walk segments draw as lines above the terrain.
- Transport and teleport jumps draw as arrows pointing toward the jump destination.
- Transport labels draw over supported transport objects or tiles.
- The route is also drawn on the minimap and floating world map.
- Clicking a route step in the left sidebar warps the 3D camera to that step.

## WikiSync Profile Lookup

To apply account-specific data, type a RuneScape username in the `WikiSync Profile` section of the 2D or 3D map controls and click `Look up`, which is labeled with the RuneLite icon.

After a successful lookup, OS Map Viewer stores the username plus the subset of WikiSync data used by the viewer: fetched timestamp, profile type, skill levels, and completed quest names. It does not store unrelated public sections such as diaries, music, combat achievements, or collection log data. On startup, OS Map Viewer refreshes that username from WikiSync before you choose 2D or 3D mode.

## Routing Limits

OS Map Viewer is not connected to the RuneLite client, so it cannot know live account state such as inventory, equipment, bank contents, planted spirit trees, spellbook, or current cooldowns.

With the global WikiSync profile loaded, the route filters transports by satisfied skill and quest requirements. Without a WikiSync profile, the plugin uses broad offline toggles for transports, teleports, wilderness avoidance, and POH links.
