# 3D Mode

3D Mode is an experimental viewer for looking at the OSRS game world with a free camera perspective.

It does **NOT** modify game files.

## Open 3D Mode

1. Open the hamburger menu.
2. Click `Experimental Options...`.
3. Click `Open 3D Viewer`.
4. Read the notice, then choose `Yes` to continue.
5. If the app cannot find your OSRS cache, choose the cache folder when prompted.

The viewer starts near the region you were looking at on the 2D map. If it for some reason cannot find the centered 2D map, it starts at Lumbridge.

## What Changes While It Is Open

- The 2D map workspace is replaced by the 3D scene.
- Active plugins are cleared while 3D Mode is open.
- Nearby regions stream in as you move. Empty areas may appear briefly while region data loads.
- Click `Return to 2D Map` or press `Esc` to leave 3D Mode.

## Controls

- `W`, `A`, `S`, `D`: move the camera.
- `Space` or `Page Up`: raise the camera.
- `Ctrl` or `Page Down`: lower the camera.
- Hold `Shift`: move and zoom faster.
- Arrow keys: rotate and tilt the camera.
- Mouse wheel: move forward or backward along the view direction.
- Hold middle mouse and move the mouse: aim the camera.
- Move the mouse over the scene: highlight the tile under the cursor and show its world tile coordinates.

## Viewer Buttons

- `Lock Camera`: freezes camera movement and rotation. Tile hover still works.
- `Debug`: shows rendering and performance details in the top-right corner.
- `Return to 2D Map`: closes the 3D viewer and returns to the normal map.

## Notes

- 3D Mode is experimental, so performance and visuals may change between releases.
- This is **NOT** the live game client. It does not show players, NPCs, or any other live game-state data. It is simply a recreation of the map scene.
- On hybrid-GPU systems, a black scene or driver crash may mean the app is using the wrong GPU. Launching Java with the dedicated GPU can help.
