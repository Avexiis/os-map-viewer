/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice and
 *    this list of conditions.
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
package com.xeon.io;

public record Viewer3DState(
	double worldTileX,
	double worldTileY,
	double cameraY,
	int plane,
	float yawDegrees,
	float pitchDegrees,
	float fovDegrees,
	int antialiasingSamples,
	int viewDistanceRegions,
	int maxVisiblePlane,
	boolean pluginOverlaysOnTop,
	boolean npcsVisible,
	boolean npcOutlinesVisible,
	boolean npcHoverTextVisible
)
{
	public static final int DEFAULT_VIEW_DISTANCE_REGIONS = 3;
	public static final int MIN_VIEW_DISTANCE_REGIONS = 2;
	public static final int MAX_VIEW_DISTANCE_REGIONS = 5;

	public Viewer3DState
	{
		viewDistanceRegions = clampViewDistanceRegions(viewDistanceRegions);
		maxVisiblePlane = clampMaxVisiblePlane(maxVisiblePlane);
	}

	public Viewer3DState(
		double worldTileX,
		double worldTileY,
		double cameraY,
		int plane,
		float yawDegrees,
		float pitchDegrees,
		float fovDegrees,
		int antialiasingSamples
	)
	{
		this(
			worldTileX,
			worldTileY,
			cameraY,
			plane,
			yawDegrees,
			pitchDegrees,
			fovDegrees,
			antialiasingSamples,
			DEFAULT_VIEW_DISTANCE_REGIONS,
			0,
			false,
			true,
			true,
			true
		);
	}

	public Viewer3DState(
		double worldTileX,
		double worldTileY,
		double cameraY,
		int plane,
		float yawDegrees,
		float pitchDegrees,
		float fovDegrees,
		int antialiasingSamples,
		int viewDistanceRegions,
		boolean pluginOverlaysOnTop
	)
	{
		this(
			worldTileX,
			worldTileY,
			cameraY,
			plane,
			yawDegrees,
			pitchDegrees,
			fovDegrees,
			antialiasingSamples,
			viewDistanceRegions,
			0,
			pluginOverlaysOnTop,
			true,
			true,
			true
		);
	}

	public boolean isValid()
	{
		return finite(worldTileX) && finite(worldTileY) && finite(cameraY)
			&& finite(yawDegrees) && finite(pitchDegrees) && finite(fovDegrees)
			&& plane >= 0 && plane <= 3
			&& antialiasingSamples >= 0
			&& viewDistanceRegions >= MIN_VIEW_DISTANCE_REGIONS
			&& viewDistanceRegions <= MAX_VIEW_DISTANCE_REGIONS
			&& maxVisiblePlane >= 0 && maxVisiblePlane <= 3
			&& isWorldTileInAtlas();
	}

	public static int clampViewDistanceRegions(int value)
	{
		if (value <= 0)
		{
			return DEFAULT_VIEW_DISTANCE_REGIONS;
		}
		return Math.max(MIN_VIEW_DISTANCE_REGIONS, Math.min(MAX_VIEW_DISTANCE_REGIONS, value));
	}

	public static int clampMaxVisiblePlane(int value)
	{
		return Math.max(0, Math.min(3, value));
	}

	public int regionId()
	{
		return (Math.floorDiv(tileX(), Paths.REGION_TILE_SIZE) << 8)
			| Math.floorDiv(tileY(), Paths.REGION_TILE_SIZE);
	}

	public int tileX()
	{
		return (int) Math.floor(worldTileX);
	}

	public int tileY()
	{
		return (int) Math.floor(worldTileY);
	}

	private boolean isWorldTileInAtlas()
	{
		int x = tileX();
		int y = tileY();
		return x >= Paths.MIN_RX * Paths.REGION_TILE_SIZE
			&& x < (Paths.MAX_RX + 1) * Paths.REGION_TILE_SIZE
			&& y >= Paths.MIN_RY * Paths.REGION_TILE_SIZE
			&& y < (Paths.MAX_RY + 1) * Paths.REGION_TILE_SIZE;
	}

	private static boolean finite(double value)
	{
		return Double.isFinite(value);
	}
}
