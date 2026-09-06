package com.xeon.view3d;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CachedMeshFixtures
{
	public record NamedMesh(String name, Map3DMesh mesh) { }

	public static List<NamedMesh> load(Path cache) throws Exception
	{
		List<NamedMesh> result = new ArrayList<>();
		try (TerrainRegionLoader.Session session = new TerrainRegionLoader().open(cache))
		{
			Set<String> names = new HashSet<>(Set.of("Man", "Guard", "Cow", "Chicken", "Giant rat"));
			for (var npc : session.npcCatalog())
			{
				if (!names.contains(npc.name())) continue;
				Map3DMesh mesh = session.npcStaticMesh(npc.id());
				if (mesh == null || mesh.faceCount() == 0) continue;
				names.remove(npc.name());
				result.add(new NamedMesh("NPC " + npc.id() + " " + npc.name(), mesh));
				if (names.isEmpty()) break;
			}
			TerrainMesh region = session.loadRegion(12850);
			Set<Integer> objects = new HashSet<>();
			for (ObjectOverlayMesh object : region.objectOverlays())
			{
				if (!objects.add(object.objectId())) continue;
				result.add(new NamedMesh("Object " + object.objectId() + " " + object.name(), Map3DMesh.fromTriangles(object.rawVertexData())));
				if (objects.size() == 15) break;
			}
		}
		return result;
	}

	public static List<NamedMesh> loadNpcSample(Path cache, int limit) throws Exception
	{
		List<NamedMesh> result = new ArrayList<>();
		try (TerrainRegionLoader.Session session = new TerrainRegionLoader().open(cache))
		{
			for (var npc : session.npcCatalog())
			{
				if (npc.name() == null || npc.name().isBlank() || "null".equalsIgnoreCase(npc.name())) continue;
				Map3DMesh mesh = session.npcStaticMesh(npc.id());
				if (mesh == null || mesh.faceCount() == 0) continue;
				result.add(new NamedMesh("NPC " + npc.id() + " " + npc.name(), mesh));
				if (result.size() >= limit) break;
			}
		}
		return result;
	}
}
