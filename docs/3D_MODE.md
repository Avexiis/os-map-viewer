# 3D Mode

<img width="1917" height="1047" alt="image" src="https://github.com/user-attachments/assets/15bab20b-39f8-4ea7-8655-f78098bc854d" />

3D Mode shows the OSRS map scene with a free camera. It reads nearby regions, objects, textures, and NPC definitions from your local OSRS cache and does **not** modify game files.

## Open 3D Mode

- When the app starts, choose `3D Viewer` in the startup dialog.
- Or open the hamburger menu and click `Open 3D Viewer`.
- Or click `Switch to 3D Map` in the 2D map controls to open the current 2D map location in 3D.

Before 3D mode opens, choose whether OS Map Viewer should auto-detect your installed OSRS cache. Choose `No` to pick a cache folder yourself. Check `Do not ask again` to keep using that choice.

If the cache is missing, invalid, or cannot be decoded after a game update, OS Map Viewer returns to 2D mode and shows update/support links. If the 3D viewer cannot create an OpenGL context, the app shows the error and returns to 2D mode.

The viewer resumes your last 3D camera position. If no saved 3D state exists, it starts at Lumbridge.

## What Changes In 3D

- The main map area becomes the 3D scene.
- Plugin sidebars stay available when the active plugin supports 3D.
- Nearby regions stream in as you move.
- NPCs appear at known OSRS spawn locations. They use idle animations when available, and NPCs with walk animations can wander near their spawn tile.
- The minimap stays in the top-right corner and can be hidden.
- `Open World Map` opens a movable floating world map.
- The area search box stays below the 3D viewer.
- Click `Switch to 2D Map` or press `Esc` to leave 3D mode.

## Controls

- `W`, `A`, `S`, `D`: move the camera.
- `Space` or `Page Up`: raise the camera.
- `Ctrl` or `Page Down`: lower the camera.
- Hold `Shift`: move and zoom faster.
- Arrow keys: rotate and tilt the camera.
- Mouse wheel: move forward or backward along the view direction.
- Hold middle mouse and move the mouse: aim the camera.
- Move the mouse over the scene: highlight the tile under the cursor and show its world tile coordinates.

Click `View Controls` in the 3D toolbar to see the current controls in the viewer. If the active plugin has 3D controls, they appear in that overlay too.

## Viewer Buttons

- `Lock Camera`: freezes camera movement and rotation. Tile hover still works.
- `Debug`: shows rendering and performance details.
- `AA`: changes multisample antialiasing.
- `View`: changes streamed region distance.
- `Overlays Front` / `Overlays Occluded`: controls whether plugin overlays draw through terrain and objects.
- `FOV`: adjusts the camera field of view.
- `View Controls`: shows or hides the controls overlay.
- `Hide Minimap`: hides the minimap and world-map button.
- `Switch to 2D Map`: closes the 3D viewer.

## Plugins In 3D

- Ground markers draw on loaded 3D regions and on the minimap/world map.
- Shortest path draws walk lines, transport arrows, and transport labels in the 3D scene, plus route overlays on the minimap/world map.
- Right-click tiles in the 3D scene to use plugin tile actions.
- Double-click a tile in the floating world map to warp the 3D camera there.
