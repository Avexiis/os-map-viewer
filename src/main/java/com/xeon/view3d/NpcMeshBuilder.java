/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.

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
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.SequenceDefinition;
import net.runelite.cache.item.RSTextureProvider;
import net.runelite.cache.models.JagexColor;
import net.runelite.cache.region.Region;

final class NpcMeshBuilder
{
	private static final int MAX_NPC_ANIMATION_FRAMES = 24;
	private static final int WANDER_RADIUS_TILES = 5;
	private static final float NPC_WALK_TILES_PER_SECOND = 1.45f;
	private static final float NPC_IDLE_MIN_SECONDS = 2.0f;
	private static final float NPC_IDLE_RANGE_SECONDS = 3.0f;
	private static final int DEFAULT_RGB = 0x8A8170;
	private static final int SPECIAL_FACE_RGB = 0x808080;
	private static final int MODEL_LIGHT_X = -42;
	private static final int MODEL_LIGHT_Y = 82;
	private static final int MODEL_LIGHT_Z = -58;
	private static final int MODEL_BASE_AMBIENT = 64;
	private static final int MODEL_BASE_CONTRAST = 850;
	private static final int NORMAL_SCALE = 256;
	private static final float MIN_VISIBLE_ALPHA = 1.0f / 255.0f;
	private static final int[] SPAWN_DIRECTION_ROTATIONS = new int[]{768, 1024, 1280, 512, 1536, 256, 0, 1792};
	private static final int ROTATION_UNITS = 2048;
	private static final int ROTATION_STEP = 256;
	private static final int MAX_WANDER_STEPS = 8;
	private static final int LOGIN_SCREEN_RENDER_PROP = 0x1;
	private static final int LOGIN_SCREEN_WALK_PROP = 0x2;
	private static final int[][] FORWARD_TURN_ORDERS = new int[][]{
		{0, -1, 1},
		{0, 1, -1},
		{-1, 0, 1},
		{1, 0, -1}
	};

	private NpcMeshBuilder()
	{
	}

	static List<NpcMesh> build(
		Region region,
		TerrainHeightMap[] heightMaps,
		NpcSpawnIndex spawnIndex,
		NpcDefinitionProvider definitionProvider,
		NpcWanderCollisionMap collisionMap,
		ObjectModelProvider modelProvider,
		ObjectAnimationProvider animationProvider,
		RSTextureProvider textureProvider,
		SceneTextureSet textureSet,
		FrameCache frameCache
	)
	{
		if (spawnIndex == null || definitionProvider == null || modelProvider == null)
		{
			return List.of();
		}

		List<NpcSpawnIndex.NpcSpawn> spawns = spawnIndex.spawnsForRegion(region.getRegionID());
		if (spawns.isEmpty())
		{
			return List.of();
		}

		Map<Integer, ModelDefinition> baseModels = new LinkedHashMap<>();
		Map<NpcAnimationKey, NpcMeshEntry> entries = new LinkedHashMap<>();
		for (NpcSpawnIndex.NpcSpawn spawn : spawns)
		{
			int localX = spawn.worldX() - region.getBaseX();
			int localY = spawn.worldY() - region.getBaseY();
			if (localX < 0 || localY < 0 || localX >= Region.X || localY >= Region.Y)
			{
				continue;
			}
			int plane = logicalNpcPlane(region, spawn.plane(), localX, localY);
			if (plane < 0 || plane >= heightMaps.length)
			{
				continue;
			}

			NpcDefinition3D definition = definitionProvider.definition(spawn.id());
			if (definition == null || !definition.hasModels())
			{
				continue;
			}
			if (!shouldRender(definition))
			{
				continue;
			}

			ModelDefinition baseModel = baseModels.computeIfAbsent(
				definition.id,
				ignored -> baseModel(definition, modelProvider)
			);
			if (baseModel == null || baseModel.vertexCount == 0 || baseModel.faceCount == 0)
			{
				continue;
			}

			AnimationChoice idleChoice = idleChoice(definition, animationProvider);
			if (idleChoice == AnimationChoice.NONE)
			{
				continue;
			}
			NpcMeshEntry idleEntry = entries.computeIfAbsent(
				new NpcAnimationKey(definition.id, idleChoice.sequenceId(), false),
				ignored -> buildEntry(
					definition,
					baseModel,
					idleChoice.sequenceId(),
					idleChoice.sequence(),
					animationProvider,
					textureProvider,
					textureSet,
					false,
					frameCache
				)
			);

			AnimationChoice walkChoice = walkChoice(definition, animationProvider);
			boolean walking = walkChoice.walking() && canWander(definition, spawn, region, plane, collisionMap);
			NpcMeshEntry walkEntry = null;
			if (walking)
			{
				walkEntry = entries.computeIfAbsent(
					new NpcAnimationKey(definition.id, walkChoice.sequenceId(), true),
					ignored -> buildEntry(
						definition,
						baseModel,
						walkChoice.sequenceId(),
						walkChoice.sequence(),
						animationProvider,
						textureProvider,
						textureSet,
						true,
						frameCache
					)
				);
			}
			NpcMesh.Instance instance = instanceFor(
				region,
				heightMaps,
				collisionMap,
				definition,
				spawn,
				plane,
				walking,
				walking && walkEntry != null ? walkEntry.totalFrameLength() : idleEntry.totalFrameLength()
			);
			idleEntry.instances.add(instance);
			if (walkEntry != null)
			{
				walkEntry.instances.add(instance);
			}
		}

		List<NpcMesh> meshes = new ArrayList<>(entries.size());
		for (NpcMeshEntry entry : entries.values())
		{
			if (!entry.instances.isEmpty() && entry.frames.length > 0)
			{
				meshes.add(new NpcMesh(
					entry.npcId,
					entry.name,
					entry.combatLevel,
					entry.sequenceId,
					entry.walkingAnimation,
					entry.frameLengths,
					entry.frameStep,
					entry.frames,
					entry.bounds,
					entry.instances
				));
			}
		}
		return meshes;
	}

	private static boolean shouldRender(NpcDefinition3D definition)
	{
		return definition.loginScreenProps == 0
			|| (definition.loginScreenProps & LOGIN_SCREEN_RENDER_PROP) != 0;
	}

	private static AnimationChoice walkChoice(NpcDefinition3D definition, ObjectAnimationProvider animationProvider)
	{
		if (animationProvider == null)
		{
			return AnimationChoice.NONE;
		}

		int walkSequenceId = definition.walkSequenceId();
		if (canUseWalkSequence(definition))
		{
			SequenceDefinition sequence = animationProvider.loadSequence(walkSequenceId);
			if (animationProvider.effectiveFrameCount(sequence) > 0)
			{
				return new AnimationChoice(walkSequenceId, sequence, true);
			}
		}
		return AnimationChoice.NONE;
	}

	private static AnimationChoice idleChoice(NpcDefinition3D definition, ObjectAnimationProvider animationProvider)
	{
		if (animationProvider == null || definition.idleSequenceId() < 0)
		{
			return AnimationChoice.NONE;
		}

		SequenceDefinition sequence = animationProvider.loadSequence(definition.idleSequenceId());
		if (animationProvider.effectiveFrameCount(sequence) <= 0)
		{
			return AnimationChoice.NONE;
		}
		return new AnimationChoice(definition.idleSequenceId(), sequence, false);
	}

	private static boolean canWander(
		NpcDefinition3D definition,
		NpcSpawnIndex.NpcSpawn spawn,
		Region region,
		int plane,
		NpcWanderCollisionMap collisionMap
	)
	{
		if (!canUseWalkSequence(definition))
		{
			return false;
		}
		List<TilePoint> path = wanderTilePath(definition, spawn, region, plane, collisionMap);
		return path.size() > 1;
	}

	private static boolean canUseWalkSequence(NpcDefinition3D definition)
	{
		int walkSequenceId = definition.walkSequenceId();
		if (walkSequenceId < 0)
		{
			return false;
		}
		if (definition.loginScreenProps != 0)
		{
			return (definition.loginScreenProps & LOGIN_SCREEN_WALK_PROP) != 0;
		}
		return walkSequenceId != definition.idleSequenceId();
	}

	private static NpcMeshEntry buildEntry(
		NpcDefinition3D definition,
		ModelDefinition baseModel,
		int sequenceId,
		SequenceDefinition sequence,
		ObjectAnimationProvider animationProvider,
		RSTextureProvider textureProvider,
		SceneTextureSet textureSet,
		boolean walking,
		FrameCache frameCache
	)
	{
		NpcAnimationKey key = new NpcAnimationKey(definition.id, sequenceId, walking);
		FrameSet frameSet = frameCache == null
			? frameSet(definition, baseModel, sequence, animationProvider, textureProvider, textureSet, walking)
			: frameCache.computeIfAbsent(
				key,
				() -> frameSet(definition, baseModel, sequence, animationProvider, textureProvider, textureSet, walking)
			);
		return new NpcMeshEntry(
			definition.id,
			definition.name,
			definition.combatLevel,
			sequenceId,
			walking,
			frameSet.frameLengths(),
			frameSet.frameStep(),
			frameSet.frames(),
			frameSet.bounds()
		);
	}

	private static FrameSet frameSet(
		NpcDefinition3D definition,
		ModelDefinition baseModel,
		SequenceDefinition sequence,
		ObjectAnimationProvider animationProvider,
		RSTextureProvider textureProvider,
		SceneTextureSet textureSet,
		boolean walking
	)
	{
		int effectiveFrameCount = animationProvider == null ? 0 : animationProvider.effectiveFrameCount(sequence);
		int bakedFrameCount = effectiveFrameCount <= 0 ? 1 : Math.min(effectiveFrameCount, MAX_NPC_ANIMATION_FRAMES);
		AnimatedObjectMesh.Frame[] frames = new AnimatedObjectMesh.Frame[bakedFrameCount];
		BoundsBuilder bounds = new BoundsBuilder();
		for (int frame = 0; frame < bakedFrameCount; frame++)
		{
			int sourceFrame = effectiveFrameCount <= 0
				? -1
				: Math.min(effectiveFrameCount - 1, frame * effectiveFrameCount / bakedFrameCount);
			ModelDefinition model = frameModel(definition, baseModel, sequence, animationProvider, sourceFrame, walking);
			SceneMeshBuffer frameData = new SceneMeshBuffer(Math.max(256, model.faceCount * 3 * TerrainMesh.FLOATS_PER_VERTEX));
			appendFrameModel(frameData, definition, model, textureProvider, textureSet);
			float[] rawVertexData = frameData.toArray();
			bounds.include(rawVertexData);
			frames[frame] = new AnimatedObjectMesh.Frame(
				rawVertexData,
				frameData.size() / TerrainMesh.FLOATS_PER_VERTEX
			);
		}
		return new FrameSet(
			frameLengths(sequence, animationProvider, effectiveFrameCount, bakedFrameCount),
			frameStep(sequence, effectiveFrameCount, bakedFrameCount),
			frames,
			bounds.build()
		);
	}

	private static ModelDefinition frameModel(
		NpcDefinition3D definition,
		ModelDefinition baseModel,
		SequenceDefinition sequence,
		ObjectAnimationProvider animationProvider,
		int sourceFrame,
		boolean walking
	)
	{
		if (sequence != null && sourceFrame >= 0 && animationProvider != null)
		{
			if (animationProvider.isMayaSequence(sequence))
			{
				ModelDefinition model = ObjectAnimationProvider.copyModel(baseModel);
				applyMayaApproximation(model, sourceFrame, animationProvider.effectiveFrameCount(sequence), definition.id, walking);
				return model;
			}

			ModelDefinition animated = animationProvider.animate(baseModel, sequence, sourceFrame);
			if (animated != null)
			{
				return animated;
			}
			if (walking)
			{
				ModelDefinition model = ObjectAnimationProvider.copyModel(baseModel);
				applyMayaApproximation(model, sourceFrame, animationProvider.effectiveFrameCount(sequence), definition.id, true);
				return model;
			}
		}
		return ObjectAnimationProvider.copyModel(baseModel);
	}

	private static int[] frameLengths(
		SequenceDefinition sequence,
		ObjectAnimationProvider animationProvider,
		int effectiveFrameCount,
		int bakedFrameCount
	)
	{
		if (sequence == null || animationProvider == null || effectiveFrameCount <= 0)
		{
			return new int[]{50};
		}
		if (animationProvider.isMayaSequence(sequence))
		{
			int[] lengths = new int[bakedFrameCount];
			Arrays.fill(lengths, Math.max(1, Math.round(effectiveFrameCount / (float) bakedFrameCount)));
			return lengths;
		}

		int[] sourceLengths = animationProvider.frameLengths(sequence);
		if (sourceLengths.length == bakedFrameCount)
		{
			return sourceLengths;
		}

		int[] lengths = new int[bakedFrameCount];
		for (int frame = 0; frame < bakedFrameCount; frame++)
		{
			int start = frame * effectiveFrameCount / bakedFrameCount;
			int end = Math.max(start + 1, (frame + 1) * effectiveFrameCount / bakedFrameCount);
			int total = 0;
			for (int sourceFrame = start; sourceFrame < end && sourceFrame < sourceLengths.length; sourceFrame++)
			{
				total += Math.max(1, sourceLengths[sourceFrame]);
			}
			lengths[frame] = Math.max(1, total);
		}
		return lengths;
	}

	private static int frameStep(SequenceDefinition sequence, int effectiveFrameCount, int bakedFrameCount)
	{
		if (sequence == null || effectiveFrameCount <= 0)
		{
			return -1;
		}
		if (sequence.frameStep <= 0)
		{
			return -1;
		}
		if (effectiveFrameCount == bakedFrameCount)
		{
			return sequence.frameStep;
		}
		return Math.max(1, Math.min(bakedFrameCount, Math.round(sequence.frameStep * bakedFrameCount / (float) effectiveFrameCount)));
	}

	private static NpcMesh.Instance instanceFor(
		Region region,
		TerrainHeightMap[] heightMaps,
		NpcWanderCollisionMap collisionMap,
		NpcDefinition3D definition,
		NpcSpawnIndex.NpcSpawn spawn,
		int plane,
		boolean walking,
		int totalFrameLength
	)
	{
		int hash = spawnHash(definition, spawn);
		int phaseOffset = Math.floorMod(hash, Math.max(1, totalFrameLength));
		float idleYaw = yawRadians(spawnRotation(definition.spawnDirection));
		if (!walking)
		{
			PathPoint point = pathPoint(region, heightMaps, plane, spawn.worldX(), spawn.worldY(), definition.size());
			return NpcMesh.Instance.stationary(plane, phaseOffset, point.x(), point.y(), point.z(), idleYaw);
		}

		List<TilePoint> tiles = wanderTilePath(definition, spawn, region, plane, collisionMap);
		if (tiles.size() <= 1)
		{
			PathPoint point = pathPoint(region, heightMaps, plane, spawn.worldX(), spawn.worldY(), definition.size());
			return NpcMesh.Instance.stationary(plane, phaseOffset, point.x(), point.y(), point.z(), idleYaw);
		}

		List<Float> x = new ArrayList<>();
		List<Float> y = new ArrayList<>();
		List<Float> z = new ArrayList<>();
		List<Float> segmentStartYaw = new ArrayList<>();
		List<Float> segmentEndYaw = new ArrayList<>();
		List<Boolean> segmentWalking = new ArrayList<>();
		List<Float> segmentSeconds = new ArrayList<>();
		PathPoint currentPoint = pathPoint(region, heightMaps, plane, tiles.get(0).x(), tiles.get(0).y(), definition.size());
		x.add(currentPoint.x());
		y.add(currentPoint.y());
		z.add(currentPoint.z());

		float loopStartYaw = yawForStep(tiles.get(tiles.size() - 2), tiles.get(tiles.size() - 1));
		float currentYaw = loopStartYaw;
		appendSegment(
			x,
			y,
			z,
			segmentStartYaw,
			segmentEndYaw,
			segmentWalking,
			segmentSeconds,
			currentPoint,
			loopStartYaw,
			loopStartYaw,
			idleSeconds(hash, -1),
			false
		);
		for (int i = 0; i < tiles.size() - 1; i++)
		{
			TilePoint a = tiles.get(i);
			TilePoint b = tiles.get(i + 1);
			float targetYaw = yawForStep(a, b);
			float turnSeconds = turnSeconds(definition.rotationSpeed, currentYaw, targetYaw);
			if (turnSeconds > 0.001f)
			{
				appendSegment(
					x,
					y,
					z,
					segmentStartYaw,
					segmentEndYaw,
					segmentWalking,
					segmentSeconds,
					currentPoint,
					currentYaw,
					targetYaw,
					turnSeconds,
					false
				);
			}

			PathPoint nextPoint = pathPoint(region, heightMaps, plane, b.x(), b.y(), definition.size());
			float distance = (float) Math.hypot(b.x() - a.x(), b.y() - a.y());
			appendSegment(
				x,
				y,
				z,
				segmentStartYaw,
				segmentEndYaw,
				segmentWalking,
				segmentSeconds,
				nextPoint,
				targetYaw,
				targetYaw,
				Math.max(0.35f, distance / NPC_WALK_TILES_PER_SECOND),
				true
			);
			currentPoint = nextPoint;
			currentYaw = targetYaw;
		}

		float movementPhaseSeconds = Math.floorMod(hash, 10_000) / 1000.0f;
		return NpcMesh.Instance.moving(
			plane,
			phaseOffset,
			movementPhaseSeconds,
			loopStartYaw,
			toFloatArray(x),
			toFloatArray(y),
			toFloatArray(z),
			toFloatArray(segmentStartYaw),
			toFloatArray(segmentEndYaw),
			toBooleanArray(segmentWalking),
			toFloatArray(segmentSeconds)
		);
	}

	private static List<TilePoint> wanderTilePath(
		NpcDefinition3D definition,
		NpcSpawnIndex.NpcSpawn spawn,
		Region region,
		int plane,
		NpcWanderCollisionMap collisionMap
	)
	{
		int size = definition.size();
		int minX = region.getBaseX();
		int minY = region.getBaseY();
		int maxX = region.getBaseX() + Region.X - size;
		int maxY = region.getBaseY() + Region.Y - size;
		int startX = clamp(spawn.worldX(), minX, maxX);
		int startY = clamp(spawn.worldY(), minY, maxY);
		if (collisionMap != null && !collisionMap.canStand(startX, startY, plane, size))
		{
			return List.of(new TilePoint(startX, startY));
		}

		List<TilePoint> path = new ArrayList<>();
		path.add(new TilePoint(startX, startY));
		int currentX = startX;
		int currentY = startY;
		int facingRotation = spawnRotation(definition.spawnDirection);
		int hash = spawnHash(definition, spawn);
		for (int step = 0; step < MAX_WANDER_STEPS; step++)
		{
			StepChoice next = nextForwardStep(
				startX,
				startY,
				currentX,
				currentY,
				minX,
				minY,
				maxX,
				maxY,
				plane,
				size,
				facingRotation,
				hash,
				step,
				collisionMap
			);
			if (next == null)
			{
				break;
			}
			path.add(next.point());
			currentX = next.point().x();
			currentY = next.point().y();
			facingRotation = next.rotation();
		}
		if (path.size() > 1)
		{
			for (int i = path.size() - 2; i >= 0; i--)
			{
				path.add(path.get(i));
			}
		}
		return path;
	}

	private static StepChoice nextForwardStep(
		int originX,
		int originY,
		int startX,
		int startY,
		int minX,
		int minY,
		int maxX,
		int maxY,
		int plane,
		int size,
		int facingRotation,
		int hash,
		int step,
		NpcWanderCollisionMap collisionMap
	)
	{
		int[] turnOrder = FORWARD_TURN_ORDERS[Math.floorMod(hash + step, FORWARD_TURN_ORDERS.length)];
		for (int turn : turnOrder)
		{
			int rotation = normalizeRotation(facingRotation + turn * ROTATION_STEP);
			TilePoint direction = directionForRotation(rotation);
			int targetX = startX + direction.x();
			int targetY = startY + direction.y();
			if (targetX < minX || targetY < minY || targetX > maxX || targetY > maxY)
			{
				continue;
			}
			if (Math.abs(targetX - originX) > WANDER_RADIUS_TILES || Math.abs(targetY - originY) > WANDER_RADIUS_TILES)
			{
				continue;
			}
			if (canStep(collisionMap, startX, startY, plane, size, direction.x(), direction.y()))
			{
				return new StepChoice(new TilePoint(targetX, targetY), rotation);
			}
		}
		return null;
	}

	private static boolean canStep(NpcWanderCollisionMap collisionMap, int x, int y, int plane, int size, int dx, int dy)
	{
		return collisionMap == null || collisionMap.canStep(x, y, plane, size, dx, dy);
	}

	private static PathPoint pathPoint(
		Region region,
		TerrainHeightMap[] heightMaps,
		int logicalPlane,
		int worldX,
		int worldY,
		int size
	)
	{
		float localX = worldX - region.getBaseX() + size * 0.5f;
		float localY = worldY - region.getBaseY() + size * 0.5f;
		int tileX = clamp(worldX - region.getBaseX(), 0, Region.X - 1);
		int tileY = clamp(worldY - region.getBaseY(), 0, Region.Y - 1);
		int heightPlane = visualNpcPlane(region, logicalPlane, tileX, tileY);
		TerrainHeightMap heightMap = heightMaps[heightPlane];
		float sceneHeight = heightMap.sceneHeightAt(localX, localY);
		return new PathPoint(
			SceneScale.worldXFromTile(localX),
			SceneScale.worldYFromSceneHeight(sceneHeight),
			SceneScale.worldZFromTile(localY)
		);
	}

	private static int logicalNpcPlane(Region region, int spawnPlane, int localX, int localY)
	{
		if (spawnPlane > 0 && SceneTileFlags.isBridge(region, localX, localY))
		{
			return spawnPlane - 1;
		}
		return spawnPlane;
	}

	private static int visualNpcPlane(Region region, int logicalPlane, int localX, int localY)
	{
		if (logicalPlane < Region.Z - 1 && SceneTileFlags.isBridge(region, localX, localY))
		{
			return logicalPlane + 1;
		}
		return logicalPlane;
	}

	private static void appendSegment(
		List<Float> x,
		List<Float> y,
		List<Float> z,
		List<Float> segmentStartYaw,
		List<Float> segmentEndYaw,
		List<Boolean> segmentWalking,
		List<Float> segmentSeconds,
		PathPoint target,
		float startYaw,
		float endYaw,
		float seconds,
		boolean walking
	)
	{
		x.add(target.x());
		y.add(target.y());
		z.add(target.z());
		segmentStartYaw.add(normalizeRadians(startYaw));
		segmentEndYaw.add(normalizeRadians(endYaw));
		segmentWalking.add(walking);
		segmentSeconds.add(Math.max(0.001f, seconds));
	}

	private static float idleSeconds(int hash, int stopIndex)
	{
		int value = Math.floorMod(hash * 31 + stopIndex * 1103515245, 1000);
		return NPC_IDLE_MIN_SECONDS + value / 1000.0f * NPC_IDLE_RANGE_SECONDS;
	}

	private static float turnSeconds(int rotationSpeed, float startYaw, float endYaw)
	{
		float delta = Math.abs(shortestAngleDelta(startYaw, endYaw));
		if (delta <= 0.001f)
		{
			return 0.0f;
		}
		int units = Math.max(1, Math.round(delta * ROTATION_UNITS / (float) (Math.PI * 2.0)));
		int speed = Math.max(1, rotationSpeed);
		return Math.max(0.04f, units / (float) speed * 0.01f);
	}

	private static float shortestAngleDelta(float startYaw, float endYaw)
	{
		float delta = normalizeRadians(endYaw) - normalizeRadians(startYaw);
		float twoPi = (float) (Math.PI * 2.0);
		while (delta > Math.PI)
		{
			delta -= twoPi;
		}
		while (delta < -Math.PI)
		{
			delta += twoPi;
		}
		return delta;
	}

	private static float normalizeRadians(float angle)
	{
		float twoPi = (float) (Math.PI * 2.0);
		float normalized = angle % twoPi;
		return normalized < 0.0f ? normalized + twoPi : normalized;
	}

	private static float[] toFloatArray(List<Float> values)
	{
		float[] out = new float[values.size()];
		for (int i = 0; i < values.size(); i++)
		{
			out[i] = values.get(i);
		}
		return out;
	}

	private static boolean[] toBooleanArray(List<Boolean> values)
	{
		boolean[] out = new boolean[values.size()];
		for (int i = 0; i < values.size(); i++)
		{
			out[i] = values.get(i);
		}
		return out;
	}

	private static ModelDefinition baseModel(NpcDefinition3D definition, ObjectModelProvider modelProvider)
	{
		List<ModelDefinition> models = new ArrayList<>();
		for (int modelId : definition.modelIds())
		{
			ModelDefinition model = modelProvider.load(modelId);
			if (model != null && model.vertexCount > 0 && model.faceCount > 0)
			{
				models.add(model);
			}
		}
		if (models.isEmpty())
		{
			return null;
		}

		ModelDefinition model = models.size() == 1
			? ObjectAnimationProvider.copyModel(models.get(0))
			: mergeModels(models);
		recolor(model, definition);
		retexture(model, definition);
		return model;
	}

	private static ModelDefinition mergeModels(List<ModelDefinition> models)
	{
		int vertexCount = 0;
		int faceCount = 0;
		int textureFaceCount = 0;
		boolean hasFaceTransparencies = false;
		boolean hasFaceRenderPriorities = false;
		boolean hasFaceRenderTypes = false;
		boolean hasFaceTextures = false;
		boolean hasFaceZOffsets = false;
		boolean hasTextureCoords = false;
		boolean hasTexturePrimaryColors = false;
		boolean hasTextureRenderTypes = false;
		boolean hasPackedVertexGroups = false;
		boolean hasPackedTransparencyGroups = false;
		boolean hasMayaGroups = false;
		int maxVertexGroup = -1;
		for (ModelDefinition model : models)
		{
			vertexCount += model.vertexCount;
			faceCount += model.faceCount;
			textureFaceCount += model.numTextureFaces;
			hasFaceTransparencies |= model.faceTransparencies != null;
			hasFaceRenderPriorities |= model.faceRenderPriorities != null;
			hasFaceRenderTypes |= model.faceRenderTypes != null;
			hasFaceTextures |= model.faceTextures != null;
			hasFaceZOffsets |= model.faceZOffsets != null;
			hasTextureCoords |= model.textureCoords != null;
			hasTexturePrimaryColors |= model.texturePrimaryColors != null;
			hasTextureRenderTypes |= model.textureRenderTypes != null;
			hasPackedVertexGroups |= model.packedVertexGroups != null;
			hasPackedTransparencyGroups |= model.packedTransparencyVertexGroups != null;
			hasMayaGroups |= model.animayaGroups != null || model.animayaScales != null;
			int[][] vertexGroups = model.getVertexGroups();
			if (vertexGroups != null)
			{
				maxVertexGroup = Math.max(maxVertexGroup, vertexGroups.length - 1);
			}
		}

		ModelDefinition merged = new ModelDefinition();
		merged.vertexCount = vertexCount;
		merged.vertexX = new int[vertexCount];
		merged.vertexY = new int[vertexCount];
		merged.vertexZ = new int[vertexCount];
		merged.faceCount = faceCount;
		merged.faceIndices1 = new int[faceCount];
		merged.faceIndices2 = new int[faceCount];
		merged.faceIndices3 = new int[faceCount];
		merged.faceColors = new short[faceCount];
		merged.faceTransparencies = hasFaceTransparencies ? new byte[faceCount] : null;
		merged.faceRenderPriorities = hasFaceRenderPriorities ? new byte[faceCount] : null;
		merged.faceRenderTypes = hasFaceRenderTypes ? new byte[faceCount] : null;
		merged.faceTextures = hasFaceTextures ? fillShort(faceCount, (short) -1) : null;
		merged.faceZOffsets = hasFaceZOffsets ? new byte[faceCount] : null;
		merged.textureCoords = hasTextureCoords ? fillByte(faceCount, (byte) -1) : null;
		merged.numTextureFaces = textureFaceCount;
		merged.texIndices1 = textureFaceCount > 0 ? new short[textureFaceCount] : null;
		merged.texIndices2 = textureFaceCount > 0 ? new short[textureFaceCount] : null;
		merged.texIndices3 = textureFaceCount > 0 ? new short[textureFaceCount] : null;
		merged.texturePrimaryColors = hasTexturePrimaryColors ? new short[textureFaceCount] : null;
		merged.textureRenderTypes = hasTextureRenderTypes ? new byte[textureFaceCount] : null;
		merged.packedVertexGroups = hasPackedVertexGroups ? new int[vertexCount] : null;
		merged.packedTransparencyVertexGroups = hasPackedTransparencyGroups ? new int[vertexCount] : null;
		merged.animayaGroups = hasMayaGroups ? new int[vertexCount][] : null;
		merged.animayaScales = hasMayaGroups ? new int[vertexCount][] : null;
		int[][] mergedVertexGroups = null;
		int[] vertexGroupOffsets = null;
		if (maxVertexGroup >= 0)
		{
			int[] vertexGroupCounts = new int[maxVertexGroup + 1];
			for (ModelDefinition model : models)
			{
				int[][] vertexGroups = model.getVertexGroups();
				if (vertexGroups == null)
				{
					continue;
				}
				for (int group = 0; group < vertexGroups.length; group++)
				{
					if (vertexGroups[group] != null)
					{
						vertexGroupCounts[group] += vertexGroups[group].length;
					}
				}
			}
			mergedVertexGroups = new int[vertexGroupCounts.length][];
			for (int group = 0; group < vertexGroupCounts.length; group++)
			{
				mergedVertexGroups[group] = new int[vertexGroupCounts[group]];
			}
			vertexGroupOffsets = new int[vertexGroupCounts.length];
		}

		int vertexOffset = 0;
		int faceOffset = 0;
		int textureOffset = 0;
		for (ModelDefinition model : models)
		{
			System.arraycopy(model.vertexX, 0, merged.vertexX, vertexOffset, model.vertexCount);
			System.arraycopy(model.vertexY, 0, merged.vertexY, vertexOffset, model.vertexCount);
			System.arraycopy(model.vertexZ, 0, merged.vertexZ, vertexOffset, model.vertexCount);
			copyIntArray(model.packedVertexGroups, merged.packedVertexGroups, vertexOffset, model.vertexCount);
			copyIntArray(model.packedTransparencyVertexGroups, merged.packedTransparencyVertexGroups, vertexOffset, model.vertexCount);
			copy2dArray(model.animayaGroups, merged.animayaGroups, vertexOffset, model.vertexCount);
			copy2dArray(model.animayaScales, merged.animayaScales, vertexOffset, model.vertexCount);
			copyVertexGroups(model.getVertexGroups(), mergedVertexGroups, vertexGroupOffsets, vertexOffset);

			for (int face = 0; face < model.faceCount; face++)
			{
				int out = faceOffset + face;
				merged.faceIndices1[out] = model.faceIndices1[face] + vertexOffset;
				merged.faceIndices2[out] = model.faceIndices2[face] + vertexOffset;
				merged.faceIndices3[out] = model.faceIndices3[face] + vertexOffset;
				merged.faceColors[out] = model.faceColors == null || face >= model.faceColors.length
					? 0
					: model.faceColors[face];
				copyByte(model.faceTransparencies, merged.faceTransparencies, face, out);
				copyByte(model.faceRenderPriorities, merged.faceRenderPriorities, face, out);
				copyByte(model.faceRenderTypes, merged.faceRenderTypes, face, out);
				copyShort(model.faceTextures, merged.faceTextures, face, out, (short) -1);
				copyByte(model.faceZOffsets, merged.faceZOffsets, face, out);
				if (merged.textureCoords != null && model.textureCoords != null && face < model.textureCoords.length)
				{
					byte coord = model.textureCoords[face];
					merged.textureCoords[out] = coord < 0 ? -1 : (byte) (textureOffset + (coord & 0xFF));
				}
			}

			for (int face = 0; face < model.numTextureFaces; face++)
			{
				int out = textureOffset + face;
				if (merged.texIndices1 != null && model.texIndices1 != null && face < model.texIndices1.length)
				{
					merged.texIndices1[out] = (short) (model.texIndices1[face] + vertexOffset);
				}
				if (merged.texIndices2 != null && model.texIndices2 != null && face < model.texIndices2.length)
				{
					merged.texIndices2[out] = (short) (model.texIndices2[face] + vertexOffset);
				}
				if (merged.texIndices3 != null && model.texIndices3 != null && face < model.texIndices3.length)
				{
					merged.texIndices3[out] = (short) (model.texIndices3[face] + vertexOffset);
				}
				copyShort(model.texturePrimaryColors, merged.texturePrimaryColors, face, out, (short) 0);
				copyByte(model.textureRenderTypes, merged.textureRenderTypes, face, out);
			}

			vertexOffset += model.vertexCount;
			faceOffset += model.faceCount;
			textureOffset += model.numTextureFaces;
		}
		if (mergedVertexGroups != null)
		{
			merged.setVertexGroups(mergedVertexGroups);
		}
		return merged;
	}

	private static void copyVertexGroups(
		int[][] source,
		int[][] target,
		int[] targetOffsets,
		int vertexOffset
	)
	{
		if (source == null || target == null || targetOffsets == null)
		{
			return;
		}
		for (int group = 0; group < source.length && group < target.length; group++)
		{
			int[] sourceVertices = source[group];
			if (sourceVertices == null || sourceVertices.length == 0)
			{
				continue;
			}
			int writeOffset = targetOffsets[group];
			for (int vertex : sourceVertices)
			{
				target[group][writeOffset++] = vertex + vertexOffset;
			}
			targetOffsets[group] = writeOffset;
		}
	}

	private static void appendFrameModel(
		SceneMeshBuffer data,
		NpcDefinition3D definition,
		ModelDefinition model,
		RSTextureProvider textureProvider,
		SceneTextureSet textureSet
	)
	{
		if (model.faceTextures != null)
		{
			model.computeTextureUVCoordinates();
		}

		Vertex[] vertices = transformedVertices(definition, model);
		for (int face = 0; face < model.faceCount; face++)
		{
			float alpha = faceAlpha(model, face);
			if (isHidden(model, face) || alpha <= MIN_VISIBLE_ALPHA)
			{
				continue;
			}

			Vertex a = vertices[model.faceIndices1[face]];
			Vertex b = vertices[model.faceIndices2[face]];
			Vertex c = vertices[model.faceIndices3[face]];
			TextureFace textureFace = textureFace(model, textureSet, face);
			Normal normal = faceNormal(a, b, c);
			int rgb = textureFace.textured()
				? texturedFaceTint(definition, normal)
				: faceRgb(model, definition, textureProvider, face, normal);
			float depthBias = faceDepthBias(model, face);
			putVertex(data, a, normal, rgb, alpha, depthBias, textureFace, textureFace.vertex(0));
			putVertex(data, b, normal, rgb, alpha, depthBias, textureFace, textureFace.vertex(1));
			putVertex(data, c, normal, rgb, alpha, depthBias, textureFace, textureFace.vertex(2));
		}
	}

	private static Vertex[] transformedVertices(NpcDefinition3D definition, ModelDefinition model)
	{
		Vertex[] vertices = new Vertex[model.vertexCount];
		float widthScale = definition.widthScale / 128.0f;
		float heightScale = definition.heightScale / 128.0f;
		for (int i = 0; i < model.vertexCount; i++)
		{
			vertices[i] = new Vertex(
				model.vertexX[i] * SceneScale.SCENE_TO_WORLD * widthScale,
				-model.vertexY[i] * SceneScale.SCENE_TO_WORLD * heightScale,
				-model.vertexZ[i] * SceneScale.SCENE_TO_WORLD * widthScale
			);
		}
		return vertices;
	}

	private static void putVertex(
		SceneMeshBuffer data,
		Vertex vertex,
		Normal normal,
		int rgb,
		float alpha,
		float depthBias,
		TextureFace textureFace,
		TextureVertex textureVertex
	)
	{
		data.addVertex(
			vertex.x(),
			vertex.y(),
			vertex.z(),
			normal.x(),
			normal.y(),
			normal.z(),
			rgb,
			alpha,
			depthBias,
			textureVertex.u(),
			textureVertex.v(),
			textureFace.layer(),
			textureFace.material().animationU(),
			textureFace.material().animationV(),
			textureFace.material().alphaCutoff()
		);
	}

	private static TextureFace textureFace(ModelDefinition model, SceneTextureSet textureSet, int face)
	{
		short texture = faceTexture(model, face);
		int layer = texture < 0 || textureSet == null ? 0 : textureSet.layerForTexture(texture);
		if (layer <= 0)
		{
			return TextureFace.NONE;
		}

		float[] u = model.faceTextureUCoordinates == null || face >= model.faceTextureUCoordinates.length
			? null
			: model.faceTextureUCoordinates[face];
		float[] v = model.faceTextureVCoordinates == null || face >= model.faceTextureVCoordinates.length
			? null
			: model.faceTextureVCoordinates[face];
		TextureVertex a = new TextureVertex(textureCoordinate(u, 0, 0.0f), textureCoordinate(v, 0, 0.0f));
		TextureVertex b = new TextureVertex(textureCoordinate(u, 1, 1.0f), textureCoordinate(v, 1, 0.0f));
		TextureVertex c = new TextureVertex(textureCoordinate(u, 2, 0.0f), textureCoordinate(v, 2, 1.0f));
		return new TextureFace(layer, textureSet.materialForLayer(layer), a, b, c);
	}

	private static float textureCoordinate(float[] values, int index, float fallback)
	{
		return values == null || index < 0 || index >= values.length ? fallback : values[index];
	}

	private static int faceRgb(
		ModelDefinition model,
		NpcDefinition3D definition,
		RSTextureProvider textureProvider,
		int face,
		Normal normal
	)
	{
		if (faceRenderType(model, face) == 3)
		{
			return SPECIAL_FACE_RGB;
		}

		short texture = faceTexture(model, face);
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

		short hsl = model.faceColors[face];
		if (hsl == -2)
		{
			return DEFAULT_RGB;
		}
		return JagexColor.HSLtoRGB(adjustLightness(hsl, modelLight(definition, normal)), JagexColor.BRIGHTNESS_MIN);
	}

	private static int texturedFaceTint(NpcDefinition3D definition, Normal normal)
	{
		int component = clamp(clampLightness(modelLight(definition, normal)) * 255 / 127, 24, 255);
		return component << 16 | component << 8 | component;
	}

	private static int modelLight(NpcDefinition3D definition, Normal normal)
	{
		int ambient = definition.ambient + MODEL_BASE_AMBIENT;
		int contrast = definition.contrast * 5 + MODEL_BASE_CONTRAST;
		int magnitude = (int) Math.sqrt(MODEL_LIGHT_X * MODEL_LIGHT_X
			+ MODEL_LIGHT_Y * MODEL_LIGHT_Y
			+ MODEL_LIGHT_Z * MODEL_LIGHT_Z);
		int intensity = Math.max(1, magnitude * contrast >> 8);
		int normalX = Math.round(normal.x() * NORMAL_SCALE);
		int normalY = Math.round(normal.y() * NORMAL_SCALE);
		int normalZ = Math.round(normal.z() * NORMAL_SCALE);
		return ambient + (MODEL_LIGHT_X * normalX + MODEL_LIGHT_Y * normalY + MODEL_LIGHT_Z * normalZ) / intensity;
	}

	private static short adjustLightness(short hsl, int lightness)
	{
		int adjustedLightness = (hsl & 0x7F) * lightness >> 7;
		return (short) ((hsl & 0xFF80) + clampLightness(adjustedLightness));
	}

	private static int clampLightness(int lightness)
	{
		return clamp(lightness, 2, 126);
	}

	private static boolean isHidden(ModelDefinition model, int face)
	{
		if (faceRenderType(model, face) == 2)
		{
			return true;
		}
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

	private static int faceRenderType(ModelDefinition model, int face)
	{
		int type = model.faceRenderTypes == null || face >= model.faceRenderTypes.length
			? 0
			: model.faceRenderTypes[face] & 0xFF;
		if (model.faceTransparencies == null || face >= model.faceTransparencies.length)
		{
			return type;
		}

		byte alpha = model.faceTransparencies[face];
		if (alpha == -2)
		{
			return 3;
		}
		if (alpha == -1)
		{
			return 2;
		}
		return type;
	}

	private static float faceDepthBias(ModelDefinition model, int face)
	{
		if (model.faceZOffsets != null && face < model.faceZOffsets.length)
		{
			return model.faceZOffsets[face] & 0xFF;
		}
		return 0.0f;
	}

	private static short faceTexture(ModelDefinition model, int face)
	{
		if (model.faceTextures == null || face >= model.faceTextures.length)
		{
			return -1;
		}
		return model.faceTextures[face];
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

	private static void applyMayaApproximation(
		ModelDefinition model,
		int sourceFrame,
		int frameCount,
		int seed,
		boolean walking
	)
	{
		if (model.vertexCount <= 0)
		{
			return;
		}
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int y : model.vertexY)
		{
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}
		float span = Math.max(1.0f, maxY - minY);
		float phase = (float) (sourceFrame * Math.PI * 2.0 / Math.max(1, frameCount) + seed * 0.017);
		float amplitude = walking ? 3.0f : 1.5f;
		for (int i = 0; i < model.vertexCount; i++)
		{
			float heightWeight = (maxY - model.vertexY[i]) / span;
			float sway = (float) Math.sin(phase + (model.vertexX[i] + model.vertexZ[i]) * 0.006f) * amplitude * heightWeight;
			float bob = walking ? (float) Math.sin(phase * 2.0f) * 1.25f * (1.0f - heightWeight * 0.35f) : 0.0f;
			model.vertexX[i] += Math.round(sway);
			model.vertexY[i] += Math.round(bob);
		}
	}

	private static void recolor(ModelDefinition model, NpcDefinition3D definition)
	{
		if (definition.recolorToFind == null || definition.recolorToReplace == null)
		{
			return;
		}
		for (int i = 0; i < definition.recolorToFind.length && i < definition.recolorToReplace.length; i++)
		{
			model.recolor(definition.recolorToFind[i], definition.recolorToReplace[i]);
		}
	}

	private static void retexture(ModelDefinition model, NpcDefinition3D definition)
	{
		if (definition.retextureToFind == null || definition.retextureToReplace == null)
		{
			return;
		}
		for (int i = 0; i < definition.retextureToFind.length && i < definition.retextureToReplace.length; i++)
		{
			model.retexture(definition.retextureToFind[i], definition.retextureToReplace[i]);
		}
	}

	private static int spawnHash(NpcDefinition3D definition, NpcSpawnIndex.NpcSpawn spawn)
	{
		int hash = definition.id;
		hash = hash * 31 + spawn.worldX();
		hash = hash * 31 + spawn.worldY();
		hash = hash * 31 + spawn.plane();
		return hash;
	}

	private static int spawnRotation(int spawnDirection)
	{
		return SPAWN_DIRECTION_ROTATIONS[Math.floorMod(spawnDirection, SPAWN_DIRECTION_ROTATIONS.length)];
	}

	private static int rotationForDirection(int dx, int dy)
	{
		if (dx < 0)
		{
			if (dy > 0)
			{
				return 768;
			}
			if (dy < 0)
			{
				return 256;
			}
			return 512;
		}
		if (dx > 0)
		{
			if (dy > 0)
			{
				return 1280;
			}
			if (dy < 0)
			{
				return 1792;
			}
			return 1536;
		}
		if (dy > 0)
		{
			return 1024;
		}
		return 0;
	}

	private static TilePoint directionForRotation(int rotation)
	{
		return switch (normalizeRotation(rotation))
		{
			case 256 -> new TilePoint(-1, -1);
			case 512 -> new TilePoint(-1, 0);
			case 768 -> new TilePoint(-1, 1);
			case 1024 -> new TilePoint(0, 1);
			case 1280 -> new TilePoint(1, 1);
			case 1536 -> new TilePoint(1, 0);
			case 1792 -> new TilePoint(1, -1);
			default -> new TilePoint(0, -1);
		};
	}

	private static int normalizeRotation(int rotation)
	{
		return Math.floorMod(rotation, ROTATION_UNITS);
	}

	private static float yawForStep(TilePoint a, TilePoint b)
	{
		int dx = Integer.compare(b.x(), a.x());
		int dy = Integer.compare(b.y(), a.y());
		return yawRadians(rotationForDirection(dx, dy));
	}

	private static float yawRadians(int rotation)
	{
		return (float) (normalizeRotation(-rotation) * Math.PI * 2.0 / ROTATION_UNITS);
	}

	private static int totalFrameLength(int[] frameLengths)
	{
		int total = 0;
		for (int frameLength : frameLengths)
		{
			total += Math.max(1, frameLength);
		}
		return Math.max(1, total);
	}

	private static short[] fillShort(int length, short value)
	{
		short[] out = new short[length];
		Arrays.fill(out, value);
		return out;
	}

	private static byte[] fillByte(int length, byte value)
	{
		byte[] out = new byte[length];
		Arrays.fill(out, value);
		return out;
	}

	private static void copyIntArray(int[] source, int[] target, int targetOffset, int length)
	{
		if (source != null && target != null)
		{
			System.arraycopy(source, 0, target, targetOffset, Math.min(length, source.length));
		}
	}

	private static void copy2dArray(int[][] source, int[][] target, int targetOffset, int length)
	{
		if (source == null || target == null)
		{
			return;
		}
		for (int i = 0; i < length && i < source.length; i++)
		{
			target[targetOffset + i] = source[i] == null ? null : Arrays.copyOf(source[i], source[i].length);
		}
	}

	private static void copyByte(byte[] source, byte[] target, int sourceIndex, int targetIndex)
	{
		if (source != null && target != null && sourceIndex < source.length)
		{
			target[targetIndex] = source[sourceIndex];
		}
	}

	private static void copyShort(short[] source, short[] target, int sourceIndex, int targetIndex, short fallback)
	{
		if (target != null)
		{
			target[targetIndex] = source != null && sourceIndex < source.length ? source[sourceIndex] : fallback;
		}
	}

	private static final class BoundsBuilder
	{
		private float minX = Float.POSITIVE_INFINITY;
		private float minY = Float.POSITIVE_INFINITY;
		private float minZ = Float.POSITIVE_INFINITY;
		private float maxX = Float.NEGATIVE_INFINITY;
		private float maxY = Float.NEGATIVE_INFINITY;
		private float maxZ = Float.NEGATIVE_INFINITY;

		private void include(float[] vertexData)
		{
			if (vertexData == null)
			{
				return;
			}
			for (int offset = 0; offset + 2 < vertexData.length; offset += TerrainMesh.FLOATS_PER_VERTEX)
			{
				float x = vertexData[offset];
				float y = vertexData[offset + 1];
				float z = vertexData[offset + 2];
				if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
				{
					continue;
				}
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				minZ = Math.min(minZ, z);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
				maxZ = Math.max(maxZ, z);
			}
		}

		private NpcMesh.Bounds build()
		{
			if (!Float.isFinite(minX)
				|| !Float.isFinite(minY)
				|| !Float.isFinite(minZ)
				|| !Float.isFinite(maxX)
				|| !Float.isFinite(maxY)
				|| !Float.isFinite(maxZ))
			{
				return NpcMesh.Bounds.fallback();
			}
			return new NpcMesh.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
		}
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private record AnimationChoice(int sequenceId, SequenceDefinition sequence, boolean walking)
	{
		private static final AnimationChoice NONE = new AnimationChoice(-1, null, false);
	}

	private record FrameSet(
		int[] frameLengths,
		int frameStep,
		AnimatedObjectMesh.Frame[] frames,
		NpcMesh.Bounds bounds
	)
	{
		private long retainedBytes()
		{
			long bytes = (long) frameLengths.length * Integer.BYTES;
			bytes += 16L;
			for (AnimatedObjectMesh.Frame frame : frames)
			{
				if (frame != null)
				{
					bytes += frame.retainedVertexBytes();
				}
			}
			return bytes;
		}
	}

	static final class FrameCache
	{
		private static final long MAX_RETAINED_BYTES = 384L * 1024L * 1024L;

		private final LinkedHashMap<NpcAnimationKey, FrameSet> entries = new LinkedHashMap<>(64, 0.75f, true);
		private long retainedBytes;

		FrameSet computeIfAbsent(NpcAnimationKey key, Supplier<FrameSet> builder)
		{
			FrameSet existing = entries.get(key);
			if (existing != null)
			{
				return existing;
			}
			FrameSet created = builder.get();
			entries.put(key, created);
			retainedBytes += created.retainedBytes();
			trim();
			return created;
		}

		void clear()
		{
			entries.clear();
			retainedBytes = 0L;
		}

		private void trim()
		{
			if (retainedBytes <= MAX_RETAINED_BYTES)
			{
				return;
			}
			Iterator<Map.Entry<NpcAnimationKey, FrameSet>> iterator = entries.entrySet().iterator();
			while (retainedBytes > MAX_RETAINED_BYTES && iterator.hasNext())
			{
				Map.Entry<NpcAnimationKey, FrameSet> entry = iterator.next();
				retainedBytes -= entry.getValue().retainedBytes();
				iterator.remove();
			}
		}
	}

	private static final class NpcMeshEntry
	{
		private final int npcId;
		private final String name;
		private final int combatLevel;
		private final int sequenceId;
		private final boolean walkingAnimation;
		private final int[] frameLengths;
		private final int frameStep;
		private final AnimatedObjectMesh.Frame[] frames;
		private final NpcMesh.Bounds bounds;
		private final List<NpcMesh.Instance> instances = new ArrayList<>();

		private NpcMeshEntry(
			int npcId,
			String name,
			int combatLevel,
			int sequenceId,
			boolean walkingAnimation,
			int[] frameLengths,
			int frameStep,
			AnimatedObjectMesh.Frame[] frames,
			NpcMesh.Bounds bounds
		)
		{
			this.npcId = npcId;
			this.name = name;
			this.combatLevel = combatLevel;
			this.sequenceId = sequenceId;
			this.walkingAnimation = walkingAnimation;
			this.frameLengths = frameLengths;
			this.frameStep = frameStep;
			this.frames = frames;
			this.bounds = bounds == null ? NpcMesh.Bounds.fallback() : bounds;
		}

		private int totalFrameLength()
		{
			return NpcMeshBuilder.totalFrameLength(frameLengths);
		}
	}

	private record NpcAnimationKey(int npcId, int sequenceId, boolean walkingAnimation)
	{
	}

	private record TilePoint(int x, int y)
	{
	}

	private record StepChoice(TilePoint point, int rotation)
	{
	}

	private record PathPoint(float x, float y, float z)
	{
	}

	private record Vertex(float x, float y, float z)
	{
	}

	private record Normal(float x, float y, float z)
	{
	}

	private record TextureFace(
		int layer,
		SceneTextureSet.Material material,
		TextureVertex a,
		TextureVertex b,
		TextureVertex c
	)
	{
		private static final TextureFace NONE = new TextureFace(
			0,
			new SceneTextureSet.Material(0.0f, 0.0f, 0.0f),
			new TextureVertex(0.0f, 0.0f),
			new TextureVertex(0.0f, 0.0f),
			new TextureVertex(0.0f, 0.0f)
		);

		private boolean textured()
		{
			return layer > 0;
		}

		private TextureVertex vertex(int index)
		{
			return switch (index)
			{
				case 0 -> a;
				case 1 -> b;
				case 2 -> c;
				default -> throw new IllegalArgumentException("Texture vertex index out of range: " + index);
			};
		}
	}

	private record TextureVertex(float u, float v)
	{
	}
}
