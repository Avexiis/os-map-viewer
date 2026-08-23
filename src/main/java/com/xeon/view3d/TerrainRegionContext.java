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

import java.util.Map;
import net.runelite.cache.region.Region;

final class TerrainRegionContext
{
	static final int BORDER_TILES = 6;

	private final Region center;
	private final Map<Integer, Region> regions;

	TerrainRegionContext(Region center, Map<Integer, Region> regions)
	{
		this.center = center;
		this.regions = Map.copyOf(regions);
	}

	Region center()
	{
		return center;
	}

	int regionId()
	{
		return center.getRegionID();
	}

	int regionX()
	{
		return center.getRegionX();
	}

	int regionY()
	{
		return center.getRegionY();
	}

	int tileHeight(int plane, int x, int y)
	{
		Sample sample = sample(x, y);
		return sample.region().getTileHeight(plane, sample.x(), sample.y());
	}

	int tileSetting(int plane, int x, int y)
	{
		Sample sample = sample(x, y);
		return sample.region().getTileSetting(plane, sample.x(), sample.y());
	}

	int underlayId(int plane, int x, int y)
	{
		Sample sample = sample(x, y);
		return sample.region().getUnderlayId(plane, sample.x(), sample.y());
	}

	int overlayId(int plane, int x, int y)
	{
		Sample sample = sample(x, y);
		return sample.region().getOverlayId(plane, sample.x(), sample.y());
	}

	int overlayPath(int plane, int x, int y)
	{
		Sample sample = sample(x, y);
		return sample.region().getOverlayPath(plane, sample.x(), sample.y());
	}

	int overlayRotation(int plane, int x, int y)
	{
		Sample sample = sample(x, y);
		return sample.region().getOverlayRotation(plane, sample.x(), sample.y());
	}

	private Sample sample(int x, int y)
	{
		int offsetX = Math.floorDiv(x, Region.X);
		int offsetY = Math.floorDiv(y, Region.Y);
		int localX = Math.floorMod(x, Region.X);
		int localY = Math.floorMod(y, Region.Y);
		Region region = regions.get(TerrainScene.regionId(regionX() + offsetX, regionY() + offsetY));
		if (region == null)
		{
			region = center;
			localX = clamp(x, 0, Region.X - 1);
			localY = clamp(y, 0, Region.Y - 1);
		}
		return new Sample(region, localX, localY);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private record Sample(
		Region region,
		int x,
		int y
	)
	{
	}
}
