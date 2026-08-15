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

import net.runelite.cache.region.Region;

final class TerrainHeightMap
{
	private final TerrainRegionContext regionContext;
	private final int plane;

	TerrainHeightMap(TerrainRegionContext regionContext, int plane)
	{
		this.regionContext = regionContext;
		this.plane = plane;
	}

	float worldHeightAt(float x, float y)
	{
		return SceneScale.worldYFromSceneHeight(sceneHeightAt(x, y));
	}

	float sceneHeightAt(float x, float y)
	{
		x = clamp(x, -TerrainRegionContext.BORDER_TILES, Region.X + TerrainRegionContext.BORDER_TILES);
		y = clamp(y, -TerrainRegionContext.BORDER_TILES, Region.Y + TerrainRegionContext.BORDER_TILES);
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int x1 = x0 + 1;
		int y1 = y0 + 1;
		float tx = x - x0;
		float ty = y - y0;
		float h00 = rawSceneHeight(x0, y0);
		float h10 = rawSceneHeight(x1, y0);
		float h01 = rawSceneHeight(x0, y1);
		float h11 = rawSceneHeight(x1, y1);
		float hx0 = lerp(h00, h10, tx);
		float hx1 = lerp(h01, h11, tx);
		return lerp(hx0, hx1, ty);
	}

	int rawSceneHeight(int x, int y)
	{
		return regionContext.tileHeight(plane, x, y);
	}

	int meanSceneHeight(int x, int y)
	{
		return (rawSceneHeight(x, y)
			+ rawSceneHeight(x + 1, y)
			+ rawSceneHeight(x + 1, y + 1)
			+ rawSceneHeight(x, y + 1)) >> 2;
	}

	int[] cornerSceneHeights(int x, int y)
	{
		return new int[]{
			rawSceneHeight(x, y),
			rawSceneHeight(x + 1, y),
			rawSceneHeight(x + 1, y + 1),
			rawSceneHeight(x, y + 1)
		};
	}

	private static float lerp(float a, float b, float t)
	{
		return a + (b - a) * t;
	}

	private static float clamp(float value, float min, float max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
