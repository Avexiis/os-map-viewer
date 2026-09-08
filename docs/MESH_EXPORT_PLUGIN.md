# Mesh Export Plugin

The built-in Mesh Export plugin creates STL or OBJ files from static NPC, scene-object, and [RuneProfile](https://www.runeprofile.com/) player models. The output can be opened in Fusion 360 or Blender and be used for 3D printing.

## Selecting A Mesh

Enable `Mesh Export` from `Plugins...`. Its right sidebar opens immediately with `No Mesh Selected`; export controls remain disabled until a model is selected.

In the 3D viewer, point at an NPC or object and right-click `Export Mesh`. Objects receive a silhouette outline while hovered, including their current animation frame, and their object ID appears in the top-left corner. The plugin hides the normal tile hover selector over objects. NPC hover outlines suppress the selector in the core viewer even when no plugin is enabled. Toggled agility obstacles remain outlined with the tile selector visible on top.

The sidebar loads the static model, builds a compacted version, and then attempts manifold repair. Use `Raw`, `Compacted`, and, when repair succeeds, `Repaired` to inspect and choose the version that will be saved. `Face edges` makes topology changes easier to inspect.

`NPC ID` and `Object ID` open a dialog for loading a model directly from its ID. The model name is used as the suggested filename when available, with the entered ID as the fallback.

## Player Models

The `RuneProfile Player` field starts with the shared WikiSync username when one is saved. Click `Fetch Player` or press Enter to load that player's uploaded model. The field is independent of WikiSync, so another RuneProfile username can be entered without changing the saved WikiSync profile. `Use WikiSync` restores the currently saved WikiSync username.

Player models are fetched from RuneProfile's API. A model is available only after its owner has uploaded one with RuneProfile's `Update Player Model` action.

## Connected Structures

Hold Shift and left-click an object to start a structure selection. Continue Shift-clicking walls, roofs, and other object parts to add them. A part is accepted only when its actual world-space triangles touch or intersect a part already in the selection. This prevents one export from containing separated structures.

Shift-click a selected object again to remove it. Removal is blocked when it would split the remaining selection into disconnected groups. `Clear Selection` resets the structure.

## Compaction And Export

Compaction preserves the visible shape without smoothing. It removes duplicate and enclosed internal shells, joins overlapping shells when it does not increase the face count, and recalculates coplanar regions with fewer faces. Compaction never produces more faces than the original mesh.

Manifold repair runs separately after compaction. It repairs winding, joins split edges, closes openings, removes excess faces around non-manifold edges, and gives disconnected open details a minimal thickness when needed for printing. This stage may add faces when bridging visible gaps. A successful repair appears as its own preview and can be selected for export.

If automatic repair cannot make the mesh watertight, the unchanged compacted mesh remains exportable. The sidebar identifies it as non-watertight, and exporting requires confirmation because another modeling or slicing application may need to repair it before printing.

Raw export preserves the source mesh without compaction or manifold repair. Raw meshes almost always contain manifold problems, so exporting one requires confirmation. This option is intended for external repair or digital uses that do not require watertight geometry.

Select the version to save, set `Longest side (mm)`, click `Export Mesh...`, and choose STL or OBJ. The file chooser starts with the NPC or object name, its ID is used when the cache has no name. Connected selections use a structure filename.
