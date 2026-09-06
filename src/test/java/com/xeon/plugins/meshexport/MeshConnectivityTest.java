package com.xeon.plugins.meshexport;

import com.xeon.view3d.Map3DMesh;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.util.List;

class MeshConnectivityTest
{
	@Test void acceptsTouchingAndIntersectingMeshes()
	{
		assertTrue(MeshConnectivity.touches(cube(0, 0, 0), cube(1, 0, 0)));
		assertTrue(MeshConnectivity.touches(cube(0, 0, 0), cube(0.5, 0.5, 0.5)));
	}

	@Test void rejectsAnyMeaningfulGap()
	{
		assertFalse(MeshConnectivity.touches(cube(0, 0, 0), cube(1 + MeshConnectivity.TOLERANCE * 2, 0, 0)));
		assertFalse(MeshConnectivity.touches(cube(0, 0, 0), cube(5, 0, 0)));
	}

	@Test void requiresTheWholeSelectionToFormOneConnectedGraph()
	{
		Map3DMesh left = cube(0, 0, 0);
		Map3DMesh middle = cube(1, 0, 0);
		Map3DMesh right = cube(2, 0, 0);
		assertTrue(MeshConnectivity.connected(List.of(left, middle, right)));
		assertFalse(MeshConnectivity.connected(List.of(left, middle, cube(4, 0, 0))));
	}

	@Test void combinedMeshRetainsEveryPlacement()
	{
		Map3DMesh combined = MeshExportPanel.combine(List.of(cube(0, 0, 0), cube(1, 0, 0)));
		assertEquals(16, combined.vertexCount());
		assertEquals(24, combined.faceCount());
		assertEquals(2, java.util.stream.IntStream.range(0, combined.vertexCount())
			.mapToDouble(vertex -> combined.coordinate(vertex, 0)).max().orElseThrow(), 0);
	}

	private static Map3DMesh cube(double x, double y, double z)
	{
		return MeshCompactorTest.cube(x, y, z, 1);
	}
}
