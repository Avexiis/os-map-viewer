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
import net.runelite.cache.region.Region;

final class TerrainMeshBuilder
{
	private static final float REGION_CENTER = 32.0f;
	private static final float HEIGHT_SCALE = 1.0f / 64.0f;
	private static final float NORMAL_SAMPLE_DISTANCE = 1.0f;
	private static final float NORMAL_Y_SCALE = 2.0f;
	private static final int MAX_TILE_TRIANGLES = 6;

	private TerrainMeshBuilder()
	{
	}

	static TerrainMesh build(Region region, int plane, TerrainColorizer colorizer)
	{
		FloatList data = new FloatList(
			Region.X * Region.Y * MAX_TILE_TRIANGLES * 3 * TerrainMesh.FLOATS_PER_VERTEX
		);
		TerrainLightMap lightMap = new TerrainLightMap(region, plane);
		float maxHeight = Float.NEGATIVE_INFINITY;

		for (int x = 0; x < Region.X; x++)
		{
			for (int y = 0; y < Region.Y; y++)
			{
				TerrainTilePaint paint = colorizer.paintFor(x, y);
				maxHeight = Math.max(maxHeight, maxCornerHeight(region, plane, x, y));
				if (paint == null || !paint.hasOverlay())
				{
					putTileQuad(data, region, plane, x, y, colorizer.underlayColorFor(paint), lightMap);
				}
				else
				{
					putOverlayTile(data, region, plane, x, y, paint, colorizer, lightMap);
				}
			}
		}

		return new TerrainMesh(
			region.getRegionID(),
			region.getRegionX(),
			region.getRegionY(),
			plane,
			data.toArray(),
			data.size() / TerrainMesh.FLOATS_PER_VERTEX,
			0.0f,
			Math.max(16.0f, maxHeight + 18.0f),
			-76.0f
		);
	}

	private static void putTileQuad(
		FloatList data,
		Region region,
		int plane,
		int x,
		int y,
		int rgb,
		TerrainLightMap lightMap
	)
	{
		Vertex[] corners = tileCorners(region, plane, x, y);
		putQuad(data, corners[0], corners[1], corners[2], corners[3], rgb, lightMap);
	}

	private static void putOverlayTile(
		FloatList data,
		Region region,
		int plane,
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
			putTileQuad(data, region, plane, x, y, colorizer.overlayColorFor(paint), lightMap);
			return;
		}

		int rotation = paint.overlayRotation() & 3;
		int[] vertexTypes = TileShapeModel.vertexTypes(shapeType);
		Vertex[] vertices = new Vertex[vertexTypes.length];
		for (int i = 0; i < vertexTypes.length; i++)
		{
			int vertexType = TileShapeModel.rotateVertexType(vertexTypes[i], rotation);
			vertices[i] = vertexAt(
				region,
				plane,
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
			int rgb = faceData[i] == TileShapeModel.FACE_OVERLAY ? overlayRgb : underlayRgb;
			putTriangle(data, vertices[a], vertices[b], vertices[c], rgb, lightMap);
		}
	}

	private static Vertex[] tileCorners(Region region, int plane, int x, int y)
	{
		return new Vertex[]{
			vertexAt(region, plane, x, y),
			vertexAt(region, plane, x + 1.0f, y),
			vertexAt(region, plane, x + 1.0f, y + 1.0f),
			vertexAt(region, plane, x, y + 1.0f)
		};
	}

	private static void putQuad(
		FloatList data,
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
		FloatList data,
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

	private static void putVertex(FloatList data, Vertex vertex, int rgb, TerrainLightMap lightMap)
	{
		rgb = lightMap.apply(rgb, vertex.tileX(), vertex.tileY());
		data.add(vertex.x());
		data.add(vertex.y());
		data.add(vertex.z());
		data.add(vertex.normalX());
		data.add(vertex.normalY());
		data.add(vertex.normalZ());
		data.add(((rgb >> 16) & 0xFF) / 255.0f);
		data.add(((rgb >> 8) & 0xFF) / 255.0f);
		data.add((rgb & 0xFF) / 255.0f);
	}

	private static Vertex vertexAt(Region region, int plane, float x, float y)
	{
		float height = heightAt(region, plane, x, y);
		float left = heightAt(region, plane, x - NORMAL_SAMPLE_DISTANCE, y);
		float right = heightAt(region, plane, x + NORMAL_SAMPLE_DISTANCE, y);
		float down = heightAt(region, plane, x, y - NORMAL_SAMPLE_DISTANCE);
		float up = heightAt(region, plane, x, y + NORMAL_SAMPLE_DISTANCE);
		float normalX = left - right;
		float normalY = NORMAL_Y_SCALE;
		float normalZ = down - up;
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

		return new Vertex(worldX(x), height, worldZ(y), normalX, normalY, normalZ, x, y);
	}

	private static float maxCornerHeight(Region region, int plane, int x, int y)
	{
		float h00 = heightAt(region, plane, x, y);
		float h10 = heightAt(region, plane, x + 1.0f, y);
		float h11 = heightAt(region, plane, x + 1.0f, y + 1.0f);
		float h01 = heightAt(region, plane, x, y + 1.0f);
		return Math.max(Math.max(h00, h10), Math.max(h11, h01));
	}

	private static float heightAt(Region region, int plane, float x, float y)
	{
		x = clamp(x, 0.0f, Region.X - 1.0f);
		y = clamp(y, 0.0f, Region.Y - 1.0f);
		int x0 = clamp((int) Math.floor(x), 0, Region.X - 1);
		int y0 = clamp((int) Math.floor(y), 0, Region.Y - 1);
		int x1 = clamp(x0 + 1, 0, Region.X - 1);
		int y1 = clamp(y0 + 1, 0, Region.Y - 1);
		float tx = x1 == x0 ? 0.0f : x - x0;
		float ty = y1 == y0 ? 0.0f : y - y0;
		float h00 = height(region, plane, x0, y0);
		float h10 = height(region, plane, x1, y0);
		float h01 = height(region, plane, x0, y1);
		float h11 = height(region, plane, x1, y1);
		float hx0 = lerp(h00, h10, tx);
		float hx1 = lerp(h01, h11, tx);
		return lerp(hx0, hx1, ty);
	}

	private static float height(Region region, int plane, int x, int y)
	{
		return -region.getTileHeight(plane, x, y) * HEIGHT_SCALE;
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

	private static float worldX(float localX)
	{
		return localX - REGION_CENTER;
	}

	private static float worldZ(float localY)
	{
		return localY - REGION_CENTER;
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

	private static final class FloatList
	{
		private float[] values;
		private int size;

		private FloatList(int initialCapacity)
		{
			values = new float[Math.max(128, initialCapacity)];
		}

		private void add(float value)
		{
			if (size == values.length)
			{
				values = Arrays.copyOf(values, values.length * 2);
			}
			values[size++] = value;
		}

		private int size()
		{
			return size;
		}

		private float[] toArray()
		{
			return Arrays.copyOf(values, size);
		}
	}
}
