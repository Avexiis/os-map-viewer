# OS Map Viewer
<img width="1917" height="1048" alt="image" src="https://github.com/user-attachments/assets/5558f01c-c0bd-4244-af9a-2ea187ec7c10" /> 
OS Map Viewer is a desktop map viewer for the Old School RuneScape world map.

The app includes a 2D world map, a 3D cache-backed scene viewer, cache-backed NPC spawns, and built-in plugins for RuneLite/HDOS ground markers and shortest-path routing.

## 3D Mode

When the app opens, choose either `2D Map` or `3D Viewer`. You can also open 3D mode later from the Options menu. 3D mode reads your local OSRS cache and renders nearby regions, objects, and NPCs around a free camera.

Preview:
<img width="1919" height="1050" alt="image" src="https://github.com/user-attachments/assets/4a77dd92-a91c-46d3-b617-9a6d6d770610" />

3D mode does **NOT** rely on a Jagex client and does **NOT** modify game files.

## Map Printer

The Options menu includes a map printer that uses the RuneLite cache library to read your installed game cache and print a new 2D atlas after game updates.

## Requirements

- Java 17 or newer
- Gradle, or the included `gradlew` script (if building from source)

## Main Features

- View the bundled OSRS world map.
- Choose 2D or 3D mode on startup.
- Pan and zoom the map.
- Fly around the game world in 3D with a free camera.
- See NPCs at known OSRS spawn locations, with idle or walk animation when cache data supports it.
- Use the 3D controls panel to change rendering, view distance, visible planes, NPC display, agility overlays, overlay colors, and jump targets.
- Use the 3D minimap, floating world map, and area search to navigate.
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
- Enable, disable, and load plugins. One plugin can be active at a time.
- Use ground markers and shortest-path overlays in both 2D and 3D.

## Documentation

- [Basic Map Use](docs/BASIC_MAP_USE.md)
- [Area Search](docs/AREA_SEARCH.md)
- [Options Menu](docs/OPTIONS_MENU.md)
- [Plugins](docs/PLUGINS.md)
- [Built-In Shortest Path Plugin](docs/SHORTEST_PATH_PLUGIN.md)
- [Built-In Ground Markers Plugin](docs/GROUND_MARKERS_PLUGIN.md)
- [3D Mode](docs/3D_MODE.md)
- [Saved Settings](docs/SAVED_SETTINGS.md)
- [Notes For Developers](docs/DEVELOPER_NOTES.md)
- [3rd Party Attributions](docs/ATTRIBUTIONS.md)

##
#### This project was developed on Linux!
