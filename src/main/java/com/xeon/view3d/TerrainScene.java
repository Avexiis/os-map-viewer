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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.region.Region;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class TerrainScene
{
	static final int REGION_SIZE = Region.X;
	private static final float EDGE_EPSILON = 0.01f;
	private static final float PICK_STEP = 0.25f;
	private static final float PICK_EPSILON = 0.08f;

	private final int originRegionX;
	private final int originRegionY;
	private final Map<Integer, TerrainMesh> meshes;
	private final SceneTextureSet textureSet;

	TerrainScene(int originRegionX, int originRegionY, Map<Integer, TerrainMesh> meshes)
	{
		this.originRegionX = originRegionX;
		this.originRegionY = originRegionY;
		this.meshes = Collections.unmodifiableMap(new LinkedHashMap<>(meshes));
		this.textureSet = firstTextureSet(this.meshes.values());
	}

	static TerrainScene empty(int originRegionX, int originRegionY)
	{
		return new TerrainScene(originRegionX, originRegionY, Map.of());
	}

	static TerrainScene single(TerrainMesh mesh)
	{
		return new TerrainScene(mesh.regionX(), mesh.regionY(), Map.of(mesh.regionId(), mesh));
	}

	static int regionId(int regionX, int regionY)
	{
		return regionX << 8 | regionY & 0xFF;
	}

	static int regionX(int regionId)
	{
		return regionId >> 8;
	}

	static int regionY(int regionId)
	{
		return regionId & 0xFF;
	}

	boolean isEmpty()
	{
		return meshes.isEmpty();
	}

	int loadedRegionCount()
	{
		return meshes.size();
	}

	Set<Integer> regionIds()
	{
		return meshes.keySet();
	}

	Collection<TerrainMesh> meshes()
	{
		return meshes.values();
	}

	TerrainMesh mesh(int regionId)
	{
		return meshes.get(regionId);
	}

	SceneTextureSet textureSet()
	{
		return textureSet;
	}

	float offsetX(TerrainMesh mesh)
	{
		return (mesh.regionX() - originRegionX) * (float) REGION_SIZE;
	}

	float offsetZ(TerrainMesh mesh)
	{
		return (originRegionY - mesh.regionY()) * (float) REGION_SIZE;
	}

	boolean containsRegion(int regionId)
	{
		return meshes.containsKey(regionId);
	}

	int regionIdForWorld(Vector3fc position)
	{
		return regionIdForWorld(position.x(), position.z());
	}

	int regionIdForWorld(float worldX, float worldZ)
	{
		return regionId(regionXForWorld(worldX), regionYForWorld(worldZ));
	}

	boolean containsWorldPosition(Vector3fc position)
	{
		return containsWorldPosition(position.x(), position.z());
	}

	boolean containsWorldPosition(float worldX, float worldZ)
	{
		TerrainMesh mesh = meshes.get(regionIdForWorld(worldX, worldZ));
		if (mesh == null)
		{
			return false;
		}

		float localX = localTileX(mesh, worldX);
		float localY = localTileY(mesh, worldZ);
		return localX >= 0.0f && localY >= 0.0f && localX < REGION_SIZE && localY < REGION_SIZE;
	}

	boolean renderableTileAtWorld(int plane, float worldX, float worldZ)
	{
		TerrainMesh mesh = meshes.get(regionIdForWorld(worldX, worldZ));
		if (mesh == null)
		{
			return false;
		}

		float localX = localTileX(mesh, worldX);
		float localY = localTileY(mesh, worldZ);
		if (localX < 0.0f || localY < 0.0f || localX >= REGION_SIZE || localY >= REGION_SIZE)
		{
			return false;
		}
		int tileX = clamp((int) Math.floor(localX), 0, REGION_SIZE - 1);
		int tileY = clamp((int) Math.floor(localY), 0, REGION_SIZE - 1);
		return mesh.renderableTileAt(plane, tileX, tileY);
	}

	float worldHeightAt(int plane, float worldX, float worldZ, float fallback)
	{
		TerrainMesh mesh = meshes.get(regionIdForWorld(worldX, worldZ));
		if (mesh == null)
		{
			return fallback;
		}

		float localX = localTileX(mesh, worldX);
		float localY = localTileY(mesh, worldZ);
		if (localX < 0.0f || localY < 0.0f || localX >= REGION_SIZE || localY >= REGION_SIZE)
		{
			return fallback;
		}
		return mesh.worldHeightAt(plane, localX, localY);
	}

	int cameraPlaneFor(float worldX, float worldY, float worldZ)
	{
		int selectedPlane = 0;
		float selectedHeight = Float.NEGATIVE_INFINITY;
		for (int plane = 0; plane < Region.Z; plane++)
		{
			if (!renderableTileAtWorld(plane, worldX, worldZ))
			{
				continue;
			}
			float height = worldHeightAt(plane, worldX, worldZ, Float.NEGATIVE_INFINITY);
			if (height <= worldY + 2.0f && height >= selectedHeight)
			{
				selectedHeight = height;
				selectedPlane = plane;
			}
		}
		return selectedPlane;
	}

	Vector3f clampMovement(Vector3fc previous, Vector3fc attempted, Vector3f destination)
	{
		if (isEmpty() || containsWorldPosition(attempted))
		{
			return destination.set(attempted);
		}

		float x = attempted.x();
		float y = attempted.y();
		float z = attempted.z();
		if (!containsWorldPosition(x, previous.z()))
		{
			x = clampXToLoadedEdge(previous.x(), x, previous.z());
		}
		if (!containsWorldPosition(x, z))
		{
			z = clampZToLoadedEdge(previous.z(), z, x);
		}
		if (!containsWorldPosition(x, z))
		{
			x = previous.x();
			z = previous.z();
		}
		return destination.set(x, y, z);
	}

	HoveredTile pickTile(Vector3fc origin, Vector3fc direction)
	{
		float[] previousDistances = new float[Region.Z];
		Arrays.fill(previousDistances, Float.NaN);
		for (float distance = PICK_STEP; distance <= SceneScale.CAMERA_FAR_PLANE; distance += PICK_STEP)
		{
			float worldX = origin.x() + direction.x() * distance;
			float worldY = origin.y() + direction.y() * distance;
			float worldZ = origin.z() + direction.z() * distance;
			TerrainMesh mesh = meshes.get(regionIdForWorld(worldX, worldZ));
			if (mesh == null)
			{
				Arrays.fill(previousDistances, Float.NaN);
				continue;
			}

			float localX = localTileX(mesh, worldX);
			float localY = localTileY(mesh, worldZ);
			if (localX < 0.0f || localY < 0.0f || localX >= REGION_SIZE || localY >= REGION_SIZE)
			{
				Arrays.fill(previousDistances, Float.NaN);
				continue;
			}

			int tileX = clamp((int) Math.floor(localX), 0, REGION_SIZE - 1);
			int tileY = clamp((int) Math.floor(localY), 0, REGION_SIZE - 1);
			for (int samplePlane = Region.Z - 1; samplePlane >= 0; samplePlane--)
			{
				if (!mesh.renderableTileAt(samplePlane, tileX, tileY))
				{
					previousDistances[samplePlane] = Float.NaN;
					continue;
				}

				float terrainY = mesh.worldHeightAt(samplePlane, localX, localY);
				float heightDistance = worldY - terrainY;
				float previousDistance = previousDistances[samplePlane];
				previousDistances[samplePlane] = heightDistance;
				if (Math.abs(heightDistance) <= PICK_EPSILON
					|| (!Float.isNaN(previousDistance) && crossedSurface(previousDistance, heightDistance)))
				{
					return new HoveredTile(
						mesh.regionId(),
						mesh.regionX() * REGION_SIZE + tileX,
						mesh.regionY() * REGION_SIZE + tileY,
						samplePlane,
						tileX,
						tileY
					);
				}
			}
		}
		return null;
	}

	private float clampXToLoadedEdge(float previousX, float attemptedX, float worldZ)
	{
		TerrainMesh mesh = meshes.get(regionIdForWorld(previousX, worldZ));
		if (mesh == null)
		{
			return previousX;
		}

		float offsetX = offsetX(mesh);
		if (attemptedX > previousX)
		{
			return offsetX + SceneScale.REGION_CENTER_TILES - EDGE_EPSILON;
		}
		return offsetX - SceneScale.REGION_CENTER_TILES + EDGE_EPSILON;
	}

	private float clampZToLoadedEdge(float previousZ, float attemptedZ, float worldX)
	{
		TerrainMesh mesh = meshes.get(regionIdForWorld(worldX, previousZ));
		if (mesh == null)
		{
			return previousZ;
		}

		float offsetZ = offsetZ(mesh);
		if (attemptedZ > previousZ)
		{
			return offsetZ + SceneScale.REGION_CENTER_TILES - EDGE_EPSILON;
		}
		return offsetZ - SceneScale.REGION_CENTER_TILES + EDGE_EPSILON;
	}

	private int regionXForWorld(float worldX)
	{
		int worldTileX = (int) Math.floor(worldX + originRegionX * REGION_SIZE + SceneScale.REGION_CENTER_TILES);
		return Math.floorDiv(worldTileX, REGION_SIZE);
	}

	private int regionYForWorld(float worldZ)
	{
		int worldTileY = (int) Math.floor(originRegionY * REGION_SIZE + SceneScale.REGION_CENTER_TILES - worldZ);
		return Math.floorDiv(worldTileY, REGION_SIZE);
	}

	private float localTileX(TerrainMesh mesh, float worldX)
	{
		return SceneScale.tileXFromWorld(worldX - offsetX(mesh));
	}

	private float localTileY(TerrainMesh mesh, float worldZ)
	{
		return SceneScale.tileYFromWorld(worldZ - offsetZ(mesh));
	}

	private static SceneTextureSet firstTextureSet(Collection<TerrainMesh> meshes)
	{
		for (TerrainMesh mesh : meshes)
		{
			if (mesh.textureSet().layerCount() > 1)
			{
				return mesh.textureSet();
			}
		}
		for (TerrainMesh mesh : meshes)
		{
			return mesh.textureSet();
		}
		return SceneTextureSet.empty();
	}

	private static boolean crossedSurface(float previousDistance, float heightDistance)
	{
		return previousDistance > 0.0f && heightDistance <= 0.0f
			|| previousDistance < 0.0f && heightDistance >= 0.0f;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
