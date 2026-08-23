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
import com.xeon.io.Paths;
import com.xeon.model.Tile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NpcLocationLayer implements MapLayer
{
	private static final String SPAWN_RESOURCE = "/com/xeon/application/data/npc-spawns-osrs.json";
	private static final Color DOT_FILL = new Color(255, 242, 80, 225);
	private static volatile NpcLocationLayer defaultLayer;

	private final Map<Integer, List<NpcLocation>> byRegion;

	private NpcLocationLayer(Map<Integer, List<NpcLocation>> byRegion)
	{
		Map<Integer, List<NpcLocation>> copy = new HashMap<>();
		byRegion.forEach((regionId, locations) -> copy.put(regionId, List.copyOf(locations)));
		this.byRegion = Map.copyOf(copy);
	}

	public static NpcLocationLayer loadDefault()
	{
		NpcLocationLayer layer = defaultLayer;
		if (layer != null)
		{
			return layer;
		}
		synchronized (NpcLocationLayer.class)
		{
			if (defaultLayer == null)
			{
				defaultLayer = loadResource();
			}
			return defaultLayer;
		}
	}

	@Override
	public void paint(MapRenderContext context, Graphics2D g, Rectangle visibleMap)
	{
		if (context == null || g == null || visibleMap == null || visibleMap.isEmpty())
		{
			return;
		}
		Rectangle tileBounds = context.tileBounds(visibleMap);
		if (tileBounds == null || tileBounds.isEmpty())
		{
			return;
		}

		int plane = context.plane();
		g.setColor(DOT_FILL);
		int startRx = Math.floorDiv(tileBounds.x, Paths.REGION_TILE_SIZE);
		int endRx = Math.floorDiv(tileBounds.x + Math.max(0, tileBounds.width - 1), Paths.REGION_TILE_SIZE);
		int startRy = Math.floorDiv(tileBounds.y, Paths.REGION_TILE_SIZE);
		int endRy = Math.floorDiv(tileBounds.y + Math.max(0, tileBounds.height - 1), Paths.REGION_TILE_SIZE);
		for (int rx = startRx; rx <= endRx; rx++)
		{
			for (int ry = startRy; ry <= endRy; ry++)
			{
				for (NpcLocation location : byRegion.getOrDefault((rx << 8) | ry, List.of()))
				{
					if (location.plane() != plane || !tileBounds.contains(location.worldX(), location.worldY()))
					{
						continue;
					}
					Rectangle rect = context.tileToRect(new Tile(location.worldX(), location.worldY(), plane));
					if (rect == null || rect.isEmpty() || !rect.intersects(visibleMap))
					{
						continue;
					}
					g.fillRect(rect.x, rect.y, rect.width, rect.height);
				}
			}
		}
	}

	@Override
	public String tooltipText(MapRenderContext context, Tile tile, int regionId)
	{
		if (context == null || tile == null || regionId < 0)
		{
			return null;
		}
		for (NpcLocation location : byRegion.getOrDefault(regionId, List.of()))
		{
			if (location.worldX() == tile.x && location.worldY() == tile.y && location.plane() == tile.z)
			{
				String name = location.name().isBlank() ? "NPC" : location.name();
				return name;
			}
		}
		return null;
	}

	private static NpcLocationLayer loadResource()
	{
		try (InputStream in = NpcLocationLayer.class.getResourceAsStream(SPAWN_RESOURCE))
		{
			if (in == null)
			{
				System.err.println("NPC spawn resource is missing: " + SPAWN_RESOURCE);
				return new NpcLocationLayer(Map.of());
			}
			return read(new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load NPC location dots: " + ex.getMessage());
			return new NpcLocationLayer(Map.of());
		}
	}

	private static NpcLocationLayer read(JsonReader reader) throws IOException
	{
		Map<Integer, List<NpcLocation>> byRegion = new HashMap<>();
		reader.beginArray();
		while (reader.hasNext())
		{
			NpcLocation location = readLocation(reader);
			if (location == null || location.id() < 0 || location.plane() < 0 || location.plane() > 3)
			{
				continue;
			}
			byRegion.computeIfAbsent(location.regionId(), ignored -> new ArrayList<>()).add(location);
		}
		reader.endArray();
		return new NpcLocationLayer(byRegion);
	}

	private static NpcLocation readLocation(JsonReader reader) throws IOException
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
		return new NpcLocation(id, Objects.toString(name, ""), x, y, plane);
	}

	private record NpcLocation(int id, String name, int worldX, int worldY, int plane)
	{
		private int regionId()
		{
			return (Math.floorDiv(worldX, Paths.REGION_TILE_SIZE) << 8)
				| Math.floorDiv(worldY, Paths.REGION_TILE_SIZE);
		}
	}
}
