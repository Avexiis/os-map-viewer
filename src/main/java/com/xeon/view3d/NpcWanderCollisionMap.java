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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.runelite.cache.region.Region;

final class NpcWanderCollisionMap
{
	private static final String COLLISION_RESOURCE = "/com/xeon/application/data/collision-map.zip";
	private static final int REGION_SIZE = Region.X;
	private static final int FLAG_COUNT = 2;
	private static final int NORTH_FLAG = 0;
	private static final int EAST_FLAG = 1;
	private static final int BITS_PER_PLANE = REGION_SIZE * REGION_SIZE * FLAG_COUNT;
	private static final int WORDS_PER_PLANE = BITS_PER_PLANE / Long.SIZE;
	private static final int REGION_MASK = REGION_SIZE - 1;
	private static volatile NpcWanderCollisionMap defaultMap;

	private final boolean available;
	private final RegionExtent extents;
	private final int widthInclusive;
	private final byte[] regionPlaneCounts;
	private final int[] regionWordOffset;
	private final long[] flags;

	private NpcWanderCollisionMap()
	{
		available = false;
		extents = new RegionExtent(0, 0, -1, -1);
		widthInclusive = 0;
		regionPlaneCounts = new byte[0];
		regionWordOffset = new int[0];
		flags = new long[0];
	}

	private NpcWanderCollisionMap(Map<Integer, byte[]> compressedRegions, RegionExtent extents)
	{
		this.available = true;
		this.extents = Objects.requireNonNull(extents, "extents");
		widthInclusive = extents.width() + 1;
		int regionCount = widthInclusive * (extents.height() + 1);
		regionPlaneCounts = new byte[regionCount];
		regionWordOffset = new int[regionCount];
		Arrays.fill(regionWordOffset, -1);

		Map<Integer, long[]> regionWords = new HashMap<>(compressedRegions.size());
		int totalWords = 0;
		for (Map.Entry<Integer, byte[]> entry : compressedRegions.entrySet())
		{
			int regionX = unpackX(entry.getKey());
			int regionY = unpackY(entry.getKey());
			int index = regionIndex(regionX, regionY);
			if (index < 0 || index >= regionWordOffset.length)
			{
				continue;
			}

			byte[] bytes = entry.getValue();
			BitSet bits = BitSet.valueOf(bytes);
			int planeCount = Math.min(Region.Z, (bytes.length * Byte.SIZE + BITS_PER_PLANE - 1) / BITS_PER_PLANE);
			if (planeCount <= 0)
			{
				continue;
			}
			regionPlaneCounts[index] = (byte) planeCount;
			regionWordOffset[index] = totalWords;
			regionWords.put(index, bits.toLongArray());
			totalWords += planeCount * WORDS_PER_PLANE;
		}

		flags = new long[totalWords];
		for (Map.Entry<Integer, long[]> entry : regionWords.entrySet())
		{
			long[] words = entry.getValue();
			int offset = regionWordOffset[entry.getKey()];
			System.arraycopy(words, 0, flags, offset, Math.min(words.length, flags.length - offset));
		}
	}

	static NpcWanderCollisionMap loadDefault()
	{
		NpcWanderCollisionMap map = defaultMap;
		if (map != null)
		{
			return map;
		}
		synchronized (NpcWanderCollisionMap.class)
		{
			if (defaultMap == null)
			{
				defaultMap = loadResource();
			}
			return defaultMap;
		}
	}

	boolean canStand(int worldX, int worldY, int plane, int size)
	{
		if (!available)
		{
			return true;
		}
		int footprint = Math.max(1, size);
		for (int x = worldX; x < worldX + footprint; x++)
		{
			for (int y = worldY; y < worldY + footprint; y++)
			{
				if (!hasData(x, y, plane) || isBlocked(x, y, plane))
				{
					return false;
				}
			}
		}
		return true;
	}

	boolean canStep(int worldX, int worldY, int plane, int size, int dx, int dy)
	{
		if (dx == 0 && dy == 0)
		{
			return true;
		}
		if (!available)
		{
			return true;
		}
		if (!hasData(worldX, worldY, plane))
		{
			return false;
		}

		int footprint = Math.max(1, size);
		int targetX = worldX + dx;
		int targetY = worldY + dy;
		if (!canStand(targetX, targetY, plane, footprint))
		{
			return false;
		}
		if (dx != 0 && !canStepCardinal(worldX, worldY, plane, footprint, dx, 0))
		{
			return false;
		}
		if (dy != 0 && !canStepCardinal(worldX, worldY, plane, footprint, 0, dy))
		{
			return false;
		}
		if (dx != 0 && dy != 0)
		{
			return canStepCardinal(worldX, targetY, plane, footprint, dx, 0)
				&& canStepCardinal(targetX, worldY, plane, footprint, 0, dy);
		}
		return true;
	}

	private boolean canStepCardinal(int worldX, int worldY, int plane, int size, int dx, int dy)
	{
		if (dx > 0)
		{
			int edgeX = worldX + size - 1;
			for (int y = worldY; y < worldY + size; y++)
			{
				if (!east(edgeX, y, plane))
				{
					return false;
				}
			}
		}
		else if (dx < 0)
		{
			for (int y = worldY; y < worldY + size; y++)
			{
				if (!west(worldX, y, plane))
				{
					return false;
				}
			}
		}
		else if (dy > 0)
		{
			int edgeY = worldY + size - 1;
			for (int x = worldX; x < worldX + size; x++)
			{
				if (!north(x, edgeY, plane))
				{
					return false;
				}
			}
		}
		else if (dy < 0)
		{
			for (int x = worldX; x < worldX + size; x++)
			{
				if (!south(x, worldY, plane))
				{
					return false;
				}
			}
		}
		return true;
	}

	private boolean isBlocked(int worldX, int worldY, int plane)
	{
		return !north(worldX, worldY, plane)
			&& !south(worldX, worldY, plane)
			&& !east(worldX, worldY, plane)
			&& !west(worldX, worldY, plane);
	}

	private boolean north(int worldX, int worldY, int plane)
	{
		return get(worldX, worldY, plane, NORTH_FLAG);
	}

	private boolean south(int worldX, int worldY, int plane)
	{
		return north(worldX, worldY - 1, plane);
	}

	private boolean east(int worldX, int worldY, int plane)
	{
		return get(worldX, worldY, plane, EAST_FLAG);
	}

	private boolean west(int worldX, int worldY, int plane)
	{
		return east(worldX - 1, worldY, plane);
	}

	private boolean hasData(int worldX, int worldY, int plane)
	{
		int index = regionIndex(Math.floorDiv(worldX, REGION_SIZE), Math.floorDiv(worldY, REGION_SIZE));
		return index >= 0
			&& index < regionWordOffset.length
			&& regionWordOffset[index] >= 0
			&& plane >= 0
			&& plane < regionPlaneCounts[index];
	}

	private boolean get(int worldX, int worldY, int plane, int flag)
	{
		int index = regionIndex(Math.floorDiv(worldX, REGION_SIZE), Math.floorDiv(worldY, REGION_SIZE));
		if (index < 0 || index >= regionWordOffset.length)
		{
			return false;
		}

		int wordOffset = regionWordOffset[index];
		if (wordOffset < 0 || plane < 0 || plane >= regionPlaneCounts[index])
		{
			return false;
		}

		int localBit = (plane * REGION_SIZE * REGION_SIZE
			+ (worldY & REGION_MASK) * REGION_SIZE
			+ (worldX & REGION_MASK)) * FLAG_COUNT + flag;
		return (flags[wordOffset + (localBit >> 6)] >>> (localBit & 63) & 1L) != 0L;
	}

	private int regionIndex(int regionX, int regionY)
	{
		return regionX < extents.minX()
			|| regionY < extents.minY()
			|| regionX > extents.maxX()
			|| regionY > extents.maxY()
			? -1
			: (regionX - extents.minX()) + (regionY - extents.minY()) * widthInclusive;
	}

	private static NpcWanderCollisionMap loadResource()
	{
		try (InputStream resource = NpcWanderCollisionMap.class.getResourceAsStream(COLLISION_RESOURCE))
		{
			if (resource == null)
			{
				System.err.println("NPC wander collision resource is missing: " + COLLISION_RESOURCE);
				return new NpcWanderCollisionMap();
			}
			return fromZipStream(resource);
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load NPC wander collision map: " + ex.getMessage());
			return new NpcWanderCollisionMap();
		}
	}

	private static NpcWanderCollisionMap fromZipStream(InputStream resource) throws IOException
	{
		Map<Integer, byte[]> compressedRegions = new HashMap<>();
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		try (ZipInputStream in = new ZipInputStream(resource))
		{
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null)
			{
				String[] name = entry.getName().split("_");
				if (name.length < 2)
				{
					continue;
				}
				int x = Integer.parseInt(name[0]);
				int y = Integer.parseInt(name[1]);
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
				compressedRegions.put(packPosition(x, y), readAllBytes(in));
			}
		}
		if (compressedRegions.isEmpty())
		{
			return new NpcWanderCollisionMap();
		}
		return new NpcWanderCollisionMap(compressedRegions, new RegionExtent(minX, minY, maxX, maxY));
	}

	private static byte[] readAllBytes(InputStream in) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) != -1)
		{
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static int unpackX(int position)
	{
		return position & 0xFFFF;
	}

	private static int unpackY(int position)
	{
		return position >> 16 & 0xFFFF;
	}

	private static int packPosition(int x, int y)
	{
		return x & 0xFFFF | (y & 0xFFFF) << 16;
	}

	private record RegionExtent(int minX, int minY, int maxX, int maxY)
	{
		private int width()
		{
			return maxX - minX;
		}

		private int height()
		{
			return maxY - minY;
		}
	}
}
