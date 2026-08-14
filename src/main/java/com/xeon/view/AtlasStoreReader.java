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
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.xeon.view;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.xeon.model.MapArea;
import com.xeon.model.Tile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

final class AtlasStoreReader implements AutoCloseable
{
	static final class Header
	{
		final int srcWidth;
		final int srcHeight;
		final int tilePx;
		final long indexOffset;
		final long dataOffset;
		final long metadataOffset;
		final int metadataLength;

		Header(int srcWidth, int srcHeight, int tilePx, long indexOffset, long dataOffset,
		       long metadataOffset, int metadataLength)
		{
			this.srcWidth = srcWidth;
			this.srcHeight = srcHeight;
			this.tilePx = tilePx;
			this.indexOffset = indexOffset;
			this.dataOffset = dataOffset;
			this.metadataOffset = metadataOffset;
			this.metadataLength = metadataLength;
		}
	}

	static final class TileEntry
	{
		final int lod;
		final int z;
		final int tx;
		final int ty;
		final int imgW;
		final int imgH;
		final long relOffset;
		final int length;

		TileEntry(int lod, int z, int tx, int ty, int imgW, int imgH, long relOffset, int length)
		{
			this.lod = lod;
			this.z = z;
			this.tx = tx;
			this.ty = ty;
			this.imgW = imgW;
			this.imgH = imgH;
			this.relOffset = relOffset;
			this.length = length;
		}
	}

	private final RandomAccessFile raf;
	private final FileChannel channel;
	private final long dataOffsetAbs;
	private final Header header;
	private final Map<String, TileEntry> index = new ConcurrentHashMap<>();
	private final Map<String, Integer> layerIndices = new ConcurrentHashMap<>();
	private byte[] metadataBytes = new byte[0];
	private volatile AreaIndex areaIndex;
	private volatile IconIndex iconIndex;
	private int maxPlanes = 1;

	Header header()
	{
		return header;
	}

	List<MapArea> searchMapAreas(String query, int limit)
	{
		String normalizedQuery = normalizeForSearch(query);
		if (normalizedQuery.length() < 3 || metadataBytes.length == 0 || limit <= 0)
		{
			return List.of();
		}
		return searchMapAreas(metadataBytes, normalizedQuery, limit);
	}

	List<MapArea> mapAreasAt(Tile tile)
	{
		if (tile == null || metadataBytes.length == 0)
		{
			return List.of();
		}
		return areaIndex().areasAt(tile);
	}

	MapArea nearestMapArea(Tile tile, int maxDistanceTiles)
	{
		if (tile == null || metadataBytes.length == 0 || maxDistanceTiles < 0)
		{
			return null;
		}
		return areaIndex().nearest(tile, maxDistanceTiles);
	}

	List<MapIconArea> nearestMapIcons(Tile tile, int maxDistanceTiles)
	{
		if (tile == null || metadataBytes.length == 0 || maxDistanceTiles < 0)
		{
			return List.of();
		}
		return iconIndex().nearest(tile, maxDistanceTiles);
	}

	boolean hasMapIconNear(Tile tile, int maxDistanceTiles)
	{
		return !nearestMapIcons(tile, maxDistanceTiles).isEmpty();
	}

	private AreaIndex areaIndex()
	{
		AreaIndex current = areaIndex;
		if (current != null)
		{
			return current;
		}
		synchronized (this)
		{
			current = areaIndex;
			if (current == null)
			{
				current = new AreaIndex(parseMapAreas(metadataBytes));
				areaIndex = current;
			}
			return current;
		}
	}

	private IconIndex iconIndex()
	{
		IconIndex current = iconIndex;
		if (current != null)
		{
			return current;
		}
		synchronized (this)
		{
			current = iconIndex;
			if (current == null)
			{
				current = new IconIndex(parseMapIconAreas(metadataBytes));
				iconIndex = current;
			}
			return current;
		}
	}

	static AtlasStoreReader openFile(Path path) throws Exception
	{
		if (path == null || !Files.isRegularFile(path))
		{
			throw new IllegalStateException("Atlas file not found");
		}
		return new AtlasStoreReader(new RandomAccessFile(path.toFile(), "r"));
	}

	private AtlasStoreReader(RandomAccessFile raf) throws Exception
	{
		this.raf = raf;
		this.header = readHeader();
		this.channel = raf.getChannel();
		this.dataOffsetAbs = header.dataOffset;
		readMetadata();
		readIndex();
	}

	private Header readHeader() throws Exception
	{
		raf.seek(0);
		byte[] magic8 = new byte[8];
		raf.readFully(magic8);
		byte[] expected7 = new byte[]{'A', 'T', 'L', 'S', 'v', '3', 0x00};
		boolean first7Match = true;
		for (int i = 0; i < 7; i++)
		{
			if (magic8[i] != expected7[i])
			{
				first7Match = false;
				break;
			}
		}
		if (!first7Match)
		{
			throw new IllegalStateException("Atlas: bad magic");
		}
		if (magic8[7] != 0x00)
		{
			raf.seek(7);
		}

		int version = readU32();
		if (version != 3)
		{
			throw new IllegalStateException("Atlas: unsupported version " + version + "; expected 3");
		}
		int srcW = readU32();
		int srcH = readU32();
		int tile = readU32();
		int numLods = readU32();
		for (int i = 0; i < numLods; i++)
		{
			readU32();
		}
		readU32();
		readU32();
		readU32();
		long indexOff = readU64();
		long dataOff = readU64();
		long metadataOff = readU64();
		int metadataLen = readU32();
		return new Header(srcW, srcH, tile, indexOff, dataOff, metadataOff, metadataLen);
	}

	private void readMetadata() throws Exception
	{
		layerIndices.clear();
		metadataBytes = new byte[0];
		areaIndex = null;
		iconIndex = null;
		if (header.metadataOffset <= 0 || header.metadataLength <= 0)
		{
			throw new IllegalStateException("Atlas: missing v3 metadata");
		}

		byte[] bytes = new byte[header.metadataLength];
		raf.seek(header.metadataOffset);
		raf.readFully(bytes);
		metadataBytes = bytes;
		boolean hasLayers = parseLayerMetadata(bytes);
		if (!hasLayers || layerIndices.isEmpty())
		{
			throw new IllegalStateException("Atlas: missing v3 layer metadata");
		}
	}

	private boolean parseLayerMetadata(byte[] bytes) throws Exception
	{
		int maxPlaneSeen = -1;
		boolean foundLayers = false;
		try (JsonReader reader = new JsonReader(new InputStreamReader(
			new ByteArrayInputStream(bytes),
			StandardCharsets.UTF_8
		)))
		{
			reader.beginObject();
			while (reader.hasNext())
			{
				String name = reader.nextName();
				if (!"layers".equals(name) || reader.peek() != JsonToken.BEGIN_ARRAY)
				{
					reader.skipValue();
					continue;
				}

				foundLayers = true;
				reader.beginArray();
				while (reader.hasNext())
				{
					LayerMetadata layer = readLayerMetadataEntry(reader);
					if (layer.kind() == null || layer.plane() < 0 || layer.index() < 0)
					{
						continue;
					}
					layerIndices.put(layerKey(layer.kind(), layer.plane()), layer.index());
					maxPlaneSeen = Math.max(maxPlaneSeen, layer.plane());
				}
				reader.endArray();
			}
			reader.endObject();
		}
		maxPlanes = Math.max(1, maxPlaneSeen + 1);
		return foundLayers;
	}

	private static LayerMetadata readLayerMetadataEntry(JsonReader reader) throws Exception
	{
		AtlasLayerType kind = null;
		int plane = -1;
		int index = -1;
		reader.beginObject();
		while (reader.hasNext())
		{
			String name = reader.nextName();
			switch (name)
			{
				case "kind" -> kind = AtlasLayerType.fromMetadataName(readStringValue(reader));
				case "plane" -> plane = readIntValue(reader, -1);
				case "index" -> index = readIntValue(reader, -1);
				default -> reader.skipValue();
			}
		}
		reader.endObject();
		return new LayerMetadata(kind, plane, index);
	}

	private static List<MapArea> searchMapAreas(byte[] bytes, String normalizedQuery, int limit)
	{
		int maxCandidates = Math.max(limit, Math.min(500, limit * 3));
		Map<String, MapArea> out = new LinkedHashMap<>();
		try (JsonReader reader = new JsonReader(new InputStreamReader(
			new ByteArrayInputStream(bytes),
			StandardCharsets.UTF_8
		)))
		{
			reader.beginObject();
			while (reader.hasNext())
			{
				if (Thread.currentThread().isInterrupted())
				{
					throw new InterruptedException();
				}
				String name = reader.nextName();
				if (("locationLabels".equals(name) || "mapIcons".equals(name))
					&& reader.peek() == JsonToken.BEGIN_ARRAY)
				{
					collectMapAreaMatches(reader, normalizedQuery, out, maxCandidates);
				}
				else
				{
					reader.skipValue();
				}
			}
			reader.endObject();
		}
		catch (Exception ex)
		{
			return List.of();
		}

		List<MapArea> sorted = new ArrayList<>(out.values());
		sorted.sort(Comparator
			.comparingInt((MapArea area) -> searchScore(area, normalizedQuery))
			.thenComparing(MapArea::displayName, String.CASE_INSENSITIVE_ORDER)
			.thenComparingInt(MapArea::plane)
			.thenComparingInt(MapArea::worldX)
			.thenComparingInt(MapArea::worldY)
			.thenComparing(MapArea::source, String.CASE_INSENSITIVE_ORDER));
		if (sorted.size() > limit)
		{
			sorted = sorted.subList(0, limit);
		}
		return List.copyOf(sorted);
	}

	private static List<MapArea> parseMapAreas(byte[] bytes)
	{
		Map<String, MapArea> out = new LinkedHashMap<>();
		try (JsonReader reader = new JsonReader(new InputStreamReader(
			new ByteArrayInputStream(bytes),
			StandardCharsets.UTF_8
		)))
		{
			reader.beginObject();
			while (reader.hasNext())
			{
				String name = reader.nextName();
				if (("locationLabels".equals(name) || "mapIcons".equals(name))
					&& reader.peek() == JsonToken.BEGIN_ARRAY)
				{
					collectMapAreas(reader, out);
				}
				else
				{
					reader.skipValue();
				}
			}
			reader.endObject();
		}
		catch (Exception ex)
		{
			return List.of();
		}
		return List.copyOf(out.values());
	}

	private static List<MapIconArea> parseMapIconAreas(byte[] bytes)
	{
		List<MapIconArea> out = new ArrayList<>();
		try (JsonReader reader = new JsonReader(new InputStreamReader(
			new ByteArrayInputStream(bytes),
			StandardCharsets.UTF_8
		)))
		{
			reader.beginObject();
			while (reader.hasNext())
			{
				String name = reader.nextName();
				if ("mapIcons".equals(name) && reader.peek() == JsonToken.BEGIN_ARRAY)
				{
					collectMapIconAreas(reader, out);
				}
				else
				{
					reader.skipValue();
				}
			}
			reader.endObject();
		}
		catch (Exception ex)
		{
			return List.of();
		}
		return List.copyOf(out);
	}

	private static void collectMapAreas(JsonReader reader, Map<String, MapArea> out) throws Exception
	{
		reader.beginArray();
		while (reader.hasNext())
		{
			MapArea area = readMapArea(reader);
			if (area != null)
			{
				out.putIfAbsent(areaKey(area), area);
			}
		}
		reader.endArray();
	}

	private static void collectMapIconAreas(JsonReader reader, List<MapIconArea> out) throws Exception
	{
		reader.beginArray();
		while (reader.hasNext())
		{
			MapIconArea area = readMapIconArea(reader);
			if (area != null)
			{
				out.add(area);
			}
		}
		reader.endArray();
	}

	private static void collectMapAreaMatches(JsonReader reader, String normalizedQuery,
	                                          Map<String, MapArea> out, int maxCandidates) throws Exception
	{
		reader.beginArray();
		while (reader.hasNext())
		{
			if (Thread.currentThread().isInterrupted())
			{
				throw new InterruptedException();
			}
			if (out.size() >= maxCandidates)
			{
				reader.skipValue();
				continue;
			}

			MapArea area = readMapArea(reader);
			if (area == null || !normalizeForSearch(area.searchText()).contains(normalizedQuery))
			{
				continue;
			}
			out.putIfAbsent(areaKey(area), area);
		}
		reader.endArray();
	}

	private static MapArea readMapArea(JsonReader reader) throws Exception
	{
		String name = null;
		String rawName = null;
		String objectName = null;
		String source = null;
		int areaId = -1;
		Integer worldX = null;
		Integer worldY = null;
		Integer plane = null;

		reader.beginObject();
		while (reader.hasNext())
		{
			String key = reader.nextName();
			switch (key)
			{
				case "name" -> name = cleanString(readStringValue(reader));
				case "rawName" -> rawName = cleanString(readStringValue(reader));
				case "objectName" -> objectName = cleanString(readStringValue(reader));
				case "source" -> source = readStringValue(reader);
				case "areaId" -> areaId = readIntValue(reader, -1);
				case "worldX" -> worldX = readNullableIntValue(reader);
				case "worldY" -> worldY = readNullableIntValue(reader);
				case "plane" -> plane = readNullableIntValue(reader);
				default -> reader.skipValue();
			}
		}
		reader.endObject();

		String displayName = firstNonBlank(name, rawName, objectName);
		if (displayName == null || worldX == null || worldY == null || plane == null)
		{
			return null;
		}
		return new MapArea(displayName, rawName, objectName, source, areaId, worldX, worldY, plane);
	}

	private static MapIconArea readMapIconArea(JsonReader reader) throws Exception
	{
		String name = null;
		String rawName = null;
		String objectName = null;
		int areaId = -1;
		int spriteId = -1;
		Integer worldX = null;
		Integer worldY = null;
		Integer plane = null;

		reader.beginObject();
		while (reader.hasNext())
		{
			String key = reader.nextName();
			switch (key)
			{
				case "name" -> name = cleanString(readStringValue(reader));
				case "rawName" -> rawName = cleanString(readStringValue(reader));
				case "objectName" -> objectName = cleanString(readStringValue(reader));
				case "areaId" -> areaId = readIntValue(reader, -1);
				case "spriteId" -> spriteId = readIntValue(reader, -1);
				case "worldX" -> worldX = readNullableIntValue(reader);
				case "worldY" -> worldY = readNullableIntValue(reader);
				case "plane" -> plane = readNullableIntValue(reader);
				default -> reader.skipValue();
			}
		}
		reader.endObject();

		if (worldX == null || worldY == null || plane == null)
		{
			return null;
		}
		return new MapIconArea(firstNonBlank(name, rawName, objectName), areaId, spriteId, worldX, worldY, plane);
	}

	private static String areaKey(MapArea area)
	{
		return area.displayName() + '\u0000'
			+ area.worldX() + '\u0000'
			+ area.worldY() + '\u0000'
			+ area.plane() + '\u0000'
			+ area.source() + '\u0000'
			+ area.areaId();
	}

	private static String firstNonBlank(String... values)
	{
		for (String value : values)
		{
			if (value != null && !value.isBlank())
			{
				return value;
			}
		}
		return null;
	}

	private static String readStringValue(JsonReader reader) throws Exception
	{
		JsonToken token = reader.peek();
		if (token == JsonToken.NULL)
		{
			reader.nextNull();
			return null;
		}
		if (token == JsonToken.STRING || token == JsonToken.NUMBER || token == JsonToken.BOOLEAN)
		{
			return reader.nextString();
		}
		reader.skipValue();
		return null;
	}

	private static Integer readNullableIntValue(JsonReader reader) throws Exception
	{
		JsonToken token = reader.peek();
		if (token == JsonToken.NULL)
		{
			reader.nextNull();
			return null;
		}
		try
		{
			if (token == JsonToken.NUMBER || token == JsonToken.STRING)
			{
				return Integer.parseInt(reader.nextString());
			}
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
		reader.skipValue();
		return null;
	}

	private static int readIntValue(JsonReader reader, int fallback) throws Exception
	{
		Integer value = readNullableIntValue(reader);
		return value == null ? fallback : value;
	}

	private static int searchScore(MapArea area, String query)
	{
		String name = normalizeForSearch(area.displayName());
		if (name.equals(query))
		{
			return 0;
		}
		if (name.startsWith(query))
		{
			return 1;
		}
		for (String token : name.split("\\s+"))
		{
			if (token.startsWith(query))
			{
				return 2;
			}
		}
		if (normalizeForSearch(area.searchText()).startsWith(query))
		{
			return 3;
		}
		return 4;
	}

	private static String normalizeForSearch(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.trim().toLowerCase(Locale.ROOT);
	}

	private record LayerMetadata(AtlasLayerType kind, int plane, int index)
	{
	}

	private static String cleanString(String value)
	{
		if (value == null)
		{
			return null;
		}
		String cleaned = value.replace("<br>", " ")
			.replace("<br/>", " ")
			.replace("<br />", " ")
			.trim();
		if ("null".equalsIgnoreCase(cleaned))
		{
			return null;
		}
		return cleaned.isEmpty() ? null : cleaned;
	}

	private void readIndex() throws Exception
	{
		raf.seek(header.indexOffset);
		long entrySize = 36L;
		long totalEntries = (header.dataOffset - header.indexOffset) / entrySize;
		for (long i = 0; i < totalEntries; i++)
		{
			int lod = readU32();
			int z = readU32();
			int tx = readU32();
			int ty = readU32();
			int width = readU32();
			int height = readU32();
			long rel = readU64();
			int length = readU32();
			TileEntry entry = new TileEntry(lod, z, tx, ty, width, height, rel, length);
			index.put(key(lod, z, tx, ty), entry);
		}
	}

	private static final class AreaIndex
	{
		private final List<MapArea> areas;
		private final Map<String, List<MapArea>> byTile;

		AreaIndex(List<MapArea> areas)
		{
			this.areas = areas == null ? List.of() : List.copyOf(areas);
			Map<String, List<MapArea>> grouped = new HashMap<>();
			for (MapArea area : this.areas)
			{
				grouped.computeIfAbsent(tileKey(area.worldX(), area.worldY(), area.plane()),
					ignored -> new ArrayList<>()).add(area);
			}
			Map<String, List<MapArea>> immutable = new HashMap<>();
			for (Map.Entry<String, List<MapArea>> entry : grouped.entrySet())
			{
				immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
			}
			byTile = Map.copyOf(immutable);
		}

		List<MapArea> areasAt(Tile tile)
		{
			if (tile == null)
			{
				return List.of();
			}
			return byTile.getOrDefault(tileKey(tile.x, tile.y, tile.z), List.of());
		}

		MapArea nearest(Tile tile, int maxDistanceTiles)
		{
			if (tile == null || maxDistanceTiles < 0)
			{
				return null;
			}
			List<MapArea> exact = areasAt(tile);
			if (!exact.isEmpty())
			{
				return exact.get(0);
			}
			long maxDistanceSq = (long) maxDistanceTiles * maxDistanceTiles;
			MapArea best = null;
			long bestDistanceSq = Long.MAX_VALUE;
			for (MapArea area : areas)
			{
				if (area.plane() != tile.z)
				{
					continue;
				}
				long dx = (long) area.worldX() - tile.x;
				long dy = (long) area.worldY() - tile.y;
				long distanceSq = dx * dx + dy * dy;
				if (distanceSq > maxDistanceSq || distanceSq > bestDistanceSq)
				{
					continue;
				}
				if (distanceSq == bestDistanceSq && best != null
					&& area.displayName().compareToIgnoreCase(best.displayName()) >= 0)
				{
					continue;
				}
				best = area;
				bestDistanceSq = distanceSq;
			}
			return best;
		}

		private static String tileKey(int x, int y, int z)
		{
			return x + ":" + y + ":" + z;
		}
	}

	private static final class IconIndex
	{
		private final Map<String, List<MapIconArea>> byTile;

		IconIndex(List<MapIconArea> areas)
		{
			Map<String, List<MapIconArea>> grouped = new HashMap<>();
			if (areas != null)
			{
				for (MapIconArea area : areas)
				{
					grouped.computeIfAbsent(tileKey(area.worldX, area.worldY, area.plane),
						ignored -> new ArrayList<>()).add(area);
				}
			}
			Map<String, List<MapIconArea>> immutable = new HashMap<>();
			for (Map.Entry<String, List<MapIconArea>> entry : grouped.entrySet())
			{
				immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
			}
			byTile = Map.copyOf(immutable);
		}

		List<MapIconArea> nearest(Tile tile, int maxDistanceTiles)
		{
			if (tile == null || maxDistanceTiles < 0 || byTile.isEmpty())
			{
				return List.of();
			}

			long bestDistanceSq = Long.MAX_VALUE;
			List<MapIconArea> best = new ArrayList<>();
			for (int dy = -maxDistanceTiles; dy <= maxDistanceTiles; dy++)
			{
				for (int dx = -maxDistanceTiles; dx <= maxDistanceTiles; dx++)
				{
					long distanceSq = (long) dx * dx + (long) dy * dy;
					if (distanceSq > (long) maxDistanceTiles * maxDistanceTiles || distanceSq > bestDistanceSq)
					{
						continue;
					}
					List<MapIconArea> areas = byTile.get(tileKey(tile.x + dx, tile.y + dy, tile.z));
					if (areas == null || areas.isEmpty())
					{
						continue;
					}
					if (distanceSq < bestDistanceSq)
					{
						bestDistanceSq = distanceSq;
						best.clear();
					}
					best.addAll(areas);
				}
			}
			return best.isEmpty() ? List.of() : List.copyOf(best);
		}

		private static String tileKey(int x, int y, int z)
		{
			return x + ":" + y + ":" + z;
		}
	}

	private static String key(int lod, int z, int tx, int ty)
	{
		return lod + ":" + z + ":" + tx + ":" + ty;
	}

	private static String layerKey(AtlasLayerType kind, int plane)
	{
		return kind.metadataName + ":" + plane;
	}

	private int readU32() throws Exception
	{
		byte[] bytes = new byte[4];
		raf.readFully(bytes);
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	private long readU64() throws Exception
	{
		byte[] bytes = new byte[8];
		raf.readFully(bytes);
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
	}

	int maxPlanes()
	{
		return maxPlanes;
	}

	int layerIndex(AtlasLayerType kind, int plane)
	{
		if (kind == null || plane < 0)
		{
			return -1;
		}
		return layerIndices.getOrDefault(layerKey(kind, plane), -1);
	}

	TileEntry getEntry(int lod, int layer, int tx, int ty)
	{
		return index.get(key(lod, layer, tx, ty));
	}

	BufferedImage readTileImage(int lod, int layer, int tx, int ty) throws Exception
	{
		TileEntry entry = getEntry(lod, layer, tx, ty);
		if (entry == null)
		{
			return null;
		}

		byte[] buf = new byte[entry.length];
		ByteBuffer bb = ByteBuffer.wrap(buf);
		long position = dataOffsetAbs + entry.relOffset;
		int read = 0;
		while (read < entry.length)
		{
			int n = channel.read(bb, position + read);
			if (n < 0)
			{
				throw new EOFException("Unexpected EOF reading tile");
			}
			read += n;
		}

		try (ByteArrayInputStream bais = new ByteArrayInputStream(buf))
		{
			return ImageIO.read(bais);
		}
	}

	@Override
	public void close() throws Exception
	{
		try
		{
			channel.close();
		}
		finally
		{
			raf.close();
		}
	}
}
