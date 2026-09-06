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
package com.xeon.plugins.meshexport;

import com.xeon.view3d.Map3DMesh;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
	private static final double REPAIR_THICKNESS = 1e-5;
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
		return !candidate.faces.isEmpty() && candidate.diagnostics().closed() ? candidate : this;
	}

	Enclosed removeEnclosedComponents()
	{
		List<Component> components = components();
		if (components.size() < 2)
		{
			return new Enclosed(this, 0, 0);
		}
		List<Bounds> bounds = components.stream().map(this::bounds).toList();
		Set<Integer> removed = new HashSet<>();
		int removedFaces = 0;
		for (int candidate = 0; candidate < components.size(); candidate++)
		{
			checkCancelled();
			Set<Integer> candidateVertices = componentVertices(components.get(candidate));
			for (int container = 0; container < components.size(); container++)
			{
				if (candidate == container || !bounds.get(container).strictlyContains(bounds.get(candidate)))
				{
					continue;
				}
				boolean enclosed = true;
				for (int vertex : candidateVertices)
				{
					if (!inside(vertices.get(vertex), components.get(container)))
					{
						enclosed = false;
						break;
					}
				}
				if (enclosed)
				{
					removed.add(candidate);
					removedFaces += components.get(candidate).faces().size();
					break;
				}
			}
		}
		if (removed.isEmpty())
		{
			return new Enclosed(this, 0, 0);
		}
		MeshTopology result = new MeshTopology();
		for (int component = 0; component < components.size(); component++)
		{
			if (!removed.contains(component))
			{
				for (Face face : components.get(component).faces())
				{
					addFace(result, face);
				}
			}
		}
		return new Enclosed(result, removed.size(), removedFaces);
	}

	private Bounds bounds(Component component)
	{
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (int vertex : componentVertices(component))
		{
			Vec point = vertices.get(vertex);
			minX = Math.min(minX, point.x());
			minY = Math.min(minY, point.y());
			minZ = Math.min(minZ, point.z());
			maxX = Math.max(maxX, point.x());
			maxY = Math.max(maxY, point.y());
			maxZ = Math.max(maxZ, point.z());
		}
		return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static Set<Integer> componentVertices(Component component)
	{
		Set<Integer> result = new HashSet<>();
		for (Face face : component.faces())
		{
			result.add(face.a());
			result.add(face.b());
			result.add(face.c());
		}
		return result;
	}

	private boolean inside(Vec point, Component component)
	{
		double solidAngle = 0;
		for (Face face : component.faces())
		{
			Vec a = vertices.get(face.a()).minus(point);
			Vec b = vertices.get(face.b()).minus(point);
			Vec c = vertices.get(face.c()).minus(point);
			double denominator = Math.sqrt(a.lengthSquared() * b.lengthSquared() * c.lengthSquared())
				+ a.dot(b) * Math.sqrt(c.lengthSquared())
				+ b.dot(c) * Math.sqrt(a.lengthSquared())
				+ c.dot(a) * Math.sqrt(b.lengthSquared());
			solidAngle += 2 * Math.atan2(a.dot(b.cross(c)), denominator);
		}
		return Math.abs(solidAngle) > Math.PI * 3;
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

	Repair repair()
	{
		Diagnostics before = diagnostics();
		MeshTopology repaired = stitchBoundaryEdges().oriented();
		Solidified solidified = repaired.solidifyOpenDetails();
		repaired = solidified.mesh().oriented();
		int removedFaces = 0;
		int filledLoops = 0;
		int addedFaces = 0;
		for (int attempt = 0; attempt < 8; attempt++)
		{
			checkCancelled();
			Pruned pruned = repaired.pruneNonManifoldEdges();
			Solidified detached = pruned.mesh().stitchBoundaryEdges().oriented().solidifyOpenDetails();
			MeshTopology consistentlyOriented = detached.mesh().oriented();
			Filled filled = consistentlyOriented.fillBoundaryLoops();
			repaired = filled.mesh().oriented();
			removedFaces += pruned.removedFaces();
			filledLoops += filled.filledLoops();
			addedFaces += filled.addedFaces();
			solidified = new Solidified(repaired, solidified.sheets() + detached.sheets());
			Diagnostics current = repaired.diagnostics();
			if (current.closed() && repaired.components().stream().allMatch(Component::closed))
			{
				break;
			}
			if (pruned.removedFaces() == 0 && filled.filledLoops() == 0 && detached.sheets() == 0)
			{
				break;
			}
		}
		Diagnostics after = repaired.diagnostics();
		boolean printable = !repaired.faces.isEmpty()
			&& after.closed()
			&& repaired.components().stream().allMatch(Component::closed);
		return new Repair(repaired, before, after, removedFaces, filledLoops, addedFaces,
			solidified.sheets(), printable);
	}

	private Solidified solidifyOpenDetails()
	{
		MeshTopology result = new MeshTopology();
		int sheets = 0;
		for (Component component : components())
		{
			Map<Edge, List<Face>> componentEdges = componentEdges(component);
			Vec normal = planarNormal(component);
			boolean openManifold = componentEdges.values().stream().anyMatch(adjacent -> adjacent.size() == 1)
				&& componentEdges.values().stream().noneMatch(adjacent -> adjacent.size() > 2);
			if (component.closed() || !openManifold || (normal == null && component.faces().size() > 8))
			{
				for (Face face : component.faces())
				{
					addFace(result, face);
				}
				continue;
			}
			Map<Integer, Vec> normals = repairNormals(component, normal);
			Map<Integer, Vec> front = new HashMap<>();
			Map<Integer, Vec> back = new HashMap<>();
			for (Face face : component.faces())
			{
				for (int corner = 0; corner < 3; corner++)
				{
					int vertex = face.at(corner);
					Vec offset = normals.get(vertex).times(REPAIR_THICKNESS / 2);
					front.computeIfAbsent(vertex, ignored -> vertices.get(vertex).plus(offset));
					back.computeIfAbsent(vertex, ignored -> vertices.get(vertex).minus(offset));
				}
				result.add(front.get(face.a()), front.get(face.b()), front.get(face.c()));
				result.add(back.get(face.c()), back.get(face.b()), back.get(face.a()));
			}
			for (Map.Entry<Edge, List<Face>> entry : componentEdges.entrySet())
			{
				if (entry.getValue().size() != 1)
				{
					continue;
				}
				BoundaryEdge boundary = boundaryEdge(entry.getKey(), entry.getValue().get(0));
				Vec fromFront = front.get(boundary.from());
				Vec toFront = front.get(boundary.to());
				Vec fromBack = back.get(boundary.from());
				Vec toBack = back.get(boundary.to());
				result.add(toFront, fromFront, fromBack);
				result.add(toFront, fromBack, toBack);
			}
			sheets++;
		}
		return new Solidified(result, sheets);
	}

	private Map<Integer, Vec> repairNormals(Component component, Vec planarNormal)
	{
		Map<Integer, Vec> normals = new HashMap<>();
		if (planarNormal != null)
		{
			for (Face face : component.faces())
			{
				for (int corner = 0; corner < 3; corner++)
				{
					normals.put(face.at(corner), planarNormal);
				}
			}
			return normals;
		}
		for (Face face : component.faces())
		{
			Vec a = vertices.get(face.a());
			Vec faceNormal = vertices.get(face.b()).minus(a).cross(vertices.get(face.c()).minus(a));
			for (int corner = 0; corner < 3; corner++)
			{
				normals.merge(face.at(corner), faceNormal, Vec::plus);
			}
		}
		normals.replaceAll((vertex, value) -> value.lengthSquared() > 1e-28
			? value.normalized() : faceNormal(component.faces().get(0)));
		return normals;
	}

	private Vec faceNormal(Face face)
	{
		Vec a = vertices.get(face.a());
		return vertices.get(face.b()).minus(a).cross(vertices.get(face.c()).minus(a)).normalized();
	}

	private Map<Edge, List<Face>> componentEdges(Component component)
	{
		Map<Edge, List<Face>> result = new LinkedHashMap<>();
		for (Face face : component.faces())
		{
			for (int corner = 0; corner < 3; corner++)
			{
				result.computeIfAbsent(Edge.of(face.at(corner), face.at((corner + 1) % 3)),
					ignored -> new ArrayList<>(2)).add(face);
			}
		}
		return result;
	}

	private Vec planarNormal(Component component)
	{
		Face first = component.faces().get(0);
		Vec origin = vertices.get(first.a());
		Vec normal = vertices.get(first.b()).minus(origin).cross(vertices.get(first.c()).minus(origin)).normalized();
		for (Face face : component.faces())
		{
			for (int corner = 0; corner < 3; corner++)
			{
				if (Math.abs(vertices.get(face.at(corner)).minus(origin).dot(normal)) > EPS)
				{
					return null;
				}
			}
		}
		return normal;
	}

	private MeshTopology stitchBoundaryEdges()
	{
		Map<Edge, List<Integer>> edgeFaces = edges();
		Map<Edge, List<Vec>> splits = new HashMap<>();
		for (Map.Entry<Edge, List<Integer>> entry : edgeFaces.entrySet())
		{
			if (entry.getValue().size() != 1)
			{
				continue;
			}
			Vec a = vertices.get(entry.getKey().a());
			Vec b = vertices.get(entry.getKey().b());
			List<Vec> points = new ArrayList<>();
			for (int vertex = 0; vertex < vertices.size(); vertex++)
			{
				if (vertex != entry.getKey().a() && vertex != entry.getKey().b()
					&& onEdge(vertices.get(vertex), a, b))
				{
					points.add(vertices.get(vertex));
				}
			}
			if (!points.isEmpty())
			{
				splits.put(entry.getKey(), points);
			}
		}
		if (splits.isEmpty())
		{
			return this;
		}
		MeshTopology result = new MeshTopology();
		for (Face face : faces)
		{
			checkCancelled();
			List<Vec[]> pieces = new ArrayList<>();
			pieces.add(new Vec[]{vertices.get(face.a()), vertices.get(face.b()), vertices.get(face.c())});
			for (int edge = 0; edge < 3; edge++)
			{
				List<Vec> points = splits.get(Edge.of(face.at(edge), face.at((edge + 1) % 3)));
				if (points == null)
				{
					continue;
				}
				for (Vec point : points)
				{
					List<Vec[]> next = new ArrayList<>();
					for (Vec[] piece : pieces)
					{
						boolean split = false;
						for (int corner = 0; corner < 3; corner++)
						{
							Vec a = piece[corner];
							Vec b = piece[(corner + 1) % 3];
							Vec opposite = piece[(corner + 2) % 3];
							if (!onEdge(point, a, b))
							{
								continue;
							}
							next.add(new Vec[]{a, point, opposite});
							next.add(new Vec[]{point, b, opposite});
							split = true;
							break;
						}
						if (!split)
						{
							next.add(piece);
						}
					}
					pieces = next;
				}
			}
			for (Vec[] piece : pieces)
			{
				result.add(piece[0], piece[1], piece[2]);
			}
		}
		return result;
	}

	private static boolean onEdge(Vec point, Vec a, Vec b)
	{
		Vec edge = b.minus(a);
		Vec offset = point.minus(a);
		double length = edge.lengthSquared();
		double position = offset.dot(edge) / length;
		return position > EPS && position < 1 - EPS
			&& offset.cross(edge).lengthSquared() <= EPS * EPS * length;
	}

	private MeshTopology oriented()
	{
		MeshTopology result = new MeshTopology();
		for (Component component : components())
		{
			for (Face face : component.faces())
			{
				addFace(result, face);
			}
		}
		return result;
	}

	private Pruned pruneNonManifoldEdges()
	{
		Set<Integer> removed = new HashSet<>();
		for (Map.Entry<Edge, List<Integer>> entry : edges().entrySet())
		{
			List<Integer> adjacent = entry.getValue();
			if (adjacent.size() <= 2)
			{
				continue;
			}
			int[] retained = retainedFaces(entry.getKey(), adjacent);
			for (int face : adjacent)
			{
				if (face != retained[0] && face != retained[1])
				{
					removed.add(face);
				}
			}
		}
		if (removed.isEmpty())
		{
			return new Pruned(this, 0);
		}
		MeshTopology result = new MeshTopology();
		for (int face = 0; face < faces.size(); face++)
		{
			if (!removed.contains(face))
			{
				addFace(result, faces.get(face));
			}
		}
		return new Pruned(result, removed.size());
	}

	private int[] retainedFaces(Edge edge, List<Integer> adjacent)
	{
		int first = adjacent.get(0);
		int second = adjacent.get(1);
		boolean opposite = oppositeDirections(edge, first, second);
		double area = faceAreaSquared(first) + faceAreaSquared(second);
		for (int i = 0; i < adjacent.size(); i++)
		{
			for (int j = i + 1; j < adjacent.size(); j++)
			{
				int candidateFirst = adjacent.get(i);
				int candidateSecond = adjacent.get(j);
				boolean candidateOpposite = oppositeDirections(edge, candidateFirst, candidateSecond);
				double candidateArea = faceAreaSquared(candidateFirst) + faceAreaSquared(candidateSecond);
				if ((candidateOpposite && !opposite) || (candidateOpposite == opposite && candidateArea > area))
				{
					first = candidateFirst;
					second = candidateSecond;
					opposite = candidateOpposite;
					area = candidateArea;
				}
			}
		}
		return new int[]{first, second};
	}

	private boolean oppositeDirections(Edge edge, int first, int second)
	{
		return faces.get(first).directed(edge.a(), edge.b()) != faces.get(second).directed(edge.a(), edge.b());
	}

	private double faceAreaSquared(int face)
	{
		Face triangle = faces.get(face);
		Vec a = vertices.get(triangle.a());
		return vertices.get(triangle.b()).minus(a).cross(vertices.get(triangle.c()).minus(a)).lengthSquared();
	}

	private Filled fillBoundaryLoops()
	{
		Map<Edge, List<Integer>> edgeFaces = edges();
		List<BoundaryEdge> boundaries = new ArrayList<>();
		Map<Integer, List<BoundaryEdge>> incident = new LinkedHashMap<>();
		for (Map.Entry<Edge, List<Integer>> entry : edgeFaces.entrySet())
		{
			if (entry.getValue().size() != 1)
			{
				continue;
			}
			BoundaryEdge boundary = boundaryEdge(entry.getKey(), faces.get(entry.getValue().get(0)));
			boundaries.add(boundary);
			incident.computeIfAbsent(boundary.from(), ignored -> new ArrayList<>()).add(boundary);
			incident.computeIfAbsent(boundary.to(), ignored -> new ArrayList<>()).add(boundary);
		}
		if (boundaries.isEmpty())
		{
			return new Filled(this, 0, 0);
		}
		MeshTopology result = copy();
		Set<Edge> used = new HashSet<>();
		Set<Edge> occupiedEdges = new HashSet<>(edgeFaces.keySet());
		int filledLoops = 0;
		int addedFaces = 0;
		for (BoundaryEdge start : boundaries)
		{
			if (used.contains(start.edge()))
			{
				continue;
			}
			BoundaryLoop boundaryLoop = boundaryLoop(start, incident, used);
			if (boundaryLoop == null || boundaryLoop.vertices().size() < 3)
			{
				continue;
			}
			List<Integer> loop = boundaryLoop.vertices();
			if (boundaryLoop.directionScore() >= 0)
			{
				Collections.reverse(loop);
			}
			List<Face> cap = triangulateLoop(loop, occupiedEdges);
			if (cap == null)
			{
				cap = fanLoop(loop, occupiedEdges);
			}
			if (cap == null)
			{
				continue;
			}
			used.addAll(boundaryLoop.edges());
			for (Face face : cap)
			{
				addFace(result, face);
				for (int corner = 0; corner < 3; corner++)
				{
					occupiedEdges.add(Edge.of(face.at(corner), face.at((corner + 1) % 3)));
				}
			}
			filledLoops++;
			addedFaces += cap.size();
		}
		return new Filled(result, filledLoops, addedFaces);
	}

	private BoundaryLoop boundaryLoop(BoundaryEdge start, Map<Integer, List<BoundaryEdge>> incident,
		Set<Edge> used)
	{
		Map<Integer, Integer> previousVertex = new HashMap<>();
		Map<Integer, BoundaryEdge> previousEdge = new HashMap<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		previousVertex.put(start.to(), start.from());
		queue.add(start.to());
		while (!queue.isEmpty() && !previousVertex.containsKey(start.from()))
		{
			checkCancelled();
			int current = queue.remove();
			for (BoundaryEdge candidate : incident.getOrDefault(current, List.of()))
			{
				if (candidate.edge().equals(start.edge()) || used.contains(candidate.edge()))
				{
					continue;
				}
				int next = candidate.edge().a() == current ? candidate.edge().b() : candidate.edge().a();
				if (previousVertex.containsKey(next))
				{
					continue;
				}
				previousVertex.put(next, current);
				previousEdge.put(next, candidate);
				queue.add(next);
			}
		}
		if (!previousVertex.containsKey(start.from()))
		{
			return null;
		}
		List<Integer> route = new ArrayList<>();
		List<BoundaryEdge> routeEdges = new ArrayList<>();
		int current = start.from();
		while (current != start.to())
		{
			route.add(current);
			routeEdges.add(previousEdge.get(current));
			current = previousVertex.get(current);
		}
		route.add(start.to());
		Collections.reverse(route);
		Collections.reverse(routeEdges);
		List<Integer> loop = new ArrayList<>(route.size());
		loop.add(start.from());
		loop.addAll(route.subList(0, route.size() - 1));
		List<Edge> loopEdges = new ArrayList<>(routeEdges.size() + 1);
		loopEdges.add(start.edge());
		int directionScore = direction(start, start.from(), start.to());
		for (int i = 0; i < routeEdges.size(); i++)
		{
			BoundaryEdge edge = routeEdges.get(i);
			loopEdges.add(edge.edge());
			directionScore += direction(edge, route.get(i), route.get(i + 1));
		}
		return new BoundaryLoop(loop, loopEdges, directionScore);
	}

	private static int direction(BoundaryEdge edge, int from, int to)
	{
		return edge.from() == from && edge.to() == to ? 1 : -1;
	}

	private BoundaryEdge boundaryEdge(Edge edge, Face face)
	{
		for (int corner = 0; corner < 3; corner++)
		{
			int from = face.at(corner);
			int to = face.at((corner + 1) % 3);
			if (Edge.of(from, to).equals(edge))
			{
				return new BoundaryEdge(edge, from, to);
			}
		}
		throw new IllegalStateException("Boundary edge is not part of its face");
	}

	private List<Face> triangulateLoop(List<Integer> loop, Set<Edge> occupiedEdges)
	{
		Vec normal = loopNormal(loop);
		if (normal.lengthSquared() <= 1e-24)
		{
			return null;
		}
		int drop = dominantAxis(normal);
		double orientation = projectedArea(loop, drop);
		if (Math.abs(orientation) <= 1e-14)
		{
			return null;
		}
		orientation = Math.copySign(1, orientation);
		List<Integer> remaining = new ArrayList<>(loop);
		List<Face> triangles = new ArrayList<>(Math.max(1, loop.size() - 2));
		while (remaining.size() > 3)
		{
			checkCancelled();
			boolean clipped = false;
			for (int i = 0; i < remaining.size(); i++)
			{
				int previous = remaining.get((i + remaining.size() - 1) % remaining.size());
				int current = remaining.get(i);
				int next = remaining.get((i + 1) % remaining.size());
				if (!isEar(previous, current, next, remaining, drop, orientation, occupiedEdges))
				{
					continue;
				}
				triangles.add(new Face(previous, current, next));
				remaining.remove(i);
				clipped = true;
				break;
			}
			if (!clipped)
			{
				return null;
			}
		}
		Face last = new Face(remaining.get(0), remaining.get(1), remaining.get(2));
		if (faceAreaSquared(last) <= 1e-28)
		{
			return null;
		}
		triangles.add(last);
		return triangles;
	}

	private List<Face> fanLoop(List<Integer> loop, Set<Edge> occupiedEdges)
	{
		for (int anchorIndex = 0; anchorIndex < loop.size(); anchorIndex++)
		{
			List<Face> triangles = new ArrayList<>(Math.max(1, loop.size() - 2));
			int anchor = loop.get(anchorIndex);
			boolean valid = true;
			for (int offset = 1; offset + 1 < loop.size(); offset++)
			{
				int nextIndex = (anchorIndex + offset + 1) % loop.size();
				if (offset + 1 < loop.size() - 1 && occupiedEdges.contains(Edge.of(anchor, loop.get(nextIndex))))
				{
					valid = false;
					break;
				}
				Face triangle = new Face(anchor, loop.get((anchorIndex + offset) % loop.size()),
					loop.get(nextIndex));
				if (faceAreaSquared(triangle) <= 1e-28)
				{
					valid = false;
					break;
				}
				triangles.add(triangle);
			}
			if (valid)
			{
				return triangles;
			}
		}
		return null;
	}

	private boolean isEar(int previous, int current, int next, List<Integer> polygon, int drop, double orientation,
		Set<Edge> occupiedEdges)
	{
		if (polygon.size() > 3 && occupiedEdges.contains(Edge.of(previous, next)))
		{
			return false;
		}
		if (projectedCross(previous, current, next, drop) * orientation <= 1e-14)
		{
			return false;
		}
		for (int point : polygon)
		{
			if (point != previous && point != current && point != next
				&& insideTriangle(point, previous, current, next, drop, orientation))
			{
				return false;
			}
		}
		return true;
	}

	private boolean insideTriangle(int point, int a, int b, int c, int drop, double orientation)
	{
		double tolerance = -1e-14;
		return projectedCross(a, b, point, drop) * orientation >= tolerance
			&& projectedCross(b, c, point, drop) * orientation >= tolerance
			&& projectedCross(c, a, point, drop) * orientation >= tolerance;
	}

	private double projectedCross(int a, int b, int c, int drop)
	{
		Vec first = vertices.get(a);
		Vec second = vertices.get(b);
		Vec third = vertices.get(c);
		double ax = projectedX(first, drop);
		double ay = projectedY(first, drop);
		double bx = projectedX(second, drop);
		double by = projectedY(second, drop);
		double cx = projectedX(third, drop);
		double cy = projectedY(third, drop);
		return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
	}

	private double projectedArea(List<Integer> loop, int drop)
	{
		double area = 0;
		for (int i = 0; i < loop.size(); i++)
		{
			Vec current = vertices.get(loop.get(i));
			Vec next = vertices.get(loop.get((i + 1) % loop.size()));
			area += projectedX(current, drop) * projectedY(next, drop)
				- projectedY(current, drop) * projectedX(next, drop);
		}
		return area / 2;
	}

	private Vec loopNormal(List<Integer> loop)
	{
		double x = 0;
		double y = 0;
		double z = 0;
		for (int i = 0; i < loop.size(); i++)
		{
			Vec current = vertices.get(loop.get(i));
			Vec next = vertices.get(loop.get((i + 1) % loop.size()));
			x += (current.y() - next.y()) * (current.z() + next.z());
			y += (current.z() - next.z()) * (current.x() + next.x());
			z += (current.x() - next.x()) * (current.y() + next.y());
		}
		return new Vec(x, y, z);
	}

	private static int dominantAxis(Vec normal)
	{
		double x = Math.abs(normal.x());
		double y = Math.abs(normal.y());
		double z = Math.abs(normal.z());
		return x > y && x > z ? 0 : y > z ? 1 : 2;
	}

	private static double projectedX(Vec point, int drop)
	{
		return drop == 0 ? point.y() : point.x();
	}

	private static double projectedY(Vec point, int drop)
	{
		return drop == 2 ? point.y() : point.z();
	}

	private double faceAreaSquared(Face face)
	{
		Vec a = vertices.get(face.a());
		return vertices.get(face.b()).minus(a).cross(vertices.get(face.c()).minus(a)).lengthSquared();
	}

	private MeshTopology copy()
	{
		MeshTopology result = new MeshTopology();
		for (Face face : faces)
		{
			addFace(result, face);
		}
		return result;
	}

	private void addFace(MeshTopology target, Face face)
	{
		target.add(vertices.get(face.a()), vertices.get(face.b()), vertices.get(face.c()));
	}

	Map3DMesh toMesh(Vec center, double extent)
	{
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

	record Enclosed(MeshTopology mesh, int components, int faces)
	{
	}

	record Repair(MeshTopology mesh, Diagnostics before, Diagnostics after, int removedFaces,
		int filledLoops, int addedFaces, int solidifiedSheets, boolean printable)
	{
		boolean changed()
		{
			return !before.closed() || removedFaces > 0 || addedFaces > 0 || solidifiedSheets > 0;
		}
	}

	private record Pruned(MeshTopology mesh, int removedFaces)
	{
	}

	private record Filled(MeshTopology mesh, int filledLoops, int addedFaces)
	{
	}

	private record Solidified(MeshTopology mesh, int sheets)
	{
	}

	private record BoundaryEdge(Edge edge, int from, int to)
	{
	}

	private record BoundaryLoop(List<Integer> vertices, List<Edge> edges, int directionScore)
	{
	}

	private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
	{
		boolean strictlyContains(Bounds other)
		{
			return minX < other.minX - EPS && minY < other.minY - EPS && minZ < other.minZ - EPS
				&& maxX > other.maxX + EPS && maxY > other.maxY + EPS && maxZ > other.maxZ + EPS;
		}
	}

	record Diagnostics(int boundaryEdges, int nonManifoldEdges, int inconsistentEdges)
	{
		boolean closed()
		{
			return boundaryEdges == 0 && nonManifoldEdges == 0 && inconsistentEdges == 0;
		}
	}
}
