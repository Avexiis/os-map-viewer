package com.xeon.view3d;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TerrainRendererMeshTest
{
	@Test void objectSnapshotsUseAbsoluteCoordinatesAcrossRegionBoundaries()
	{
		int westRegion = TerrainScene.regionId(50, 50);
		int eastRegion = TerrainScene.regionId(51, 50);
		int northRegion = TerrainScene.regionId(50, 51);
		float[] westEdge = {32, 0, 0, 32, 1, 0, 32, 0, 1};
		float[] eastEdge = {-32, 0, 0, -32, 1, 0, -32, 0, 1};
		float[] southEdge = {0, 0, -32, 1, 0, -32, 0, 1, -32};
		float[] northEdge = {0, 0, 32, 1, 0, 32, 0, 1, 32};

		assertArrayEquals(
			TerrainRenderer.worldObjectMesh(westEdge, westRegion).positions(),
			TerrainRenderer.worldObjectMesh(eastEdge, eastRegion).positions(),
			1e-9
		);
		assertArrayEquals(
			TerrainRenderer.worldObjectMesh(southEdge, westRegion).positions(),
			TerrainRenderer.worldObjectMesh(northEdge, northRegion).positions(),
			1e-9
		);
	}
}
