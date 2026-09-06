package com.xeon.plugins.meshexport;

import com.xeon.view3d.Map3DMesh;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

final class MeshTopology
{
	static final double EPS = 1e-10;
	final List<Vec> vertices = new ArrayList<>();
	final List<Face> faces = new ArrayList<>();
	private final Map<PointKey, Integer> vertexIds = new HashMap<>();
	private final Set<FaceKey> faceIds = new HashSet<>();
	private final Map<FaceKey, Face> firstFaces = new HashMap<>();
	private final Set<Face> oppositeDuplicates = new HashSet<>();

	static void checkCancelled()
	{
		if (Thread.currentThread().isInterrupted())
		{
			throw new CancellationException();
		}
	}

	void add(Vec a, Vec b, Vec c)
	{
		if (b.minus(a).cross(c.minus(a)).lengthSquared() <= 1e-28)
		{
			return;
		}
		int ai = vertex(a), bi = vertex(b), ci = vertex(c);
		if (ai == bi || bi == ci || ci == ai)
		{
			return;
		}
		FaceKey key = FaceKey.of(ai, bi, ci);
		if (faceIds.add(key))
		{
			Face face = new Face(ai, bi, ci);
			faces.add(face);
			firstFaces.put(key, face);
		}
		else if (!firstFaces.get(key).directed(ai, bi))
		{
			oppositeDuplicates.add(firstFaces.get(key));
		}
	}

	MeshTopology removeCoincidentInternalFaces()
	{
		if (oppositeDuplicates.isEmpty() || diagnostics().boundaryEdges() != 0)
		{
			return this;
		}
		MeshTopology candidate = new MeshTopology();
		for (Face face : faces)
		{
			if (!oppositeDuplicates.contains(face))
			{
				candidate.add(vertices.get(face.a), vertices.get(face.b), vertices.get(face.c));
			}
		}
		// Opposite sheets alone must not vanish. Accept cancellation only when it leaves a closed shell.
		return !candidate.faces.isEmpty() && candidate.diagnostics().closed() ? candidate : this;
	}

	private int vertex(Vec point)
	{
		PointKey key = new PointKey(Math.round(point.x / EPS), Math.round(point.y / EPS), Math.round(point.z / EPS));
		return vertexIds.computeIfAbsent(key, ignored -> {
			vertices.add(point);
			return vertices.size() - 1;
		});
	}

	Map<Edge, List<Integer>> edges()
	{
		Map<Edge, List<Integer>> edges = new LinkedHashMap<>();
		for (int i = 0; i < faces.size(); i++)
		{
			Face face = faces.get(i);
			for (int c = 0; c < 3; c++)
			{
				edges.computeIfAbsent(Edge.of(face.at(c), face.at((c + 1) % 3)),
					ignored -> new ArrayList<>(2)).add(i);
			}
		}
		return edges;
	}

	List<Component> components()
	{
		Map<Edge, List<Integer>> edges = edges();
		boolean[] visited = new boolean[faces.size()];
		boolean[] flip = new boolean[faces.size()];
		List<Component> result = new ArrayList<>();
		for (int start = 0; start < faces.size(); start++)
		{
			if (visited[start])
			{
				continue;
			}
			checkCancelled();
			List<Integer> members = new ArrayList<>();
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(start);
			visited[start] = true;
			boolean closed = true;
			while (!queue.isEmpty())
			{
				int current = queue.remove();
				members.add(current);
				Face face = faces.get(current);
				for (int c = 0; c < 3; c++)
				{
					int from = face.at(c), to = face.at((c + 1) % 3);
					List<Integer> adjacent = edges.get(Edge.of(from, to));
					if (adjacent.size() != 2)
					{
						closed = false;
					}
					for (int neighbor : adjacent)
					{
						if (neighbor == current)
						{
							continue;
						}
						boolean expectedFlip = flip[current] ^ faces.get(neighbor).directed(from, to);
						if (visited[neighbor])
						{
							if (flip[neighbor] != expectedFlip)
							{
								closed = false;
							}
						}
						else
						{
							visited[neighbor] = true;
							flip[neighbor] = expectedFlip;
							queue.add(neighbor);
						}
					}
				}
			}
			List<Face> oriented = new ArrayList<>();
			double volume = 0;
			for (int member : members)
			{
				Face face = flip[member] ? faces.get(member).flipped() : faces.get(member);
				oriented.add(face);
				volume += vertices.get(face.a).dot(vertices.get(face.b).cross(vertices.get(face.c))) / 6;
			}
			if (Math.abs(volume) < 1e-18)
			{
				closed = false;
			}
			if (closed && volume < 0)
			{
				oriented.replaceAll(Face::flipped);
			}
			result.add(new Component(oriented, closed));
		}
		return result;
	}

	Diagnostics diagnostics()
	{
		int boundary = 0, nonManifold = 0, inconsistent = 0;
		for (Map.Entry<Edge, List<Integer>> entry : edges().entrySet())
		{
			List<Integer> adjacent = entry.getValue();
			if (adjacent.size() == 1)
			{
				boundary++;
			}
			else if (adjacent.size() > 2)
			{
				nonManifold++;
			}
			else if (faces.get(adjacent.get(0)).directed(entry.getKey().a, entry.getKey().b)
				== faces.get(adjacent.get(1)).directed(entry.getKey().a, entry.getKey().b))
			{
				inconsistent++;
			}
		}
		return new Diagnostics(boundary, nonManifold, inconsistent);
	}

	Map3DMesh toMesh(Vec center, double extent)
	{
		// Only referenced vertices are retained, so discarded internal parts cannot affect export bounds.
		Map<Integer, Integer> used = new LinkedHashMap<>();
		int[] indices = new int[faces.size() * 3];
		for (int f = 0; f < faces.size(); f++)
		{
			for (int c = 0; c < 3; c++)
			{
				indices[f * 3 + c] = used.computeIfAbsent(faces.get(f).at(c), ignored -> used.size());
			}
		}
		double[] positions = new double[used.size() * 3];
		used.forEach((source, target) -> {
			Vec point = vertices.get(source).times(extent).plus(center);
			positions[target * 3] = point.x;
			positions[target * 3 + 1] = point.y;
			positions[target * 3 + 2] = point.z;
		});
		return new Map3DMesh(positions, indices);
	}

	record Vec(double x, double y, double z)
	{
		Vec plus(Vec b)
		{
			return new Vec(x + b.x, y + b.y, z + b.z);
		}

		Vec minus(Vec b)
		{
			return new Vec(x - b.x, y - b.y, z - b.z);
		}

		Vec times(double s)
		{
			return new Vec(x * s, y * s, z * s);
		}

		double dot(Vec b)
		{
			return x * b.x + y * b.y + z * b.z;
		}

		Vec cross(Vec b)
		{
			return new Vec(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x);
		}

		double lengthSquared()
		{
			return dot(this);
		}

		Vec normalized()
		{
			return times(1 / Math.sqrt(lengthSquared()));
		}
	}

	record Face(int a, int b, int c)
	{
		int at(int corner)
		{
			return corner == 0 ? a : corner == 1 ? b : c;
		}

		Face flipped()
		{
			return new Face(c, b, a);
		}

		boolean directed(int from, int to)
		{
			return a == from && b == to || b == from && c == to || c == from && a == to;
		}
	}

	record Edge(int a, int b)
	{
		static Edge of(int a, int b)
		{
			return new Edge(Math.min(a, b), Math.max(a, b));
		}
	}

	private record PointKey(long x, long y, long z)
	{
	}

	private record FaceKey(int a, int b, int c)
	{
		static FaceKey of(int a, int b, int c)
		{
			int min = Math.min(a, Math.min(b, c)), max = Math.max(a, Math.max(b, c));
			return new FaceKey(min, a + b + c - min - max, max);
		}
	}

	record Component(List<Face> faces, boolean closed)
	{
	}

	record Diagnostics(int boundaryEdges, int nonManifoldEdges, int inconsistentEdges)
	{
		boolean closed()
		{
			return boundaryEdges == 0 && nonManifoldEdges == 0 && inconsistentEdges == 0;
		}
	}
}
