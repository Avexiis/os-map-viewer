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

import static com.xeon.plugins.meshexport.MeshTopology.Component;
import static com.xeon.plugins.meshexport.MeshTopology.Diagnostics;
import static com.xeon.plugins.meshexport.MeshTopology.Face;
import static com.xeon.plugins.meshexport.MeshTopology.Vec;
import static com.xeon.plugins.meshexport.MeshTopology.checkCancelled;
import com.xeon.view3d.Map3DMesh;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class MeshCompactor
{
	private static final double CSG_SCALE = 1024;

	record Result(Map3DMesh mesh, Diagnostics diagnostics, List<String> notes)
	{
	}

	static Result compact(Map3DMesh input, Consumer<String> progress)
	{
		if (input == null || input.faceCount() == 0)
		{
			throw new IllegalArgumentException("The model has no visible faces");
		}
		checkCancelled();
		progress.accept("Cleaning mesh...");
		double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
		double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		for (int f = 0; f < input.faceCount(); f++)
		{
			for (int c = 0; c < 3; c++)
			{
				for (int axis = 0; axis < 3; axis++)
				{
					double value = input.coordinate(input.vertexIndex(f, c), axis);
					min[axis] = Math.min(min[axis], value);
					max[axis] = Math.max(max[axis], value);
				}
			}
		}
		Vec center = new Vec((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2);
		double extent = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
		if (!(extent > 0))
		{
			throw new IllegalArgumentException("The model has no surface area");
		}
		MeshTopology source = new MeshTopology();
		for (int f = 0; f < input.faceCount(); f++)
		{
			Vec[] points = new Vec[3];
			for (int c = 0; c < 3; c++)
			{
				int v = input.vertexIndex(f, c);
				points[c] = new Vec(input.coordinate(v, 0), input.coordinate(v, 1), input.coordinate(v, 2))
					.minus(center).times(1 / extent);
			}
			source.add(points[0], points[1], points[2]);
		}
		if (source.faces.isEmpty())
		{
			throw new IllegalArgumentException("The model has no surface area");
		}
		source = source.removeCoincidentInternalFaces();
		List<String> notes = new ArrayList<>();
		List<Component> components = source.components();
		List<CSG> solids = new ArrayList<>();
		MeshTopology open = new MeshTopology();
		for (Component component : components)
		{
			checkCancelled();
			if (!component.closed())
			{
				for (Face face : component.faces())
				{
					addFace(open, source, face);
				}
				continue;
			}
			List<Polygon> polygons = new ArrayList<>();
			for (Face face : component.faces())
			{
				polygons.add(Polygon.fromPoints(
					vector(source.vertices.get(face.a())), vector(source.vertices.get(face.b())), vector(source.vertices.get(face.c()))));
			}
			solids.add(CSG.fromPolygons(polygons).optimization(CSG.OptType.NONE));
		}
		if (!open.faces.isEmpty())
		{
			notes.add("Open or non-manifold surfaces were retained; internal removal is limited to closed parts.");
		}
		progress.accept("Removing internal surfaces...");
		while (solids.size() > 1)
		{
			List<CSG> next = new ArrayList<>();
			for (int i = 0; i < solids.size(); i += 2)
			{
				checkCancelled();
				try
				{
					next.add(i + 1 < solids.size() ? solids.get(i).union(solids.get(i + 1)) : solids.get(i));
				}
				catch (StackOverflowError error)
				{
					throw new IllegalStateException("This model is too complex for solid compaction", error);
				}
			}
			solids = next;
		}
		MeshTopology surface = new MeshTopology();
		if (!solids.isEmpty())
		{
			for (Polygon polygon : solids.get(0).getPolygons())
			{
				checkCancelled();
				for (int i = 1; i + 1 < polygon.vertices.size(); i++)
				{
					surface.add(
						point(polygon.vertices.get(0).pos), point(polygon.vertices.get(i).pos), point(polygon.vertices.get(i + 1).pos));
				}
			}
		}
		for (Face face : open.faces)
		{
			addFace(surface, open, face);
		}
		progress.accept("Merging coplanar faces...");
		MeshTopology compact = PlanarMeshSimplifier.simplify(surface, notes);
		if (compact.faces.isEmpty())
		{
			throw new IllegalStateException("Compaction produced an empty mesh");
		}
		checkCancelled();
		Diagnostics diagnostics = compact.diagnostics();
		if (!diagnostics.closed())
		{
			notes.add("Mesh has open edges or non-manifold topology; repair may be needed before printing.");
		}
		return new Result(compact.toMesh(center, extent), diagnostics, List.copyOf(notes));
	}

	private static void addFace(MeshTopology target, MeshTopology source, Face face)
	{
		target.add(source.vertices.get(face.a()), source.vertices.get(face.b()), source.vertices.get(face.c()));
	}

	private static Vector3d vector(Vec v)
	{
		return new Vector3d(v.x() * CSG_SCALE, v.y() * CSG_SCALE, v.z() * CSG_SCALE);
	}

	private static Vec point(Vector3d v)
	{
		return new Vec(v.x / CSG_SCALE, v.y / CSG_SCALE, v.z / CSG_SCALE);
	}
}
