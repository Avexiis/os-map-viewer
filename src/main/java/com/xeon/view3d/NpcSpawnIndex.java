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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.cache.region.Region;

final class NpcSpawnIndex
{
	private static final String SPAWN_RESOURCE = "/com/xeon/application/data/npc-spawns-osrs.json";
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

	List<NpcSpawn> spawnsForRegion(int regionId)
	{
		return byRegion.getOrDefault(regionId, List.of());
	}

	private static NpcSpawnIndex loadResource()
	{
		try (InputStream in = NpcSpawnIndex.class.getResourceAsStream(SPAWN_RESOURCE))
		{
			if (in == null)
			{
				System.err.println("NPC spawn resource is missing: " + SPAWN_RESOURCE);
				return new NpcSpawnIndex(Map.of());
			}
			return read(new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load NPC spawns: " + ex.getMessage());
			return new NpcSpawnIndex(Map.of());
		}
	}

	private static NpcSpawnIndex read(JsonReader reader) throws IOException
	{
		Map<Integer, List<NpcSpawn>> byRegion = new HashMap<>();
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
		return new NpcSpawnIndex(byRegion);
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

	record NpcSpawn(
		int id,
		String name,
		int worldX,
		int worldY,
		int plane
	)
	{
		private int regionId()
		{
			return TerrainScene.regionId(Math.floorDiv(worldX, Region.X), Math.floorDiv(worldY, Region.Y));
		}
	}
}
