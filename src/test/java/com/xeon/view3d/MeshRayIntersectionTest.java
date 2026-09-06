package com.xeon.view3d;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeshRayIntersectionTest
{
	@Test void trianglePickingRejectsEmptySpaceInsideBoundsAndSupportsBothSides()
	{
		float[] triangle = {0,0,0, 1,0,0, 0,1,0};
		Vector3f direction = new Vector3f(0, 0, -1);
		assertEquals(2, MeshRayIntersection.distance(triangle, new Vector3f(.2f, .2f, 2), direction), 1e-6);
		assertEquals(Float.POSITIVE_INFINITY, MeshRayIntersection.distance(triangle, new Vector3f(.8f, .8f, 2), direction));
		assertEquals(2, MeshRayIntersection.distance(triangle, new Vector3f(.2f, .2f, -2), new Vector3f(0, 0, 1)), 1e-6);
		assertEquals(Float.POSITIVE_INFINITY, MeshRayIntersection.distance(triangle, new Vector3f(.2f, .2f, -2), direction));
		assertFalse(MeshRayIntersection.hitsBounds(MeshRayIntersection.bounds(triangle), new Vector3f(2, 2, 2), direction));
	}
}
