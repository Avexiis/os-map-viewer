/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.

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
package com.xeon.view3d;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.runelite.cache.region.Region;

final class NpcSpawnIndex
{
	private static final String SPAWN_RESOURCE = "/com/xeon/application/data/npc-spawns-osrs.json";
	private static final String CUSTOM_SPAWN_RESOURCE = "/com/xeon/application/data/npc-spawns-custom.tsv";
	private static final Path SOURCE_DATA_DIR = Path.of("src", "main", "resources", "com", "xeon", "application", "data");
	private static final Path SOURCE_SPAWN_PATH = SOURCE_DATA_DIR.resolve("npc-spawns-osrs.json");
	private static final Path SOURCE_CUSTOM_SPAWN_PATH = SOURCE_DATA_DIR.resolve("npc-spawns-custom.tsv");
	private static volatile NpcSpawnIndex defaultIndex;

	private final Map<Integer, List<NpcSpawn>> byRegion;

	private NpcSpawnIndex(Map<Integer, List<NpcSpawn>> byRegion)
	{
		Map<Integer, List<NpcSpawn>> copy = new HashMap<>();
		byRegion.forEach((regionId, spawns) -> copy.put(regionId, List.copyOf(spawns)));
		this.byRegion = Map.copyOf(copy);
	}

	static NpcSpawnIndex loadDefault()
	{
		NpcSpawnIndex index = defaultIndex;
		if (index != null)
		{
			return index;
		}
		synchronized (NpcSpawnIndex.class)
		{
			if (defaultIndex == null)
			{
				defaultIndex = loadResource();
			}
			return defaultIndex;
		}
	}

	static void reloadDefault()
	{
		synchronized (NpcSpawnIndex.class)
		{
			defaultIndex = loadResource();
		}
	}

	static Path sourceSpawnPath()
	{
		return SOURCE_SPAWN_PATH.toAbsolutePath().normalize();
	}

	static Path sourceCustomSpawnPath()
	{
		return SOURCE_CUSTOM_SPAWN_PATH.toAbsolutePath().normalize();
	}

	List<NpcSpawn> spawnsForRegion(int regionId)
	{
		return byRegion.getOrDefault(regionId, List.of());
	}

	private static NpcSpawnIndex loadResource()
	{
		Map<Integer, List<NpcSpawn>> byRegion = new HashMap<>();
		try (Reader reader = openReader(SOURCE_SPAWN_PATH, SPAWN_RESOURCE))
		{
			if (reader == null)
			{
				System.err.println("NPC spawn resource is missing: " + SPAWN_RESOURCE);
				return new NpcSpawnIndex(Map.of());
			}
			readJson(new JsonReader(reader), byRegion);
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load NPC spawns: " + ex.getMessage());
			return new NpcSpawnIndex(Map.of());
		}
		try
		{
			readCustomTsv(byRegion);
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load custom NPC spawns: " + ex.getMessage());
		}
		return new NpcSpawnIndex(byRegion);
	}

	private static Reader openReader(Path sourcePath, String resourcePath) throws IOException
	{
		if (Files.isRegularFile(sourcePath))
		{
			return Files.newBufferedReader(sourcePath, StandardCharsets.UTF_8);
		}
		InputStream in = NpcSpawnIndex.class.getResourceAsStream(resourcePath);
		return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
	}

	private static void readJson(JsonReader reader, Map<Integer, List<NpcSpawn>> byRegion) throws IOException
	{
		reader.beginArray();
		while (reader.hasNext())
		{
			NpcSpawn spawn = readSpawn(reader);
			if (spawn == null || spawn.id() < 0 || spawn.plane() < 0 || spawn.plane() >= Region.Z)
			{
				continue;
			}
			byRegion.computeIfAbsent(spawn.regionId(), ignored -> new ArrayList<>()).add(spawn);
		}
		reader.endArray();
	}

	private static NpcSpawn readSpawn(JsonReader reader) throws IOException
	{
		int id = -1;
		int x = Integer.MIN_VALUE;
		int y = Integer.MIN_VALUE;
		int plane = 0;
		String name = "";
		reader.beginObject();
		while (reader.hasNext())
		{
			String field = reader.nextName();
			JsonToken token = reader.peek();
			if ("id".equals(field) && token == JsonToken.NUMBER)
			{
				id = reader.nextInt();
			}
			else if ("x".equals(field) && token == JsonToken.NUMBER)
			{
				x = reader.nextInt();
			}
			else if ("y".equals(field) && token == JsonToken.NUMBER)
			{
				y = reader.nextInt();
			}
			else if ("level".equals(field) && token == JsonToken.NUMBER)
			{
				plane = reader.nextInt();
			}
			else if ("name".equals(field) && token == JsonToken.STRING)
			{
				name = reader.nextString();
			}
			else
			{
				reader.skipValue();
			}
		}
		reader.endObject();
		if (id < 0 || x == Integer.MIN_VALUE || y == Integer.MIN_VALUE)
		{
			return null;
		}
		return new NpcSpawn(id, Objects.toString(name, ""), x, y, plane);
	}

	private static void readCustomTsv(Map<Integer, List<NpcSpawn>> byRegion) throws IOException
	{
		try (Reader reader = openReader(SOURCE_CUSTOM_SPAWN_PATH, CUSTOM_SPAWN_RESOURCE))
		{
			if (reader == null)
			{
				return;
			}
			try (BufferedReader buffered = new BufferedReader(reader))
			{
				String headerLine = buffered.readLine();
				if (headerLine == null)
				{
					return;
				}
				String[] headers = parseHeaderLine(headerLine);
				String line;
				while ((line = buffered.readLine()) != null)
				{
					if (line.isBlank() || line.startsWith("#"))
					{
						continue;
					}
					NpcSpawn spawn = readCustomSpawn(line, headers);
					if (spawn == null || spawn.id() < 0 || spawn.plane() < 0 || spawn.plane() >= Region.Z)
					{
						continue;
					}
					byRegion.computeIfAbsent(spawn.regionId(), ignored -> new ArrayList<>()).add(spawn);
				}
			}
		}
	}

	private static String[] parseHeaderLine(String headerLine)
	{
		String normalized = headerLine;
		if (normalized.startsWith("# "))
		{
			normalized = normalized.substring(2);
		}
		else if (normalized.startsWith("#"))
		{
			normalized = normalized.substring(1);
		}
		return normalized.split("\t", -1);
	}

	private static NpcSpawn readCustomSpawn(String line, String[] headers)
	{
		String[] fields = line.split("\t", -1);
		Map<String, String> values = new HashMap<>();
		for (int i = 0; i < headers.length; i++)
		{
			values.put(headers[i], i < fields.length ? fields[i] : "");
		}

		int id = parseInt(values.get("id"), -1);
		int x = parseInt(values.get("x"), Integer.MIN_VALUE);
		int y = parseInt(values.get("y"), Integer.MIN_VALUE);
		int plane = parseInt(values.getOrDefault("level", values.get("plane")), 0);
		if (id < 0 || x == Integer.MIN_VALUE || y == Integer.MIN_VALUE)
		{
			return null;
		}
		return new NpcSpawn(
			id,
			Objects.toString(values.get("name"), ""),
			x,
			y,
			plane,
			parseFaceDirection(values.get("faceDirection")),
			parseWalk(values.get("walk")),
			SpawnSource.TSV
		);
	}

	private static int parseInt(String value, int fallback)
	{
		if (value == null || value.isBlank())
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	private static Integer parseFaceDirection(String value)
	{
		if (value == null || value.isBlank() || "default".equalsIgnoreCase(value.trim()))
		{
			return null;
		}
		int numeric = parseInt(value, Integer.MIN_VALUE);
		if (numeric >= 0 && numeric <= 7)
		{
			return numeric;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", ""))
		{
			case "northwest", "nw" -> 0;
			case "north", "n" -> 1;
			case "northeast", "ne" -> 2;
			case "west", "w" -> 3;
			case "east", "e" -> 4;
			case "southwest", "sw" -> 5;
			case "south", "s" -> 6;
			case "southeast", "se" -> 7;
			default -> null;
		};
	}

	private static Boolean parseWalk(String value)
	{
		if (value == null || value.isBlank() || "default".equalsIgnoreCase(value.trim()))
		{
			return null;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT))
		{
			case "true", "yes", "y", "1", "enabled", "enable" -> Boolean.TRUE;
			case "false", "no", "n", "0", "disabled", "disable" -> Boolean.FALSE;
			default -> null;
		};
	}

	enum SpawnSource
	{
		JSON,
		TSV
	}

	record NpcSpawn(
		int id,
		String name,
		int worldX,
		int worldY,
		int plane,
		Integer faceDirection,
		Boolean walkEnabled,
		SpawnSource source
	)
	{
		private NpcSpawn(int id, String name, int worldX, int worldY, int plane)
		{
			this(id, name, worldX, worldY, plane, null, null, SpawnSource.JSON);
		}

		NpcSpawn
		{
			name = Objects.toString(name, "");
			source = source == null ? SpawnSource.JSON : source;
			if (faceDirection != null && (faceDirection < 0 || faceDirection > 7))
			{
				faceDirection = null;
			}
		}

		private int regionId()
		{
			return TerrainScene.regionId(Math.floorDiv(worldX, Region.X), Math.floorDiv(worldY, Region.Y));
		}
	}
}
