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

final class TerrainLightMap
{
	private static final int BASE_LIGHT = 96;
	private static final int DIFFUSION = 768;
	private static final int LIGHT_X = -50;
	private static final int LIGHT_Y = -10;
	private static final int LIGHT_Z = -50;
	private static final float FLAT_LIGHT = 84.0f;
	private static final float MIN_MULTIPLIER = 0.62f;
	private static final float MAX_MULTIPLIER = 1.32f;

	private final Region region;
	private final int plane;
	private final float[][] lights = new float[Region.X + 1][Region.Y + 1];

	TerrainLightMap(Region region, int plane)
	{
		this.region = region;
		this.plane = plane;
		build();
	}

	int apply(int rgb, float x, float y)
	{
		float multiplier = clamp(lightAt(x, y) / FLAT_LIGHT, MIN_MULTIPLIER, MAX_MULTIPLIER);
		return scale(rgb, multiplier);
	}

	private void build()
	{
		int light = DIFFUSION * (int) Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z) >> 8;
		for (int x = 0; x <= Region.X; x++)
		{
			for (int y = 0; y <= Region.Y; y++)
			{
				int dhX = rawHeight(x + 1, y) - rawHeight(x - 1, y);
				int dhY = rawHeight(x, y + 1) - rawHeight(x, y - 1);
				int distance = (int) Math.sqrt(dhX * dhX + 0x10000 + dhY * dhY);
				int normalX = (dhX << 8) / distance;
				int normalY = 0x10000 / distance;
				int normalZ = (dhY << 8) / distance;
				lights[x][y] = BASE_LIGHT + (LIGHT_X * normalX + LIGHT_Y * normalY + LIGHT_Z * normalZ) / light;
			}
		}
	}

	private float lightAt(float x, float y)
	{
		x = clamp(x, 0.0f, Region.X);
		y = clamp(y, 0.0f, Region.Y);
		int x0 = clamp((int) Math.floor(x), 0, Region.X);
		int y0 = clamp((int) Math.floor(y), 0, Region.Y);
		int x1 = clamp(x0 + 1, 0, Region.X);
		int y1 = clamp(y0 + 1, 0, Region.Y);
		float tx = x1 == x0 ? 0.0f : x - x0;
		float ty = y1 == y0 ? 0.0f : y - y0;
		float lx0 = lerp(lights[x0][y0], lights[x1][y0], tx);
		float lx1 = lerp(lights[x0][y1], lights[x1][y1], tx);
		return lerp(lx0, lx1, ty);
	}

	private int rawHeight(int x, int y)
	{
		return region.getTileHeight(plane, clamp(x, 0, Region.X - 1), clamp(y, 0, Region.Y - 1));
	}

	private static int scale(int rgb, float multiplier)
	{
		int red = scaleComponent((rgb >> 16) & 0xFF, multiplier);
		int green = scaleComponent((rgb >> 8) & 0xFF, multiplier);
		int blue = scaleComponent(rgb & 0xFF, multiplier);
		return red << 16 | green << 8 | blue;
	}

	private static int scaleComponent(int value, float multiplier)
	{
		return clamp(Math.round(value * multiplier), 0, 255);
	}

	private static float lerp(float a, float b, float t)
	{
		return a + (b - a) * t;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
