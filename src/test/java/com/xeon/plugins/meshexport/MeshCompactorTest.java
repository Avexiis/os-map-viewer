package com.xeon.plugins.meshexport;

import com.xeon.view3d.Map3DMesh;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;

class MeshCompactorTest
{
	@Test void removesNestedSolidWithoutChangingOuterShell()
	{
		MeshCompactor.Result result = compact(combine(cube(0, 0, 0, 2), cube(0.5, 0.5, 0.5, 1)));
		assertEquals(12, result.mesh().faceCount());
		assertEquals(8, volume(result.mesh()), 1e-9);
		assertTrue(result.diagnostics().closed(), result.diagnostics().toString());
	}

	@Test void unionsOverlappingSolidsAndRemovesCoplanarCuts()
	{
		MeshCompactor.Result result = compact(combine(cube(0, 0, 0, 1), cube(0.5, 0, 0, 1)));
		assertEquals(12, result.mesh().faceCount());
		assertEquals(1.5, volume(result.mesh()), 1e-9);
		assertTrue(result.diagnostics().closed(), result.diagnostics().toString());
	}

	@Test void stitchesBooleanIntersectionsIntoClosedSurface()
	{
		MeshCompactor.Result result = compact(combine(cube(0, 0, 0, 1), cube(0.5, 0.5, 0.5, 1)));
		assertEquals(1.875, volume(result.mesh()), 1e-9);
		assertTrue(result.diagnostics().closed(), result.diagnostics().toString());
	}

	@Test void joinsTouchingSolids()
	{
		MeshCompactor.Result result = compact(combine(cube(0, 0, 0, 1), cube(1, 0, 0, 1)));
		assertEquals(12, result.mesh().faceCount());
		assertEquals(2, volume(result.mesh()), 1e-9);
		assertTrue(result.diagnostics().closed(), result.diagnostics().toString());
	}

	@Test void retainsDisconnectedSolids()
	{
		MeshCompactor.Result result = compact(combine(cube(0, 0, 0, 1), cube(3, 0, 0, 1)));
		assertEquals(24, result.mesh().faceCount());
		assertEquals(2, volume(result.mesh()), 1e-9);
		assertTrue(result.diagnostics().closed());
	}

	@Test void reducesPlanarFanAndReportsOpenBoundary()
	{
		Map3DMesh fan = new Map3DMesh(new double[]{0,0,0, 1,0,0, 1,1,0, 0,1,0, .5,.5,0},
			new int[]{0,1,4, 1,2,4, 2,3,4, 3,0,4});
		MeshCompactor.Result result = compact(fan);
		assertEquals(2, result.mesh().faceCount());
		assertEquals(4, result.diagnostics().boundaryEdges());
		assertEquals(1, area(result.mesh()), 1e-10);
	}

	@Test void retainsPlanarHole()
	{
		Map3DMesh ring = new Map3DMesh(new double[]{0,0,0, 4,0,0, 4,4,0, 0,4,0, 1,1,0, 3,1,0, 3,3,0, 1,3,0},
			new int[]{0,1,5, 0,5,4, 1,2,6, 1,6,5, 2,3,7, 2,7,6, 3,0,4, 3,4,7});
		MeshCompactor.Result result = compact(ring);
		assertEquals(12, area(result.mesh()), 1e-10);
		assertEquals(8, result.diagnostics().boundaryEdges());
	}

	@Test void preservesNonCoplanarDetail()
	{
		Map3DMesh ridge = new Map3DMesh(new double[]{0,0,0, 1,0,0, 1,1,0.0001, 0,1,0}, new int[]{0,1,2, 0,2,3});
		MeshCompactor.Result result = compact(ridge);
		assertEquals(area(ridge), area(result.mesh()), 1e-12);
		assertEquals(2, result.mesh().faceCount());
		assertEquals(0.0001, java.util.stream.IntStream.range(0, result.mesh().vertexCount())
			.mapToDouble(v -> result.mesh().coordinate(v, 2)).max().orElseThrow(), 1e-12);
	}

	@Test void retainsOriginalPatchWhenPolygonTriangulationFails()
	{
		Map3DMesh fan = new Map3DMesh(new double[]{0,0,0, 1,0,0, 1,1,0, 0,1,0, .5,.5,0},
			new int[]{0,1,4, 1,2,4, 2,3,4, 3,0,4});
		MeshTopology topology = new MeshTopology();
		for (int face = 0; face < fan.faceCount(); face++)
			topology.add(point(fan, face, 0), point(fan, face, 1), point(fan, face, 2));
		java.util.List<String> notes = new java.util.ArrayList<>();
		MeshTopology result = PlanarMeshSimplifier.simplify(topology, notes, ignored -> {
			throw new IllegalStateException("Unable to find shell join index with interior join line");
		});

		assertEquals(4, result.faces.size());
		assertEquals(1, area(result.toMesh(new MeshTopology.Vec(0, 0, 0), 1)), 1e-10);
		assertTrue(notes.stream().anyMatch(note -> note.contains("retained unchanged")));
	}

	@Test void fixesWindingAndRemovesDuplicateAndDegenerateFaces()
	{
		Map3DMesh cube = cube(0, 0, 0, 1);
		int[] indices = java.util.Arrays.copyOf(cube.triangles(), 42);
		int swap = indices[0]; indices[0] = indices[2]; indices[2] = swap;
		indices[36] = indices[0]; indices[37] = indices[1]; indices[38] = indices[2];
		MeshCompactor.Result result = compact(new Map3DMesh(cube.positions(), indices));
		assertEquals(12, result.mesh().faceCount());
		assertEquals(1, volume(result.mesh()), 1e-10);
		assertTrue(result.diagnostics().closed());
	}

	@Test void cancellationAndInvalidMeshAreExplicit()
	{
		assertThrows(IllegalArgumentException.class, () -> compact(new Map3DMesh(new double[0], new int[0])));
		Thread.currentThread().interrupt();
		try { assertThrows(CancellationException.class, () -> compact(cube(0, 0, 0, 1))); }
		finally { Thread.interrupted(); }
	}

	static MeshCompactor.Result compact(Map3DMesh mesh) { return MeshCompactor.compact(mesh, ignored -> { }); }
	static Map3DMesh cube(double x, double y, double z, double s)
	{
		double[] positions = {0,0,0, 1,0,0, 1,1,0, 0,1,0, 0,0,1, 1,0,1, 1,1,1, 0,1,1};
		for (int i = 0; i < positions.length; i += 3)
		{
			positions[i] = positions[i] * s + x;
			positions[i + 1] = positions[i + 1] * s + y;
			positions[i + 2] = positions[i + 2] * s + z;
		}
		return new Map3DMesh(positions, new int[]{0,2,1, 0,3,2, 4,5,6, 4,6,7, 0,1,5, 0,5,4,
			3,7,6, 3,6,2, 0,4,7, 0,7,3, 1,2,6, 1,6,5});
	}
	static Map3DMesh combine(Map3DMesh a, Map3DMesh b)
	{
		double[] positions = java.util.Arrays.copyOf(a.positions(), (a.vertexCount() + b.vertexCount()) * 3);
		System.arraycopy(b.positions(), 0, positions, a.vertexCount() * 3, b.vertexCount() * 3);
		int[] indices = java.util.Arrays.copyOf(a.triangles(), (a.faceCount() + b.faceCount()) * 3);
		for (int i = 0; i < b.faceCount() * 3; i++) indices[a.faceCount() * 3 + i] = b.triangles()[i] + a.vertexCount();
		return new Map3DMesh(positions, indices);
	}
	static double volume(Map3DMesh mesh)
	{
		double volume = 0;
		for (int f = 0; f < mesh.faceCount(); f++) volume += point(mesh, f, 0).dot(point(mesh, f, 1).cross(point(mesh, f, 2))) / 6;
		return volume;
	}
	static double area(Map3DMesh mesh)
	{
		double area = 0;
		for (int f = 0; f < mesh.faceCount(); f++) area += Math.sqrt(point(mesh, f, 1).minus(point(mesh, f, 0))
			.cross(point(mesh, f, 2).minus(point(mesh, f, 0))).lengthSquared()) / 2;
		return area;
	}
	private static MeshTopology.Vec point(Map3DMesh mesh, int f, int c)
	{
		int v = mesh.vertexIndex(f, c);
		return new MeshTopology.Vec(mesh.coordinate(v, 0), mesh.coordinate(v, 1), mesh.coordinate(v, 2));
	}
}
