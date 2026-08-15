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
- Experimental 3D viewer that reads the local OSRS cache and streams nearby terrain regions around a free camera.

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

## Documentation

- [Basic Map Use](docs/BASIC_MAP_USE.md)
- [Area Search](docs/AREA_SEARCH.md)
- [Options Menu](docs/OPTIONS_MENU.md)
- [Plugins](docs/PLUGINS.md)
- [Built-In Shortest Path Plugin](docs/SHORTEST_PATH_PLUGIN.md)
- [Built-In Ground Markers Plugin](docs/GROUND_MARKERS_PLUGIN.md)
- [Experimental 3D Viewer](docs/3D_MODE.md)
- [Saved Settings](docs/SAVED_SETTINGS.md)
- [Notes For Developers](docs/DEVELOPER_NOTES.md)
- [3rd Party Attributions](docs/ATTRIBUTIONS.md)

##
#### This project was developed on Linux!
