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

import static com.xeon.plugins.meshexport.MeshTopology.EPS;
import static com.xeon.plugins.meshexport.MeshTopology.Edge;
import static com.xeon.plugins.meshexport.MeshTopology.Face;
import static com.xeon.plugins.meshexport.MeshTopology.Vec;
import static com.xeon.plugins.meshexport.MeshTopology.checkCancelled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator;

final class PlanarMeshSimplifier
{
	private static final GeometryFactory GEOMETRY = new GeometryFactory();

	static MeshTopology simplify(MeshTopology mesh, List<String> notes)
	{
		return simplify(mesh, notes, PolygonTriangulator::triangulate);
	}

	static MeshTopology simplify(MeshTopology mesh, List<String> notes, Triangulator triangulator)
	{
		Map<PlaneKey, List<Patch>> groups = new LinkedHashMap<>();
		for (Face face : mesh.faces)
		{
			checkCancelled();
			Vec a = mesh.vertices.get(face.a()), b = mesh.vertices.get(face.b()), c = mesh.vertices.get(face.c());
			Vec normal = b.minus(a).cross(c.minus(a)).normalized();
			double distance = normal.dot(a);
			PlaneKey key = new PlaneKey(Math.round(normal.x() * 1e9), Math.round(normal.y() * 1e9),
				Math.round(normal.z() * 1e9), Math.round(distance * 1e9));
			List<Patch> candidates = groups.computeIfAbsent(key, ignored -> new ArrayList<>());
			Patch patch = candidates.stream().filter(p -> p.normal.minus(normal).lengthSquared() < 1e-24
				&& Math.abs(p.distance - distance) < 1e-12).findFirst().orElse(null);
			if (patch == null)
			{
				patch = new Patch(normal, distance);
				candidates.add(patch);
			}
			patch.triangles.add(GEOMETRY.createPolygon(new Coordinate[]{patch.project(a), patch.project(b),
				patch.project(c), patch.project(a)}));
		}
		MeshTopology simplified = new MeshTopology();
		for (List<Patch> patches : groups.values())
		{
			for (Patch patch : patches)
			{
				checkCancelled();
				try
				{
					Geometry union = UnaryUnionOp.union(patch.triangles);
					List<Geometry> polygons = new ArrayList<>();
					for (int i = 0; i < union.getNumGeometries(); i++)
					{
						org.locationtech.jts.geom.Polygon polygon = (org.locationtech.jts.geom.Polygon) union.getGeometryN(i);
						LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
						for (int h = 0; h < holes.length; h++)
						{
							holes[h] = cleanRing(polygon.getInteriorRingN(h));
						}
						polygons.add(GEOMETRY.createPolygon(cleanRing(polygon.getExteriorRing()), holes));
					}
					Geometry triangles = triangulator.triangulate(GEOMETRY.buildGeometry(polygons));
					for (int i = 0; i < triangles.getNumGeometries(); i++)
					{
						appendTriangle(simplified, patch, triangles.getGeometryN(i).getCoordinates());
					}
				}
				catch (RuntimeException ex)
				{
					checkCancelled();
					for (Geometry triangle : patch.triangles)
					{
						appendTriangle(simplified, patch, triangle.getCoordinates());
					}
					String note = "Some coplanar faces could not be merged and were retained unchanged.";
					if (!notes.contains(note))
					{
						notes.add(note);
					}
				}
			}
		}
		return stitchEdges(simplified);
	}

	@FunctionalInterface
	interface Triangulator
	{
		Geometry triangulate(Geometry geometry);
	}

	private static void appendTriangle(MeshTopology mesh, Patch patch, Coordinate[] coordinates)
	{
		Vec a = patch.unproject(coordinates[0]), b = patch.unproject(coordinates[1]), c = patch.unproject(coordinates[2]);
		if (b.minus(a).cross(c.minus(a)).dot(patch.normal) < 0)
		{
			mesh.add(c, b, a);
		}
		else
		{
			mesh.add(a, b, c);
		}
	}

	private static LinearRing cleanRing(LineString ring)
	{
		List<Coordinate> points = new ArrayList<>(Arrays.asList(ring.getCoordinates()));
		points.remove(points.size() - 1);
		boolean changed;
		do
		{
			changed = false;
			for (int i = 0; i < points.size() && points.size() > 3; i++)
			{
				Coordinate a = points.get((i + points.size() - 1) % points.size());
				Coordinate b = points.get(i), c = points.get((i + 1) % points.size());
				double cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
				if (Math.abs(cross) <= 1e-13 * a.distance(c)
					&& (b.x - a.x) * (b.x - c.x) + (b.y - a.y) * (b.y - c.y) <= 0)
				{
					points.remove(i--);
					changed = true;
				}
			}
		} while (changed);
		points.add(points.get(0));
		return GEOMETRY.createLinearRing(points.toArray(Coordinate[]::new));
	}

	private static MeshTopology stitchEdges(MeshTopology mesh)
	{
		STRtree index = new STRtree();
		for (Vec point : mesh.vertices)
		{
			index.insert(new Envelope(point.x(), point.x(), point.y(), point.y()), point);
		}
		Map<Edge, List<Vec>> splits = new HashMap<>();
		for (Map.Entry<Edge, List<Integer>> entry : mesh.edges().entrySet())
		{
			if (entry.getValue().size() != 1)
			{
				continue;
			}
			checkCancelled();
			Vec a = mesh.vertices.get(entry.getKey().a()), b = mesh.vertices.get(entry.getKey().b());
			Envelope bounds = new Envelope(a.x(), b.x(), a.y(), b.y());
			bounds.expandBy(EPS);
			List<Vec> points = new ArrayList<>();
			for (Object candidate : index.query(bounds))
			{
				Vec point = (Vec) candidate;
				if (onEdge(point, a, b))
				{
					points.add(point);
				}
			}
			if (!points.isEmpty())
			{
				splits.put(entry.getKey(), points);
			}
		}
		if (splits.isEmpty())
		{
			return mesh;
		}
		MeshTopology result = new MeshTopology();
		for (Face face : mesh.faces)
		{
			checkCancelled();
			List<Vec[]> pieces = new ArrayList<>();
			pieces.add(new Vec[]{mesh.vertices.get(face.a()), mesh.vertices.get(face.b()), mesh.vertices.get(face.c())});
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
						for (int c = 0; c < 3; c++)
						{
							Vec a = piece[c], b = piece[(c + 1) % 3], opposite = piece[(c + 2) % 3];
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
		Vec edge = b.minus(a), offset = point.minus(a);
		double length = edge.lengthSquared(), t = offset.dot(edge) / length;
		return t > EPS && t < 1 - EPS && offset.cross(edge).lengthSquared() <= EPS * EPS * length;
	}

	private record PlaneKey(long x, long y, long z, long distance)
	{
	}

	private static final class Patch
	{
		final Vec normal;
		final double distance;
		final int drop;
		final List<Geometry> triangles = new ArrayList<>();

		Patch(Vec normal, double distance)
		{
			this.normal = normal;
			this.distance = distance;
			drop = Math.abs(normal.x()) > Math.abs(normal.y()) && Math.abs(normal.x()) > Math.abs(normal.z())
				? 0 : Math.abs(normal.y()) > Math.abs(normal.z()) ? 1 : 2;
		}

		Coordinate project(Vec p)
		{
			return drop == 0 ? new Coordinate(p.y(), p.z()) : drop == 1 ? new Coordinate(p.x(), p.z()) : new Coordinate(p.x(), p.y());
		}

		Vec unproject(Coordinate p)
		{
			return drop == 0 ? new Vec((distance - normal.y() * p.x - normal.z() * p.y) / normal.x(), p.x, p.y)
				: drop == 1 ? new Vec(p.x, (distance - normal.x() * p.x - normal.z() * p.y) / normal.y(), p.y)
				: new Vec(p.x, p.y, (distance - normal.x() * p.x - normal.y() * p.y) / normal.z());
		}
	}
}
