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
package com.xeon.plugins.groundmarkers;

import com.xeon.io.Paths;
import com.xeon.model.Tile;

import java.awt.*;
import java.util.Locale;
import java.util.Objects;

public final class GroundMarker
{
	public static final String DEFAULT_COLOR = "#FFFFFF00";

	public int regionX;
	public int z;
	public int regionId;
	public String label;
	public int regionY;
	public String color = DEFAULT_COLOR;

	public GroundMarker()
	{
	}

	public GroundMarker(int regionX, int z, int regionId, String label, int regionY, String color)
	{
		this.regionX = clampLocal(regionX);
		this.z = Math.max(0, Math.min(3, z));
		this.regionId = regionId;
		this.label = normalizeLabel(label);
		this.regionY = clampLocal(regionY);
		this.color = normalizeColor(color);
	}

	public static GroundMarker fromTile(Tile tile, String label, String color)
	{
		int regionId = regionId(tile);
		return new GroundMarker(
			localX(tile),
			tile.z,
			regionId,
			label,
			localY(tile),
			color
		);
	}

	public GroundMarker copy()
	{
		return new GroundMarker(regionX, z, regionId, label, regionY, color);
	}

	public Tile tile()
	{
		return new Tile(worldX(), worldY(), z);
	}

	public int worldX()
	{
		return regionX(regionId) * Paths.REGION_TILE_SIZE + regionX;
	}

	public int worldY()
	{
		return regionY(regionId) * Paths.REGION_TILE_SIZE + regionY;
	}

	public MarkerKey key()
	{
		return new MarkerKey(regionId, regionX, regionY, z);
	}

	public String displayLabel()
	{
		if (label != null && !label.isBlank())
		{
			return label;
		}
		return "Region " + regionId + " @ " + regionX + "," + regionY + "," + z;
	}

	public Color awtColor()
	{
		String normalized = normalizeColor(color);
		String hex = normalized.substring(1);
		long argb = Long.parseLong(hex, 16);
		return new Color((int) argb, true);
	}

	public static int regionId(Tile tile)
	{
		return (Math.floorDiv(tile.x, Paths.REGION_TILE_SIZE) << 8)
			| Math.floorDiv(tile.y, Paths.REGION_TILE_SIZE);
	}

	public static int regionX(int regionId)
	{
		return regionId >> 8;
	}

	public static int regionY(int regionId)
	{
		return regionId & 0xFF;
	}

	public static int localX(Tile tile)
	{
		return Math.floorMod(tile.x, Paths.REGION_TILE_SIZE);
	}

	public static int localY(Tile tile)
	{
		return Math.floorMod(tile.y, Paths.REGION_TILE_SIZE);
	}

	public static String normalizeColor(String raw)
	{
		if (raw == null || raw.isBlank())
		{
			return DEFAULT_COLOR;
		}
		String value = raw.trim();
		if (value.startsWith("0x") || value.startsWith("0X"))
		{
			value = value.substring(2);
		}
		else if (value.startsWith("#"))
		{
			value = value.substring(1);
		}
		if (value.length() == 6)
		{
			value = "FF" + value;
		}
		if (value.length() != 8 || !value.matches("[0-9a-fA-F]{8}"))
		{
			try
			{
				return String.format("#%08X", Integer.decode(raw.trim()));
			}
			catch (RuntimeException ex)
			{
				return DEFAULT_COLOR;
			}
		}
		return "#" + value.toUpperCase(Locale.ROOT);
	}

	public static String normalizeLabel(String label)
	{
		if (label == null)
		{
			return null;
		}
		String trimmed = label.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public record MarkerKey(int regionId, int regionX, int regionY, int z)
	{
		public static MarkerKey fromTile(Tile tile)
		{
			return new MarkerKey(GroundMarker.regionId(tile), localX(tile), localY(tile), tile.z);
		}
	}

	private static int clampLocal(int value)
	{
		return Math.max(0, Math.min(Paths.REGION_TILE_SIZE - 1, value));
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof GroundMarker that))
		{
			return false;
		}
		return regionX == that.regionX
			&& z == that.z
			&& regionId == that.regionId
			&& regionY == that.regionY
			&& Objects.equals(label, that.label)
			&& Objects.equals(color, that.color);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(regionX, z, regionId, label, regionY, color);
	}

	@Override
	public String toString()
	{
		return displayLabel();
	}
}
