# Mesh Export Plugin

The built-in Mesh Export plugin creates STL or OBJ files from static NPC, scene-object, and RuneProfile player geometry. The output can be opened in Fusion 360 or Blender and prepared for 3D printing.

## Selecting A Mesh

Enable `Mesh Export` from `Plugins...`. Its right sidebar opens immediately with `No Mesh Selected`; export controls remain disabled until a model is selected.

In the 3D viewer, point at an NPC or object and right-click `Export Mesh`. Objects receive a silhouette outline while hovered, and the plugin hides the normal tile hover selector over them. NPC hover outlines suppress the selector in the core viewer even when no plugin is enabled. Toggled agility obstacles remain outlined with the tile selector visible on top.

The sidebar loads the static model and automatically builds a compacted version. Use `Original` and `Compacted` to inspect both versions before saving. `Face edges` makes topology changes easier to inspect.

## Player Models

The `RuneProfile Player` field starts with the shared WikiSync username when one is saved. Click `Fetch Player` or press Enter to load that player's uploaded model. The field is independent of WikiSync, so another RuneProfile username can be entered without changing the saved WikiSync profile. `Use WikiSync` restores the currently saved WikiSync username.

Player models are fetched as model bytes from RuneProfile's first-party `GET /profiles/models/{username}` API route. The plugin does not read or parse profile web pages. Current GLB uploads and legacy binary PLY models are supported. A model is available only after its owner has uploaded one with RuneProfile's `Update Player Model` action.

Fetching, parsing, and compaction run away from the Swing event thread. The request is limited to 25 MB and can be cancelled from the sidebar.

## Connected Structures

Hold Shift and left-click an object to start a structure selection. Continue Shift-clicking walls, roofs, and other object parts to add them. A part is accepted only when its actual world-space triangles touch or intersect a part already in the selection. This prevents one export from containing separated structures.

Shift-click a selected object again to remove it. Removal is blocked when it would split the remaining selection into disconnected groups. `Clear Selection` resets the structure.

## Compaction And Export

Compaction preserves the visible shape without mutilation or smoothing. It removes duplicate and coincident internal faces, unions closed overlapping shells, and retriangulates coplanar regions with fewer faces. Open or non-manifold model parts are retained because treating them as solids could change their shape. The sidebar reports these topology conditions for inspection.

If a coplanar region cannot be merged safely, its original triangles are retained and a warning is shown instead of failing the export.

Set `Longest side (mm)`, click `Export Mesh...`, and choose STL or OBJ. The file chooser starts with the NPC or object name, its ID is used when the cache has no name. Connected selections use a structure filename.
