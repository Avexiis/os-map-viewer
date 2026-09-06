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
import static com.xeon.plugins.meshexport.MeshTopology.Enclosed;
import static com.xeon.plugins.meshexport.MeshTopology.Face;
import static com.xeon.plugins.meshexport.MeshTopology.Vec;
import static com.xeon.plugins.meshexport.MeshTopology.checkCancelled;
import com.xeon.view3d.Map3DMesh;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

final class MeshCompactor
{
	private static final double CSG_SCALE = 1024;

	record Result(Map3DMesh compactedMesh, Map3DMesh repairedMesh, Diagnostics diagnostics,
		boolean watertight, List<String> notes)
	{
		boolean repairApplied()
		{
			return repairedMesh != null;
		}

		Map3DMesh exportMesh()
		{
			return repairedMesh == null ? compactedMesh : repairedMesh;
		}
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
		progress.accept("Compacting mesh...");
		Candidate candidate = compactTopology(source, progress);
		MeshTopology compact = candidate.mesh();
		if (compact.faces.isEmpty())
		{
			throw new IllegalStateException("Compaction produced an empty mesh");
		}
		if (compact.faces.size() > input.faceCount())
		{
			throw new IllegalStateException("Compaction exceeded the original face count");
		}
		List<String> notes = new ArrayList<>(candidate.notes());
		progress.accept("Repairing mesh manifold...");
		MeshTopology.Repair repair = compact.repair();
		boolean watertight = repair.printable();
		boolean repairApplied = watertight && repair.changed();
		Diagnostics diagnostics = watertight ? repair.after() : compact.diagnostics();
		if (watertight)
		{
			addRepairNote(notes, repair);
		}
		else
		{
			Diagnostics remaining = repair.after();
			notes.add("Automatic manifold repair could not make the mesh watertight ("
				+ remaining.boundaryEdges() + " open edges, "
				+ remaining.nonManifoldEdges() + " non-manifold edges, "
				+ remaining.inconsistentEdges() + " winding conflicts). The compacted mesh was retained unchanged.");
		}
		Map3DMesh compactedMesh = compact.toMesh(center, extent);
		Map3DMesh repairedMesh = repairApplied ? repair.mesh().toMesh(center, extent) : null;
		return new Result(compactedMesh, repairedMesh, diagnostics, watertight, List.copyOf(notes));
	}

	private static Candidate compactTopology(MeshTopology source, Consumer<String> progress)
	{
		Candidate best = new Candidate(source, List.of());
		List<String> componentNotes = new ArrayList<>();
		MeshTopology componentSurface = componentSurface(source, componentNotes, progress);
		if (!componentSurface.faces.isEmpty() && componentSurface.faces.size() <= best.mesh().faces.size())
		{
			best = new Candidate(componentSurface, List.copyOf(componentNotes));
		}
		progress.accept("Merging coplanar faces...");
		List<String> simplifiedNotes = new ArrayList<>(componentNotes);
		MeshTopology simplified = PlanarMeshSimplifier.simplify(componentSurface, simplifiedNotes);
		if (!simplified.faces.isEmpty() && simplified.faces.size() <= best.mesh().faces.size())
		{
			best = new Candidate(simplified, List.copyOf(simplifiedNotes));
		}
		return best;
	}

	private static MeshTopology componentSurface(MeshTopology source, List<String> notes,
		Consumer<String> progress)
	{
		MeshTopology open = new MeshTopology();
		MeshTopology closed = new MeshTopology();
		for (Component component : source.components())
		{
			MeshTopology target = component.closed() ? closed : open;
			for (Face face : component.faces())
			{
				addFace(target, source, face);
			}
		}
		MeshTopology compactClosed = closed;
		if (!closed.faces.isEmpty())
		{
			progress.accept("Removing enclosed shells...");
			Enclosed enclosed = closed.removeEnclosedComponents();
			compactClosed = enclosed.mesh();
			if (enclosed.components() > 0)
			{
				notes.add("Compaction removed " + enclosed.faces() + " faces from " + enclosed.components()
					+ " fully enclosed shells.");
			}
			List<Component> components = compactClosed.components();
			if (components.size() > 1)
			{
				try
				{
					progress.accept("Removing internal surfaces...");
					compactClosed = unionSurface(compactClosed, components);
				}
				catch (CancellationException ex)
				{
					throw ex;
				}
				catch (RuntimeException | StackOverflowError ex)
				{
					checkCancelled();
					compactClosed = enclosed.mesh();
					notes.add("Some closed shells could not be combined and were retained unchanged.");
				}
			}
		}
		MeshTopology result = new MeshTopology();
		for (Face face : compactClosed.faces)
		{
			addFace(result, compactClosed, face);
		}
		for (Face face : open.faces)
		{
			addFace(result, open, face);
		}
		return result;
	}

	private static MeshTopology unionSurface(MeshTopology source, List<Component> components)
	{
		List<CSG> solids = new ArrayList<>();
		for (Component component : components)
		{
			checkCancelled();
			List<Polygon> polygons = new ArrayList<>();
			for (Face face : component.faces())
			{
				polygons.add(Polygon.fromPoints(
					vector(source.vertices.get(face.a())), vector(source.vertices.get(face.b())),
					vector(source.vertices.get(face.c()))));
			}
			solids.add(CSG.fromPolygons(polygons).optimization(CSG.OptType.NONE));
		}
		while (solids.size() > 1)
		{
			List<CSG> next = new ArrayList<>();
			for (int i = 0; i < solids.size(); i += 2)
			{
				checkCancelled();
				next.add(i + 1 < solids.size() ? solids.get(i).union(solids.get(i + 1)) : solids.get(i));
			}
			solids = next;
		}
		MeshTopology surface = new MeshTopology();
		for (Polygon polygon : solids.get(0).getPolygons())
		{
			checkCancelled();
			for (int i = 1; i + 1 < polygon.vertices.size(); i++)
			{
				surface.add(point(polygon.vertices.get(0).pos), point(polygon.vertices.get(i).pos),
					point(polygon.vertices.get(i + 1).pos));
			}
		}
		return surface;
	}

	private static void addRepairNote(List<String> notes, MeshTopology.Repair repair)
	{
		if (!repair.changed())
		{
			return;
		}
		String note = "Manifold repair filled " + repair.filledLoops() + " openings with "
			+ repair.addedFaces() + " faces, removed " + repair.removedFaces()
			+ " non-manifold faces, and corrected " + repair.before().inconsistentEdges() + " winding conflicts.";
		if (!notes.contains(note))
		{
			notes.add(note);
		}
		if (repair.solidifiedSheets() > 0)
		{
			notes.add("Manifold repair gave " + repair.solidifiedSheets()
				+ " open surface parts a minimal printable thickness.");
		}
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

	private record Candidate(MeshTopology mesh, List<String> notes)
	{
	}
}
