package com.xeon.view3d;

public final class Map3DMesh
{
	private final double[] positions;
	private final int[] triangles;
	private final int[] colors;

	public Map3DMesh(double[] positions, int[] triangles)
	{
		this(positions, triangles, null);
	}

	public Map3DMesh(double[] positions, int[] triangles, int[] faceColors)
	{
		if (positions == null || triangles == null || positions.length % 3 != 0 || triangles.length % 3 != 0)
		{
			throw new IllegalArgumentException("Expected XYZ positions and triangle indices");
		}
		this.positions = positions.clone();
		this.triangles = triangles.clone();
		for (double value : this.positions)
		{
			if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite mesh position");
		}
		for (int index : this.triangles)
		{
			if (index < 0 || index >= vertexCount()) throw new IllegalArgumentException("Invalid mesh index");
		}
		if (faceColors != null && faceColors.length != faceCount())
		{
			throw new IllegalArgumentException("Expected one ARGB color per face");
		}
		colors = faceColors == null ? null : faceColors.clone();
	}

	public static Map3DMesh fromTriangles(float[] xyz)
	{
		if (xyz == null || xyz.length % 9 != 0) throw new IllegalArgumentException("Expected XYZ triangles");
		double[] positions = new double[xyz.length];
		int[] triangles = new int[xyz.length / 3];
		for (int i = 0; i < xyz.length; i++) positions[i] = xyz[i];
		for (int i = 0; i < triangles.length; i++) triangles[i] = i;
		return new Map3DMesh(positions, triangles);
	}

	public int vertexCount() { return positions.length / 3; }
	public int faceCount() { return triangles.length / 3; }
	public double coordinate(int vertex, int axis) { return positions[vertex * 3 + axis]; }
	public int vertexIndex(int face, int corner) { return triangles[face * 3 + corner]; }
	public int faceColor(int face) { return colors == null ? 0xFFB9BEC6 : colors[face]; }
	public double[] positions() { return positions.clone(); }
	public int[] triangles() { return triangles.clone(); }
}
