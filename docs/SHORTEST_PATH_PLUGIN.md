# Built-In Shortest Path Plugin

<img width="1917" height="1045" alt="image" src="https://github.com/user-attachments/assets/0e9a7b01-2123-4e77-941d-b408baeb330e" />

The built-in `Shortest Path` plugin can draw a route from one tile to another over the viewer map.

It is adapted from the RuneLite plugin hub `shortest-path` plugin for use on a 2D only map.
It can also use public OSRS WikiSync profile data for account-aware skill and quest filtering.

This plugin is experimental only.

## Enable The Plugin

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

## WikiSync Profile Lookup

To apply account-specific data, type a RuneScape username in the Shortest Path panel and click `Look up`, which is labeled with the RuneLite icon.

After a successful lookup, the app asks if you want to store the profile. If you choose to store it, OS Map Viewer saves the username plus the subset of WikiSync data used by the shortest-path viewer: fetched timestamp, profile type, skill levels, and completed quest names. It does not store unrelated public sections such as diaries, music, combat achievements, or collection log data. The next time the Shortest Path plugin loads, it refreshes that username from WikiSync and replaces the stored profile.

## Routing Limits

OS Map Viewer is not connected to the RuneLite client, so it cannot know live account state such as inventory, equipment, bank contents, planted spirit trees, spellbook, or current cooldowns.

With a WikiSync profile loaded, the route filters transports by satisfied skill and quest requirements. Without a WikiSync profile, the plugin uses broad offline toggles for transports, teleports, wilderness avoidance, and POH links.
