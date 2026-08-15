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
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.item.RSTextureProvider;
import net.runelite.cache.models.JagexColor;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;

final class ObjectMeshBuilder
{
	private static final int TYPE_WALL_CORNER = 2;
	private static final int TYPE_WALL_DECORATION = 4;
	private static final int TYPE_DIAGONAL_WALL = 9;
	private static final int TYPE_GAME_OBJECT = 10;
	private static final int TYPE_GAME_OBJECT_DIAGONAL = 11;
	private static final int TYPE_FLOOR_DECORATION = 22;
	private static final int[] COSINE_VERTICES = new int[]{1, 0, -1, 0};
	private static final int[] SINE_VERTICES = new int[]{0, -1, 0, 1};
	private static final int FULL_TURN = 2048;
	private static final int QUARTER_TURN = 512;
	private static final int HALF_DIAGONAL_TURN = 256;
	private static final int DEFAULT_RGB = 0x8A8170;
	private static final float MIN_VISIBLE_ALPHA = 1.0f / 255.0f;

	private ObjectMeshBuilder()
	{
	}

	static void append(
		SceneMeshBuffer data,
		Region region,
		int plane,
		TerrainHeightMap heightMap,
		ObjectManager objectManager,
		ObjectModelProvider modelProvider,
		RSTextureProvider textureProvider
	)
	{
		for (Location location : visibleLocations(region, plane))
		{
			ObjectDefinition definition = objectManager.getObject(location.getId());
			if (definition == null || definition.getObjectModels() == null)
			{
				continue;
			}

			int localX = location.getPosition().getX() - region.getBaseX();
			int localY = location.getPosition().getY() - region.getBaseY();
			appendLocation(data, heightMap, modelProvider, textureProvider, definition, localX, localY, location);
		}
	}

	private static List<Location> visibleLocations(Region region, int plane)
	{
		List<Location> out = new ArrayList<>();
		for (Location location : region.getLocations())
		{
			Position position = location.getPosition();
			int localX = position.getX() - region.getBaseX();
			int localY = position.getY() - region.getBaseY();
			if (localX < 0 || localY < 0 || localX >= Region.X || localY >= Region.Y)
			{
				continue;
			}

			boolean bridge = (region.getTileSetting(1, localX, localY) & 2) != 0;
			int tilePlane = plane + (bridge ? 1 : 0);
			if (position.getZ() == tilePlane && (region.getTileSetting(plane, localX, localY) & 24) == 0)
			{
				out.add(location);
			}
			else if (plane < Region.Z - 1
				&& position.getZ() == tilePlane + 1
				&& (region.getTileSetting(plane + 1, localX, localY) & 8) != 0)
			{
				out.add(location);
			}
		}
		return out;
	}

	private static void appendLocation(
		SceneMeshBuffer data,
		TerrainHeightMap heightMap,
		ObjectModelProvider modelProvider,
		RSTextureProvider textureProvider,
		ObjectDefinition definition,
		int localX,
		int localY,
		Location location
	)
	{
		int type = location.getType();
		int orientation = location.getOrientation();
		if (type == TYPE_WALL_CORNER)
		{
			Placement placement = singleTilePlacement(heightMap, localX, localY, 0, 0, 0);
			appendModel(data, modelProvider, textureProvider, definition, type, orientation + 4, placement);
			appendModel(data, modelProvider, textureProvider, definition, type, orientation + 1 & 3, placement);
			return;
		}

		Placement placement = placementFor(heightMap, definition, localX, localY, type, orientation);
		ModelUse modelUse = modelUseFor(type, orientation);
		appendModel(
			data,
			modelProvider,
			textureProvider,
			definition,
			modelUse.modelType(),
			modelUse.modelOrientation(),
			placement.withYaw(modelUse.extraYaw())
		);
	}

	private static Placement placementFor(
		TerrainHeightMap heightMap,
		ObjectDefinition definition,
		int localX,
		int localY,
		int type,
		int orientation
	)
	{
		if (type == TYPE_GAME_OBJECT || type == TYPE_GAME_OBJECT_DIAGONAL)
		{
			int width = definition.getSizeX();
			int length = definition.getSizeY();
			if ((orientation & 1) == 1)
			{
				int tmp = width;
				width = length;
				length = tmp;
			}
			return sizedPlacement(heightMap, localX, localY, width, length, 0);
		}

		if (type >= 5 && type <= 8)
		{
			int displacement = type == 5 ? Math.max(1, definition.getDecorDisplacement()) : 0;
			int xDisplacement = COSINE_VERTICES[orientation & 3] * displacement;
			int yDisplacement = SINE_VERTICES[orientation & 3] * displacement;
			return singleTilePlacement(heightMap, localX, localY, xDisplacement, yDisplacement, 0);
		}

		if (type == TYPE_FLOOR_DECORATION
			|| type == TYPE_DIAGONAL_WALL
			|| type <= TYPE_WALL_DECORATION
			|| type >= 12)
		{
			return singleTilePlacement(heightMap, localX, localY, 0, 0, 0);
		}

		return singleTilePlacement(heightMap, localX, localY, 0, 0, 0);
	}

	private static Placement sizedPlacement(TerrainHeightMap heightMap, int x, int y, int width, int length, int yaw)
	{
		int centerSceneX = x * SceneScale.SCENE_UNITS_PER_TILE + SceneScale.SCENE_UNITS_PER_TILE * width / 2;
		int centerSceneY = y * SceneScale.SCENE_UNITS_PER_TILE + SceneScale.SCENE_UNITS_PER_TILE * length / 2;
		return new Placement(centerSceneX, centerSceneY, heightMap.meanSceneHeight(x, y), heightMap.cornerSceneHeights(x, y), yaw);
	}

	private static Placement singleTilePlacement(
		TerrainHeightMap heightMap,
		int x,
		int y,
		int xDisplacement,
		int yDisplacement,
		int yaw
	)
	{
		int centerSceneX = x * SceneScale.SCENE_UNITS_PER_TILE + SceneScale.SCENE_UNITS_PER_TILE / 2 + xDisplacement;
		int centerSceneY = y * SceneScale.SCENE_UNITS_PER_TILE + SceneScale.SCENE_UNITS_PER_TILE / 2 + yDisplacement;
		return new Placement(centerSceneX, centerSceneY, heightMap.meanSceneHeight(x, y), heightMap.cornerSceneHeights(x, y), yaw);
	}

	private static ModelUse modelUseFor(int type, int orientation)
	{
		if (type == TYPE_GAME_OBJECT_DIAGONAL)
		{
			return new ModelUse(TYPE_GAME_OBJECT, orientation, HALF_DIAGONAL_TURN);
		}
		if (type >= 5 && type <= 8)
		{
			int yaw = switch (type)
			{
				case 6 -> orientation * QUARTER_TURN + HALF_DIAGONAL_TURN;
				case 7 -> orientation * QUARTER_TURN + QUARTER_TURN;
				case 8 -> orientation * QUARTER_TURN + QUARTER_TURN + HALF_DIAGONAL_TURN;
				default -> orientation * QUARTER_TURN;
			};
			return new ModelUse(TYPE_WALL_DECORATION, 0, yaw);
		}
		if (type == TYPE_WALL_DECORATION)
		{
			return new ModelUse(TYPE_WALL_DECORATION, 0, orientation * QUARTER_TURN);
		}
		return new ModelUse(type, orientation, 0);
	}

	private static void appendModel(
		SceneMeshBuffer data,
		ObjectModelProvider modelProvider,
		RSTextureProvider textureProvider,
		ObjectDefinition definition,
		int modelType,
		int orientation,
		Placement placement
	)
	{
		int[] modelIds = modelIds(definition, modelType);
		if (modelIds == null)
		{
			return;
		}

		for (int modelId : modelIds)
		{
			ModelDefinition model = modelProvider.load(modelId & 0xFFFF);
			if (model == null || model.vertexCount == 0 || model.faceCount == 0)
			{
				continue;
			}
			appendModel(data, textureProvider, definition, model, orientation, placement);
		}
	}

	private static int[] modelIds(ObjectDefinition definition, int modelType)
	{
		int[] objectModels = definition.getObjectModels();
		if (objectModels == null)
		{
			return null;
		}

		int[] objectTypes = definition.getObjectTypes();
		if (objectTypes == null)
		{
			return modelType == TYPE_GAME_OBJECT ? objectModels : null;
		}

		int count = 0;
		for (int objectType : objectTypes)
		{
			if (objectType == modelType)
			{
				count++;
			}
		}
		if (count == 0)
		{
			return null;
		}

		int[] out = new int[count];
		int index = 0;
		for (int i = 0; i < objectTypes.length; i++)
		{
			if (objectTypes[i] == modelType)
			{
				out[index++] = objectModels[i];
			}
		}
		return out;
	}

	private static void appendModel(
		SceneMeshBuffer data,
		RSTextureProvider textureProvider,
		ObjectDefinition definition,
		ModelDefinition model,
		int orientation,
		Placement placement
	)
	{
		TransformedModel transformed = transform(definition, model, orientation, placement);
		for (int face = 0; face < model.faceCount; face++)
		{
			float alpha = faceAlpha(model, face);
			if (isHidden(model, face) || alpha <= MIN_VISIBLE_ALPHA)
			{
				continue;
			}

			int a = model.faceIndices1[face];
			int b = model.faceIndices2[face];
			int c = model.faceIndices3[face];
			if (transformed.inverted() ^ SceneScale.MIRRORS_WORLD_Z)
			{
				int tmp = a;
				a = c;
				c = tmp;
			}

			int rgb = faceRgb(model, definition, textureProvider, face);
			Vertex va = transformed.vertex(a);
			Vertex vb = transformed.vertex(b);
			Vertex vc = transformed.vertex(c);
			Normal normal = faceNormal(va, vb, vc);
			data.addVertex(va.x(), va.y(), va.z(), normal.x(), normal.y(), normal.z(), rgb, alpha);
			data.addVertex(vb.x(), vb.y(), vb.z(), normal.x(), normal.y(), normal.z(), rgb, alpha);
			data.addVertex(vc.x(), vc.y(), vc.z(), normal.x(), normal.y(), normal.z(), rgb, alpha);
		}
	}

	private static TransformedModel transform(
		ObjectDefinition definition,
		ModelDefinition model,
		int orientation,
		Placement placement
	)
	{
		boolean inverted = definition.isRotated() ^ orientation > 3;
		int rotation = orientation & 3;
		Vertex[] vertices = new Vertex[model.vertexCount];
		for (int i = 0; i < model.vertexCount; i++)
		{
			float x = model.vertexX[i];
			float y = model.vertexY[i];
			float z = model.vertexZ[i];
			if (inverted)
			{
				z = -z;
			}

			for (int r = 0; r < rotation; r++)
			{
				float nextX = z;
				z = -x;
				x = nextX;
			}

			x = x * definition.getModelSizeX() / 128.0f;
			y = y * definition.getModelSizeHeight() / 128.0f;
			z = z * definition.getModelSizeY() / 128.0f;

			x += signedShort(definition.getOffsetX());
			y += signedShort(definition.getOffsetHeight());
			z += signedShort(definition.getOffsetY());

			if (definition.getContouredGround() >= 0)
			{
				y += contouredHeightOffset(placement.cornerHeights(), placement.meanSceneHeight(), x, z);
			}

			if (placement.yaw() != 0)
			{
				double angle = placement.yaw() * Math.PI * 2.0D / FULL_TURN;
				float sin = (float) Math.sin(angle);
				float cos = (float) Math.cos(angle);
				float nextX = x * cos + z * sin;
				z = z * cos - x * sin;
				x = nextX;
			}

			vertices[i] = new Vertex(
				SceneScale.worldXFromScene(placement.centerSceneX() + x),
				SceneScale.worldYFromSceneHeight(placement.meanSceneHeight() + y),
				SceneScale.worldZFromScene(placement.centerSceneY() + z)
			);
		}
		return new TransformedModel(vertices, inverted);
	}

	private static float contouredHeightOffset(int[] corners, int mean, float x, float z)
	{
		float sceneX = (x + SceneScale.SCENE_UNITS_PER_TILE / 2.0f) / SceneScale.SCENE_UNITS_PER_TILE;
		float sceneY = (z + SceneScale.SCENE_UNITS_PER_TILE / 2.0f) / SceneScale.SCENE_UNITS_PER_TILE;
		float south = corners[0] + (corners[1] - corners[0]) * sceneX;
		float north = corners[3] + (corners[2] - corners[3]) * sceneX;
		return south + (north - south) * sceneY - mean;
	}

	private static boolean isHidden(ModelDefinition model, int face)
	{
		return model.faceColors != null && face < model.faceColors.length && model.faceColors[face] == -2;
	}

	private static float faceAlpha(ModelDefinition model, int face)
	{
		if (model.faceTransparencies == null || face >= model.faceTransparencies.length)
		{
			return 1.0f;
		}
		int transparency = model.faceTransparencies[face] & 0xFF;
		return Math.max(0.0f, Math.min(1.0f, (255.0f - transparency) / 255.0f));
	}

	private static int faceRgb(
		ModelDefinition model,
		ObjectDefinition definition,
		RSTextureProvider textureProvider,
		int face
	)
	{
		short texture = faceTexture(model, definition, face);
		if (texture >= 0 && textureProvider != null)
		{
			try
			{
				int textureHsl = textureProvider.getAverageTextureRGB(texture);
				if (textureHsl != -2)
				{
					int fullHsl = JagexColor.packHSLFull(
						JagexColor.unpackHue((short) textureHsl) * 4,
						JagexColor.unpackSaturation((short) textureHsl) * 32,
						JagexColor.unpackLuminance((short) textureHsl) * 2
					);
					return JagexColor.getRGBFull(fullHsl);
				}
			}
			catch (RuntimeException ex)
			{
				return DEFAULT_RGB;
			}
		}

		if (model.faceColors == null || face >= model.faceColors.length)
		{
			return DEFAULT_RGB;
		}

		short hsl = faceColor(model.faceColors[face], definition);
		return JagexColor.HSLtoRGB(hsl, JagexColor.BRIGHTNESS_MIN);
	}

	private static short faceTexture(ModelDefinition model, ObjectDefinition definition, int face)
	{
		if (model.faceTextures == null || face >= model.faceTextures.length)
		{
			return -1;
		}

		short texture = model.faceTextures[face];
		short[] find = definition.getRetextureToFind();
		short[] replace = definition.getTextureToReplace();
		if (find == null || replace == null)
		{
			return texture;
		}

		for (int i = 0; i < find.length && i < replace.length; i++)
		{
			if (texture == find[i])
			{
				return replace[i];
			}
		}
		return texture;
	}

	private static short faceColor(short color, ObjectDefinition definition)
	{
		short[] find = definition.getRecolorToFind();
		short[] replace = definition.getRecolorToReplace();
		if (find == null || replace == null)
		{
			return color;
		}

		for (int i = 0; i < find.length && i < replace.length; i++)
		{
			if (color == find[i])
			{
				return replace[i];
			}
		}
		return color;
	}

	private static Normal faceNormal(Vertex a, Vertex b, Vertex c)
	{
		float abX = b.x() - a.x();
		float abY = b.y() - a.y();
		float abZ = b.z() - a.z();
		float acX = c.x() - a.x();
		float acY = c.y() - a.y();
		float acZ = c.z() - a.z();
		float normalX = abY * acZ - abZ * acY;
		float normalY = abZ * acX - abX * acZ;
		float normalZ = abX * acY - abY * acX;
		float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
		if (length <= 0.00001f)
		{
			return new Normal(0.0f, 1.0f, 0.0f);
		}
		return new Normal(normalX / length, normalY / length, normalZ / length);
	}

	private static int signedShort(int value)
	{
		return value > 32767 ? value - 65536 : value;
	}

	private record ModelUse(
		int modelType,
		int modelOrientation,
		int extraYaw
	)
	{
	}

	private record Placement(
		int centerSceneX,
		int centerSceneY,
		int meanSceneHeight,
		int[] cornerHeights,
		int yaw
	)
	{
		private Placement withYaw(int yaw)
		{
			return new Placement(centerSceneX, centerSceneY, meanSceneHeight, cornerHeights, yaw);
		}
	}

	private record TransformedModel(
		Vertex[] vertices,
		boolean inverted
	)
	{
		private Vertex vertex(int index)
		{
			return vertices[index];
		}
	}

	private record Vertex(
		float x,
		float y,
		float z
	)
	{
	}

	private record Normal(
		float x,
		float y,
		float z
	)
	{
	}
}
