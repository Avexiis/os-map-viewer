package com.xeon.tools;

import com.displee.cache.CacheLibrary;
import com.xeon.view3d.TerrainRegionLoader;
import com.xeon.view3d.TerrainMesh;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.index.FileData;
import net.runelite.cache.fs.jagex.DiskStorage;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class HdosCacheProbe
{
    private static final Path DEFAULT_HDOS_CACHE = Path.of(
        "/home/xeon/.local/share/bolt-launcher/hdos/bin/cache/staged/runescape");
    private static final int DEFAULT_REGION_ID = 12850;
    private static final int[] ZERO_XTEA = new int[4];

    private static final IndexType[] REQUIRED_RUNELITE_INDEXES = {
        IndexType.ANIMATIONS,
        IndexType.SKELETONS,
        IndexType.CONFIGS,
        IndexType.MAPS,
        IndexType.MODELS,
        IndexType.SPRITES,
        IndexType.TEXTURES
    };

    private static final int[] REQUIRED_DISPLEE_INDEXES = {
        0, // animations
        1, // skeletons
        2, // configs
        5, // maps
        7, // models
        8, // sprites
        9  // textures
    };

    private static final IndexType[] SHAPE_RUNELITE_INDEXES = {
        IndexType.ANIMATIONS,
        IndexType.SKELETONS,
        IndexType.CONFIGS,
        IndexType.MAPS,
        IndexType.MODELS,
        IndexType.SPRITES,
        IndexType.TEXTURES,
        IndexType.ANIMAYAS
    };

    private static final ConfigArchive[] CURRENT_CONFIG_ARCHIVES = {
        new ConfigArchive("underlay", 1),
        new ConfigArchive("overlay", 4),
        new ConfigArchive("object", 6),
        new ConfigArchive("npc", 9),
        new ConfigArchive("sequence", 12),
        new ConfigArchive("varbit", 14)
    };

    private HdosCacheProbe()
    {
    }

    public static void main(String[] args) throws Exception
    {
        Options options = Options.parse(args);
        Path cacheDirectory = options.cacheDirectory().toAbsolutePath().normalize();
        int regionId = options.regionId();

        System.out.println("HDOS cache probe");
        System.out.println("cache=" + cacheDirectory);
        System.out.println("region=" + regionId + " (" + regionX(regionId) + "," + regionY(regionId) + ")");

        if (!Files.isDirectory(cacheDirectory))
        {
            throw new IllegalArgumentException("Cache directory does not exist: " + cacheDirectory);
        }

        List<ProbeResult> results = new ArrayList<>();

        ObjectDefinition[] objectDefinitions = new ObjectDefinition[0];
        try (Store store = new Store(new DiskStorage(cacheDirectory.toFile())))
        {
            Store runeLiteStore = store;
            results.add(probe("RuneLite Store.load", true, () -> {
                runeLiteStore.load();
                return runeLiteStore.getIndexes().size() + " indexes";
            }));

            results.add(probe("RuneLite required indexes", true, () -> requiredRuneLiteIndexes(runeLiteStore)));
            results.add(probe("RuneLite HDOS index shape", false, () -> runeLiteIndexShape(runeLiteStore)));
            results.add(probe("RuneLite current config archives", true, () -> runeLiteConfigArchives(runeLiteStore)));
            results.add(probe("RuneLite animation indexes", true, () -> runeLiteAnimationIndexes(runeLiteStore)));

            ObjectManagerResult objectResult = probeObjectDefinitions(runeLiteStore);
            objectDefinitions = objectResult.objectDefinitions();
            results.add(objectResult.result());

            results.add(probe("RuneLite underlays/overlays", true, () -> {
                net.runelite.cache.definitions.loaders.UnderlayLoader underlayLoader =
                    new net.runelite.cache.definitions.loaders.UnderlayLoader();
                net.runelite.cache.definitions.loaders.OverlayLoader overlayLoader =
                    new net.runelite.cache.definitions.loaders.OverlayLoader();
                loadConfigArchive(runeLiteStore, IndexType.CONFIGS, 1, underlayLoader::load);
                loadConfigArchive(runeLiteStore, IndexType.CONFIGS, 4, overlayLoader::load);
                return "underlay archive 1 and overlay archive 4 decoded";
            }));

            results.add(probe("RuneLite NPCs", true, () -> {
                net.runelite.cache.definitions.loaders.NpcLoader npcLoader =
                    new net.runelite.cache.definitions.loaders.NpcLoader();
                int count = loadConfigArchive(runeLiteStore, IndexType.CONFIGS, 9, npcLoader::load);
                return count + " npc definitions";
            }));

            results.add(probe("RuneLite varbits", true, () -> {
                net.runelite.cache.definitions.loaders.VarbitLoader varbitLoader =
                    new net.runelite.cache.definitions.loaders.VarbitLoader();
                int count = loadConfigArchive(runeLiteStore, IndexType.CONFIGS, 14, varbitLoader::load);
                return count + " varbits";
            }));

            results.add(probeTextures(runeLiteStore));

            results.add(probe("RuneLite sprites", false, () -> {
                net.runelite.cache.SpriteManager spriteManager = new net.runelite.cache.SpriteManager(runeLiteStore);
                spriteManager.load();
                return spriteManager.getSprites().size() + " sprites";
            }));

            ObjectDefinition[] objectDefinitionsFinal = objectDefinitions;
            results.add(probe("RuneLite sample object model", true,
                () -> sampleObjectModel(runeLiteStore, objectDefinitionsFinal)));

            results.add(probe("RuneLite RegionLoader", true,
                () -> loadRegion(runeLiteStore, regionId)));

            results.add(probe("OS Map 3D terrain loader", true,
                () -> loadFullScene(cacheDirectory, regionId)));
        }
        catch (Throwable ex)
        {
            results.add(ProbeResult.fail("RuneLite Store probe setup", true, rootMessage(ex)));
        }

        CacheLibrary library = null;
        try
        {
            library = CacheLibrary.create(cacheDirectory.toString());
            CacheLibrary displeeLibrary = library;
            results.add(ProbeResult.pass("Displee CacheLibrary.load", true, displeeSummary(displeeLibrary)));
            results.add(probe("Displee required indexes", true, () -> requiredDispleeIndexes(displeeLibrary)));
            results.add(probe("Displee all index scan", false, () -> displeeAllIndexScan(displeeLibrary)));
            results.add(probe("Displee HDOS index shape", false, () -> displeeIndexShape(displeeLibrary)));
            results.add(probe("Displee current config archives", true,
                () -> displeeCurrentConfigArchives(displeeLibrary)));
            results.add(probe("Displee config archive scan", false,
                () -> displeeArchiveScan(displeeLibrary, 2, 32)));
            results.add(probe("Displee config decoder scan", false,
                () -> withSuppressedErr(() -> displeeConfigDecoderScan(displeeLibrary))));
            results.add(probe("Displee map archives", true,
                () -> displeeMapArchives(displeeLibrary, regionId)));
            results.add(probe("Displee model data with RuneLite model decoder", true,
                () -> displeeModelDecode(displeeLibrary)));
            results.add(probe("Displee animation/skeleton data", true,
                () -> displeeAnimationData(displeeLibrary)));
            results.add(probe("Displee sprite/texture data", false,
                () -> displeeSpriteTextureData(displeeLibrary)));
            results.add(probe("HDOS maps-cache sidecar", false,
                () -> hdosMapsCacheSidecar(cacheDirectory, regionId)));
        }
        catch (Throwable ex)
        {
            results.add(ProbeResult.fail("Displee CacheLibrary probe setup", true, rootMessage(ex)));
        }
        finally
        {
            if (library != null)
            {
                library.close();
            }
        }

        System.out.println();
        boolean failedRequired = false;
        for (ProbeResult result : results)
        {
            String status = result.passed() ? "PASS" : result.required() ? "FAIL" : "WARN";
            System.out.printf("[%s] %s - %s%n", status, result.name(), result.detail());
            failedRequired |= result.required() && !result.passed();
        }

        if (failedRequired)
        {
            System.out.flush();
            System.out.println("One or more required HDOS cache probes failed.");
            System.exit(1);
        }
    }

    private static String requiredRuneLiteIndexes(Store store)
    {
        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (IndexType type : REQUIRED_RUNELITE_INDEXES)
        {
            Index index = store.getIndex(type);
            String label = type.name().toLowerCase(Locale.ROOT) + "(idx" + type.getNumber() + ")";
            if (index == null)
            {
                missing.add(label);
            }
            else
            {
                present.add(label);
            }
        }
        if (!missing.isEmpty())
        {
            throw new IllegalStateException("missing indexes " + missing + "; present " + present);
        }
        return "present " + present;
    }

    private static String runeLiteIndexShape(Store store)
    {
        List<String> parts = new ArrayList<>();
        for (IndexType type : SHAPE_RUNELITE_INDEXES)
        {
            Index index = store.getIndex(type);
            if (index == null)
            {
                parts.add(type.name().toLowerCase(Locale.ROOT) + "(idx" + type.getNumber() + ")=missing");
            }
            else
            {
                parts.add(type.name().toLowerCase(Locale.ROOT) + "(idx" + type.getNumber() + ")="
                    + index.getArchives().size() + " archives");
            }
        }
        Index configs = store.getIndex(IndexType.CONFIGS);
        if (configs != null)
        {
            parts.add("configIds=" + formatRuneLiteArchiveIds(configs, 64));
        }
        return String.join("; ", parts);
    }

    private static String runeLiteConfigArchives(Store store)
    {
        Index configs = requireIndex(store, IndexType.CONFIGS);
        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (ConfigArchive configArchive : CURRENT_CONFIG_ARCHIVES)
        {
            Archive archive = configs.getArchive(configArchive.id());
            if (archive == null)
            {
                missing.add(configArchive.label() + "(" + configArchive.id() + ")");
            }
            else
            {
                present.add(configArchive.label() + "(" + configArchive.id() + ")="
                    + archive.getFileData().length + " files");
            }
        }
        if (!missing.isEmpty())
        {
            throw new IllegalStateException("missing current RuneLite config archives " + missing
                + "; present " + present);
        }
        return String.join(", ", present);
    }

    private static String runeLiteAnimationIndexes(Store store) throws IOException
    {
        Index animations = requireIndex(store, IndexType.ANIMATIONS);
        Index skeletons = requireIndex(store, IndexType.SKELETONS);
        StringBuilder result = new StringBuilder();
        result.append("animations=").append(animations.getArchives().size()).append(" archives");
        result.append(", skeletons=").append(skeletons.getArchives().size()).append(" archives");

        Archive animationArchive = firstArchive(animations);
        if (animationArchive != null)
        {
            byte[] animationData = firstArchiveFileData(store, animationArchive);
            result.append(", sample animation archive ").append(animationArchive.getArchiveId())
                .append(" first file ").append(dataSummary(animationData));
        }

        Archive skeletonArchive = firstArchive(skeletons);
        if (skeletonArchive != null)
        {
            byte[] skeletonData = firstArchiveFileData(store, skeletonArchive);
            result.append(", sample skeleton archive ").append(skeletonArchive.getArchiveId())
                .append(" first file ").append(dataSummary(skeletonData));
        }

        return result.toString();
    }

    private static ObjectManagerResult probeObjectDefinitions(Store store)
    {
        try
        {
            net.runelite.cache.ObjectManager objectManager = new net.runelite.cache.ObjectManager(store);
            objectManager.load();
            ObjectDefinition[] definitions = objectManager.getObjects().toArray(ObjectDefinition[]::new);
            return new ObjectManagerResult(definitions,
                ProbeResult.pass("RuneLite objects", true, definitions.length + " object definitions"));
        }
        catch (Throwable ex)
        {
            return new ObjectManagerResult(new ObjectDefinition[0],
                ProbeResult.fail("RuneLite objects", true, rootMessage(ex)));
        }
    }

    private static ProbeResult probeTextures(Store store)
    {
        try
        {
            TextureLoader textureLoader = new TextureLoader();
            Index index = requireIndex(store, IndexType.TEXTURES);
            Archive archive = index.getArchive(0);
            if (archive == null)
            {
                throw new IllegalStateException("texture archive 0 missing");
            }
            byte[] archiveData = store.getStorage().loadArchive(archive);
            ArchiveFiles files = archive.getFiles(archiveData);
            int textureCount = 0;
            for (FileData fileData : archive.getFileData())
            {
                FSFile file = files.findFile(fileData.getId());
                if (file == null || file.getContents() == null)
                {
                    continue;
                }
                textureLoader.load(fileData.getId(), file.getContents());
                textureCount++;
            }
            return ProbeResult.pass("RuneLite textures", true, textureCount + " textures");
        }
        catch (Throwable ex)
        {
            return ProbeResult.fail("RuneLite textures", true, rootMessage(ex));
        }
    }

    private static String sampleObjectModel(Store store, ObjectDefinition[] objectDefinitions) throws IOException
    {
        if (objectDefinitions.length == 0)
        {
            throw new IllegalStateException("no object definitions loaded");
        }

        Index modelIndex = requireIndex(store, IndexType.MODELS);
        ModelLoader modelLoader = new ModelLoader();
        List<String> skipped = new ArrayList<>();

        for (ObjectDefinition object : objectDefinitions)
        {
            if (object == null || object.getObjectModels() == null)
            {
                continue;
            }

            for (int modelId : object.getObjectModels())
            {
                Archive archive = modelIndex.getArchive(modelId);
                if (archive == null)
                {
                    skipped.add(modelId + ":archive missing");
                    continue;
                }

                try
                {
                    byte[] data = store.getStorage().loadArchive(archive);
                    ModelDefinition model = modelLoader.load(modelId, data);
                    return "object " + object.getId() + " model " + modelId
                        + " vertices=" + model.getVertexCount()
                        + " faces=" + model.getFaceCount()
                        + " bytes=" + data.length;
                }
                catch (Throwable ex)
                {
                    skipped.add(modelId + ":" + rootMessage(ex));
                    if (skipped.size() >= 8)
                    {
                        throw new IllegalStateException("failed first sample models " + skipped, ex);
                    }
                }
            }
        }

        throw new IllegalStateException("no object model ids found; skipped " + skipped);
    }

    private static String loadRegion(Store store, int regionId) throws IOException
    {
        RegionLoader regionLoader = new RegionLoader(store, id -> ZERO_XTEA);
        Region region = regionLoader.loadRegionFromArchive(regionId);
        if (region == null)
        {
            throw new IllegalStateException("region not found after load");
        }
        return "region " + region.getRegionID()
            + ", locations=" + region.getLocations().size()
            + ", base=(" + region.getBaseX() + "," + region.getBaseY() + ")";
    }

    private static String loadFullScene(Path cacheDirectory, int regionId) throws IOException
    {
        TerrainMesh mesh = new TerrainRegionLoader().loadFullScene(cacheDirectory, regionId, false);
        return "region " + mesh.regionId()
            + ", vertices=" + mesh.vertexCount()
            + ", camera=(" + mesh.initialCameraX() + ","
            + mesh.initialCameraY() + ","
            + mesh.initialCameraZ() + ")";
    }

    private static int loadConfigArchive(
        Store store,
        IndexType indexType,
        int archiveId,
        ConfigLoader loader) throws IOException
    {
        Index index = requireIndex(store, indexType);
        Archive archive = index.getArchive(archiveId);
        if (archive == null)
        {
            throw new IllegalStateException("archive " + archiveId + " missing from " + indexType);
        }

        byte[] archiveData = store.getStorage().loadArchive(archive);
        ArchiveFiles files = archive.getFiles(archiveData);
        int count = 0;
        for (FileData fileData : archive.getFileData())
        {
            FSFile file = files.findFile(fileData.getId());
            if (file == null || file.getContents() == null)
            {
                continue;
            }
            loader.load(fileData.getId(), file.getContents());
            count++;
        }
        return count;
    }

    private static String displeeSummary(CacheLibrary library)
    {
        return library.validIndices().length + " valid indexes; totalSlots=" + library.getIndices().length
            + "; mode=" + (library.is317() ? "317" : "modern")
            + "; osrs=" + library.isOSRS()
            + "; rs3=" + library.isRS3();
    }

    private static String requiredDispleeIndexes(CacheLibrary library)
    {
        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (int indexId : REQUIRED_DISPLEE_INDEXES)
        {
            com.displee.cache.index.Index index = library.index(indexId);
            String label = displeeIndexLabel(indexId);
            if (index == null)
            {
                missing.add(label);
            }
            else
            {
                present.add(label);
            }
        }
        if (!missing.isEmpty())
        {
            throw new IllegalStateException("missing indexes " + missing + "; present " + present);
        }
        return "present " + present;
    }

    private static String displeeIndexShape(CacheLibrary library)
    {
        List<String> parts = new ArrayList<>();
        for (int indexId : new int[] {0, 1, 2, 5, 7, 8, 9, 22})
        {
            com.displee.cache.index.Index index = library.index(indexId);
            if (index == null)
            {
                parts.add(displeeIndexLabel(indexId) + "=missing");
            }
            else
            {
                parts.add(displeeIndexLabel(indexId) + "=" + index.archiveIds().length + " archives"
                    + ", revision=" + index.getRevision());
            }
        }

        com.displee.cache.index.Index configs = library.index(2);
        if (configs != null)
        {
            parts.add("configIds=" + formatIds(configs.archiveIds(), 64));
        }
        return String.join("; ", parts);
    }

    private static String displeeAllIndexScan(CacheLibrary library)
    {
        List<String> parts = new ArrayList<>();
        for (com.displee.cache.index.Index index : library.validIndices())
        {
            if (index == null)
            {
                continue;
            }
            String sample = "";
            int[] archiveIds = index.archiveIds();
            if (archiveIds.length > 0)
            {
                try
                {
                    com.displee.cache.index.archive.Archive archive = index.archive(archiveIds[0]);
                    if (archive != null)
                    {
                        com.displee.cache.index.archive.file.File file = archive.first();
                        sample = ", firstArchive=" + archive.getId()
                            + ", files=" + archive.files().length
                            + ", " + archive.getCompressionType()
                            + ", first=" + dataSummary(file == null ? null : file.getData());
                    }
                }
                catch (Throwable ex)
                {
                    sample = ", firstArchive=" + archiveIds[0] + " ERR " + rootMessage(ex);
                }
            }
            parts.add("idx" + index.getId()
                + " archives=" + archiveIds.length
                + ", revision=" + index.getRevision()
                + sample);
        }
        return String.join("; ", parts);
    }

    private static String displeeCurrentConfigArchives(CacheLibrary library)
    {
        com.displee.cache.index.Index configs = requireDispleeIndex(library, 2);
        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (ConfigArchive configArchive : CURRENT_CONFIG_ARCHIVES)
        {
            try
            {
                com.displee.cache.index.archive.Archive archive = configs.archive(configArchive.id());
                if (archive == null)
                {
                    missing.add(configArchive.label() + "(" + configArchive.id() + ")");
                }
                else
                {
                    present.add(configArchive.label() + "(" + configArchive.id() + ")="
                        + archive.files().length + " files, compression=" + archive.getCompressionType());
                }
            }
            catch (Throwable ex)
            {
                errors.add(configArchive.label() + "(" + configArchive.id() + "):" + rootMessage(ex));
            }
        }

        if (!missing.isEmpty() || !errors.isEmpty())
        {
            throw new IllegalStateException("missing current config archives " + missing
                + "; errors " + errors + "; present " + present);
        }
        return String.join(", ", present);
    }

    private static String displeeArchiveScan(CacheLibrary library, int indexId, int limit)
    {
        com.displee.cache.index.Index index = requireDispleeIndex(library, indexId);
        int[] archiveIds = index.archiveIds();
        List<String> parts = new ArrayList<>();
        int count = Math.min(limit, archiveIds.length);

        for (int i = 0; i < count; i++)
        {
            int archiveId = archiveIds[i];
            try
            {
                com.displee.cache.index.archive.Archive archive = index.archive(archiveId);
                if (archive == null)
                {
                    parts.add(archiveId + ":missing");
                    continue;
                }
                com.displee.cache.index.archive.file.File file = archive.first();
                byte[] data = file == null ? null : file.getData();
                parts.add(archiveId + ":" + archive.files().length + " files"
                    + ", " + archive.getCompressionType()
                    + ", first=" + dataSummary(data));
            }
            catch (Throwable ex)
            {
                parts.add(archiveId + ":ERR " + rootMessage(ex));
            }
        }

        if (archiveIds.length > count)
        {
            parts.add("...+" + (archiveIds.length - count) + " archives");
        }
        return String.join("; ", parts);
    }

    private static String displeeConfigDecoderScan(CacheLibrary library)
    {
        com.displee.cache.index.Index configs = requireDispleeIndex(library, 2);
        DecoderCandidate[] candidates = {
            new DecoderCandidate("underlay", 2, revision -> {
                net.runelite.cache.definitions.loaders.UnderlayLoader loader =
                    new net.runelite.cache.definitions.loaders.UnderlayLoader();
                return (id, data) -> loader.load(id, data);
            }),
            new DecoderCandidate("overlay", 2, revision -> {
                net.runelite.cache.definitions.loaders.OverlayLoader loader =
                    new net.runelite.cache.definitions.loaders.OverlayLoader();
                return (id, data) -> loader.load(id, data);
            }),
            new DecoderCandidate("object", 4, revision -> {
                net.runelite.cache.definitions.loaders.ObjectLoader loader =
                    new net.runelite.cache.definitions.loaders.ObjectLoader().configureForRevision(revision);
                return (id, data) -> loader.load(id, data);
            }),
            new DecoderCandidate("npc", 4, revision -> {
                net.runelite.cache.definitions.loaders.NpcLoader loader =
                    new net.runelite.cache.definitions.loaders.NpcLoader().configureForRevision(revision);
                return (id, data) -> loader.load(id, data);
            }),
            new DecoderCandidate("sequence", 4, revision -> {
                net.runelite.cache.definitions.loaders.SequenceLoader loader =
                    new net.runelite.cache.definitions.loaders.SequenceLoader().configureForRevision(revision);
                return (id, data) -> loader.load(id, data);
            }),
            new DecoderCandidate("varbit", 4, revision -> {
                net.runelite.cache.definitions.loaders.VarbitLoader loader =
                    new net.runelite.cache.definitions.loaders.VarbitLoader();
                return (id, data) -> loader.load(id, data);
            })
        };

        List<String> candidateSummaries = new ArrayList<>();
        for (DecoderCandidate candidate : candidates)
        {
            List<String> hits = new ArrayList<>();
            for (int archiveId : configs.archiveIds())
            {
                try
                {
                    com.displee.cache.index.archive.Archive archive = configs.archive(archiveId);
                    if (archive == null)
                    {
                        continue;
                    }

                    DecodeStats stats = scanConfigArchive(archive, candidate);
                    if (probableDecoderMatch(stats))
                    {
                        hits.add(archiveId + "=" + stats.successes() + "/" + stats.consideredFiles()
                            + " decoded, errors=" + stats.failures()
                            + ", first=" + stats.firstSuccessFileId());
                    }
                }
                catch (Throwable ex)
                {
                    hits.add(archiveId + "=ERR " + rootMessage(ex));
                }
            }

            if (hits.isEmpty())
            {
                candidateSummaries.add(candidate.label() + ": no likely archive");
            }
            else
            {
                candidateSummaries.add(candidate.label() + ": " + String.join(", ", hits));
            }
        }

        return String.join("; ", candidateSummaries);
    }

    private static DecodeStats scanConfigArchive(
        com.displee.cache.index.archive.Archive archive,
        DecoderCandidate candidate)
    {
        ConfigLoader decoder = candidate.factory().create(archive.getRevision());
        int totalFiles = 0;
        int consideredFiles = 0;
        int successes = 0;
        int failures = 0;
        int firstSuccessFileId = -1;

        for (com.displee.cache.index.archive.file.File file : archive.files())
        {
            totalFiles++;
            byte[] data = file.getData();
            if (data == null || data.length < candidate.minimumBytes())
            {
                continue;
            }

            consideredFiles++;
            try
            {
                decoder.load(file.getId(), data);
                successes++;
                if (firstSuccessFileId < 0)
                {
                    firstSuccessFileId = file.getId();
                }
            }
            catch (Throwable ex)
            {
                failures++;
            }
        }

        return new DecodeStats(totalFiles, consideredFiles, successes, failures, firstSuccessFileId);
    }

    private static boolean probableDecoderMatch(DecodeStats stats)
    {
        if (stats.consideredFiles() < 4 || stats.successes() == 0)
        {
            return false;
        }
        if (stats.failures() == 0)
        {
            return stats.successes() >= 4;
        }
        return stats.successes() >= 8 && stats.successes() >= stats.failures() * 3;
    }

    private static String displeeMapArchives(CacheLibrary library, int regionId)
    {
        int x = regionX(regionId);
        int y = regionY(regionId);
        String mapName = "m" + x + "_" + y;
        String locationName = "l" + x + "_" + y;

        byte[] mapData = library.data(5, mapName, ZERO_XTEA);
        byte[] locationData = library.data(5, locationName, ZERO_XTEA);

        if (mapData == null || locationData == null)
        {
            throw new IllegalStateException(mapName + "=" + dataSummary(mapData)
                + ", " + locationName + "=" + dataSummary(locationData));
        }

        return mapName + "=" + dataSummary(mapData)
            + ", " + locationName + "=" + dataSummary(locationData);
    }

    private static String displeeModelDecode(CacheLibrary library)
    {
        com.displee.cache.index.Index models = requireDispleeIndex(library, 7);
        ModelLoader modelLoader = new ModelLoader();
        List<String> skipped = new ArrayList<>();

        for (int modelId : models.archiveIds())
        {
            byte[] data;
            try
            {
                data = library.data(7, modelId);
            }
            catch (Throwable ex)
            {
                skipped.add(modelId + ":" + rootMessage(ex));
                if (skipped.size() >= 16)
                {
                    break;
                }
                continue;
            }

            if (data == null || data.length == 0)
            {
                continue;
            }

            try
            {
                ModelDefinition model = modelLoader.load(modelId, data);
                return "model " + modelId
                    + " vertices=" + model.getVertexCount()
                    + " faces=" + model.getFaceCount()
                    + " " + dataSummary(data);
            }
            catch (Throwable ex)
            {
                skipped.add(modelId + ":" + rootMessage(ex) + " " + dataSummary(data));
                if (skipped.size() >= 16)
                {
                    break;
                }
            }
        }

        throw new IllegalStateException("no Displee model payload decoded with RuneLite ModelLoader; first failures "
            + skipped);
    }

    private static String displeeAnimationData(CacheLibrary library)
    {
        String animations = displeeSampleArchive(library, 0, 8);
        String skeletons = displeeSampleArchive(library, 1, 8);
        return "animations: " + animations + "; skeletons: " + skeletons;
    }

    private static String displeeSpriteTextureData(CacheLibrary library)
    {
        String sprites = displeeSampleArchive(library, 8, 8);
        String textures = displeeSampleArchive(library, 9, 8);
        return "sprites: " + sprites + "; textures: " + textures;
    }

    private static String hdosMapsCacheSidecar(Path cacheDirectory, int regionId) throws IOException
    {
        Path binDirectory = cacheDirectory.getParent() == null
            || cacheDirectory.getParent().getParent() == null
            || cacheDirectory.getParent().getParent().getParent() == null
            ? null
            : cacheDirectory.getParent().getParent().getParent();
        Path mapsCache = binDirectory == null ? null : binDirectory.resolve("maps-cache");
        if (mapsCache == null || !Files.isDirectory(mapsCache))
        {
            throw new IllegalStateException("maps-cache sidecar not found next to HDOS bin directory");
        }

        int x = regionX(regionId);
        int y = regionY(regionId);
        String prefix = "ml" + x + "_" + y + "-";
        List<String> matches = new ArrayList<>();
        long totalFiles;
        try (Stream<Path> stream = Files.list(mapsCache))
        {
            List<Path> files = stream
                .filter(Files::isRegularFile)
                .toList();
            totalFiles = files.size();
            for (Path file : files)
            {
                String fileName = file.getFileName().toString();
                if (fileName.startsWith(prefix))
                {
                    matches.add(fileName + "=" + Files.size(file) + " bytes " + filePrefix(file, 12));
                }
            }
        }

        if (matches.isEmpty())
        {
            return "files=" + totalFiles + ", no " + prefix + "* sidecar file";
        }
        return "files=" + totalFiles + ", " + prefix + "*=" + matches;
    }

    private static String filePrefix(Path file, int limit) throws IOException
    {
        byte[] data = Files.readAllBytes(file);
        if (data.length <= limit)
        {
            return hexPrefix(data, limit);
        }
        byte[] prefix = new byte[limit];
        System.arraycopy(data, 0, prefix, 0, limit);
        return hexPrefix(prefix, limit) + "...";
    }

    private static String displeeSampleArchive(CacheLibrary library, int indexId, int limit)
    {
        com.displee.cache.index.Index index = requireDispleeIndex(library, indexId);
        List<String> parts = new ArrayList<>();
        int checked = 0;

        for (int archiveId : index.archiveIds())
        {
            if (checked >= limit)
            {
                break;
            }

            try
            {
                com.displee.cache.index.archive.Archive archive = index.archive(archiveId);
                if (archive == null)
                {
                    continue;
                }
                com.displee.cache.index.archive.file.File first = archive.first();
                byte[] data = first == null ? null : first.getData();
                parts.add(archiveId + ":" + archive.files().length + " files"
                    + ", " + archive.getCompressionType()
                    + ", first=" + dataSummary(data));
                checked++;
            }
            catch (Throwable ex)
            {
                parts.add(archiveId + ":ERR " + rootMessage(ex));
                checked++;
            }
        }

        if (parts.isEmpty())
        {
            throw new IllegalStateException(displeeIndexLabel(indexId) + " contains no readable archives");
        }

        return String.join("; ", parts);
    }

    private static Index requireIndex(Store store, IndexType type)
    {
        Index index = store.getIndex(type);
        if (index == null)
        {
            throw new IllegalStateException("missing index " + type + " (" + type.getNumber() + ")");
        }
        return index;
    }

    private static com.displee.cache.index.Index requireDispleeIndex(CacheLibrary library, int indexId)
    {
        com.displee.cache.index.Index index = library.index(indexId);
        if (index == null)
        {
            throw new IllegalStateException("missing index " + displeeIndexLabel(indexId));
        }
        return index;
    }

    private static Archive firstArchive(Index index)
    {
        for (Archive archive : index.getArchives())
        {
            if (archive != null)
            {
                return archive;
            }
        }
        return null;
    }

    private static byte[] firstArchiveFileData(Store store, Archive archive) throws IOException
    {
        byte[] archiveData = store.getStorage().loadArchive(archive);
        ArchiveFiles files = archive.getFiles(archiveData);
        for (FileData fileData : archive.getFileData())
        {
            FSFile file = files.findFile(fileData.getId());
            if (file != null && file.getContents() != null)
            {
                return file.getContents();
            }
        }
        return null;
    }

    private static String formatRuneLiteArchiveIds(Index index, int limit)
    {
        List<Integer> ids = new ArrayList<>();
        for (Archive archive : index.getArchives())
        {
            if (archive != null)
            {
                ids.add(archive.getArchiveId());
            }
        }
        int[] primitiveIds = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++)
        {
            primitiveIds[i] = ids.get(i);
        }
        return formatIds(primitiveIds, limit);
    }

    private static String formatIds(int[] ids, int limit)
    {
        StringBuilder builder = new StringBuilder("[");
        int count = Math.min(ids.length, limit);
        for (int i = 0; i < count; i++)
        {
            if (i > 0)
            {
                builder.append(", ");
            }
            builder.append(ids[i]);
        }
        if (ids.length > count)
        {
            builder.append(", ...+").append(ids.length - count);
        }
        return builder.append(']').toString();
    }

    private static String dataSummary(byte[] data)
    {
        if (data == null)
        {
            return "null";
        }
        return data.length + " bytes " + hexPrefix(data, 12);
    }

    private static String hexPrefix(byte[] data, int limit)
    {
        if (data == null || data.length == 0)
        {
            return "";
        }

        StringBuilder builder = new StringBuilder("hex=");
        int count = Math.min(data.length, limit);
        for (int i = 0; i < count; i++)
        {
            if (i > 0)
            {
                builder.append(' ');
            }
            builder.append(String.format("%02x", data[i] & 0xff));
        }
        if (data.length > count)
        {
            builder.append("...");
        }
        return builder.toString();
    }

    private static String displeeIndexLabel(int indexId)
    {
        return switch (indexId)
        {
            case 0 -> "animations(idx0)";
            case 1 -> "skeletons(idx1)";
            case 2 -> "configs(idx2)";
            case 5 -> "maps(idx5)";
            case 7 -> "models(idx7)";
            case 8 -> "sprites(idx8)";
            case 9 -> "textures(idx9)";
            case 22 -> "animayas(idx22)";
            default -> "idx" + indexId;
        };
    }

    private static int regionX(int regionId)
    {
        return (regionId >> 8) & 0xff;
    }

    private static int regionY(int regionId)
    {
        return regionId & 0xff;
    }

    private static ProbeResult probe(String name, boolean required, Probe probe)
    {
        try
        {
            return ProbeResult.pass(name, required, probe.run());
        }
        catch (Throwable ex)
        {
            return ProbeResult.fail(name, required, rootMessage(ex));
        }
    }

    private static String withSuppressedErr(Probe probe) throws Exception
    {
        PrintStream previousErr = System.err;
        try (PrintStream ignored = new PrintStream(OutputStream.nullOutputStream()))
        {
            System.setErr(ignored);
            return probe.run();
        }
        finally
        {
            System.setErr(previousErr);
        }
    }

    private static String rootMessage(Throwable throwable)
    {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current)
        {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank())
        {
            message = throwable.getMessage();
        }
        if (message == null || message.isBlank())
        {
            message = current.getClass().getSimpleName();
        }
        return current.getClass().getSimpleName() + ": " + message;
    }

    @FunctionalInterface
    private interface Probe
    {
        String run() throws Exception;
    }

    @FunctionalInterface
    private interface ConfigLoader
    {
        void load(int id, byte[] data);
    }

    @FunctionalInterface
    private interface DecoderFactory
    {
        ConfigLoader create(int archiveRevision);
    }

    private record ProbeResult(String name, boolean required, boolean passed, String detail)
    {
        static ProbeResult pass(String name, boolean required, String detail)
        {
            return new ProbeResult(name, required, true, detail);
        }

        static ProbeResult fail(String name, boolean required, String detail)
        {
            return new ProbeResult(name, required, false, detail);
        }
    }

    private record ObjectManagerResult(ObjectDefinition[] objectDefinitions, ProbeResult result)
    {
    }

    private record ConfigArchive(String label, int id)
    {
    }

    private record DecoderCandidate(String label, int minimumBytes, DecoderFactory factory)
    {
    }

    private record DecodeStats(
        int totalFiles,
        int consideredFiles,
        int successes,
        int failures,
        int firstSuccessFileId)
    {
    }

    private record Options(Path cacheDirectory, int regionId)
    {
        private static Options parse(String[] args)
        {
            Path cacheDirectory = Path.of(System.getProperty("hdos.cache",
                System.getenv().getOrDefault("HDOS_CACHE_DIR", DEFAULT_HDOS_CACHE.toString())));
            int regionId = Integer.getInteger("hdos.region", DEFAULT_REGION_ID);

            for (int i = 0; i < args.length; i++)
            {
                String arg = args[i];
                if ("--cache".equals(arg) && i + 1 < args.length)
                {
                    cacheDirectory = Path.of(args[++i]);
                }
                else if ("--region".equals(arg) && i + 1 < args.length)
                {
                    regionId = Integer.parseInt(args[++i]);
                }
                else if (arg.startsWith("--cache="))
                {
                    cacheDirectory = Path.of(arg.substring("--cache=".length()));
                }
                else if (arg.startsWith("--region="))
                {
                    regionId = Integer.parseInt(arg.substring("--region=".length()));
                }
                else if (!arg.startsWith("-"))
                {
                    cacheDirectory = Path.of(arg);
                }
                else
                {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return new Options(cacheDirectory, regionId);
        }
    }
}
