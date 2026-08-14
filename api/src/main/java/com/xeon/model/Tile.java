package com.xeon.model;

import java.util.Objects;

public class Tile
{
	public Tile()
	{
	}

	public int x;
	public int y;
	public int z;

	public Tile(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof Tile))
		{
			return false;
		}
		Tile t = (Tile) o;
		return x == t.x && y == t.y && z == t.z;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y, z);
	}

	@Override
	public String toString()
	{
		return "x=" + x + ",y=" + y + ",z=" + z;
	}
}
