/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * Copyright (c) 2022-2023, dennisdev
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

import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;

final class HdosSceneLighting
{
	private static final int WALL = 0;
	private static final int WALL_TRI_CORNER = 1;
	private static final int WALL_RECT_CORNER = 3;
	private static final int NORMAL = 10;
	private static final int NORMAL_DIAGONAL = 11;
	private static final int LIGHT_DIR_X = -50;
	private static final int LIGHT_DIR_Y = -10;
	private static final int LIGHT_DIR_Z = -50;
	private static final int LIGHT_INTENSITY_BASE = 96;
	private static final int LIGHT_INTENSITY_FACTOR = 768;
	private static final int HEIGHT_SCALE = 65_536;
	private static final int WALL_OCCLUSION = 50;
	private static final int LOC_OCCLUSION = 15;

	private HdosSceneLighting()
	{
	}

	static int[][][] tileLightOcclusions(Region region, ObjectManager objectManager)
	{
		int[][][] occlusions = new int[Region.Z][Region.X + 1][Region.Y + 1];
		if (region == null || objectManager == null)
		{
			return occlusions;
		}

		for (Location location : region.getLocations())
		{
			Position position = location.getPosition();
			int level = position.getZ();
			if (level < 0 || level >= Region.Z)
			{
				continue;
			}
			int tileX = position.getX() - region.getBaseX();
			int tileY = position.getY() - region.getBaseY();
			if (tileX < 0 || tileY < 0 || tileX >= Region.X || tileY >= Region.Y)
			{
				continue;
			}

			ObjectDefinition definition = ObjectMeshBuilder.completionStateDefinition(
				objectManager,
				objectManager.getObject(location.getId())
			);
			if (definition == null || !definition.isShadow())
			{
				continue;
			}

			int rotation = location.getOrientation() & 3;
			int type = location.getType();
			switch (type)
			{
				case NORMAL, NORMAL_DIAGONAL -> addLocOcclusion(occlusions[level], definition, tileX, tileY, rotation);
				case WALL -> addWallOcclusion(occlusions[level], tileX, tileY, rotation);
				case WALL_TRI_CORNER, WALL_RECT_CORNER -> addCornerOcclusion(occlusions[level], tileX, tileY, rotation);
				default ->
				{
				}
			}
		}
		return occlusions;
	}

	static int tileLight(TerrainHeightMap heightMap, int[][] occlusions, int x, int y)
	{
		int lightMagnitude = (int) Math.sqrt(LIGHT_DIR_X * LIGHT_DIR_X
			+ LIGHT_DIR_Y * LIGHT_DIR_Y
			+ LIGHT_DIR_Z * LIGHT_DIR_Z);
		int lightIntensity = Math.max(1, lightMagnitude * LIGHT_INTENSITY_FACTOR >> 8);
		int deltaX = heightMap.rawSceneHeight(x + 1, y) - heightMap.rawSceneHeight(x - 1, y);
		int deltaY = heightMap.rawSceneHeight(x, y + 1) - heightMap.rawSceneHeight(x, y - 1);
		int normalLength = (int) Math.sqrt(deltaY * deltaY + deltaX * deltaX + HEIGHT_SCALE);
		if (normalLength <= 0)
		{
			normalLength = 1;
		}
		int normalX = (deltaX << 8) / normalLength;
		int normalY = HEIGHT_SCALE / normalLength;
		int normalZ = (deltaY << 8) / normalLength;
		int dot = normalX * LIGHT_DIR_X + normalY * LIGHT_DIR_Y + normalZ * LIGHT_DIR_Z;
		int sunLight = dot / lightIntensity + LIGHT_INTENSITY_BASE;
		return sunLight - lightOcclusion(occlusions, x, y);
	}

	private static int lightOcclusion(int[][] occlusions, int x, int y)
	{
		if (occlusions == null)
		{
			return 0;
		}
		return occlusionAt(occlusions, x - 1, y) / 4
			+ occlusionAt(occlusions, x, y - 1) / 4
			+ occlusionAt(occlusions, x + 1, y) / 8
			+ occlusionAt(occlusions, x, y + 1) / 8
			+ occlusionAt(occlusions, x, y) / 2;
	}

	private static void addLocOcclusion(int[][] occlusions, ObjectDefinition definition, int tileX, int tileY, int rotation)
	{
		int sizeX = Math.max(1, definition.getSizeX());
		int sizeY = Math.max(1, definition.getSizeY());
		if ((rotation & 1) == 1)
		{
			int swap = sizeX;
			sizeX = sizeY;
			sizeY = swap;
		}

		for (int x = tileX; x <= tileX + sizeX; x++)
		{
			for (int y = tileY; y <= tileY + sizeY; y++)
			{
				setMaxOcclusion(occlusions, x, y, LOC_OCCLUSION);
			}
		}
	}

	private static void addWallOcclusion(int[][] occlusions, int tileX, int tileY, int rotation)
	{
		switch (rotation)
		{
			case 0 ->
			{
				setMaxOcclusion(occlusions, tileX, tileY, WALL_OCCLUSION);
				setMaxOcclusion(occlusions, tileX, tileY + 1, WALL_OCCLUSION);
			}
			case 1 ->
			{
				setMaxOcclusion(occlusions, tileX, tileY + 1, WALL_OCCLUSION);
				setMaxOcclusion(occlusions, tileX + 1, tileY + 1, WALL_OCCLUSION);
			}
			case 2 ->
			{
				setMaxOcclusion(occlusions, tileX + 1, tileY, WALL_OCCLUSION);
				setMaxOcclusion(occlusions, tileX + 1, tileY + 1, WALL_OCCLUSION);
			}
			case 3 ->
			{
				setMaxOcclusion(occlusions, tileX, tileY, WALL_OCCLUSION);
				setMaxOcclusion(occlusions, tileX + 1, tileY, WALL_OCCLUSION);
			}
			default ->
			{
			}
		}
	}

	private static void addCornerOcclusion(int[][] occlusions, int tileX, int tileY, int rotation)
	{
		switch (rotation)
		{
			case 0 -> setMaxOcclusion(occlusions, tileX, tileY + 1, WALL_OCCLUSION);
			case 1 -> setMaxOcclusion(occlusions, tileX + 1, tileY + 1, WALL_OCCLUSION);
			case 2 -> setMaxOcclusion(occlusions, tileX + 1, tileY, WALL_OCCLUSION);
			case 3 -> setMaxOcclusion(occlusions, tileX, tileY, WALL_OCCLUSION);
			default ->
			{
			}
		}
	}

	private static int occlusionAt(int[][] occlusions, int x, int y)
	{
		if (x < 0 || y < 0 || x >= occlusions.length || y >= occlusions[x].length)
		{
			return 0;
		}
		return occlusions[x][y];
	}

	private static void setMaxOcclusion(int[][] occlusions, int x, int y, int value)
	{
		if (x < 0 || y < 0 || x >= occlusions.length || y >= occlusions[x].length)
		{
			return;
		}
		if (value > occlusions[x][y])
		{
			occlusions[x][y] = value;
		}
	}
}
