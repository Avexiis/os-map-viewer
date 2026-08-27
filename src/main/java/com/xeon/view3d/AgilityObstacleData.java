/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Portions of the obstacle id/category data are copied or adapted from the
 * RuneLite agility plugin and AgilityShortcut definitions:
 *
 * Copyright (c) 2018, SomeoneWithAnInternetConnection
 * Copyright (c) 2019, MrGroggle
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

import com.xeon.model.Tile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;

final class AgilityObstacleData
{
	private static final String AGILITY_OBSTACLES_RESOURCE = "/com/xeon/application/data/agility_obstacles.tsv";
	private static final String AGILITY_SHORTCUTS_RESOURCE =
		"/com/xeon/application/data/transports/agility_shortcuts.tsv";
	private static final int TYPE_GAME_OBJECT = 10;
	private static final int TYPE_GAME_OBJECT_DIAGONAL = 11;
	private static final Set<Integer> TRAP_OBSTACLE_REGIONS = Set.of(12105, 13356);

	private static volatile Data loadedData;

	private AgilityObstacleData()
	{
	}

	enum Kind
	{
		COURSE,
		SHORTCUT,
		PORTAL,
		TRAP,
		SEPULCHRE,
		SEPULCHRE_SKILL
	}

	static List<AgilityObstacleInstance> collect(Region region, ObjectManager objectManager)
	{
		if (region == null || objectManager == null)
		{
			return List.of();
		}
		Data data = data();
		if (data.definitions().isEmpty())
		{
			return List.of();
		}

		List<AgilityObstacleInstance> instances = new ArrayList<>();
		Set<InstanceKey> seen = new HashSet<>();
		for (Location location : region.getLocations())
		{
			Position position = location.getPosition();
			if (position == null)
			{
				continue;
			}
			int sourcePlane = position.getZ();
			if (sourcePlane < 0 || sourcePlane >= Region.Z)
			{
				continue;
			}
			int localX = position.getX() - region.getBaseX();
			int localY = position.getY() - region.getBaseY();
			if (localX < 0 || localY < 0 || localX >= Region.X || localY >= Region.Y)
			{
				continue;
			}
			int displayPlane = SceneTileFlags.displayPlaneForSource(region, sourcePlane, localX, localY);
			if (displayPlane < 0 || displayPlane >= Region.Z)
			{
				continue;
			}

			ObjectDefinition baseDefinition = objectManager.getObject(location.getId());
			ObjectDefinition renderedDefinition = ObjectMeshBuilder.completionStateDefinition(objectManager, baseDefinition);
			int renderedObjectId = renderedDefinition == null ? -1 : renderedDefinition.getId();
			MatchedDefinition matched = match(data, location.getId(), renderedObjectId,
				position.getX(), position.getY(), sourcePlane, region.getRegionID());
			if (matched == null)
			{
				continue;
			}

			Footprint footprint = footprint(renderedDefinition == null ? baseDefinition : renderedDefinition,
				location.getType(), location.getOrientation());
			InstanceKey key = new InstanceKey(
				position.getX(),
				position.getY(),
				displayPlane,
				footprint.width(),
				footprint.height(),
				location.getId(),
				renderedObjectId
			);
			if (!seen.add(key))
			{
				continue;
			}
			instances.add(new AgilityObstacleInstance(
				new Tile(position.getX(), position.getY(), displayPlane),
				footprint.width(),
				footprint.height(),
				location.getId(),
				renderedObjectId,
				matched.kind(),
				matched.level(),
				matched.label()
			));
		}
		instances.sort(Comparator
			.comparingInt((AgilityObstacleInstance obstacle) -> obstacle.tile().z)
			.thenComparingInt(obstacle -> obstacle.tile().x)
			.thenComparingInt(obstacle -> obstacle.tile().y)
			.thenComparingInt(AgilityObstacleInstance::objectId));
		return instances.isEmpty() ? List.of() : List.copyOf(instances);
	}

	private static MatchedDefinition match(Data data, int objectId, int renderedObjectId,
	                                       int worldX, int worldY, int plane, int regionId)
	{
		Definition definition = bestDefinition(data.definitions().get(objectId), worldX, worldY, plane, regionId);
		if (definition == null && renderedObjectId >= 0 && renderedObjectId != objectId)
		{
			definition = bestDefinition(data.definitions().get(renderedObjectId), worldX, worldY, plane, regionId);
		}
		if (definition == null)
		{
			return null;
		}

		ShortcutRequirement requirement = bestRequirement(data.requirements().get(objectId), worldX, worldY, plane);
		if (requirement == null && renderedObjectId >= 0 && renderedObjectId != objectId)
		{
			requirement = bestRequirement(data.requirements().get(renderedObjectId), worldX, worldY, plane);
		}

		int definitionLevel = definition.level();
		int requirementLevel = requirement == null ? -1 : requirement.level();
		int level = Math.max(definitionLevel, requirementLevel);
		String label = displayLabel(definition, requirement);
		return new MatchedDefinition(definition.kind(), level, label);
	}

	private static Definition bestDefinition(List<Definition> definitions, int worldX, int worldY, int plane, int regionId)
	{
		if (definitions == null || definitions.isEmpty())
		{
			return null;
		}
		Definition best = null;
		int bestScore = Integer.MAX_VALUE;
		for (Definition definition : definitions)
		{
			if (definition.kind() == Kind.TRAP && !TRAP_OBSTACLE_REGIONS.contains(regionId))
			{
				continue;
			}
			int score = definition.distanceScore(worldX, worldY, plane);
			if (score < bestScore)
			{
				best = definition;
				bestScore = score;
			}
		}
		return best;
	}

	private static ShortcutRequirement bestRequirement(List<ShortcutRequirement> requirements,
	                                                   int worldX, int worldY, int plane)
	{
		if (requirements == null || requirements.isEmpty())
		{
			return null;
		}
		ShortcutRequirement best = null;
		int bestScore = Integer.MAX_VALUE;
		for (ShortcutRequirement requirement : requirements)
		{
			int score = requirement.distanceScore(worldX, worldY, plane);
			if (score < bestScore)
			{
				best = requirement;
				bestScore = score;
			}
		}
		return best;
	}

	private static String displayLabel(Definition definition, ShortcutRequirement requirement)
	{
		String label = definition.label();
		if (requirement != null && !requirement.label().isBlank() && genericLabel(label))
		{
			return requirement.label();
		}
		return label;
	}

	private static boolean genericLabel(String label)
	{
		return label == null
			|| label.isBlank()
			|| "Course obstacle".equalsIgnoreCase(label)
			|| "Shortcut".equalsIgnoreCase(label);
	}

	private static Footprint footprint(ObjectDefinition definition, int type, int orientation)
	{
		int width = 1;
		int height = 1;
		if (definition != null && (type == TYPE_GAME_OBJECT || type == TYPE_GAME_OBJECT_DIAGONAL))
		{
			width = Math.max(1, definition.getSizeX());
			height = Math.max(1, definition.getSizeY());
			if ((orientation & 1) == 1)
			{
				int tmp = width;
				width = height;
				height = tmp;
			}
		}
		return new Footprint(width, height);
	}

	private static Data data()
	{
		Data current = loadedData;
		if (current != null)
		{
			return current;
		}
		synchronized (AgilityObstacleData.class)
		{
			current = loadedData;
			if (current == null)
			{
				current = new Data(loadDefinitions(), loadRequirements());
				loadedData = current;
			}
			return current;
		}
	}

	private static Map<Integer, List<Definition>> loadDefinitions()
	{
		Map<Integer, List<Definition>> definitions = new LinkedHashMap<>();
		try (BufferedReader reader = resourceReader(AGILITY_OBSTACLES_RESOURCE))
		{
			if (reader == null)
			{
				System.err.println("Missing agility obstacle resource " + AGILITY_OBSTACLES_RESOURCE);
				return Map.of();
			}
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isBlank() || line.startsWith("#"))
				{
					continue;
				}
				String[] fields = line.split("\\t", -1);
				if (fields.length < 8)
				{
					continue;
				}
				int objectId = parseInt(fields[0], -1);
				Kind kind = parseKind(fields[1]);
				if (objectId < 0 || kind == null)
				{
					continue;
				}
				Definition definition = new Definition(
					objectId,
					kind,
					parseInt(fields[2], -1),
					fields[3],
					parseNullableInt(fields[4]),
					parseNullableInt(fields[5]),
					parseNullableInt(fields[6])
				);
				definitions.computeIfAbsent(objectId, ignored -> new ArrayList<>()).add(definition);
			}
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load agility obstacle data: " + ex.getMessage());
			return Map.of();
		}
		return freeze(definitions);
	}

	private static Map<Integer, List<ShortcutRequirement>> loadRequirements()
	{
		Map<Integer, List<ShortcutRequirement>> requirements = new LinkedHashMap<>();
		try (BufferedReader reader = resourceReader(AGILITY_SHORTCUTS_RESOURCE))
		{
			if (reader == null)
			{
				return Map.of();
			}
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isBlank() || line.startsWith("#"))
				{
					continue;
				}
				String[] fields = line.split("\\t", -1);
				if (fields.length < 4)
				{
					continue;
				}
				WorldPoint origin = parseWorldPoint(fields[0]);
				int objectId = parseObjectId(fields[2]);
				int level = parseAgilityLevel(fields[3]);
				if (origin == null || objectId < 0 || level < 0)
				{
					continue;
				}
				ShortcutRequirement requirement = new ShortcutRequirement(
					objectId,
					level,
					objectInfoLabel(fields[2]),
					origin.x(),
					origin.y(),
					origin.plane()
				);
				requirements.computeIfAbsent(objectId, ignored -> new ArrayList<>()).add(requirement);
			}
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load agility shortcut requirements: " + ex.getMessage());
			return Map.of();
		}
		return freeze(requirements);
	}

	private static <T> Map<Integer, List<T>> freeze(Map<Integer, List<T>> source)
	{
		if (source.isEmpty())
		{
			return Map.of();
		}
		Map<Integer, List<T>> out = new LinkedHashMap<>();
		source.forEach((key, value) -> out.put(key, List.copyOf(value)));
		return Map.copyOf(out);
	}

	private static BufferedReader resourceReader(String resource) throws IOException
	{
		InputStream in = AgilityObstacleData.class.getResourceAsStream(resource);
		return in == null ? null : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	private static Kind parseKind(String value)
	{
		if (value == null || value.isBlank())
		{
			return null;
		}
		try
		{
			return Kind.valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	private static int parseObjectId(String objectInfo)
	{
		if (objectInfo == null || objectInfo.isBlank())
		{
			return -1;
		}
		String[] parts = objectInfo.trim().split("\\s+");
		return parts.length == 0 ? -1 : parseInt(parts[parts.length - 1], -1);
	}

	private static int parseAgilityLevel(String skills)
	{
		if (skills == null || skills.isBlank())
		{
			return -1;
		}
		for (String requirement : skills.split(";"))
		{
			String[] parts = requirement.trim().split("\\s+");
			if (parts.length >= 2 && "agility".equalsIgnoreCase(parts[1]))
			{
				return parseInt(parts[0], -1);
			}
		}
		return -1;
	}

	private static WorldPoint parseWorldPoint(String value)
	{
		if (value == null || value.isBlank())
		{
			return null;
		}
		String[] parts = value.trim().split("\\s+");
		if (parts.length != 3)
		{
			return null;
		}
		int x = parseInt(parts[0], -1);
		int y = parseInt(parts[1], -1);
		int plane = parseInt(parts[2], -1);
		return x < 0 || y < 0 || plane < 0 ? null : new WorldPoint(x, y, plane);
	}

	private static String objectInfoLabel(String objectInfo)
	{
		if (objectInfo == null)
		{
			return "";
		}
		return objectInfo.trim().replaceFirst("\\s+\\d+$", "").replaceAll("\\s+", " ");
	}

	private static Integer parseNullableInt(String value)
	{
		int parsed = parseInt(value, Integer.MIN_VALUE);
		return parsed == Integer.MIN_VALUE ? null : parsed;
	}

	private static int parseInt(String value, int fallback)
	{
		if (value == null || value.isBlank())
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	private record Data(
		Map<Integer, List<Definition>> definitions,
		Map<Integer, List<ShortcutRequirement>> requirements
	)
	{
	}

	private record Definition(
		int objectId,
		Kind kind,
		int level,
		String label,
		Integer worldX,
		Integer worldY,
		Integer plane
	)
	{
		Definition
		{
			level = Math.max(-1, level);
			label = label == null ? "" : label.trim();
		}

		private int distanceScore(int x, int y, int z)
		{
			if (worldX == null || worldY == null || plane == null)
			{
				return 1_000_000;
			}
			int planeScore = plane == z ? 0 : 500_000;
			return planeScore + Math.max(Math.abs(worldX - x), Math.abs(worldY - y));
		}
	}

	private record ShortcutRequirement(
		int objectId,
		int level,
		String label,
		int worldX,
		int worldY,
		int plane
	)
	{
		ShortcutRequirement
		{
			level = Math.max(-1, level);
			label = label == null ? "" : label.trim();
		}

		private int distanceScore(int x, int y, int z)
		{
			int planeScore = plane == z ? 0 : 500_000;
			return planeScore + Math.max(Math.abs(worldX - x), Math.abs(worldY - y));
		}
	}

	private record MatchedDefinition(
		Kind kind,
		int level,
		String label
	)
	{
	}

	private record Footprint(
		int width,
		int height
	)
	{
	}

	private record InstanceKey(
		int x,
		int y,
		int plane,
		int width,
		int height,
		int objectId,
		int renderedObjectId
	)
	{
	}

	private record WorldPoint(
		int x,
		int y,
		int plane
	)
	{
	}
}
