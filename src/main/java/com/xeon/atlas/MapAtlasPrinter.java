/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.xeon.atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.runelite.cache.AreaManager;
import net.runelite.cache.IndexType;
import net.runelite.cache.MapImageDumper;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.WorldMapManager;
import net.runelite.cache.definitions.AreaDefinition;
import net.runelite.cache.definitions.LocationsDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.WorldMapElementDefinition;
import net.runelite.cache.definitions.loaders.LocationsLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.BigBufferedImage;
import net.runelite.cache.util.KeyProvider;
import net.runelite.cache.util.XteaKeyManager;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public final class MapAtlasPrinter
{
	private static final byte[] MAGIC = new byte[]{'A', 'T', 'L', 'S', 'v', '3', 0, 0};
	private static final String XTEA_RESOURCE = "/com/xeon/xteas.json";
	public static final String DEFAULT_LODS = "1,2,4";
	private static final String LAYER_BASE = "base";
	private static final String LAYER_ICONS = "icons";
	private static final String LAYER_LABELS = "labels";
	private static final String[] LAYER_KINDS = new String[]{LAYER_BASE, LAYER_ICONS, LAYER_LABELS};
	public static final int TOTAL_STEPS = 16;
	private static final int VERSION = 3;
	public static final int DEFAULT_TILE_PX = 256;
	private static final int MAP_SCALE = 4;
	private static final int ENTRY_SIZE = 36;
	private static final int HEADER_SIZE_WITHOUT_LODS = 68;
	private static final ProgressListener NO_PROGRESS = (message, step, totalSteps) ->
	{
	};

	private MapAtlasPrinter()
	{
	}

	public record PrintRequest(
		Path cacheDirectory,
		Path xteaPath,
		Path output,
		Path temporaryDirectory,
		int tilePx,
		int[] lods,
		boolean transparentEmptyRegions
	)
	{
		public PrintRequest
		{
			if (cacheDirectory == null)
			{
				throw new IllegalArgumentException("cacheDirectory must not be null");
			}
			if (output == null)
			{
				throw new IllegalArgumentException("output must not be null");
			}
			if (tilePx <= 0)
			{
				throw new IllegalArgumentException("tilePx must be positive");
			}
			lods = lods == null ? defaultLods() : Arrays.copyOf(lods, lods.length);
			if (lods.length == 0)
			{
				throw new IllegalArgumentException("At least one LOD is required");
			}
			for (int lod : lods)
			{
				if (lod != 1 && lod != 2 && lod != 4)
				{
					throw new IllegalArgumentException("LOD values must be a subset of 1,2,4");
				}
			}
		}

		@Override
		public int[] lods()
		{
			return Arrays.copyOf(lods, lods.length);
		}
	}

	@FunctionalInterface
	public interface ProgressListener
	{
		void step(String message, int step, int totalSteps);
	}

	public static void main(String[] args) throws IOException
	{
		Options options = new Options();
		options.addOption(Option.builder().longOpt("cachedir").hasArg().desc("RuneLite OSRS cache directory").build());
		options.addOption(Option.builder().longOpt("xteapath").hasArg().desc("Path to xtea json; defaults to bundled zero-key resource").build());
		options.addOption(Option.builder().longOpt("output").hasArg().desc("Output .atlas path").build());
		options.addOption(Option.builder().longOpt("outputdir").hasArg().desc("Output directory for " + AtlasStorage.ATLAS_FILE_NAME).build());
		options.addOption(Option.builder().longOpt("tile").hasArg().desc("Atlas tile size, default 256").build());
		options.addOption(Option.builder().longOpt("lods").hasArg().desc("Comma-separated LOD subsamples, default 1,2,4").build());
		options.addOption(Option.builder()
			.longOpt("opaque-empty-regions")
			.desc("Keep empty cache regions black instead of transparent")
			.build());

		CommandLine cmd = parse(options, args);
		int tilePx = parseTilePx(cmd.getOptionValue("tile"));
		int[] lods = parseLods(cmd.getOptionValue("lods", DEFAULT_LODS));
		Path output = resolveOutput(cmd);
		Path xteaPath = cmd.hasOption("xteapath") ? Path.of(cmd.getOptionValue("xteapath")) : null;
		Path cacheDirectory = resolveCacheDirectory(cmd);
		PrintRequest request = new PrintRequest(
			cacheDirectory,
			xteaPath,
			output,
			output.toAbsolutePath().getParent(),
			tilePx,
			lods,
			!cmd.hasOption("opaque-empty-regions")
		);
		print(request, (message, step, totalSteps) ->
			System.out.println("[" + step + "/" + totalSteps + "] " + message));
	}

	public static int[] defaultLods()
	{
		return parseLods(DEFAULT_LODS);
	}

	public static void print(PrintRequest request, ProgressListener progressListener) throws IOException
	{
		if (request == null)
		{
			throw new IllegalArgumentException("request must not be null");
		}
		ProgressListener progress = progressListener == null ? NO_PROGRESS : progressListener;

		step(progress, "Loading map decryption keys", 1);
		KeyProvider keyProvider = loadKeyProvider(request.xteaPath());

		step(progress, "Opening OSRS cache", 2);
		File base = request.cacheDirectory().toFile();
		try (Store store = new Store(base))
		{
			store.load();

			step(progress, "Loading RuneLite map data", 3);
			RegionLoader regionLoader = new UnencryptedFallbackRegionLoader(store, keyProvider);
			MapImageDumper dumper = new MapImageDumper(store, regionLoader);
			dumper.load();

			packAtlas(
				dumper,
				regionLoader,
				request.output().toFile(),
				request.temporaryDirectory(),
				request.tilePx(),
				request.lods(),
				request.transparentEmptyRegions(),
				progress
			);
		}
	}

	private static void step(ProgressListener progress, String message, int step)
	{
		progress.step(message, step, TOTAL_STEPS);
	}

	private static KeyProvider loadKeyProvider(Path xteaPath) throws IOException
	{
		XteaKeyManager keyManager = new XteaKeyManager();
		byte[] bytes = readXteaBytes(xteaPath);
		String contents = new String(bytes, StandardCharsets.UTF_8).trim();
		if (contents.isEmpty() || "[]".equals(contents) || "[ ]".equals(contents))
		{
			return keyManager;
		}

		try (ByteArrayInputStream in = new ByteArrayInputStream(bytes))
		{
			keyManager.loadKeys(in);
		}
		return keyManager;
	}

	private static byte[] readXteaBytes(Path xteaPath) throws IOException
	{
		if (xteaPath != null)
		{
			if (!Files.isRegularFile(xteaPath))
			{
				throw new IOException("XTEA file not found: " + xteaPath);
			}
			return Files.readAllBytes(xteaPath);
		}

		try (InputStream in = MapAtlasPrinter.class.getResourceAsStream(XTEA_RESOURCE))
		{
			return in == null ? new byte[0] : in.readAllBytes();
		}
	}

	private static CommandLine parse(Options options, String[] args)
	{
		CommandLineParser parser = new DefaultParser();
		try
		{
			return parser.parse(options, args);
		}
		catch (ParseException ex)
		{
			System.err.println("Error parsing command line options: " + ex.getMessage());
			new HelpFormatter().printHelp(MapAtlasPrinter.class.getSimpleName(), options);
			System.exit(2);
			throw new IllegalStateException(ex);
		}
	}

	private static int parseTilePx(String value)
	{
		if (value == null)
		{
			return DEFAULT_TILE_PX;
		}

		try
		{
			int tilePx = Integer.parseInt(value);
			if (tilePx <= 0)
			{
				throw new NumberFormatException("tile must be positive");
			}
			return tilePx;
		}
		catch (NumberFormatException ex)
		{
			throw new IllegalArgumentException("Invalid --tile: " + value, ex);
		}
	}

	private static int[] parseLods(String value)
	{
		String[] parts = value.split(",");
		List<Integer> lods = new ArrayList<>();
		for (String part : parts)
		{
			String trimmed = part.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}

			int lod;
			try
			{
				lod = Integer.parseInt(trimmed);
			}
			catch (NumberFormatException ex)
			{
				throw new IllegalArgumentException("Invalid --lods value: " + trimmed, ex);
			}

			if (lod != 1 && lod != 2 && lod != 4)
			{
				throw new IllegalArgumentException("LOD values must be a subset of 1,2,4");
			}
			lods.add(lod);
		}

		if (lods.isEmpty())
		{
			throw new IllegalArgumentException("At least one LOD is required");
		}

		int[] out = new int[lods.size()];
		for (int i = 0; i < lods.size(); i++)
		{
			out[i] = lods.get(i);
		}
		return out;
	}

	private static Path resolveCacheDirectory(CommandLine cmd)
	{
		if (cmd.hasOption("cachedir"))
		{
			return Path.of(cmd.getOptionValue("cachedir"));
		}

		Path cacheDirectory = RuneLiteCacheLocator.locateCacheDirectory();
		if (cacheDirectory == null)
		{
			throw new IllegalArgumentException("Could not locate the RuneLite OSRS cache. "
				+ "Pass --cachedir pointing at the oldschool/LIVE folder.");
		}
		return cacheDirectory;
	}

	private static Path resolveOutput(CommandLine cmd) throws IOException
	{
		String output = cmd.getOptionValue("output");
		String outputDir = cmd.getOptionValue("outputdir");
		if (output != null && outputDir != null)
		{
			throw new IllegalArgumentException("Use only one of --output or --outputdir");
		}
		if (output != null)
		{
			return Path.of(output);
		}
		if (outputDir != null)
		{
			return Path.of(outputDir).resolve(AtlasStorage.ATLAS_FILE_NAME);
		}
		AtlasStorage.ensureDirectories();
		return AtlasStorage.installedAtlasPath();
	}

	private static void packAtlas(
		MapImageDumper dumper,
		RegionLoader regionLoader,
		File output,
		Path temporaryDirectory,
		int tilePx,
		int[] lods,
		boolean transparentEmptyRegions,
		ProgressListener progress
	) throws IOException
	{
		Path chunkRoot = temporaryDirectory == null ? null : temporaryDirectory.toAbsolutePath();
		if (chunkRoot != null)
		{
			Files.createDirectories(chunkRoot);
		}
		Path chunkDir = chunkRoot == null
			? Files.createTempDirectory("atlas-tiles-")
			: Files.createTempDirectory(chunkRoot, "atlas-tiles-");
		Map<String, TileChunk> chunks = new HashMap<>();
		AtlasLayout layout = null;
		DumperAccess dumperAccess = new DumperAccess(dumper);
		byte[] metadata = null;
		int progressStep = 4;

		try
		{
			for (int z = 0; z < Region.Z; z++)
			{
				final int plane = z;
				step(progress, "Rendering base map plane " + plane, progressStep++);
				BufferedImage image = renderBaseLayer(dumper, plane);
				try
				{
					if (layout == null)
					{
						layout = AtlasLayout.create(image, regionLoader, tilePx, lods, transparentEmptyRegions);
						metadata = buildMetadata(dumperAccess, regionLoader, layout, lods);
					}
					else
					{
						layout.requireSameSize(image);
					}

					packRenderedLayer(chunks, image, layout, chunkDir, tilePx, lods, plane, LAYER_BASE, false);
				}
				finally
				{
					BigBufferedImage.dispose(image);
					image.flush();
				}

				step(progress, "Rendering map icons plane " + plane, progressStep++);
				BufferedImage iconLayer = renderTransparentLayer(layout, layer -> dumperAccess.drawMapIcons(layer, plane));
				try
				{
					packRenderedLayer(chunks, iconLayer, layout, chunkDir, tilePx, lods, plane, LAYER_ICONS, true);
				}
				finally
				{
					BigBufferedImage.dispose(iconLayer);
					iconLayer.flush();
				}

				step(progress, "Rendering map labels plane " + plane, progressStep++);
				BufferedImage labelLayer = renderTransparentLayer(layout, layer -> dumperAccess.drawMapLabels(layer, plane));
				try
				{
					packRenderedLayer(chunks, labelLayer, layout, chunkDir, tilePx, lods, plane, LAYER_LABELS, true);
				}
				finally
				{
					BigBufferedImage.dispose(labelLayer);
					labelLayer.flush();
				}
			}

			if (layout == null || metadata == null)
			{
				throw new IOException("No map images were rendered");
			}

			step(progress, "Writing atlas file", TOTAL_STEPS);
			writeAtlasFile(output, layout, lods, chunks, metadata);
		}
		finally
		{
			deleteChunks(chunks);
			Files.deleteIfExists(chunkDir);
		}
	}

	private static BufferedImage renderBaseLayer(MapImageDumper dumper, int z)
	{
		dumper.setRenderIcons(false);
		dumper.setRenderLabels(false);
		return dumper.drawMap(z);
	}

	private static BufferedImage renderTransparentLayer(AtlasLayout layout, LayerRenderer renderer) throws IOException
	{
		BufferedImage image = BigBufferedImage.create(layout.width, layout.height, BufferedImage.TYPE_INT_ARGB);
		try
		{
			renderer.render(image);
			return image;
		}
		catch (IOException | RuntimeException ex)
		{
			BigBufferedImage.dispose(image);
			image.flush();
			throw ex;
		}
	}

	private static void packRenderedLayer(
		Map<String, TileChunk> chunks,
		BufferedImage image,
		AtlasLayout layout,
		Path chunkDir,
		int tilePx,
		int[] lods,
		int plane,
		String kind,
		boolean preserveSourceAlpha
	) throws IOException
	{
		int layerIndex = layerIndex(kind, plane);
		for (int lod : lods)
		{
			TileChunk chunk = writeLayerLodChunk(
				image,
				layout.mask,
				chunkDir,
				tilePx,
				lod,
				layerIndex,
				preserveSourceAlpha
			);
			chunks.put(chunkKey(lod, layerIndex), chunk);
		}
	}

	private static byte[] buildMetadata(
		DumperAccess dumperAccess,
		RegionLoader regionLoader,
		AtlasLayout layout,
		int[] lods
	)
	{
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("format", "ATLSv1");
		root.put("version", VERSION);
		root.put("generator", MapAtlasPrinter.class.getName());
		root.put("width", layout.width);
		root.put("height", layout.height);
		root.put("tilePx", layout.tilePx);
		root.put("lods", toList(lods));
		root.put("tilesXFull", layout.tilesXFull);
		root.put("tilesYFull", layout.tilesYFull);
		root.put("numLayers", layout.layers.size());
		root.put("tileEntryLayerField", "z");
		root.put("layers", layerMetadata(layout.layers));
		root.put("regions", regionMetadata(layout.mask));
		root.put("locationLabels", locationLabelMetadata(dumperAccess, layout));
		root.put("mapIcons", mapIconMetadata(dumperAccess, regionLoader, layout));

		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		return gson.toJson(root).getBytes(StandardCharsets.UTF_8);
	}

	private static List<Integer> toList(int[] values)
	{
		List<Integer> out = new ArrayList<>(values.length);
		for (int value : values)
		{
			out.add(value);
		}
		return out;
	}

	private static List<Map<String, Object>> layerMetadata(List<AtlasLayer> layers)
	{
		List<Map<String, Object>> out = new ArrayList<>(layers.size());
		for (AtlasLayer layer : layers)
		{
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("index", layer.index);
			entry.put("kind", layer.kind);
			entry.put("plane", layer.plane);
			out.add(entry);
		}
		return out;
	}

	private static Map<String, Object> regionMetadata(RegionMask mask)
	{
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("minRegionX", mask.minRegionX);
		out.put("minRegionY", mask.minRegionY);
		out.put("maxRegionX", mask.maxRegionX);
		out.put("maxRegionY", mask.maxRegionY);
		out.put("cols", mask.cols);
		out.put("rows", mask.rows);
		out.put("regionPixelSize", mask.regionPixelSize);
		out.put("mapScale", MAP_SCALE);
		return out;
	}

	private static List<Map<String, Object>> locationLabelMetadata(DumperAccess dumperAccess, AtlasLayout layout)
	{
		List<Map<String, Object>> out = new ArrayList<>();
		for (WorldMapElementDefinition element : dumperAccess.worldMapManager.getElements())
		{
			AreaDefinition area = dumperAccess.areas.getArea(element.getAreaDefinitionId());
			Position worldPosition = element.getWorldPosition();
			if (area == null || area.getName() == null || !isPlane(worldPosition.getZ()))
			{
				continue;
			}

			Map<String, Object> entry = baseAreaMetadata(area, worldPosition, layout, LAYER_LABELS, "worldMapLabel");
			int labelMapTileY = mapTileYForLabel(worldPosition.getY(), layout.mask);
			entry.put("mapTileY", labelMapTileY);
			entry.put("mapPixelY", labelMapTileY * MAP_SCALE);
			entry.put("membersOnly", element.isMembersOnly());
			out.add(entry);
		}
		return out;
	}

	private static List<Map<String, Object>> mapIconMetadata(
		DumperAccess dumperAccess,
		RegionLoader regionLoader,
		AtlasLayout layout
	)
	{
		List<Map<String, Object>> out = new ArrayList<>();
		for (Region region : regionLoader.getRegions())
		{
			for (Location location : region.getLocations())
			{
				Position position = location.getPosition();
				if (!isPlane(position.getZ()))
				{
					continue;
				}

				ObjectDefinition object = dumperAccess.objectManager.getObject(location.getId());
				if (object == null || object.getMapAreaId() == -1)
				{
					continue;
				}

				AreaDefinition area = dumperAccess.areas.getArea(object.getMapAreaId());
				if (area == null)
				{
					continue;
				}

				Map<String, Object> entry = baseAreaMetadata(area, position, layout, LAYER_ICONS, "objectMapIcon");
				entry.put("objectId", location.getId());
				entry.put("objectName", normalizeName(object.getName()));
				entry.put("objectType", location.getType());
				entry.put("objectOrientation", location.getOrientation());
				out.add(entry);
			}
		}

		for (WorldMapElementDefinition element : dumperAccess.worldMapManager.getElements())
		{
			AreaDefinition area = dumperAccess.areas.getArea(element.getAreaDefinitionId());
			Position worldPosition = element.getWorldPosition();
			if (area == null || area.getName() != null || !isPlane(worldPosition.getZ()))
			{
				continue;
			}

			Map<String, Object> entry = baseAreaMetadata(area, worldPosition, layout, LAYER_ICONS, "worldMapIcon");
			entry.put("membersOnly", element.isMembersOnly());
			out.add(entry);
		}

		return out;
	}

	private static Map<String, Object> baseAreaMetadata(
		AreaDefinition area,
		Position position,
		AtlasLayout layout,
		String layerKind,
		String source
	)
	{
		int mapTileX = mapTileX(position.getX(), layout.mask);
		int mapTileY = mapTileYForIcon(position.getY(), layout.mask);

		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("source", source);
		entry.put("areaId", area.getId());
		entry.put("name", normalizeName(area.getName()));
		entry.put("rawName", area.getName());
		entry.put("spriteId", area.spriteId);
		entry.put("category", area.category);
		entry.put("textColor", area.getTextColor());
		entry.put("textScale", area.getTextScale());
		entry.put("worldX", position.getX());
		entry.put("worldY", position.getY());
		entry.put("plane", position.getZ());
		entry.put("mapTileX", mapTileX);
		entry.put("mapTileY", mapTileY);
		entry.put("mapPixelX", mapTileX * MAP_SCALE);
		entry.put("mapPixelY", mapTileY * MAP_SCALE);
		entry.put("layer", layerIndex(layerKind, position.getZ()));
		return entry;
	}

	private static String normalizeName(String name)
	{
		if (name == null)
		{
			return null;
		}

		String normalized = name.replace("<br>", " ").trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private static int mapTileX(int worldX, RegionMask mask)
	{
		return worldX - mask.minRegionX * Region.X;
	}

	private static int mapTileYForIcon(int worldY, RegionMask mask)
	{
		return mask.maxRegionY * Region.Y - worldY + Region.Y - 1;
	}

	private static int mapTileYForLabel(int worldY, RegionMask mask)
	{
		return mask.maxRegionY * Region.Y - worldY + Region.Y - 2;
	}

	private static boolean isPlane(int plane)
	{
		return plane >= 0 && plane < Region.Z;
	}

	private static TileChunk writeLayerLodChunk(
		BufferedImage source,
		RegionMask mask,
		Path chunkDir,
		int tilePx,
		int lod,
		int layerIndex,
		boolean preserveSourceAlpha
	) throws IOException
	{
		int lodWidth = source.getWidth() / lod;
		int lodHeight = source.getHeight() / lod;
		int tilesX = ceilDiv(lodWidth, tilePx);
		int tilesY = ceilDiv(lodHeight, tilePx);

		TileChunk chunk = new TileChunk(Files.createTempFile(chunkDir, "layer" + layerIndex + "-lod" + lod + "-", ".bin"));
		try (java.io.OutputStream out = Files.newOutputStream(chunk.path))
		{
			for (int ty = 0; ty < tilesY; ty++)
			{
				int y0 = ty * tilePx;
				int h = Math.min(tilePx, lodHeight - y0);

				for (int tx = 0; tx < tilesX; tx++)
				{
					int x0 = tx * tilePx;
					int w = Math.min(tilePx, lodWidth - x0);
					byte[] pngBytes = encodeTilePng(source, mask, lod, x0, y0, w, h, preserveSourceAlpha);
					chunk.entries.add(new TileEntry(lod, layerIndex, tx, ty, w, h, chunk.size, pngBytes.length));
					out.write(pngBytes);
					chunk.size += pngBytes.length;
				}
			}
		}

		return chunk;
	}

	private static byte[] encodeTilePng(
		BufferedImage source,
		RegionMask mask,
		int subsample,
		int lodX,
		int lodY,
		int tileWidth,
		int tileHeight,
		boolean preserveSourceAlpha
	) throws IOException
	{
		int sourceX = lodX * subsample;
		int sourceY = lodY * subsample;
		int sourceWidth = tileWidth * subsample;
		int sourceHeight = tileHeight * subsample;
		MaskMode maskMode = mask.classify(sourceX, sourceY, sourceWidth, sourceHeight);

		BufferedImage tile = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_ARGB);
		if (maskMode != MaskMode.ALL_TRANSPARENT)
		{
			if (subsample == 1)
			{
				copyFullResolutionTile(
					source,
					tile,
					mask,
					maskMode,
					sourceX,
					sourceY,
					tileWidth,
					tileHeight,
					preserveSourceAlpha
				);
			}
			else
			{
				downsampleTile(
					source,
					tile,
					mask,
					maskMode,
					subsample,
					sourceX,
					sourceY,
					tileWidth,
					tileHeight,
					preserveSourceAlpha
				);
			}
		}

		return encodePngBytes(tile);
	}

	private static void copyFullResolutionTile(
		BufferedImage source,
		BufferedImage tile,
		RegionMask mask,
		MaskMode maskMode,
		int sourceX,
		int sourceY,
		int tileWidth,
		int tileHeight,
		boolean preserveSourceAlpha
	)
	{
		if (maskMode == MaskMode.ALL_OPAQUE)
		{
			int[] pixels = source.getRGB(sourceX, sourceY, tileWidth, tileHeight, null, 0, tileWidth);
			if (!preserveSourceAlpha)
			{
				for (int i = 0; i < pixels.length; i++)
				{
					pixels[i] = 0xFF000000 | (pixels[i] & 0x00FFFFFF);
				}
			}
			tile.setRGB(0, 0, tileWidth, tileHeight, pixels, 0, tileWidth);
			return;
		}

		int[] row = new int[tileWidth];
		for (int y = 0; y < tileHeight; y++)
		{
			source.getRGB(sourceX, sourceY + y, tileWidth, 1, row, 0, tileWidth);
			for (int x = 0; x < tileWidth; x++)
			{
				if (mask.isPresent(sourceX + x, sourceY + y))
				{
					if (!preserveSourceAlpha)
					{
						row[x] = 0xFF000000 | (row[x] & 0x00FFFFFF);
					}
				}
				else
				{
					row[x] = 0;
				}
			}
			tile.setRGB(0, y, tileWidth, 1, row, 0, tileWidth);
		}
	}

	private static void downsampleTile(
		BufferedImage source,
		BufferedImage tile,
		RegionMask mask,
		MaskMode maskMode,
		int subsample,
		int sourceX,
		int sourceY,
		int tileWidth,
		int tileHeight,
		boolean preserveSourceAlpha
	)
	{
		int sourceWidth = tileWidth * subsample;
		int[] sourceRow = new int[sourceWidth];
		int[] outRow = new int[tileWidth];
		int[] sumA = new int[tileWidth];
		int[] sumR = new int[tileWidth];
		int[] sumG = new int[tileWidth];
		int[] sumB = new int[tileWidth];
		int area = subsample * subsample;

		for (int y = 0; y < tileHeight; y++)
		{
			Arrays.fill(sumA, 0);
			Arrays.fill(sumR, 0);
			Arrays.fill(sumG, 0);
			Arrays.fill(sumB, 0);

			for (int sy = 0; sy < subsample; sy++)
			{
				int sourceRowY = sourceY + y * subsample + sy;
				source.getRGB(sourceX, sourceRowY, sourceWidth, 1, sourceRow, 0, sourceWidth);

				for (int x = 0; x < tileWidth; x++)
				{
					for (int sx = 0; sx < subsample; sx++)
					{
						int sourceColX = sourceX + x * subsample + sx;
						if (maskMode == MaskMode.MIXED && !mask.isPresent(sourceColX, sourceRowY))
						{
							continue;
						}

						int rgb = sourceRow[x * subsample + sx];
						int a = preserveSourceAlpha ? (rgb >>> 24) & 0xFF : 255;
						sumA[x] += a;
						if (preserveSourceAlpha)
						{
							sumR[x] += ((rgb >>> 16) & 0xFF) * a;
							sumG[x] += ((rgb >>> 8) & 0xFF) * a;
							sumB[x] += (rgb & 0xFF) * a;
						}
						else
						{
							sumR[x] += (rgb >>> 16) & 0xFF;
							sumG[x] += (rgb >>> 8) & 0xFF;
							sumB[x] += rgb & 0xFF;
						}
					}
				}
			}

			for (int x = 0; x < tileWidth; x++)
			{
				int a = sumA[x] / area;
				int colorDivisor = preserveSourceAlpha ? sumA[x] : area;
				int r = colorDivisor == 0 ? 0 : sumR[x] / colorDivisor;
				int g = colorDivisor == 0 ? 0 : sumG[x] / colorDivisor;
				int b = colorDivisor == 0 ? 0 : sumB[x] / colorDivisor;
				outRow[x] = (a << 24) | (r << 16) | (g << 8) | b;
			}

			tile.setRGB(0, y, tileWidth, 1, outRow, 0, tileWidth);
		}
	}

	private static byte[] encodePngBytes(BufferedImage tile) throws IOException
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
		if (!writers.hasNext())
		{
			throw new IOException("No PNG ImageIO writer is available");
		}

		ImageWriter writer = writers.next();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes))
		{
			ImageWriteParam param = writer.getDefaultWriteParam();
			if (param.canWriteCompressed())
			{
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				param.setCompressionQuality(0.0f);
			}

			writer.setOutput(out);
			writer.write(null, new IIOImage(tile, null, null), param);
		}
		finally
		{
			writer.dispose();
		}

		return bytes.toByteArray();
	}

	private static void writeAtlasFile(
		File output,
		AtlasLayout layout,
		int[] lods,
		Map<String, TileChunk> chunks,
		byte[] metadata
	) throws IOException
	{
		File absoluteOutput = output.getAbsoluteFile();
		File parent = absoluteOutput.getParentFile();
		if (parent != null)
		{
			Files.createDirectories(parent.toPath());
		}

		Path tmp = Files.createTempFile(parent == null ? null : parent.toPath(), absoluteOutput.getName(), ".tmp");
		List<TileEntry> finalEntries = new ArrayList<>();

		try
		{
			try (RandomAccessFile atlas = new RandomAccessFile(tmp.toFile(), "rw"))
			{
				long indexOffset = layout.headerSize;
				long dataOffset = indexOffset + layout.totalEntries * ENTRY_SIZE;
				long metadataOffset = dataOffset + totalChunkBytes(chunks);
				writeHeader(atlas, layout, lods, indexOffset, dataOffset, metadataOffset, metadata.length);
				atlas.seek(dataOffset);

				long cursor = 0L;
				byte[] copyBuffer = new byte[1024 * 1024];
				for (int lod : lods)
				{
					for (AtlasLayer layer : layout.layers)
					{
						TileChunk chunk = chunks.get(chunkKey(lod, layer.index));
						if (chunk == null)
						{
							throw new IOException("Missing tile chunk for layer " + layer.index + " LOD " + lod);
						}

						for (TileEntry entry : chunk.entries)
						{
							finalEntries.add(entry.withRelativeOffset(cursor + entry.relOffset));
						}

						try (InputStream in = Files.newInputStream(chunk.path))
						{
							int read;
							while ((read = in.read(copyBuffer)) != -1)
							{
								atlas.write(copyBuffer, 0, read);
							}
						}
						cursor += chunk.size;
					}
				}

				if (finalEntries.size() != layout.totalEntries)
				{
					throw new IOException("Expected " + layout.totalEntries + " entries, wrote " + finalEntries.size());
				}

				atlas.seek(indexOffset);
				for (TileEntry entry : finalEntries)
				{
					writeEntry(atlas, entry);
				}

				atlas.seek(metadataOffset);
				atlas.write(metadata);
			}

			Files.move(tmp, absoluteOutput.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		finally
		{
			Files.deleteIfExists(tmp);
		}
	}

	private static void writeHeader(
		RandomAccessFile out,
		AtlasLayout layout,
		int[] lods,
		long indexOffset,
		long dataOffset,
		long metadataOffset,
		int metadataLength
	) throws IOException
	{
		out.write(MAGIC);
		writeU32(out, VERSION);
		writeU32(out, layout.width);
		writeU32(out, layout.height);
		writeU32(out, layout.tilePx);
		writeU32(out, lods.length);
		for (int lod : lods)
		{
			writeU32(out, lod);
		}
		writeU32(out, layout.tilesXFull);
		writeU32(out, layout.tilesYFull);
		writeU32(out, layout.layers.size());
		writeU64(out, indexOffset);
		writeU64(out, dataOffset);
		writeU64(out, metadataOffset);
		writeU32(out, metadataLength);
	}

	private static void writeEntry(RandomAccessFile out, TileEntry entry) throws IOException
	{
		writeU32(out, entry.lod);
		writeU32(out, entry.z);
		writeU32(out, entry.tx);
		writeU32(out, entry.ty);
		writeU32(out, entry.width);
		writeU32(out, entry.height);
		writeU64(out, entry.relOffset);
		writeU32(out, entry.length);
	}

	private static void writeU32(RandomAccessFile out, int value) throws IOException
	{
		out.write(value & 0xFF);
		out.write((value >>> 8) & 0xFF);
		out.write((value >>> 16) & 0xFF);
		out.write((value >>> 24) & 0xFF);
	}

	private static void writeU64(RandomAccessFile out, long value) throws IOException
	{
		out.write((int) (value & 0xFF));
		out.write((int) ((value >>> 8) & 0xFF));
		out.write((int) ((value >>> 16) & 0xFF));
		out.write((int) ((value >>> 24) & 0xFF));
		out.write((int) ((value >>> 32) & 0xFF));
		out.write((int) ((value >>> 40) & 0xFF));
		out.write((int) ((value >>> 48) & 0xFF));
		out.write((int) ((value >>> 56) & 0xFF));
	}

	private static int ceilDiv(int value, int divisor)
	{
		return (value + divisor - 1) / divisor;
	}

	private static long totalChunkBytes(Map<String, TileChunk> chunks)
	{
		long total = 0L;
		for (TileChunk chunk : chunks.values())
		{
			total += chunk.size;
		}
		return total;
	}

	private static int layerIndex(String kind, int plane)
	{
		if (!isPlane(plane))
		{
			throw new IllegalArgumentException("Invalid plane: " + plane);
		}

		for (int i = 0; i < LAYER_KINDS.length; i++)
		{
			if (LAYER_KINDS[i].equals(kind))
			{
				return i * Region.Z + plane;
			}
		}

		throw new IllegalArgumentException("Unknown layer kind: " + kind);
	}

	private static List<AtlasLayer> buildLayers()
	{
		List<AtlasLayer> layers = new ArrayList<>(LAYER_KINDS.length * Region.Z);
		for (String kind : LAYER_KINDS)
		{
			for (int plane = 0; plane < Region.Z; plane++)
			{
				layers.add(new AtlasLayer(layerIndex(kind, plane), kind, plane));
			}
		}
		return layers;
	}

	private static String chunkKey(int lod, int layerIndex)
	{
		return lod + ":" + layerIndex;
	}

	private static String join(int[] values)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++)
		{
			if (i > 0)
			{
				sb.append(',');
			}
			sb.append(values[i]);
		}
		return sb.toString();
	}

	private static void deleteChunks(Map<String, TileChunk> chunks)
	{
		for (TileChunk chunk : chunks.values())
		{
			try
			{
				Files.deleteIfExists(chunk.path);
			}
			catch (IOException ex)
			{
				System.err.println("Unable to delete temporary chunk " + chunk.path + ": " + ex.getMessage());
			}
		}
	}

	@FunctionalInterface
	private interface LayerRenderer
	{
		void render(BufferedImage image) throws IOException;
	}

	private static final class DumperAccess
	{
		private final MapImageDumper dumper;
		private final Method drawMapIcons;
		private final Method drawMapLabels;
		private final AreaManager areas;
		private final WorldMapManager worldMapManager;
		private final ObjectManager objectManager;

		private DumperAccess(MapImageDumper dumper) throws IOException
		{
			this.dumper = dumper;
			this.drawMapIcons = method("drawMapIcons", BufferedImage.class, int.class);
			this.drawMapLabels = method("drawMapLabels", BufferedImage.class, int.class);
			this.areas = field(dumper, "areas", AreaManager.class);
			this.worldMapManager = field(dumper, "worldMapManager", WorldMapManager.class);
			this.objectManager = field(dumper, "objectManager", ObjectManager.class);
		}

		private void drawMapIcons(BufferedImage image, int z) throws IOException
		{
			dumper.setRenderIcons(true);
			invoke(drawMapIcons, image, z);
		}

		private void drawMapLabels(BufferedImage image, int z) throws IOException
		{
			dumper.setRenderLabels(true);
			invoke(drawMapLabels, image, z);
		}

		private void invoke(Method method, BufferedImage image, int z) throws IOException
		{
			try
			{
				method.invoke(dumper, image, z);
			}
			catch (IllegalAccessException ex)
			{
				throw new IOException("Unable to invoke MapImageDumper." + method.getName(), ex);
			}
			catch (InvocationTargetException ex)
			{
				Throwable cause = ex.getCause();
				if (cause instanceof IOException)
				{
					throw (IOException) cause;
				}
				if (cause instanceof RuntimeException)
				{
					throw (RuntimeException) cause;
				}
				throw new IOException("MapImageDumper." + method.getName() + " failed", cause);
			}
		}

		private static Method method(String name, Class<?>... parameterTypes) throws IOException
		{
			try
			{
				Method method = MapImageDumper.class.getDeclaredMethod(name, parameterTypes);
				method.setAccessible(true);
				return method;
			}
			catch (NoSuchMethodException ex)
			{
				throw new IOException("MapImageDumper no longer has private method " + name, ex);
			}
		}

		private static <T> T field(MapImageDumper dumper, String name, Class<T> type) throws IOException
		{
			try
			{
				Field field = MapImageDumper.class.getDeclaredField(name);
				field.setAccessible(true);
				return type.cast(field.get(dumper));
			}
			catch (NoSuchFieldException | IllegalAccessException | ClassCastException ex)
			{
				throw new IOException("MapImageDumper no longer exposes compatible field " + name, ex);
			}
		}
	}

	private static final class AtlasLayer
	{
		private final int index;
		private final String kind;
		private final int plane;

		private AtlasLayer(int index, String kind, int plane)
		{
			this.index = index;
			this.kind = kind;
			this.plane = plane;
		}
	}

	private enum MaskMode
	{
		ALL_OPAQUE,
		ALL_TRANSPARENT,
		MIXED
	}

	private static final class UnencryptedFallbackRegionLoader extends RegionLoader
	{
		private final Store store;
		private final Index index;
		private final KeyProvider keyProvider;

		private UnencryptedFallbackRegionLoader(Store store, KeyProvider keyProvider)
		{
			super(store, keyProvider);
			this.store = store;
			this.index = store.getIndex(IndexType.MAPS);
			this.keyProvider = keyProvider;
		}

		@Override
		public LocationsDefinition loadLocDef(int i) throws IOException
		{
			int x = i >> 8;
			int y = i & 0xFF;

			Storage storage = store.getStorage();
			byte[] data;
			if (index.isNamed())
			{
				Archive locs = index.findArchiveByName("l" + x + "_" + y);
				if (locs == null)
				{
					return null;
				}

				byte[] archiveData = storage.loadArchive(locs);
				int[] keys = keyProvider.getKey(i);
				if (keys == null)
				{
					data = decompressUnencryptedOrSkip(locs, archiveData);
					if (data == null)
					{
						return null;
					}
				}
				else
				{
					data = locs.decompress(archiveData, keys);
				}
			}
			else
			{
				Archive archive = index.getArchive(i);
				if (archive == null)
				{
					return null;
				}

				data = archive.getFiles(storage.loadArchive(archive))
					.findFile(1)
					.getContents();
			}

			return new LocationsLoader().load(x, y, data);
		}

		private byte[] decompressUnencryptedOrSkip(Archive locs, byte[] archiveData) throws IOException
		{
			try
			{
				return locs.decompress(archiveData);
			}
			catch (IOException ex)
			{
				return null;
			}
		}
	}

	private static final class AtlasLayout
	{
		private final int width;
		private final int height;
		private final int tilePx;
		private final int tilesXFull;
		private final int tilesYFull;
		private final int headerSize;
		private final int totalEntries;
		private final RegionMask mask;
		private final List<AtlasLayer> layers;

		private AtlasLayout(
			int width,
			int height,
			int tilePx,
			int[] lods,
			RegionMask mask,
			List<AtlasLayer> layers
		)
		{
			this.width = width;
			this.height = height;
			this.tilePx = tilePx;
			this.tilesXFull = ceilDiv(width, tilePx);
			this.tilesYFull = ceilDiv(height, tilePx);
			this.headerSize = HEADER_SIZE_WITHOUT_LODS + lods.length * Integer.BYTES;
			this.mask = mask;
			this.layers = layers;
			this.totalEntries = countEntries(width, height, tilePx, lods, layers.size());
		}

		private static AtlasLayout create(
			BufferedImage image,
			RegionLoader regionLoader,
			int tilePx,
			int[] lods,
			boolean transparentEmptyRegions
		)
		{
			RegionMask mask = RegionMask.create(image, regionLoader, transparentEmptyRegions);
			return new AtlasLayout(image.getWidth(), image.getHeight(), tilePx, lods, mask, buildLayers());
		}

		private static int countEntries(int width, int height, int tilePx, int[] lods, int layerCount)
		{
			long entries = 0L;
			for (int lod : lods)
			{
				entries += (long) ceilDiv(width / lod, tilePx) * ceilDiv(height / lod, tilePx) * layerCount;
			}
			if (entries > Integer.MAX_VALUE)
			{
				throw new IllegalArgumentException("Too many atlas entries: " + entries);
			}
			return (int) entries;
		}

		private void requireSameSize(BufferedImage image) throws IOException
		{
			if (image.getWidth() != width || image.getHeight() != height)
			{
				throw new IOException("All rendered planes must match in size. Plane 0 is "
					+ width + "x" + height + ", but rendered plane is " + image.getWidth() + "x" + image.getHeight());
			}
		}
	}

	private static final class RegionMask
	{
		private final boolean transparentEmptyRegions;
		private final int minRegionX;
		private final int minRegionY;
		private final int maxRegionX;
		private final int maxRegionY;
		private final int cols;
		private final int rows;
		private final int regionPixelSize;
		private final boolean[] present;

		private RegionMask(
			boolean transparentEmptyRegions,
			int minRegionX,
			int minRegionY,
			int maxRegionX,
			int maxRegionY,
			int cols,
			int rows,
			int regionPixelSize,
			boolean[] present
		)
		{
			this.transparentEmptyRegions = transparentEmptyRegions;
			this.minRegionX = minRegionX;
			this.minRegionY = minRegionY;
			this.maxRegionX = maxRegionX;
			this.maxRegionY = maxRegionY;
			this.cols = cols;
			this.rows = rows;
			this.regionPixelSize = regionPixelSize;
			this.present = present;
		}

		private static RegionMask create(
			BufferedImage image,
			RegionLoader regionLoader,
			boolean transparentEmptyRegions
		)
		{
			int minRegionX = regionLoader.getLowestX().getRegionX();
			int maxRegionX = regionLoader.getHighestX().getRegionX();
			int minRegionY = regionLoader.getLowestY().getRegionY();
			int maxRegionY = regionLoader.getHighestY().getRegionY();
			int cols = maxRegionX - minRegionX + 1;
			int rows = maxRegionY - minRegionY + 1;

			if (image.getWidth() % cols != 0 || image.getHeight() % rows != 0)
			{
				throw new IllegalArgumentException("Rendered map dimensions do not line up with region bounds");
			}

			int regionPixelWidth = image.getWidth() / cols;
			int regionPixelHeight = image.getHeight() / rows;
			if (regionPixelWidth != regionPixelHeight)
			{
				throw new IllegalArgumentException("Rendered regions are not square in pixels");
			}

			boolean[] present = new boolean[cols * rows];
			for (Region region : regionLoader.getRegions())
			{
				int col = region.getRegionX() - minRegionX;
				int row = maxRegionY - region.getRegionY();
				if (col >= 0 && col < cols && row >= 0 && row < rows)
				{
					present[row * cols + col] = true;
				}
			}

			return new RegionMask(
				transparentEmptyRegions,
				minRegionX,
				minRegionY,
				maxRegionX,
				maxRegionY,
				cols,
				rows,
				regionPixelWidth,
				present
			);
		}

		private MaskMode classify(int sourceX, int sourceY, int sourceWidth, int sourceHeight)
		{
			if (!transparentEmptyRegions)
			{
				return MaskMode.ALL_OPAQUE;
			}

			int col0 = sourceX / regionPixelSize;
			int row0 = sourceY / regionPixelSize;
			int col1 = (sourceX + sourceWidth - 1) / regionPixelSize;
			int row1 = (sourceY + sourceHeight - 1) / regionPixelSize;

			boolean anyPresent = false;
			boolean anyMissing = false;
			for (int row = row0; row <= row1; row++)
			{
				for (int col = col0; col <= col1; col++)
				{
					if (isRegionPresent(col, row))
					{
						anyPresent = true;
					}
					else
					{
						anyMissing = true;
					}
				}
			}

			if (anyPresent && anyMissing)
			{
				return MaskMode.MIXED;
			}
			return anyPresent ? MaskMode.ALL_OPAQUE : MaskMode.ALL_TRANSPARENT;
		}

		private boolean isPresent(int sourceX, int sourceY)
		{
			if (!transparentEmptyRegions)
			{
				return true;
			}

			return isRegionPresent(sourceX / regionPixelSize, sourceY / regionPixelSize);
		}

		private boolean isRegionPresent(int col, int row)
		{
			return col >= 0 && col < cols && row >= 0 && row < rows && present[row * cols + col];
		}
	}

	private static final class TileChunk
	{
		private final Path path;
		private final List<TileEntry> entries = new ArrayList<>();
		private long size;

		private TileChunk(Path path)
		{
			this.path = path;
		}
	}

	private static final class TileEntry
	{
		private final int lod;
		private final int z;
		private final int tx;
		private final int ty;
		private final int width;
		private final int height;
		private final long relOffset;
		private final int length;

		private TileEntry(int lod, int z, int tx, int ty, int width, int height, long relOffset, int length)
		{
			this.lod = lod;
			this.z = z;
			this.tx = tx;
			this.ty = ty;
			this.width = width;
			this.height = height;
			this.relOffset = relOffset;
			this.length = length;
		}

		private TileEntry withRelativeOffset(long relOffset)
		{
			return new TileEntry(lod, z, tx, ty, width, height, relOffset, length);
		}
	}
}
