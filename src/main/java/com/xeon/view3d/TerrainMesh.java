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
import java.util.List;
import net.runelite.cache.region.Region;
import org.joml.Vector3fc;

public final class TerrainMesh
{
	public static final int FLOATS_PER_VERTEX = 17;
	private static final float PICK_STEP = 0.25f;
	private static final float PICK_EPSILON = 0.08f;
	private static final int HEIGHT_GRID_SIZE = Region.X + 1;
	private static final int HEIGHTS_PER_PLANE = HEIGHT_GRID_SIZE * HEIGHT_GRID_SIZE;
	private static final int RENDERABLE_TILES_PER_PLANE = Region.X * Region.Y;

	private final int regionId;
	private final int regionX;
	private final int regionY;
	private final int plane;
	private final boolean allPlanes;
	private float[] vertexData;
	private byte[] compressedVertexData;
	private final int vertexCount;
	private final int[] planeStartVertices;
	private final int[] planeVertexCounts;
	private final int[] planeTerrainVertexCounts;
	private final int[] planeTransparentStartVertices;
	private final int[] planeTransparentVertexCounts;
	private final float minY;
	private final float maxY;
	private final List<AnimatedObjectMesh> animatedObjects;
	private final List<NpcMesh> npcMeshes;
	private final List<AgilityObstacleInstance> agilityObstacles;
	private final List<ObjectOverlayMesh> objectOverlays;
	private final SceneTextureSet textureSet;
	private final int[] sceneHeights;
	private final boolean[] renderableTiles;
	private final float initialCameraX;
	private final float initialCameraY;
	private final float initialCameraZ;

	static TerrainMesh empty(int regionId)
	{
		return new TerrainMesh(
			regionId,
			TerrainScene.regionX(regionId),
			TerrainScene.regionY(regionId),
			Region.Z - 1,
			true,
			new float[0],
			0,
			new int[Region.Z],
			new int[Region.Z],
			new int[Region.Z],
			new int[Region.Z],
			new int[Region.Z],
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			SceneTextureSet.empty(),
			new int[Region.Z * HEIGHTS_PER_PLANE],
			new boolean[Region.Z * Region.X * Region.Y],
			0.0f,
			22.0f,
			0.0f
		);
	}

	public TerrainMesh(
		int regionId,
		int regionX,
		int regionY,
		int plane,
		boolean allPlanes,
		float[] vertexData,
		int vertexCount,
		int[] planeStartVertices,
		int[] planeVertexCounts,
		int[] planeTerrainVertexCounts,
		int[] planeTransparentStartVertices,
		int[] planeTransparentVertexCounts,
		List<AnimatedObjectMesh> animatedObjects,
		List<NpcMesh> npcMeshes,
		List<AgilityObstacleInstance> agilityObstacles,
		List<ObjectOverlayMesh> objectOverlays,
		SceneTextureSet textureSet,
		int[] sceneHeights,
		boolean[] renderableTiles,
		float initialCameraX,
		float initialCameraY,
		float initialCameraZ
	)
	{
		this.regionId = regionId;
		this.regionX = regionX;
		this.regionY = regionY;
		this.plane = plane;
		this.allPlanes = allPlanes;
		this.vertexData = vertexData == null ? new float[0] : vertexData;
		this.vertexCount = vertexCount;
		this.planeStartVertices = normalizedPlaneArray(planeStartVertices);
		this.planeVertexCounts = normalizedPlaneArray(planeVertexCounts);
		this.planeTerrainVertexCounts = normalizedPlaneArray(planeTerrainVertexCounts);
		this.planeTransparentStartVertices = normalizedPlaneArray(planeTransparentStartVertices);
		this.planeTransparentVertexCounts = normalizedPlaneArray(planeTransparentVertexCounts);
		this.animatedObjects = animatedObjects == null ? List.of() : List.copyOf(animatedObjects);
		this.npcMeshes = npcMeshes == null ? List.of() : List.copyOf(npcMeshes);
		this.agilityObstacles = agilityObstacles == null ? List.of() : List.copyOf(agilityObstacles);
		this.objectOverlays = objectOverlays == null ? List.of() : List.copyOf(objectOverlays);
		float[] bounds = heightBounds(this.vertexData, this.animatedObjects, this.npcMeshes);
		this.minY = bounds[0];
		this.maxY = bounds[1];
		this.textureSet = textureSet == null ? SceneTextureSet.empty() : textureSet;
		this.sceneHeights = sceneHeights == null ? new int[Region.Z * HEIGHTS_PER_PLANE] : sceneHeights;
		this.renderableTiles = Arrays.copyOf(renderableTiles, renderableTiles.length);
		this.initialCameraX = initialCameraX;
		this.initialCameraY = initialCameraY;
		this.initialCameraZ = initialCameraZ;
	}

	public int regionId()
	{
		return regionId;
	}

	public int regionX()
	{
		return regionX;
	}

	public int regionY()
	{
		return regionY;
	}

	public int plane()
	{
		return plane;
	}

	public boolean allPlanes()
	{
		return allPlanes;
	}

	public float[] vertexData()
	{
		float[] raw = rawVertexData();
		return Arrays.copyOf(raw, raw.length);
	}

	synchronized float[] rawVertexData()
	{
		if ((vertexData == null || vertexData.length == 0) && compressedVertexData != null && compressedVertexData.length > 0)
		{
			vertexData = FloatDataCodec.inflate(
				compressedVertexData,
				Math.multiplyExact(vertexCount, FLOATS_PER_VERTEX)
			);
		}
		return vertexData;
	}

	void releaseVertexData()
	{
		synchronized (this)
		{
			vertexData = new float[0];
			compressedVertexData = null;
		}
		for (AnimatedObjectMesh animatedObject : animatedObjects)
		{
			animatedObject.releaseVertexData();
		}
		for (NpcMesh npcMesh : npcMeshes)
		{
			npcMesh.releaseVertexData();
		}
		for (ObjectOverlayMesh objectOverlay : objectOverlays)
		{
			objectOverlay.releaseVertexData();
		}
	}

	void compactStaticVertexData()
	{
		compactStaticVertexData(null);
	}

	void compactStaticVertexData(Runnable pause)
	{
		float[] source;
		synchronized (this)
		{
			if (vertexData == null || vertexData.length == 0)
			{
				return;
			}
			if (compressedVertexData != null)
			{
				vertexData = new float[0];
				return;
			}
			source = vertexData;
		}

		byte[] compressed = FloatDataCodec.deflate(source, pause);
		synchronized (this)
		{
			if (vertexData == source && compressedVertexData == null)
			{
				compressedVertexData = compressed;
				vertexData = new float[0];
			}
		}
		for (ObjectOverlayMesh objectOverlay : objectOverlays)
		{
			objectOverlay.compactVertexData(pause);
		}
	}

	synchronized long retainedStaticVertexBytes()
	{
		long bytes = 0L;
		if (vertexData != null && vertexData.length > 0)
		{
			bytes += (long) vertexData.length * Float.BYTES;
		}
		else if (compressedVertexData != null)
		{
			bytes += compressedVertexData.length;
		}
		for (ObjectOverlayMesh objectOverlay : objectOverlays)
		{
			bytes += objectOverlay.retainedVertexBytes();
		}
		return bytes;
	}

	public int vertexCount()
	{
		return vertexCount;
	}

	int planeStartVertex(int samplePlane)
	{
		return planeStartVertices[clamp(samplePlane, 0, Region.Z - 1)];
	}

	int planeVertexCount(int samplePlane)
	{
		return planeVertexCounts[clamp(samplePlane, 0, Region.Z - 1)];
	}

	int planeTerrainVertexCount(int samplePlane)
	{
		return planeTerrainVertexCounts[clamp(samplePlane, 0, Region.Z - 1)];
	}

	int planeTransparentStartVertex(int samplePlane)
	{
		return planeTransparentStartVertices[clamp(samplePlane, 0, Region.Z - 1)];
	}

	int planeTransparentVertexCount(int samplePlane)
	{
		return planeTransparentVertexCounts[clamp(samplePlane, 0, Region.Z - 1)];
	}

	float minY()
	{
		return minY;
	}

	float maxY()
	{
		return maxY;
	}

	SceneTextureSet textureSet()
	{
		return textureSet;
	}

	List<AnimatedObjectMesh> animatedObjects()
	{
		return animatedObjects;
	}

	List<NpcMesh> npcMeshes()
	{
		return npcMeshes;
	}

	List<AgilityObstacleInstance> agilityObstacles()
	{
		return agilityObstacles;
	}

	List<ObjectOverlayMesh> objectOverlays()
	{
		return objectOverlays;
	}

	float worldHeightAt(int samplePlane, float x, float y)
	{
		return SceneScale.worldYFromSceneHeight(sceneHeightAt(samplePlane, x, y));
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
			float localX = SceneScale.tileXFromWorld(worldX);
			float localY = SceneScale.tileYFromWorld(worldZ);
			if (localX < 0.0f || localY < 0.0f || localX >= Region.X || localY >= Region.Y)
			{
				Arrays.fill(previousDistances, Float.NaN);
				continue;
			}

			int tileX = clamp((int) Math.floor(localX), 0, Region.X - 1);
			int tileY = clamp((int) Math.floor(localY), 0, Region.Y - 1);
			for (int samplePlane = Region.Z - 1; samplePlane >= 0; samplePlane--)
			{
				if (!isRenderableTile(samplePlane, tileX, tileY))
				{
					previousDistances[samplePlane] = Float.NaN;
					continue;
				}

				float terrainY = worldHeightAt(samplePlane, localX, localY);
				float heightDistance = worldY - terrainY;
				float previousDistance = previousDistances[samplePlane];
				previousDistances[samplePlane] = heightDistance;
				if (Math.abs(heightDistance) <= PICK_EPSILON
					|| (!Float.isNaN(previousDistance) && crossedSurface(previousDistance, heightDistance)))
				{
					return new HoveredTile(
						regionId,
						regionX * Region.X + tileX,
						regionY * Region.Y + tileY,
						samplePlane,
						tileX,
						tileY
					);
				}
			}
		}
		return null;
	}

	public float initialCameraX()
	{
		return initialCameraX;
	}

	public float initialCameraY()
	{
		return initialCameraY;
	}

	public float initialCameraZ()
	{
		return initialCameraZ;
	}

	private float sceneHeightAt(int samplePlane, float x, float y)
	{
		x = clamp(x, 0.0f, Region.X);
		y = clamp(y, 0.0f, Region.Y);
		int x0 = clamp((int) Math.floor(x), 0, Region.X);
		int y0 = clamp((int) Math.floor(y), 0, Region.Y);
		int x1 = clamp(x0 + 1, 0, Region.X);
		int y1 = clamp(y0 + 1, 0, Region.Y);
		float tx = x1 == x0 ? 0.0f : x - x0;
		float ty = y1 == y0 ? 0.0f : y - y0;
		float h00 = rawSceneHeight(samplePlane, x0, y0);
		float h10 = rawSceneHeight(samplePlane, x1, y0);
		float h01 = rawSceneHeight(samplePlane, x0, y1);
		float h11 = rawSceneHeight(samplePlane, x1, y1);
		float hx0 = lerp(h00, h10, tx);
		float hx1 = lerp(h01, h11, tx);
		return lerp(hx0, hx1, ty);
	}

	private int rawSceneHeight(int samplePlane, int x, int y)
	{
		samplePlane = clamp(samplePlane, 0, Region.Z - 1);
		x = clamp(x, 0, Region.X);
		y = clamp(y, 0, Region.Y);
		return sceneHeights[samplePlane * HEIGHTS_PER_PLANE + x * HEIGHT_GRID_SIZE + y];
	}

	private boolean isRenderableTile(int samplePlane, int x, int y)
	{
		return renderableTileAt(samplePlane, x, y);
	}

	boolean renderableTileAt(int samplePlane, int x, int y)
	{
		samplePlane = clamp(samplePlane, 0, Region.Z - 1);
		x = clamp(x, 0, Region.X - 1);
		y = clamp(y, 0, Region.Y - 1);
		return renderableTiles[samplePlane * RENDERABLE_TILES_PER_PLANE + x * Region.Y + y];
	}

	private static boolean crossedSurface(float previousDistance, float heightDistance)
	{
		return previousDistance > 0.0f && heightDistance <= 0.0f
			|| previousDistance < 0.0f && heightDistance >= 0.0f;
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

	private static float[] heightBounds(
		float[] vertexData,
		List<AnimatedObjectMesh> animatedObjects,
		List<NpcMesh> npcMeshes
	)
	{
		float minY = Float.POSITIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float[] bounds = heightBounds(vertexData, minY, maxY);
		minY = bounds[0];
		maxY = bounds[1];
		for (AnimatedObjectMesh animatedObject : animatedObjects)
		{
			for (AnimatedObjectMesh.Frame frame : animatedObject.frames())
			{
				if (frame == null)
				{
					continue;
				}
				bounds = heightBounds(frame.rawVertexData(), minY, maxY);
				minY = bounds[0];
				maxY = bounds[1];
			}
		}
		for (NpcMesh npcMesh : npcMeshes)
		{
			float npcMinY = Float.POSITIVE_INFINITY;
			float npcMaxY = Float.NEGATIVE_INFINITY;
			for (AnimatedObjectMesh.Frame frame : npcMesh.frames())
			{
				if (frame == null)
				{
					continue;
				}
				bounds = heightBounds(frame.rawVertexData(), npcMinY, npcMaxY);
				npcMinY = bounds[0];
				npcMaxY = bounds[1];
			}
			if (!Float.isFinite(npcMinY) || !Float.isFinite(npcMaxY))
			{
				continue;
			}
			for (NpcMesh.Instance instance : npcMesh.instances())
			{
				for (float baseY : instance.y())
				{
					minY = Math.min(minY, baseY + npcMinY);
					maxY = Math.max(maxY, baseY + npcMaxY);
				}
			}
		}
		if (!Float.isFinite(minY) || !Float.isFinite(maxY))
		{
			return new float[]{-64.0f, 64.0f};
		}
		return new float[]{minY, maxY};
	}

	private static float[] heightBounds(float[] vertexData, float minY, float maxY)
	{
		for (int i = 0; i + 1 < vertexData.length; i += FLOATS_PER_VERTEX)
		{
			minY = Math.min(minY, vertexData[i + 1]);
			maxY = Math.max(maxY, vertexData[i + 1]);
		}
		return new float[]{minY, maxY};
	}

	private static int[] normalizedPlaneArray(int[] values)
	{
		int[] out = new int[Region.Z];
		if (values != null)
		{
			System.arraycopy(values, 0, out, 0, Math.min(out.length, values.length));
		}
		return out;
	}

}
