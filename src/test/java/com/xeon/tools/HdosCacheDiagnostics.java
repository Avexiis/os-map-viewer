package com.xeon.tools;

import com.displee.cache.CacheLibrary;
import com.displee.cache.index.Index;
import com.displee.cache.index.archive.Archive;
import com.displee.cache.index.archive.ArchiveSector;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class HdosCacheDiagnostics
{
    public static final Path DEFAULT_HDOS_CACHE = Path.of(
        "/home/xeon/.local/share/bolt-launcher/hdos/bin/cache/staged/runescape");
    public static final int DEFAULT_REGION_ID = 12850;

    private static final int CONFIG_INDEX = 2;
    private static final int MAP_INDEX = 5;
    private static final int[] ZERO_XTEA = new int[4];
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0");

    private static final List<IndexRequirement> VIEWER_INDEXES = List.of(
        new IndexRequirement(0, "animations", true),
        new IndexRequirement(1, "skeletons", true),
        new IndexRequirement(2, "configs", true),
        new IndexRequirement(5, "maps", true),
        new IndexRequirement(7, "models", true),
        new IndexRequirement(8, "sprites", true),
        new IndexRequirement(9, "textures", true),
        new IndexRequirement(22, "animayas", false)
    );

    private static final Map<Integer, String> CONFIG_ARCHIVE_HINTS = Map.ofEntries(
        Map.entry(1, "underlays?"),
        Map.entry(3, "identkits?"),
        Map.entry(4, "overlays?"),
        Map.entry(5, "inventories?"),
        Map.entry(6, "objects?"),
        Map.entry(8, "enums?"),
        Map.entry(9, "npcs?"),
        Map.entry(10, "items?"),
        Map.entry(11, "params?"),
        Map.entry(12, "sequences?"),
        Map.entry(13, "spotanims?"),
        Map.entry(14, "varbits?"),
        Map.entry(16, "varplayers?"),
        Map.entry(19, "areas?"),
        Map.entry(26, "varclients?")
    );

    private HdosCacheDiagnostics()
    {
    }

    public static void main(String[] args) throws Exception
    {
        if (hasHelpFlag(args))
        {
            printUsage(System.out);
            return;
        }

        Result result = run(Options.parse(args), System.out);
        if (!result.loadableForDiagnostics())
        {
            System.exit(1);
        }
    }

    public static Result run(Options options, PrintStream out) throws IOException
    {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(out, "out");

        Path cacheDirectory = options.cacheDirectory().toAbsolutePath().normalize();
        int regionId = options.regionId();
        int regionX = regionX(regionId);
        int regionY = regionY(regionId);

        out.println("HDOS Displee cache diagnostics");
        out.println("cache=" + cacheDirectory);
        out.println("region=" + regionId + " (" + regionX + "," + regionY + ")");

        if (!Files.isDirectory(cacheDirectory))
        {
            throw new IllegalArgumentException("Cache directory does not exist: " + cacheDirectory);
        }

        CacheLibrary library = CacheLibrary.create(cacheDirectory.toString());
        try
        {
            Index[] validIndices = library.validIndices();
            out.println();
            out.printf(Locale.ROOT, "container: format=%s validIndices=%d slots=%d osrs=%s rs3=%s%n",
                library.is317() ? "legacy-317" : "modern-dat2",
                validIndices.length,
                library.getIndices().length,
                library.isOSRS(),
                library.isRS3());

            boolean requiredIndexesPresent = printRequiredIndexes(library, out);
            printIndexContainerScan(library, out);
            printConfigArchiveDiagnostics(library, out, options);
            RegionDiagnostics region = printRegionDiagnostics(library, out, regionId);
            printMapsCacheSidecar(cacheDirectory, out, regionId);

            return new Result(
                validIndices.length,
                requiredIndexesPresent,
                region.mapPresent(),
                region.locationsPresent(),
                region.terrainWalk().complete(),
                region.locationsSmallSmartWalk().complete(),
                region.locationsIncrSmallSmartWalk().complete());
        }
        finally
        {
            library.close();
        }
    }

    public record Options(Path cacheDirectory, int regionId, int samplesPerArchive, int traceRows)
    {
        public Options
        {
            Objects.requireNonNull(cacheDirectory, "cacheDirectory");
            if (regionId < 0 || regionId > 65535)
            {
                throw new IllegalArgumentException("regionId must be between 0 and 65535: " + regionId);
            }
            if (samplesPerArchive < 0)
            {
                throw new IllegalArgumentException("samplesPerArchive must be >= 0: " + samplesPerArchive);
            }
            if (traceRows < 0)
            {
                throw new IllegalArgumentException("traceRows must be >= 0: " + traceRows);
            }
        }

        public static Options parse(String[] args)
        {
            Path cacheDirectory = DEFAULT_HDOS_CACHE;
            int regionId = DEFAULT_REGION_ID;
            int samples = 2;
            int traceRows = 12;
            boolean cacheSpecified = false;

            for (int i = 0; i < args.length; i++)
            {
                String arg = args[i];
                if (arg.startsWith("--cache="))
                {
                    cacheDirectory = Path.of(arg.substring("--cache=".length()));
                    cacheSpecified = true;
                    continue;
                }
                if (arg.startsWith("--region="))
                {
                    regionId = Integer.parseInt(arg.substring("--region=".length()));
                    continue;
                }
                if (arg.startsWith("--samples="))
                {
                    samples = Integer.parseInt(arg.substring("--samples=".length()));
                    continue;
                }
                if (arg.startsWith("--trace-bytes="))
                {
                    traceRows = Integer.parseInt(arg.substring("--trace-bytes=".length()));
                    continue;
                }
                if (arg.startsWith("--trace-rows="))
                {
                    traceRows = Integer.parseInt(arg.substring("--trace-rows=".length()));
                    continue;
                }

                switch (arg)
                {
                    case "--cache" -> {
                        cacheDirectory = Path.of(requireValue(args, ++i, arg));
                        cacheSpecified = true;
                    }
                    case "--region" -> regionId = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--samples" -> samples = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--trace-bytes", "--trace-rows" -> traceRows = Integer.parseInt(requireValue(args, ++i, arg));
                    default -> {
                        if (arg.startsWith("--"))
                        {
                            throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                        if (cacheSpecified)
                        {
                            throw new IllegalArgumentException("Unexpected positional argument: " + arg);
                        }
                        cacheDirectory = Path.of(arg);
                        cacheSpecified = true;
                    }
                }
            }

            return new Options(cacheDirectory, regionId, samples, traceRows);
        }
    }

    public record Result(
        int validIndexCount,
        boolean requiredIndexesPresent,
        boolean regionMapPresent,
        boolean regionLocationsPresent,
        boolean terrainWalkComplete,
        boolean smallSmartLocationWalkComplete,
        boolean incrSmallSmartLocationWalkComplete)
    {
        public boolean loadableForDiagnostics()
        {
            return validIndexCount > 0
                && requiredIndexesPresent
                && regionMapPresent
                && regionLocationsPresent;
        }
    }

    private static boolean printRequiredIndexes(CacheLibrary library, PrintStream out)
    {
        out.println();
        out.println("viewer index availability:");
        boolean allRequiredPresent = true;
        for (IndexRequirement requirement : VIEWER_INDEXES)
        {
            Index index = library.index(requirement.id());
            boolean present = index != null;
            if (!present && requirement.required())
            {
                allRequiredPresent = false;
            }

            if (present)
            {
                out.printf(Locale.ROOT,
                    "  idx%-2d %-11s required=%-5s archives=%-6d revision=%-7d version=%d%n",
                    requirement.id(),
                    requirement.label(),
                    requirement.required(),
                    index.archiveIds().length,
                    index.getRevision(),
                    index.getVersion());
            }
            else
            {
                out.printf(Locale.ROOT,
                    "  idx%-2d %-11s required=%-5s missing%n",
                    requirement.id(),
                    requirement.label(),
                    requirement.required());
            }
        }
        return allRequiredPresent;
    }

    private static void printIndexContainerScan(CacheLibrary library, PrintStream out)
    {
        out.println();
        out.println("index container scan:");
        List<Index> indices = new ArrayList<>(Arrays.asList(library.validIndices()));
        indices.sort(Comparator.comparingInt(Index::getId));
        for (Index index : indices)
        {
            int[] archiveIds = sorted(index.archiveIds());
            String firstArchive = archiveIds.length == 0 ? "none" : describeFirstArchive(index, archiveIds[0]);
            out.printf(Locale.ROOT,
                "  idx%-2d archives=%-6d revision=%-7d version=%d tableCompression=%s %s%n",
                index.getId(),
                archiveIds.length,
                index.getRevision(),
                index.getVersion(),
                index.getCompressionType(),
                firstArchive);
        }
    }

    private static String describeFirstArchive(Index index, int archiveId)
    {
        ArchiveSector sector = index.readArchiveSector(archiveId);
        StringBuilder builder = new StringBuilder();
        builder.append("firstArchive=").append(archiveId);
        builder.append(" sector=").append(containerSummary(sector == null ? null : sector.getData()));

        try
        {
            Archive archive = index.archive(archiveId);
            if (archive == null)
            {
                builder.append(" decoded=missing");
                return builder.toString();
            }

            com.displee.cache.index.archive.file.File firstFile = archive.first();
            builder.append(" decodedFiles=").append(archive.files().length);
            builder.append(" archiveCompression=").append(archive.getCompressionType());
            builder.append(" firstFile=");
            builder.append(firstFile == null ? "none" : payloadSummary(firstFile.getData()));
        }
        catch (Throwable ex)
        {
            builder.append(" decodedError=").append(rootMessage(ex));
        }

        return builder.toString();
    }

    private static void printConfigArchiveDiagnostics(CacheLibrary library, PrintStream out, Options options)
    {
        out.println();
        out.println("config archive diagnostics:");
        Index configs = library.index(CONFIG_INDEX);
        if (configs == null)
        {
            out.println("  idx2 configs missing");
            return;
        }

        int[] archiveIds = sorted(configs.archiveIds());
        out.println("  archiveIds=" + Arrays.toString(archiveIds));
        for (int archiveId : archiveIds)
        {
            ArchiveSector sector = configs.readArchiveSector(archiveId);
            String readError = null;
            Archive archive = null;
            try
            {
                archive = configs.archive(archiveId);
            }
            catch (Throwable ex)
            {
                readError = rootMessage(ex);
            }

            out.printf(Locale.ROOT,
                "  archive %-5d%-15s sector=%s%n",
                archiveId,
                archiveHint(archiveId),
                containerSummary(sector == null ? null : sector.getData()));

            if (readError != null)
            {
                out.println("    decodedError=" + readError);
                continue;
            }
            if (archive == null)
            {
                out.println("    decoded=missing");
                continue;
            }

            List<ArchiveFileData> files = archiveFiles(archive);
            out.printf(Locale.ROOT,
                "    decoded: files=%d revision=%d compression=%s compressedLength=%d decompressedLength=%d%n",
                files.size(),
                archive.getRevision(),
                archive.getCompressionType(),
                archive.getCompressedLength(),
                archive.getDecompressedLength());
            if (files.isEmpty())
            {
                continue;
            }

            LengthStats stats = LengthStats.from(files);
            ZeroStats zeroStats = ZeroStats.from(files);
            out.println("    lengths: " + stats);
            out.println("    zeroes: " + zeroStats);
            out.println("    first opcode candidates: " + firstByteHistogram(files).topHex(10));
            out.println("    all byte histogram: " + allByteHistogram(files).topHex(12));
            out.println("    strings: " + sampleStrings(files, 5));
            printConfigSamples(out, files, options.samplesPerArchive(), options.traceRows());
        }
    }

    private static RegionDiagnostics printRegionDiagnostics(CacheLibrary library, PrintStream out, int regionId)
    {
        int regionX = regionX(regionId);
        int regionY = regionY(regionId);
        String mapName = "m" + regionX + "_" + regionY;
        String locationName = "l" + regionX + "_" + regionY;

        out.println();
        out.println("region archive diagnostics:");
        byte[] terrain = library.data(MAP_INDEX, mapName, ZERO_XTEA);
        byte[] locations = library.data(MAP_INDEX, locationName, ZERO_XTEA);
        out.println("  " + mapName + ": " + payloadSummary(terrain));
        TerrainWalk terrainWalk = terrain == null ? TerrainWalk.missing() : walkTerrain(terrain);
        out.println("    classic terrain walk: " + terrainWalk.format());

        out.println("  " + locationName + ": " + payloadSummary(locations));
        LocationWalk smallSmartWalk = locations == null
            ? LocationWalk.missing()
            : walkLocations(locations, LocationIdReader.UNSIGNED_SHORT_SMART);
        LocationWalk incrSmallSmartWalk = locations == null
            ? LocationWalk.missing()
            : walkLocations(locations, LocationIdReader.INCR_SMALL_SMART);
        out.println("    location walk ushort-smart object deltas: " + smallSmartWalk.format());
        out.println("    location walk incr-small-smart object deltas: " + incrSmallSmartWalk.format());

        return new RegionDiagnostics(
            terrain != null,
            locations != null,
            terrainWalk,
            smallSmartWalk,
            incrSmallSmartWalk);
    }

    private static void printMapsCacheSidecar(Path cacheDirectory, PrintStream out, int regionId) throws IOException
    {
        Path sidecar = mapsCacheSidecar(cacheDirectory);
        int regionX = regionX(regionId);
        int regionY = regionY(regionId);
        String token = regionX + "_" + regionY;

        out.println();
        out.println("HDOS maps-cache sidecar:");
        out.println("  directory=" + sidecar);
        if (!Files.isDirectory(sidecar))
        {
            out.println("  missing");
            return;
        }

        long regularFileCount;
        try (Stream<Path> files = Files.list(sidecar))
        {
            regularFileCount = files.filter(Files::isRegularFile).count();
        }

        List<Path> matches = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sidecar, "*" + token + "*"))
        {
            for (Path path : stream)
            {
                if (Files.isRegularFile(path))
                {
                    matches.add(path);
                }
            }
        }
        matches.sort(Comparator.comparing(path -> path.getFileName().toString()));

        out.printf(Locale.ROOT, "  files=%d regionMatches=%d%n", regularFileCount, matches.size());
        for (Path path : matches)
        {
            byte[] data = Files.readAllBytes(path);
            out.printf(Locale.ROOT, "  %s: %s%n", path.getFileName(), payloadSummary(data));
            out.println("    raw terrain walk: " + walkTerrain(data).format());
            out.println("    raw location walk ushort-smart object deltas: "
                + walkLocations(data, LocationIdReader.UNSIGNED_SHORT_SMART).format());
            out.println("    raw location walk incr-small-smart object deltas: "
                + walkLocations(data, LocationIdReader.INCR_SMALL_SMART).format());
        }
    }

    private static void printConfigSamples(
        PrintStream out,
        List<ArchiveFileData> files,
        int samplesPerArchive,
        int traceRows)
    {
        if (samplesPerArchive == 0 || traceRows == 0)
        {
            return;
        }

        int printed = 0;
        for (ArchiveFileData file : files)
        {
            if (file.data().length == 0)
            {
                continue;
            }

            out.printf(Locale.ROOT,
                "    sample file=%d len=%d endsZero=%s prefix=%s%n",
                file.fileId(),
                file.data().length,
                file.data()[file.data().length - 1] == 0,
                hex(file.data(), 0, Math.min(24, file.data().length)));

            int rows = Math.min(traceRows, file.data().length);
            for (int offset = 0; offset < rows; offset++)
            {
                out.println("      " + candidateRow(file.data(), offset));
            }

            printed++;
            if (printed >= samplesPerArchive)
            {
                return;
            }
        }
    }

    private static TerrainWalk walkTerrain(byte[] data)
    {
        ByteCursor cursor = new ByteCursor(data);
        IntCounter opcodes = new IntCounter();
        int tiles = 0;
        int heightOverrides = 0;
        int overlayPayloads = 0;
        int tileSettings = 0;
        int underlays = 0;

        try
        {
            for (int plane = 0; plane < 4; plane++)
            {
                for (int x = 0; x < 64; x++)
                {
                    for (int y = 0; y < 64; y++)
                    {
                        while (true)
                        {
                            int opcode = cursor.readUnsignedByte();
                            opcodes.add(opcode);
                            if (opcode == 0)
                            {
                                break;
                            }
                            if (opcode == 1)
                            {
                                cursor.readUnsignedByte();
                                heightOverrides++;
                                break;
                            }
                            if (opcode <= 49)
                            {
                                cursor.readUnsignedByte();
                                overlayPayloads++;
                            }
                            else if (opcode <= 81)
                            {
                                tileSettings++;
                            }
                            else
                            {
                                underlays++;
                            }
                        }
                        tiles++;
                    }
                }
            }

            return new TerrainWalk(
                true,
                cursor.offset(),
                cursor.remaining(),
                tiles,
                heightOverrides,
                overlayPayloads,
                tileSettings,
                underlays,
                opcodes.topHex(12),
                null);
        }
        catch (ReadPastEndException ex)
        {
            return new TerrainWalk(
                false,
                cursor.offset(),
                cursor.remaining(),
                tiles,
                heightOverrides,
                overlayPayloads,
                tileSettings,
                underlays,
                opcodes.topHex(12),
                ex.getMessage());
        }
    }

    private static LocationWalk walkLocations(byte[] data, LocationIdReader idReader)
    {
        ByteCursor cursor = new ByteCursor(data);
        IntCounter objectTypes = new IntCounter();
        int objectId = -1;
        int objectCount = 0;
        int placementCount = 0;
        int minObjectId = Integer.MAX_VALUE;
        int maxObjectId = Integer.MIN_VALUE;

        try
        {
            while (true)
            {
                int idOffset = idReader.read(cursor);
                if (idOffset == 0)
                {
                    break;
                }

                objectId += idOffset;
                objectCount++;
                minObjectId = Math.min(minObjectId, objectId);
                maxObjectId = Math.max(maxObjectId, objectId);

                int position = 0;
                while (true)
                {
                    int positionOffset = cursor.readUnsignedShortSmart();
                    if (positionOffset == 0)
                    {
                        break;
                    }
                    position += positionOffset - 1;
                    int attributes = cursor.readUnsignedByte();
                    objectTypes.add(attributes >> 2);
                    placementCount++;
                }
            }

            return new LocationWalk(
                true,
                cursor.offset(),
                cursor.remaining(),
                objectCount,
                placementCount,
                objectCount == 0 ? -1 : minObjectId,
                objectCount == 0 ? -1 : maxObjectId,
                objectTypes.top(12),
                null);
        }
        catch (ReadPastEndException ex)
        {
            return new LocationWalk(
                false,
                cursor.offset(),
                cursor.remaining(),
                objectCount,
                placementCount,
                objectCount == 0 ? -1 : minObjectId,
                objectCount == 0 ? -1 : maxObjectId,
                objectTypes.top(12),
                ex.getMessage());
        }
    }

    private static List<ArchiveFileData> archiveFiles(Archive archive)
    {
        List<ArchiveFileData> files = new ArrayList<>();
        for (com.displee.cache.index.archive.file.File file : archive.files())
        {
            byte[] data = file.getData();
            if (data != null)
            {
                files.add(new ArchiveFileData(file.getId(), data));
            }
        }
        files.sort(Comparator.comparingInt(ArchiveFileData::fileId));
        return files;
    }

    private static IntCounter firstByteHistogram(List<ArchiveFileData> files)
    {
        IntCounter counter = new IntCounter();
        for (ArchiveFileData file : files)
        {
            if (file.data().length > 0)
            {
                counter.add(file.data()[0] & 0xFF);
            }
        }
        return counter;
    }

    private static IntCounter allByteHistogram(List<ArchiveFileData> files)
    {
        IntCounter counter = new IntCounter();
        for (ArchiveFileData file : files)
        {
            for (byte value : file.data())
            {
                counter.add(value & 0xFF);
            }
        }
        return counter;
    }

    private static String sampleStrings(List<ArchiveFileData> files, int limit)
    {
        List<String> strings = new ArrayList<>();
        for (ArchiveFileData file : files)
        {
            extractStrings(file.data(), strings, limit);
            if (strings.size() >= limit)
            {
                break;
            }
        }
        if (strings.isEmpty())
        {
            return "<none>";
        }
        return strings.stream()
            .map(value -> '"' + value + '"')
            .collect(Collectors.joining(" | "));
    }

    private static void extractStrings(byte[] data, List<String> strings, int limit)
    {
        StringBuilder current = new StringBuilder();
        for (byte datum : data)
        {
            int value = datum & 0xFF;
            if (value >= 32 && value <= 126)
            {
                current.append((char) value);
            }
            else
            {
                addStringCandidate(strings, current, limit);
                if (strings.size() >= limit)
                {
                    return;
                }
            }
        }
        addStringCandidate(strings, current, limit);
    }

    private static void addStringCandidate(List<String> strings, StringBuilder current, int limit)
    {
        if (current.length() >= 4 && strings.size() < limit)
        {
            String value = current.toString();
            if (value.length() > 80)
            {
                value = value.substring(0, 77) + "...";
            }
            strings.add(value);
        }
        current.setLength(0);
    }

    private static String candidateRow(byte[] data, int offset)
    {
        int u8 = data[offset] & 0xFF;
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.ROOT, "@%04d op=0x%02X next=%s", offset, u8,
            hex(data, offset + 1, Math.min(8, data.length - offset - 1))));
        builder.append(" | u8=").append(u8);
        builder.append(" i8=").append((byte) data[offset]);
        builder.append(" u16=").append(has(data, offset, 2) ? readUnsignedShort(data, offset) : "-");
        builder.append(" i16=").append(has(data, offset, 2) ? (short) readUnsignedShort(data, offset) : "-");
        builder.append(" u24=").append(has(data, offset, 3) ? readUnsignedMedium(data, offset) : "-");
        builder.append(" u32=").append(has(data, offset, 4) ? readUnsignedInt(data, offset) : "-");
        builder.append(" i32=").append(has(data, offset, 4) ? readInt(data, offset) : "-");
        builder.append(" u64=").append(has(data, offset, 8) ? readUnsignedLong(data, offset) : "-");
        builder.append(" smart=").append(formatSmart(peekUnsignedShortSmart(data, offset)));
        builder.append(" incrSmart=").append(formatSmart(peekIncrSmallSmart(data, offset)));
        builder.append(" cstr=").append(cStringCandidate(data, offset));
        return builder.toString();
    }

    private static String formatSmart(SmartValue value)
    {
        if (value == null)
        {
            return "-";
        }
        return value.value() + "/" + value.bytes() + "b";
    }

    private static SmartValue peekUnsignedShortSmart(byte[] data, int offset)
    {
        if (!has(data, offset, 1))
        {
            return null;
        }

        int peek = data[offset] & 0xFF;
        if (peek < 128)
        {
            return new SmartValue(peek, 1);
        }
        if (!has(data, offset, 2))
        {
            return null;
        }
        return new SmartValue(readUnsignedShort(data, offset) - 32768, 2);
    }

    private static SmartValue peekIncrSmallSmart(byte[] data, int offset)
    {
        int total = 0;
        int bytes = 0;
        int currentOffset = offset;
        while (true)
        {
            SmartValue value = peekUnsignedShortSmart(data, currentOffset);
            if (value == null)
            {
                return null;
            }
            total += value.value();
            bytes += value.bytes();
            currentOffset += value.bytes();
            if (value.value() != 32767)
            {
                return new SmartValue(total, bytes);
            }
            if (bytes > 16)
            {
                return new SmartValue(total, bytes);
            }
        }
    }

    private static String cStringCandidate(byte[] data, int offset)
    {
        if (!has(data, offset, 1) || !isPrintableAscii(data[offset] & 0xFF))
        {
            return "-";
        }

        int end = offset;
        int max = Math.min(data.length, offset + 40);
        while (end < max && data[end] != 0)
        {
            int value = data[end] & 0xFF;
            if (!isPrintableAscii(value))
            {
                return "-";
            }
            end++;
        }

        if (end >= data.length || data[end] != 0 || end - offset < 3)
        {
            return "-";
        }

        return '"' + new String(data, offset, end - offset, StandardCharsets.US_ASCII) + '"';
    }

    private static boolean isPrintableAscii(int value)
    {
        return value >= 32 && value <= 126;
    }

    private static String containerSummary(byte[] raw)
    {
        if (raw == null)
        {
            return "missing";
        }
        if (raw.length < 5)
        {
            return "rawLen=" + raw.length + " malformedPrefix=" + hex(raw, 0, raw.length);
        }

        int type = raw[0] & 0xFF;
        long compressedSize = readUnsignedInt(raw, 1);
        int payloadOffset = type == 0 ? 5 : 9;
        String decompressedSize = type == 0 || raw.length < 9 ? "-" : Long.toString(readUnsignedInt(raw, 5));
        long trailing = raw.length - (long) payloadOffset - compressedSize;

        return String.format(Locale.ROOT,
            "rawLen=%d type=%s compressed=%d decompressed=%s trailing=%d prefix=%s payloadPrefix=%s",
            raw.length,
            compressionTypeName(type),
            compressedSize,
            decompressedSize,
            trailing,
            hex(raw, 0, Math.min(16, raw.length)),
            hex(raw, Math.min(payloadOffset, raw.length), Math.min(16, Math.max(0, raw.length - payloadOffset))));
    }

    private static String payloadSummary(byte[] data)
    {
        if (data == null)
        {
            return "missing";
        }
        return "len=" + data.length + " prefix=" + hex(data, 0, Math.min(24, data.length));
    }

    private static String compressionTypeName(int type)
    {
        return switch (type)
        {
            case 0 -> "NONE";
            case 1 -> "BZIP2";
            case 2 -> "GZIP";
            case 3 -> "LZMA";
            default -> "UNKNOWN(" + type + ")";
        };
    }

    private static Path mapsCacheSidecar(Path cacheDirectory)
    {
        Path current = cacheDirectory.toAbsolutePath().normalize();
        for (int i = 0; i < 3 && current.getParent() != null; i++)
        {
            current = current.getParent();
        }
        return current.resolve("maps-cache");
    }

    private static String archiveHint(int archiveId)
    {
        String hint = CONFIG_ARCHIVE_HINTS.get(archiveId);
        if (hint == null)
        {
            return "";
        }
        return " (" + hint + ")";
    }

    private static String requireValue(String[] args, int index, String option)
    {
        if (index >= args.length)
        {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static boolean hasHelpFlag(String[] args)
    {
        for (String arg : args)
        {
            if ("--help".equals(arg) || "-h".equals(arg))
            {
                return true;
            }
        }
        return false;
    }

    private static void printUsage(PrintStream out)
    {
        out.println("Usage: gradle probeHdosCache --args=\"[--cache path] [--region id] [--samples n] [--trace-bytes n]\"");
    }

    private static int regionX(int regionId)
    {
        return regionId >> 8;
    }

    private static int regionY(int regionId)
    {
        return regionId & 0xFF;
    }

    private static int[] sorted(int[] values)
    {
        int[] copy = values.clone();
        Arrays.sort(copy);
        return copy;
    }

    private static boolean has(byte[] data, int offset, int length)
    {
        return offset >= 0 && length >= 0 && offset + length <= data.length;
    }

    private static int readUnsignedShort(byte[] data, int offset)
    {
        return (data[offset] & 0xFF) << 8
            | data[offset + 1] & 0xFF;
    }

    private static int readUnsignedMedium(byte[] data, int offset)
    {
        return (data[offset] & 0xFF) << 16
            | (data[offset + 1] & 0xFF) << 8
            | data[offset + 2] & 0xFF;
    }

    private static int readInt(byte[] data, int offset)
    {
        return (data[offset] & 0xFF) << 24
            | (data[offset + 1] & 0xFF) << 16
            | (data[offset + 2] & 0xFF) << 8
            | data[offset + 3] & 0xFF;
    }

    private static long readUnsignedInt(byte[] data, int offset)
    {
        return Integer.toUnsignedLong(readInt(data, offset));
    }

    private static BigInteger readUnsignedLong(byte[] data, int offset)
    {
        return new BigInteger(1, Arrays.copyOfRange(data, offset, offset + 8));
    }

    private static String hex(byte[] data, int offset, int length)
    {
        if (data == null || length <= 0 || offset >= data.length)
        {
            return "-";
        }

        int end = Math.min(data.length, offset + length);
        StringBuilder builder = new StringBuilder((end - offset) * 3);
        for (int i = offset; i < end; i++)
        {
            if (i > offset)
            {
                builder.append(' ');
            }
            builder.append(String.format(Locale.ROOT, "%02x", data[i] & 0xFF));
        }
        return builder.toString();
    }

    private static String rootMessage(Throwable throwable)
    {
        Throwable current = throwable;
        while (current.getCause() != null)
        {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank())
        {
            return current.getClass().getSimpleName();
        }
        return current.getClass().getSimpleName() + ": " + message;
    }

    private enum LocationIdReader
    {
        UNSIGNED_SHORT_SMART
        {
            @Override
            int read(ByteCursor cursor)
            {
                return cursor.readUnsignedShortSmart();
            }
        },
        INCR_SMALL_SMART
        {
            @Override
            int read(ByteCursor cursor)
            {
                return cursor.readIncrSmallSmart();
            }
        };

        abstract int read(ByteCursor cursor);
    }

    private record IndexRequirement(int id, String label, boolean required)
    {
    }

    private record ArchiveFileData(int fileId, byte[] data)
    {
    }

    private record SmartValue(int value, int bytes)
    {
    }

    private record RegionDiagnostics(
        boolean mapPresent,
        boolean locationsPresent,
        TerrainWalk terrainWalk,
        LocationWalk locationsSmallSmartWalk,
        LocationWalk locationsIncrSmallSmartWalk)
    {
    }

    private record TerrainWalk(
        boolean complete,
        int offset,
        int remaining,
        int tiles,
        int heightOverrides,
        int overlayPayloads,
        int tileSettings,
        int underlays,
        String topOpcodes,
        String error)
    {
        static TerrainWalk missing()
        {
            return new TerrainWalk(false, 0, 0, 0, 0, 0, 0, 0, "<none>", "missing");
        }

        String format()
        {
            return String.format(Locale.ROOT,
                "complete=%s exact=%s offset=%d remaining=%d tiles=%d heights=%d overlays=%d settings=%d underlays=%d topOpcodes=%s%s",
                complete,
                complete && remaining == 0,
                offset,
                remaining,
                tiles,
                heightOverrides,
                overlayPayloads,
                tileSettings,
                underlays,
                topOpcodes,
                error == null ? "" : " error=" + error);
        }
    }

    private record LocationWalk(
        boolean complete,
        int offset,
        int remaining,
        int objects,
        int placements,
        int minObjectId,
        int maxObjectId,
        String topTypes,
        String error)
    {
        static LocationWalk missing()
        {
            return new LocationWalk(false, 0, 0, 0, 0, -1, -1, "<none>", "missing");
        }

        String format()
        {
            String range = objects == 0 ? "-" : minObjectId + ".." + maxObjectId;
            return String.format(Locale.ROOT,
                "complete=%s exact=%s offset=%d remaining=%d objects=%d placements=%d objectIdRange=%s topTypes=%s%s",
                complete,
                complete && remaining == 0,
                offset,
                remaining,
                objects,
                placements,
                range,
                topTypes,
                error == null ? "" : " error=" + error);
        }
    }

    private record LengthStats(
        int files,
        int nonEmpty,
        int min,
        int median,
        int p95,
        int max,
        double average)
    {
        static LengthStats from(List<ArchiveFileData> files)
        {
            int[] lengths = files.stream()
                .mapToInt(file -> file.data().length)
                .sorted()
                .toArray();
            int nonEmpty = 0;
            long total = 0;
            for (int length : lengths)
            {
                if (length > 0)
                {
                    nonEmpty++;
                }
                total += length;
            }

            if (lengths.length == 0)
            {
                return new LengthStats(0, 0, 0, 0, 0, 0, 0.0);
            }

            return new LengthStats(
                lengths.length,
                nonEmpty,
                lengths[0],
                percentile(lengths, 0.50),
                percentile(lengths, 0.95),
                lengths[lengths.length - 1],
                total / (double) lengths.length);
        }

        @Override
        public String toString()
        {
            return String.format(Locale.ROOT,
                "files=%d nonEmpty=%d min=%d median=%d p95=%d max=%d avg=%s",
                files,
                nonEmpty,
                min,
                median,
                p95,
                max,
                ONE_DECIMAL.format(average));
        }
    }

    private record ZeroStats(int terminalZeroes, int containingZeroes, int nonEmptyFiles)
    {
        static ZeroStats from(List<ArchiveFileData> files)
        {
            int terminalZeroes = 0;
            int containingZeroes = 0;
            int nonEmpty = 0;
            for (ArchiveFileData file : files)
            {
                byte[] data = file.data();
                if (data.length == 0)
                {
                    continue;
                }
                nonEmpty++;
                if (data[data.length - 1] == 0)
                {
                    terminalZeroes++;
                }
                for (byte datum : data)
                {
                    if (datum == 0)
                    {
                        containingZeroes++;
                        break;
                    }
                }
            }
            return new ZeroStats(terminalZeroes, containingZeroes, nonEmpty);
        }

        @Override
        public String toString()
        {
            return "terminal=" + ratio(terminalZeroes, nonEmptyFiles)
                + " contains=" + ratio(containingZeroes, nonEmptyFiles);
        }
    }

    private static int percentile(int[] sortedValues, double percentile)
    {
        int index = (int) Math.ceil(percentile * sortedValues.length) - 1;
        index = Math.max(0, Math.min(sortedValues.length - 1, index));
        return sortedValues[index];
    }

    private static String ratio(int numerator, int denominator)
    {
        if (denominator == 0)
        {
            return "0/0";
        }
        return numerator + "/" + denominator
            + " (" + ONE_DECIMAL.format(numerator * 100.0 / denominator) + "%)";
    }

    private static final class IntCounter
    {
        private final Map<Integer, Integer> counts = new HashMap<>();

        void add(int value)
        {
            counts.merge(value, 1, Integer::sum);
        }

        String top(int limit)
        {
            return top(limit, false);
        }

        String topHex(int limit)
        {
            return top(limit, true);
        }

        private String top(int limit, boolean hex)
        {
            if (counts.isEmpty())
            {
                return "<none>";
            }

            return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int byCount = Integer.compare(right.getValue(), left.getValue());
                    if (byCount != 0)
                    {
                        return byCount;
                    }
                    return Integer.compare(left.getKey(), right.getKey());
                })
                .limit(limit)
                .map(entry -> (hex
                    ? String.format(Locale.ROOT, "0x%02X", entry.getKey())
                    : Integer.toString(entry.getKey()))
                    + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        }
    }

    private static final class ByteCursor
    {
        private final byte[] data;
        private int offset;

        ByteCursor(byte[] data)
        {
            this.data = data;
        }

        int offset()
        {
            return offset;
        }

        int remaining()
        {
            return Math.max(0, data.length - offset);
        }

        int readUnsignedByte()
        {
            require(1);
            return data[offset++] & 0xFF;
        }

        int readUnsignedShort()
        {
            require(2);
            int value = HdosCacheDiagnostics.readUnsignedShort(data, offset);
            offset += 2;
            return value;
        }

        int readUnsignedShortSmart()
        {
            require(1);
            if ((data[offset] & 0xFF) < 128)
            {
                return readUnsignedByte();
            }
            return readUnsignedShort() - 32768;
        }

        int readIncrSmallSmart()
        {
            int total = 0;
            while (true)
            {
                int value = readUnsignedShortSmart();
                if (value != 32767)
                {
                    return total + value;
                }
                total += 32767;
            }
        }

        private void require(int length)
        {
            if (offset + length > data.length)
            {
                throw new ReadPastEndException(
                    "need=" + length + " offset=" + offset + " remaining=" + remaining());
            }
        }
    }

    private static final class ReadPastEndException extends RuntimeException
    {
        ReadPastEndException(String message)
        {
            super(message);
        }
    }
}
