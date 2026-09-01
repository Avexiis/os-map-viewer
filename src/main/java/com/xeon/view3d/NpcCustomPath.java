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
package com.xeon.view3d;

import com.xeon.model.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NpcCustomPath
{
	private NpcCustomPath()
	{
	}

	static boolean parseEnabled(String value)
	{
		if (value == null || value.isBlank())
		{
			return false;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT))
		{
			case "true", "yes", "y", "1", "enabled", "enable" -> true;
			default -> false;
		};
	}

	static List<Point> parse(String value)
	{
		if (value == null || value.isBlank())
		{
			return List.of();
		}

		List<Point> points = new ArrayList<>();
		String[] parts = value.split(";", -1);
		for (String part : parts)
		{
			Point point = parsePoint(part);
			if (point != null)
			{
				points.add(point);
			}
		}
		return normalize(points);
	}

	static String format(List<Point> points)
	{
		List<Point> normalized = normalize(points);
		if (normalized.isEmpty())
		{
			return "";
		}

		StringBuilder builder = new StringBuilder();
		for (Point point : normalized)
		{
			if (builder.length() > 0)
			{
				builder.append(';');
			}
			builder
				.append(point.worldX())
				.append(',')
				.append(point.worldY())
				.append(',')
				.append(point.plane());
		}
		return builder.toString();
	}

	static List<Point> normalize(List<Point> points)
	{
		if (points == null || points.isEmpty())
		{
			return List.of();
		}
		List<Point> normalized = new ArrayList<>(points.size());
		for (Point point : points)
		{
			if (point != null)
			{
				normalized.add(new Point(point.worldX(), point.worldY(), point.plane()));
			}
		}
		return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
	}

	static List<Point> fromTiles(List<Tile> tiles)
	{
		if (tiles == null || tiles.isEmpty())
		{
			return List.of();
		}
		List<Point> points = new ArrayList<>(tiles.size());
		for (Tile tile : tiles)
		{
			if (tile != null)
			{
				points.add(new Point(tile.x, tile.y, tile.z));
			}
		}
		return normalize(points);
	}

	static List<Tile> toTiles(List<Point> points)
	{
		List<Point> normalized = normalize(points);
		if (normalized.isEmpty())
		{
			return List.of();
		}
		List<Tile> tiles = new ArrayList<>(normalized.size());
		for (Point point : normalized)
		{
			tiles.add(point.tile());
		}
		return List.copyOf(tiles);
	}

	static boolean trueLoop(List<Point> points)
	{
		List<Point> normalized = normalize(points);
		return normalized.size() >= 2 && normalized.get(0).equals(normalized.get(normalized.size() - 1));
	}

	private static Point parsePoint(String value)
	{
		if (value == null || value.isBlank())
		{
			return null;
		}
		String[] fields = value.trim().split(",", -1);
		if (fields.length != 3)
		{
			return null;
		}
		try
		{
			return new Point(
				Integer.parseInt(fields[0].trim()),
				Integer.parseInt(fields[1].trim()),
				Integer.parseInt(fields[2].trim())
			);
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	record Point(int worldX, int worldY, int plane)
	{
		Point
		{
			plane = Math.max(0, Math.min(3, plane));
		}

		Tile tile()
		{
			return new Tile(worldX, worldY, plane);
		}

		boolean sameTile(Tile tile)
		{
			return tile != null && worldX == tile.x && worldY == tile.y && plane == tile.z;
		}
	}
}
