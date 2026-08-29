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

import java.util.ArrayList;
import java.util.List;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.providers.OverlayProvider;
import net.runelite.cache.definitions.providers.UnderlayProvider;
import net.runelite.cache.item.RSTextureProvider;
import net.runelite.cache.region.Region;

final class TerrainMeshBuilder
{
	private static final float NORMAL_SAMPLE_DISTANCE = 1.0f;
	private static final float NORMAL_Y_SCALE = 2.0f;
	private static final int INITIAL_TILE_TRIANGLES = 2;
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private TerrainMeshBuilder()
	{
	}

	static TerrainMesh build(
		TerrainRegionContext regionContext,
		UnderlayProvider underlays,
		OverlayProvider overlays,
		ObjectManager objectManager,
		NpcSpawnIndex npcSpawnIndex,
		NpcDefinitionProvider npcDefinitionProvider,
		NpcWanderCollisionMap npcCollisionMap,
		ObjectModelProvider modelProvider,
		ObjectAnimationProvider animationProvider,
		RSTextureProvider textureProvider,
		TerrainFloorTextures floorTextures,
		SceneTextureSet textureSet,
		NpcMeshBuilder.FrameCache npcFrameCache,
		boolean hdosCache
	)
	{
		SceneMeshBuffer[] planeData = new SceneMeshBuffer[Region.Z];
		SceneMeshBuffer[] transparentPlaneData = new SceneMeshBuffer[Region.Z];
		for (int plane = 0; plane < Region.Z; plane++)
		{
			planeData[plane] = new SceneMeshBuffer(
				Region.X * Region.Y * INITIAL_TILE_TRIANGLES * 3 * TerrainMesh.FLOATS_PER_VERTEX
			);
			transparentPlaneData[plane] = new SceneMeshBuffer(256 * TerrainMesh.FLOATS_PER_VERTEX);
		}
		Region region = regionContext.center();
		int[] sceneHeights = sceneHeights(regionContext);
		boolean[] renderableTiles = new boolean[Region.Z * Region.X * Region.Y];
		boolean[] renderedTiles = new boolean[Region.Z * Region.X * Region.Y];
		TerrainColorizer[] colorizers = new TerrainColorizer[Region.Z];
		HdosTerrainColorizer[] hdosColorizers = new HdosTerrainColorizer[Region.Z];
		TerrainHeightMap[] heightMaps = new TerrainHeightMap[Region.Z];
		TerrainLightMap[] lightMaps = new TerrainLightMap[Region.Z];
		float maxHeight = Float.NEGATIVE_INFINITY;
		int[][][] hdosLightOcclusions = hdosCache
			? HdosSceneLighting.tileLightOcclusions(region, objectManager)
			: null;

		for (int sourcePlane = 0; sourcePlane < Region.Z; sourcePlane++)
		{
			heightMaps[sourcePlane] = new TerrainHeightMap(regionContext, sourcePlane);
			if (hdosCache)
			{
				hdosColorizers[sourcePlane] = new HdosTerrainColorizer(
					regionContext,
					underlays,
					overlays,
					textureProvider,
					floorTextures,
					textureSet,
					heightMaps[sourcePlane],
					hdosLightOcclusions[sourcePlane],
					sourcePlane
				);
			}
			else
			{
				colorizers[sourcePlane] = new TerrainColorizer(
					regionContext,
					underlays,
					overlays,
					textureProvider,
					floorTextures,
					textureSet,
					sourcePlane
				);
				lightMaps[sourcePlane] = new TerrainLightMap(heightMaps[sourcePlane]);
			}
		}

		for (int sourcePlane = 0; sourcePlane < Region.Z; sourcePlane++)
		{
			for (int x = 0; x < Region.X; x++)
			{
				for (int y = 0; y < Region.Y; y++)
				{
					int displayPlane = SceneTileFlags.displayPlaneForSource(region, sourcePlane, x, y);
					if (hdosCache)
					{
						maxHeight = putHdosSourceTile(
							planeData[displayPlane],
							region,
							hdosColorizers,
							heightMaps,
							renderableTiles,
							renderedTiles,
							displayPlane,
							sourcePlane,
							x,
							y,
							textureSet,
							maxHeight
						);
					}
					else
					{
						maxHeight = putSourceTile(
							planeData[displayPlane],
							region,
							colorizers,
							heightMaps,
							lightMaps,
							renderableTiles,
							renderedTiles,
							displayPlane,
							sourcePlane,
							x,
							y,
							textureSet,
							maxHeight
						);
					}
				}
			}
		}
		List<AnimatedObjectMesh> animatedObjects = List.of();
		List<AgilityObstacleInstance> agilityObstacles = List.of();
		List<ObjectOverlayMesh> objectOverlays = new ArrayList<>();
		if (objectManager != null)
		{
			agilityObstacles = AgilityObstacleData.collect(region, objectManager);
		}
		if (objectManager != null && modelProvider != null)
		{
			animatedObjects = ObjectMeshBuilder.append(
				planeData,
				transparentPlaneData,
				region,
				heightMaps,
				objectManager,
				modelProvider,
				animationProvider,
				textureProvider,
				textureSet,
				objectOverlays,
				hdosCache
			);
		}
		long totalDataSize = 0L;
		for (int plane = 0; plane < Region.Z; plane++)
		{
			totalDataSize += planeData[plane].size();
			totalDataSize += transparentPlaneData[plane].size();
		}
		if (totalDataSize > MAX_ARRAY_SIZE)
		{
			throw new OutOfMemoryError("3D mesh buffer exceeded maximum Java array size");
		}

		float[] vertexData = new float[(int) totalDataSize];
		int dataSize = 0;
		int[] planeStartVertices = new int[Region.Z];
		int[] planeVertexCounts = new int[Region.Z];
		int[] planeTransparentStartVertices = new int[Region.Z];
		int[] planeTransparentVertexCounts = new int[Region.Z];
		for (int plane = 0; plane < Region.Z; plane++)
		{
			planeStartVertices[plane] = dataSize / TerrainMesh.FLOATS_PER_VERTEX;
			planeVertexCounts[plane] = planeData[plane].size() / TerrainMesh.FLOATS_PER_VERTEX;
			planeData[plane].copyTo(vertexData, dataSize);
			dataSize += planeData[plane].size();
			planeData[plane] = null;
			planeTransparentStartVertices[plane] = dataSize / TerrainMesh.FLOATS_PER_VERTEX;
			planeTransparentVertexCounts[plane] = transparentPlaneData[plane].size() / TerrainMesh.FLOATS_PER_VERTEX;
			transparentPlaneData[plane].copyTo(vertexData, dataSize);
			dataSize += transparentPlaneData[plane].size();
			transparentPlaneData[plane] = null;
		}
		List<NpcMesh> npcMeshes = hdosCache ? List.of() : NpcMeshBuilder.build(
				region,
				heightMaps,
				npcSpawnIndex,
				npcDefinitionProvider,
				npcCollisionMap,
				objectManager,
				modelProvider,
				animationProvider,
				textureProvider,
				textureSet,
				npcFrameCache
			);

		return new TerrainMesh(
			region.getRegionID(),
			region.getRegionX(),
			region.getRegionY(),
			Region.Z - 1,
			true,
			vertexData,
			dataSize / TerrainMesh.FLOATS_PER_VERTEX,
			planeStartVertices,
			planeVertexCounts,
			planeTransparentStartVertices,
			planeTransparentVertexCounts,
			animatedObjects,
			npcMeshes,
			agilityObstacles,
			objectOverlays,
			textureSet,
			sceneHeights,
			renderableTiles,
			0.0f,
			Math.max(16.0f, maxHeight + 18.0f),
			0.0f
		);
	}

	private static float putHdosSourceTile(
		SceneMeshBuffer data,
		Region region,
		HdosTerrainColorizer[] colorizers,
		TerrainHeightMap[] heightMaps,
		boolean[] renderableTiles,
		boolean[] renderedTiles,
		int displayPlane,
		int sourcePlane,
		int x,
		int y,
		SceneTextureSet textureSet,
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

		HdosTerrainColorizer colorizer = colorizers[sourcePlane];
		HdosTerrainPaint paint = colorizer.paintFor(x, y);
		if (paint == null || (!paint.hasUnderlay() && !paint.hasOverlay()))
		{
			return maxHeight;
		}

		TerrainHeightMap heightMap = heightMaps[sourcePlane];
		renderedTiles[index] = true;
		renderableTiles[tileIndex(displayPlane, x, y)] = true;
		maxHeight = Math.max(maxHeight, maxCornerHeight(heightMap, x, y));
		if (!paint.hasOverlay())
		{
			putHdosTileQuad(data, heightMap, x, y, paint, false, 0, textureSet);
		}
		else
		{
			putHdosOverlayTile(data, heightMap, x, y, paint, textureSet);
		}
		return maxHeight;
	}

	private static float putSourceTile(
		SceneMeshBuffer data,
		Region region,
		TerrainColorizer[] colorizers,
		TerrainHeightMap[] heightMaps,
		TerrainLightMap[] lightMaps,
		boolean[] renderableTiles,
		boolean[] renderedTiles,
		int displayPlane,
		int sourcePlane,
		int x,
		int y,
		SceneTextureSet textureSet,
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
		renderableTiles[tileIndex(displayPlane, x, y)] = true;
		maxHeight = Math.max(maxHeight, maxCornerHeight(heightMap, x, y));
		if (!paint.hasOverlay())
		{
			putTileQuad(
				data,
				heightMap,
				x,
				y,
				colorizer.underlayColorFor(paint),
				lightMap,
				paint.underlayTextureLayer(),
				textureSet
			);
		}
		else
		{
			putOverlayTile(data, heightMap, x, y, paint, colorizer, lightMap, textureSet);
		}
		return maxHeight;
	}

	private static void putTileQuad(
		SceneMeshBuffer data,
		TerrainHeightMap heightMap,
		int x,
		int y,
		int rgb,
		TerrainLightMap lightMap,
		int textureLayer,
		SceneTextureSet textureSet
	)
	{
		Vertex[] corners = tileCorners(heightMap, x, y);
		putQuad(data, corners[0], corners[1], corners[2], corners[3], rgb, lightMap, x, y, textureLayer, textureSet);
	}

	private static void putOverlayTile(
		SceneMeshBuffer data,
		TerrainHeightMap heightMap,
		int x,
		int y,
		TerrainTilePaint paint,
		TerrainColorizer colorizer,
		TerrainLightMap lightMap,
		SceneTextureSet textureSet
	)
	{
		int shapeType = TileShapeModel.shapeTypeFor(paint.overlayPath());
		if (shapeType == TileShapeModel.SIMPLE_OVERLAY_TYPE || !TileShapeModel.isShaped(shapeType))
		{
			putTileQuad(
				data,
				heightMap,
				x,
				y,
				colorizer.overlayColorFor(paint),
				lightMap,
				paint.overlayTextureLayer(),
				textureSet
			);
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
			int textureLayer = overlayFace ? paint.overlayTextureLayer() : paint.underlayTextureLayer();
			putTriangle(data, vertices[a], vertices[b], vertices[c], rgb, lightMap, x, y, textureLayer, textureSet);
		}
	}

	private static void putHdosTileQuad(
		SceneMeshBuffer data,
		TerrainHeightMap heightMap,
		int x,
		int y,
		HdosTerrainPaint paint,
		boolean overlay,
		int textureLayer,
		SceneTextureSet textureSet
	)
	{
		Vertex[] corners = tileCorners(heightMap, x, y);
		int rgbSw = overlay ? paint.overlayRgbForVertex(1) : paint.underlayRgbForVertex(1);
		int rgbSe = overlay ? paint.overlayRgbForVertex(3) : paint.underlayRgbForVertex(3);
		int rgbNe = overlay ? paint.overlayRgbForVertex(5) : paint.underlayRgbForVertex(5);
		int rgbNw = overlay ? paint.overlayRgbForVertex(7) : paint.underlayRgbForVertex(7);
		putHdosQuad(data, corners[0], corners[1], corners[2], corners[3], rgbSw, rgbSe, rgbNe, rgbNw, x, y, textureLayer, textureSet);
	}

	private static void putHdosOverlayTile(
		SceneMeshBuffer data,
		TerrainHeightMap heightMap,
		int x,
		int y,
		HdosTerrainPaint paint,
		SceneTextureSet textureSet
	)
	{
		int shapeType = TileShapeModel.shapeTypeFor(paint.overlayPath());
		if (shapeType == TileShapeModel.SIMPLE_OVERLAY_TYPE || !TileShapeModel.isShaped(shapeType))
		{
			putHdosTileQuad(data, heightMap, x, y, paint, true, paint.overlayTextureLayer(), textureSet);
			return;
		}

		int rotation = paint.overlayRotation() & 3;
		int[] vertexTypes = TileShapeModel.vertexTypes(shapeType);
		Vertex[] vertices = new Vertex[vertexTypes.length];
		int[] underlayRgb = new int[vertexTypes.length];
		int[] overlayRgb = new int[vertexTypes.length];
		for (int i = 0; i < vertexTypes.length; i++)
		{
			int vertexType = TileShapeModel.rotateVertexType(vertexTypes[i], rotation);
			vertices[i] = vertexAt(
				heightMap,
				x + TileShapeModel.localX(vertexType),
				y + TileShapeModel.localY(vertexType)
			);
			underlayRgb[i] = paint.underlayRgbForVertex(vertexType);
			overlayRgb[i] = paint.overlayRgbForVertex(vertexType);
		}

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
			int textureLayer = overlayFace ? paint.overlayTextureLayer() : 0;
			int[] rgb = overlayFace ? overlayRgb : underlayRgb;
			putHdosTriangle(data, vertices[a], vertices[b], vertices[c], rgb[a], rgb[b], rgb[c], x, y, textureLayer, textureSet);
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
		TerrainLightMap lightMap,
		int tileX,
		int tileY,
		int textureLayer,
		SceneTextureSet textureSet
	)
	{
		putTriangle(data, v00, v11, v10, rgb, lightMap, tileX, tileY, textureLayer, textureSet);
		putTriangle(data, v00, v01, v11, rgb, lightMap, tileX, tileY, textureLayer, textureSet);
	}

	private static void putTriangle(
		SceneMeshBuffer data,
		Vertex a,
		Vertex b,
		Vertex c,
		int rgb,
		TerrainLightMap lightMap,
		int tileX,
		int tileY,
		int textureLayer,
		SceneTextureSet textureSet
	)
	{
		putVertex(data, a, rgb, lightMap, tileX, tileY, textureLayer, textureSet);
		putVertex(data, b, rgb, lightMap, tileX, tileY, textureLayer, textureSet);
		putVertex(data, c, rgb, lightMap, tileX, tileY, textureLayer, textureSet);
	}

	private static void putHdosQuad(
		SceneMeshBuffer data,
		Vertex v00,
		Vertex v10,
		Vertex v11,
		Vertex v01,
		int rgbSw,
		int rgbSe,
		int rgbNe,
		int rgbNw,
		int tileX,
		int tileY,
		int textureLayer,
		SceneTextureSet textureSet
	)
	{
		putHdosTriangle(data, v00, v11, v10, rgbSw, rgbNe, rgbSe, tileX, tileY, textureLayer, textureSet);
		putHdosTriangle(data, v00, v01, v11, rgbSw, rgbNw, rgbNe, tileX, tileY, textureLayer, textureSet);
	}

	private static void putHdosTriangle(
		SceneMeshBuffer data,
		Vertex a,
		Vertex b,
		Vertex c,
		int rgbA,
		int rgbB,
		int rgbC,
		int tileX,
		int tileY,
		int textureLayer,
		SceneTextureSet textureSet
	)
	{
		putHdosVertex(data, a, rgbA, tileX, tileY, textureLayer, textureSet);
		putHdosVertex(data, b, rgbB, tileX, tileY, textureLayer, textureSet);
		putHdosVertex(data, c, rgbC, tileX, tileY, textureLayer, textureSet);
	}

	private static void putVertex(
		SceneMeshBuffer data,
		Vertex vertex,
		int rgb,
		TerrainLightMap lightMap,
		int tileX,
		int tileY,
		int textureLayer,
		SceneTextureSet textureSet
	)
	{
		boolean textured = textureLayer > 0;
		rgb = lightMap.apply(textured ? 0xFF_FFFF : rgb, vertex.tileX(), vertex.tileY());
		SceneTextureSet.Material material = textureSet.materialForLayer(textureLayer);
		data.addVertex(
			vertex.x(),
			vertex.y(),
			vertex.z(),
			vertex.normalX(),
			vertex.normalY(),
			vertex.normalZ(),
			rgb,
			1.0f,
			0.0f,
			vertex.tileX() - tileX,
			vertex.tileY() - tileY,
			textureLayer,
			material.animationU(),
			material.animationV(),
			material.alphaCutoff()
		);
	}

	private static void putHdosVertex(
		SceneMeshBuffer data,
		Vertex vertex,
		int rgb,
		int tileX,
		int tileY,
		int textureLayer,
		SceneTextureSet textureSet
	)
	{
		SceneTextureSet.Material material = textureSet.materialForLayer(textureLayer);
		data.addVertex(
			vertex.x(),
			vertex.y(),
			vertex.z(),
			vertex.normalX(),
			vertex.normalY(),
			vertex.normalZ(),
			textureLayer > 0 ? 0xFF_FFFF : rgb,
			1.0f,
			0.0f,
			vertex.tileX() - tileX,
			vertex.tileY() - tileY,
			textureLayer,
			material.animationU(),
			material.animationV(),
			material.alphaCutoff()
		);
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

	private static int[] sceneHeights(TerrainRegionContext regionContext)
	{
		int gridSize = Region.X + 1;
		int[] heights = new int[Region.Z * gridSize * gridSize];
		for (int plane = 0; plane < Region.Z; plane++)
		{
			for (int x = 0; x <= Region.X; x++)
			{
				for (int y = 0; y <= Region.Y; y++)
				{
					heights[plane * gridSize * gridSize + x * gridSize + y] = regionContext.tileHeight(plane, x, y);
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
