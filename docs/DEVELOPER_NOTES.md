# Notes For Developers

## Code Convention

This repository uses the same code convention as RuneLite expects for their core and plugin development.

More information on this can be found at https://github.com/runelite/runelite/wiki/Code-Conventions.

You should also attach a header with the BSD 2-Clause license and your copyright information in each file, just like RuneLite expects.

## Plugins

The plugin API document is at [PLUGIN_API.md](PLUGIN_API.md)

Build the API JAR by running the Gradle task `buildPluginApi` from your IDE or Gradle tool window.

The API lets plugins do things like:

- Add a button to the left rail.
- Add panels to the left or right side of the app.
- Draw overlays on the map.
- Draw overlays in the 3D viewer.
- Show tooltips for map tiles.
- Handle map clicks and tile selection.
- Handle 3D tile context actions.
- Search bundled map area metadata.
- Save plugin settings in the shared config manager.

Plugins are loaded from JAR files with Java `ServiceLoader`. See [PLUGIN_API.md](PLUGIN_API.md) for the exact setup.

## RuneLite Cache Dependency

This project uses a shaded RuneLite cache JAR that is bundled with this repository:

`lib/runelite-cache/cache-<version>-SNAPSHOT-shaded.jar`

This file is not published by RuneLite, however it is simply their Cache module with dependencies included.

**How to generate a shaded RuneLite cache module .jar file without modifying RuneLite's Gradle settings and use it for OS Map Viewer:**

1. In this repository, open the excluded `RuneLite Cache Shadow Script` folder and copy `runelite-cache-shadow.gradle.kts`.
2. In your home folder, open `.gradle/init.d` and paste the script there. Create the `init.d` folder if it does not exist. On Windows this is under `C:\Users\<you>\.gradle\init.d`.
3. Fork the RuneLite repository into a local folder if you haven't already, then open it into IntelliJ and run the Gradle tasks `shadowJar` and `publishToMavenLocal` for the **cache** module.
4. After the Gradle tasks finish, open your home folder and browse to `.m2/repository/net/runelite/cache/<version>-SNAPSHOT/`.
5. Copy `cache-<version>-SNAPSHOT-shaded.jar` from that folder.
6. In this repository, open `lib/runelite-cache`, remove the old shaded cache JAR, and paste the new one.
7. Open the root `build.gradle.kts` and update `runeliteCacheVersion` to the same version number, without `-SNAPSHOT`.
8. Rebuild OS Map Viewer from your IDE or Gradle tool window to verify the updated bundled JAR.

The inclusion of this Gradle script is purely an open-source measure. OS Map Viewer will bump the cache module version as game updates roll out.

## 3D Cache Decoding

The 3D viewer should keep cache-specific renderer extensions in `com.xeon.view3d`. Do not import bundled plugin packages into core viewer code, and do not modify the RuneLite cache module for viewer-only fields.

NPC rendering follows that rule. `view3d` owns the extra NPC definition decoding, spawn index loading, Maya-sequence approximation, and NPC wander collision helper used by the renderer. NPC wander may use the bundled 2D collision resource, but camera collision must not use that map because it is tile-based and has no object mesh height.

## NVIDIA PRIME Development Launch

Hybrid-GPU Linux machines using discrete NVIDIA graphics may need NVIDIA PRIME render offload for the 3D viewer to use the discrete GPU. The normal Gradle `run` task does not force this, so forks and machines with correct OS-level GPU routing are unaffected.

For local development, use either:

```bash
./gradlew runNvidiaPrime
```

or:

```bash
./gradlew run -Posmapviewer.nvidiaPrime=true
```

In IntelliJ, run the Gradle task `runNvidiaPrime`, or put `-Posmapviewer.nvidiaPrime=true` in the Gradle run configuration's arguments field.

If a system needs an explicit PRIME provider name, pass it as:

```bash
./gradlew runNvidiaPrime -Posmapviewer.nvidiaPrimeProvider=NVIDIA-G0
```

The equivalent environment variables are `OSMAPVIEWER_NVIDIA_PRIME=true` and `OSMAPVIEWER_NVIDIA_PRIME_PROVIDER=NVIDIA-G0`, but the dedicated task is preferred because Gradle daemon environment changes can be stale between runs.
