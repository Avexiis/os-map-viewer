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

## Documentation

- [Basic Map Use](docs/BASIC_MAP_USE.md)
- [Area Search](docs/AREA_SEARCH.md)
- [Options Menu](docs/OPTIONS_MENU.md)
- [Plugins](docs/PLUGINS.md)
- [Built-In Shortest Path Plugin](docs/SHORTEST_PATH_PLUGIN.md)
- [Built-In Ground Markers Plugin](docs/GROUND_MARKERS_PLUGIN.md)
- [Saved Settings](docs/SAVED_SETTINGS.md)
- [Notes For Developers](docs/DEVELOPER_NOTES.md)

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
RuneLite's license has been copied into `com.xeon.atlas.MapAtlasPrinter` including the copyright line. This is the only file that uses RuneLite code at this time.
- RuneLite is available at https://runelite.net/ and https://github.com/runelite/runelite/

Shortest Path does not list a year or author in their license, a generic license with no year or author has been inserted into the files ported over from it.
- Shortest Path was released by GitHub user **Runemoro** in 2020: https://github.com/Runemoro/shortest-path
- Its current version is maintained by GitHub user **Skretzo**: https://github.com/Skretzo/shortest-path
##
#### This project was developed on Linux!
