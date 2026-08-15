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

import net.runelite.cache.ObjectManager;
import net.runelite.cache.OverlayManager;
import net.runelite.cache.UnderlayManager;
import net.runelite.cache.item.RSTextureProvider;
import net.runelite.cache.region.Region;

final class TerrainMeshBuilder
{
	private static final float NORMAL_SAMPLE_DISTANCE = 1.0f;
	private static final float NORMAL_Y_SCALE = 2.0f;
	private static final int MAX_TILE_TRIANGLES = 6;

	private TerrainMeshBuilder()
	{
	}

	static TerrainMesh build(
		Region region,
		UnderlayManager underlays,
		OverlayManager overlays,
		ObjectManager objectManager,
		ObjectModelProvider modelProvider,
		RSTextureProvider textureProvider
	)
	{
		SceneMeshBuffer data = new SceneMeshBuffer(
			Region.X * Region.Y * MAX_TILE_TRIANGLES * 3 * TerrainMesh.FLOATS_PER_VERTEX * Region.Z
		);
		int[] sceneHeights = sceneHeights(region);
		boolean[] renderableTiles = new boolean[Region.Z * Region.X * Region.Y];
		boolean[] renderedTiles = new boolean[Region.Z * Region.X * Region.Y];
		TerrainColorizer[] colorizers = new TerrainColorizer[Region.Z];
		TerrainHeightMap[] heightMaps = new TerrainHeightMap[Region.Z];
		TerrainLightMap[] lightMaps = new TerrainLightMap[Region.Z];
		float maxHeight = Float.NEGATIVE_INFINITY;

		for (int sourcePlane = 0; sourcePlane < Region.Z; sourcePlane++)
		{
			colorizers[sourcePlane] = new TerrainColorizer(region, underlays, overlays, textureProvider, sourcePlane);
			heightMaps[sourcePlane] = new TerrainHeightMap(region, sourcePlane);
			lightMaps[sourcePlane] = new TerrainLightMap(heightMaps[sourcePlane]);
		}

		for (int sourcePlane = 0; sourcePlane < Region.Z; sourcePlane++)
		{
			for (int x = 0; x < Region.X; x++)
			{
				for (int y = 0; y < Region.Y; y++)
				{
					maxHeight = putSourceTile(
						data,
						region,
						colorizers,
						heightMaps,
						lightMaps,
						renderableTiles,
						renderedTiles,
						sourcePlane,
						x,
						y,
						maxHeight
					);
				}
			}
		}
		if (objectManager != null && modelProvider != null)
		{
			ObjectMeshBuilder.append(
				data,
				region,
				heightMaps,
				objectManager,
				modelProvider,
				textureProvider
			);
		}

		return new TerrainMesh(
			region.getRegionID(),
			region.getRegionX(),
			region.getRegionY(),
			Region.Z - 1,
			true,
			data.toArray(),
			data.size() / TerrainMesh.FLOATS_PER_VERTEX,
			sceneHeights,
			renderableTiles,
			0.0f,
			Math.max(16.0f, maxHeight + 18.0f),
			-76.0f
		);
	}

	private static float putSourceTile(
		SceneMeshBuffer data,
		Region region,
		TerrainColorizer[] colorizers,
		TerrainHeightMap[] heightMaps,
		TerrainLightMap[] lightMaps,
		boolean[] renderableTiles,
		boolean[] renderedTiles,
		int sourcePlane,
		int x,
		int y,
		float maxHeight
	)
	{
		if (!SceneTileFlags.canRenderSourceLayer(region, sourcePlane, x, y))
		{
			return maxHeight;
		}

		int index = tileIndex(sourcePlane, x, y);
		if (renderedTiles[index])
		{
			return maxHeight;
		}

		TerrainColorizer colorizer = colorizers[sourcePlane];
		TerrainTilePaint paint = colorizer.paintFor(x, y);
		if (paint == null || (!paint.hasUnderlay() && !paint.hasOverlay()))
		{
			return maxHeight;
		}

		TerrainHeightMap heightMap = heightMaps[sourcePlane];
		TerrainLightMap lightMap = lightMaps[sourcePlane];
		renderedTiles[index] = true;
		renderableTiles[index] = true;
		maxHeight = Math.max(maxHeight, maxCornerHeight(heightMap, x, y));
		if (!paint.hasOverlay())
		{
			putTileQuad(data, heightMap, x, y, colorizer.underlayColorFor(paint), lightMap);
		}
		else
		{
			putOverlayTile(data, heightMap, x, y, paint, colorizer, lightMap);
		}
		return maxHeight;
	}

	private static void putTileQuad(
		SceneMeshBuffer data,
		TerrainHeightMap heightMap,
		int x,
		int y,
		int rgb,
		TerrainLightMap lightMap
	)
	{
		Vertex[] corners = tileCorners(heightMap, x, y);
		putQuad(data, corners[0], corners[1], corners[2], corners[3], rgb, lightMap);
	}

	private static void putOverlayTile(
		SceneMeshBuffer data,
		TerrainHeightMap heightMap,
		int x,
		int y,
		TerrainTilePaint paint,
		TerrainColorizer colorizer,
		TerrainLightMap lightMap
	)
	{
		int shapeType = TileShapeModel.shapeTypeFor(paint.overlayPath());
		if (shapeType == TileShapeModel.SIMPLE_OVERLAY_TYPE || !TileShapeModel.isShaped(shapeType))
		{
			putTileQuad(data, heightMap, x, y, colorizer.overlayColorFor(paint), lightMap);
			return;
		}

		int rotation = paint.overlayRotation() & 3;
		int[] vertexTypes = TileShapeModel.vertexTypes(shapeType);
		Vertex[] vertices = new Vertex[vertexTypes.length];
		for (int i = 0; i < vertexTypes.length; i++)
		{
			int vertexType = TileShapeModel.rotateVertexType(vertexTypes[i], rotation);
			vertices[i] = vertexAt(
				heightMap,
				x + TileShapeModel.localX(vertexType),
				y + TileShapeModel.localY(vertexType)
			);
		}

		int underlayRgb = colorizer.underlayColorFor(paint);
		int overlayRgb = colorizer.overlayColorFor(paint);
		int[] faceData = TileShapeModel.faceData(shapeType);
		for (int i = 0; i < faceData.length; i += TileShapeModel.FACE_STRIDE)
		{
			int a = TileShapeModel.rotateFaceVertex(faceData[i + 1], rotation);
			int b = TileShapeModel.rotateFaceVertex(faceData[i + 2], rotation);
			int c = TileShapeModel.rotateFaceVertex(faceData[i + 3], rotation);
			boolean overlayFace = faceData[i] == TileShapeModel.FACE_OVERLAY;
			if (overlayFace && !paint.hasOverlay() || !overlayFace && !paint.hasUnderlay())
			{
				continue;
			}
			int rgb = overlayFace ? overlayRgb : underlayRgb;
			putTriangle(data, vertices[a], vertices[b], vertices[c], rgb, lightMap);
		}
	}

	private static Vertex[] tileCorners(TerrainHeightMap heightMap, int x, int y)
	{
		return new Vertex[]{
			vertexAt(heightMap, x, y),
			vertexAt(heightMap, x + 1.0f, y),
			vertexAt(heightMap, x + 1.0f, y + 1.0f),
			vertexAt(heightMap, x, y + 1.0f)
		};
	}

	private static void putQuad(
		SceneMeshBuffer data,
		Vertex v00,
		Vertex v10,
		Vertex v11,
		Vertex v01,
		int rgb,
		TerrainLightMap lightMap
	)
	{
		putTriangle(data, v00, v11, v10, rgb, lightMap);
		putTriangle(data, v00, v01, v11, rgb, lightMap);
	}

	private static void putTriangle(
		SceneMeshBuffer data,
		Vertex a,
		Vertex b,
		Vertex c,
		int rgb,
		TerrainLightMap lightMap
	)
	{
		putVertex(data, a, rgb, lightMap);
		putVertex(data, b, rgb, lightMap);
		putVertex(data, c, rgb, lightMap);
	}

	private static void putVertex(SceneMeshBuffer data, Vertex vertex, int rgb, TerrainLightMap lightMap)
	{
		rgb = lightMap.apply(rgb, vertex.tileX(), vertex.tileY());
		data.addVertex(vertex.x(), vertex.y(), vertex.z(), vertex.normalX(), vertex.normalY(), vertex.normalZ(), rgb);
	}

	private static Vertex vertexAt(TerrainHeightMap heightMap, float x, float y)
	{
		float height = heightMap.worldHeightAt(x, y);
		float left = heightMap.worldHeightAt(x - NORMAL_SAMPLE_DISTANCE, y);
		float right = heightMap.worldHeightAt(x + NORMAL_SAMPLE_DISTANCE, y);
		float down = heightMap.worldHeightAt(x, y - NORMAL_SAMPLE_DISTANCE);
		float up = heightMap.worldHeightAt(x, y + NORMAL_SAMPLE_DISTANCE);
		float normalX = left - right;
		float normalY = NORMAL_Y_SCALE;
		float normalZ = up - down;
		float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
		if (length <= 0.00001f)
		{
			normalX = 0.0f;
			normalY = 1.0f;
			normalZ = 0.0f;
		}
		else
		{
			normalX /= length;
			normalY /= length;
			normalZ /= length;
		}

		return new Vertex(
			SceneScale.worldXFromTile(x),
			height,
			SceneScale.worldZFromTile(y),
			normalX,
			normalY,
			normalZ,
			x,
			y
		);
	}

	private static float maxCornerHeight(TerrainHeightMap heightMap, int x, int y)
	{
		float h00 = heightMap.worldHeightAt(x, y);
		float h10 = heightMap.worldHeightAt(x + 1.0f, y);
		float h11 = heightMap.worldHeightAt(x + 1.0f, y + 1.0f);
		float h01 = heightMap.worldHeightAt(x, y + 1.0f);
		return Math.max(Math.max(h00, h10), Math.max(h11, h01));
	}

	private static int[] sceneHeights(Region region)
	{
		int[] heights = new int[Region.Z * Region.X * Region.Y];
		for (int plane = 0; plane < Region.Z; plane++)
		{
			for (int x = 0; x < Region.X; x++)
			{
				for (int y = 0; y < Region.Y; y++)
				{
					heights[plane * Region.X * Region.Y + x * Region.Y + y] = region.getTileHeight(plane, x, y);
				}
			}
		}
		return heights;
	}

	private static int tileIndex(int plane, int x, int y)
	{
		return plane * Region.X * Region.Y + x * Region.Y + y;
	}

	private record Vertex(
		float x,
		float y,
		float z,
		float normalX,
		float normalY,
		float normalZ,
		float tileX,
		float tileY
	)
	{
	}
}
