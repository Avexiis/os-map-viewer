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

final class SceneTileFlags
{
	private static final int BRIDGE_TILE = 2;
	private static final int RENDER_ON_LOWER_Z = 8;
	private static final int DISABLE_RENDERING = 16;
	private static final int HIDDEN_RENDER_FLAGS = RENDER_ON_LOWER_Z | DISABLE_RENDERING;

	private SceneTileFlags()
	{
	}

	static int visualPlane(Region region, int displayPlane, int x, int y)
	{
		return displayPlane + (isBridge(region, x, y) ? 1 : 0);
	}

	static boolean canRenderBaseLayer(Region region, int displayPlane, int x, int y)
	{
		return (tileSetting(region, displayPlane, x, y) & HIDDEN_RENDER_FLAGS) == 0;
	}

	static boolean renderOnLowerPlane(Region region, int sourcePlane, int x, int y)
	{
		return sourcePlane >= 0
			&& sourcePlane < Region.Z
			&& (tileSetting(region, sourcePlane, x, y) & RENDER_ON_LOWER_Z) != 0;
	}

	static boolean visibleOnDisplayPlane(Region region, int displayPlane, int sourcePlane, int x, int y)
	{
		int visualPlane = visualPlane(region, displayPlane, x, y);
		if (visualPlane >= Region.Z)
		{
			return false;
		}
		if (sourcePlane == visualPlane && canRenderBaseLayer(region, displayPlane, x, y))
		{
			return true;
		}

		int lowerSourcePlane = visualPlane + 1;
		return sourcePlane == lowerSourcePlane
			&& lowerSourcePlane < Region.Z
			&& renderOnLowerPlane(region, lowerSourcePlane, x, y);
	}

	private static boolean isBridge(Region region, int x, int y)
	{
		return (tileSetting(region, 1, x, y) & BRIDGE_TILE) != 0;
	}

	private static int tileSetting(Region region, int plane, int x, int y)
	{
		if (plane < 0 || plane >= Region.Z || x < 0 || y < 0 || x >= Region.X || y >= Region.Y)
		{
			return 0;
		}
		return region.getTileSetting(plane, x, y);
	}
}
