package com.xeon.view3d;

import com.xeon.model.Tile;
import java.util.Objects;

public record Map3DEntity(Kind kind, int id, String name, Tile tile, MeshSource meshSource)
{
	public enum Kind { NPC, OBJECT }

	@FunctionalInterface
	public interface MeshSource
	{
		Map3DMesh load() throws Exception;
	}

	public Map3DEntity
	{
		Objects.requireNonNull(kind);
		Objects.requireNonNull(meshSource);
		name = name == null ? "" : name;
		tile = tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}

	@Override
	public Tile tile()
	{
		return tile == null ? null : new Tile(tile.x, tile.y, tile.z);
	}
}
