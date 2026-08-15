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

final class SceneScale
{
	static final int SCENE_UNITS_PER_TILE = 128;
	static final float REGION_CENTER_TILES = 32.0f;
	static final float SCENE_TO_WORLD = 1.0f / SCENE_UNITS_PER_TILE;
	static final float CAMERA_FOV_RADIANS = (float) Math.toRadians(68.0);
	static final float CAMERA_NEAR_PLANE = 0.5f;
	static final float CAMERA_FAR_PLANE = 260.0f;
	static final boolean MIRRORS_WORLD_Z = true;

	private SceneScale()
	{
	}

	static float worldXFromTile(float tileX)
	{
		return tileX - REGION_CENTER_TILES;
	}

	static float worldZFromTile(float tileY)
	{
		return REGION_CENTER_TILES - tileY;
	}

	static float worldXFromScene(float sceneX)
	{
		return worldXFromTile(sceneX * SCENE_TO_WORLD);
	}

	static float worldYFromSceneHeight(float sceneHeight)
	{
		return -sceneHeight * SCENE_TO_WORLD;
	}

	static float worldZFromScene(float sceneY)
	{
		return worldZFromTile(sceneY * SCENE_TO_WORLD);
	}

	static float tileXFromWorld(float worldX)
	{
		return worldX + REGION_CENTER_TILES;
	}

	static float tileYFromWorld(float worldZ)
	{
		return REGION_CENTER_TILES - worldZ;
	}
}
