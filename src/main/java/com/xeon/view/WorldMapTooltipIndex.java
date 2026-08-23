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

import com.xeon.model.Tile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class WorldMapTooltipIndex
{
	private static final String RESOURCE = "/com/xeon/application/data/worldmap_tooltips.tsv";
	private static final String ICON_LABEL_RESOURCE = "/com/xeon/application/data/worldmap_icon_labels.tsv";
	private static final int HIT_RADIUS_TILES = 2;
	private static final int HIT_RADIUS_SQUARED = HIT_RADIUS_TILES * HIT_RADIUS_TILES;

	private final Map<Long, List<Entry>> byTile;
	private final List<Marker> markers;
	private final Map<Integer, String> iconLabelsBySpriteId;

	private WorldMapTooltipIndex(TooltipData tooltipData, Map<Integer, String> iconLabelsBySpriteId)
	{
		this.byTile = tooltipData.byTile();
		this.markers = tooltipData.markers();
		this.iconLabelsBySpriteId = iconLabelsBySpriteId;
	}

	static WorldMapTooltipIndex loadDefault()
	{
		return new WorldMapTooltipIndex(loadTooltipData(), loadIconLabels());
	}

	String tooltipAt(Tile tile)
	{
		if (tile == null || byTile.isEmpty())
		{
			return null;
		}

		int bestDistance = Integer.MAX_VALUE;
		long bestKey = 0L;
		List<Entry> bestEntries = null;
		for (int dy = -HIT_RADIUS_TILES; dy <= HIT_RADIUS_TILES; dy++)
		{
			for (int dx = -HIT_RADIUS_TILES; dx <= HIT_RADIUS_TILES; dx++)
			{
				int distance = dx * dx + dy * dy;
				if (distance > HIT_RADIUS_SQUARED || distance > bestDistance)
				{
					continue;
				}
				long key = key(tile.x + dx, tile.y + dy, tile.z);
				List<Entry> entries = byTile.get(key);
				if (entries == null || entries.isEmpty())
				{
					continue;
				}
				if (distance < bestDistance || bestEntries == null)
				{
					bestDistance = distance;
					bestKey = key;
					bestEntries = entries;
				}
				else if (key < bestKey)
				{
					bestKey = key;
					bestEntries = entries;
				}
			}
		}

		return bestEntries == null ? null : formatTooltip(bestEntries);
	}

	String iconLabel(int spriteId)
	{
		return iconLabelsBySpriteId.get(spriteId);
	}

	List<Marker> markers()
	{
		return markers;
	}

	static String formatLines(List<String> rawLines)
	{
		Set<String> lines = new LinkedHashSet<>();
		for (String rawLine : rawLines)
		{
			if (rawLine == null)
			{
				continue;
			}
			for (String line : rawLine.split("(?i)<br>"))
			{
				if (!line.isBlank())
				{
					lines.add(line.trim());
				}
			}
		}
		if (lines.isEmpty())
		{
			return null;
		}
		if (lines.size() == 1)
		{
			return lines.iterator().next();
		}
		List<String> escapedLines = new ArrayList<>(lines.size());
		for (String line : lines)
		{
			escapedLines.add(escapeHtml(line));
		}
		return "<html>" + String.join("<br>", escapedLines) + "</html>";
	}

	private static TooltipData loadTooltipData()
	{
		Map<Long, List<Entry>> index = new HashMap<>();
		Map<Long, Marker> markers = new HashMap<>();
		try (InputStream in = WorldMapTooltipIndex.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				return new TooltipData(Map.of(), List.of());
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (line.isBlank() || line.startsWith("#"))
					{
						continue;
					}
					String[] parts = line.split("\t", -1);
					if (parts.length < 5)
					{
						continue;
					}
					try
					{
						String tooltip = parts[1].trim();
						int x = Integer.parseInt(parts[2]);
						int y = Integer.parseInt(parts[3]);
						int z = Integer.parseInt(parts[4]);
						if (!tooltip.isBlank())
						{
							long key = key(x, y, z);
							index.computeIfAbsent(key, ignored -> new ArrayList<>())
								.add(new Entry(tooltip));
							markers.putIfAbsent(key, new Marker(x, y, z));
						}
					}
					catch (NumberFormatException ignored)
					{
						// Ignore malformed optional data rows.
					}
				}
			}
		}
		catch (Exception ignored)
		{
			return new TooltipData(Map.of(), List.of());
		}
		return new TooltipData(index, List.copyOf(markers.values()));
	}

	private static Map<Integer, String> loadIconLabels()
	{
		Map<Integer, String> labels = new HashMap<>();
		try (InputStream in = WorldMapTooltipIndex.class.getResourceAsStream(ICON_LABEL_RESOURCE))
		{
			if (in == null)
			{
				return Map.of();
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (line.isBlank() || line.startsWith("#"))
					{
						continue;
					}
					String[] parts = line.split("\t", -1);
					if (parts.length < 2)
					{
						continue;
					}
					try
					{
						int spriteId = Integer.parseInt(parts[0]);
						String label = parts[1].trim();
						if (!label.isBlank())
						{
							labels.put(spriteId, label);
						}
					}
					catch (NumberFormatException ignored)
					{
						// Ignore malformed optional data rows.
					}
				}
			}
		}
		catch (Exception ignored)
		{
			return Map.of();
		}
		return labels;
	}

	private static String formatTooltip(List<Entry> entries)
	{
		List<String> lines = new ArrayList<>(entries.size());
		for (Entry entry : entries)
		{
			lines.add(entry.tooltip());
		}
		return formatLines(lines);
	}

	private static String escapeHtml(String value)
	{
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}

	private static long key(int x, int y, int z)
	{
		return (((long) z) << 56)
			^ (((long) x & 0x0FFFFFFFL) << 28)
			^ ((long) y & 0x0FFFFFFFL);
	}

	private record Entry(String tooltip)
	{
	}

	record Marker(int x, int y, int z)
	{
	}

	private record TooltipData(Map<Long, List<Entry>> byTile, List<Marker> markers)
	{
	}
}
