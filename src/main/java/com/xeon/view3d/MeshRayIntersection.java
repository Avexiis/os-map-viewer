package com.xeon.view3d;

import org.joml.Vector3fc;

final class MeshRayIntersection
{
	private MeshRayIntersection()
	{
	}

	static float[] bounds(float[] triangles)
	{
		float[] bounds = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
			Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		for (int i = 0; i < triangles.length; i++)
		{
			int axis = i % 3;
			bounds[axis] = Math.min(bounds[axis], triangles[i]);
			bounds[axis + 3] = Math.max(bounds[axis + 3], triangles[i]);
		}
		return bounds;
	}

	static boolean hitsBounds(float[] bounds, Vector3fc origin, Vector3fc direction)
	{
		double near = 0, far = Double.POSITIVE_INFINITY;
		for (int axis = 0; axis < 3; axis++)
		{
			double o = origin.get(axis), d = direction.get(axis);
			if (Math.abs(d) < 1e-12)
			{
				if (o < bounds[axis] || o > bounds[axis + 3])
				{
					return false;
				}
				continue;
			}
			double a = (bounds[axis] - o) / d, b = (bounds[axis + 3] - o) / d;
			near = Math.max(near, Math.min(a, b));
			far = Math.min(far, Math.max(a, b));
			if (near > far)
			{
				return false;
			}
		}
		return true;
	}

	static float distance(float[] triangles, Vector3fc origin, Vector3fc direction)
	{
		double best = Double.POSITIVE_INFINITY;
		for (int i = 0; i + 8 < triangles.length; i += 9)
		{
			double ax = triangles[i], ay = triangles[i + 1], az = triangles[i + 2];
			double e1x = triangles[i + 3] - ax, e1y = triangles[i + 4] - ay, e1z = triangles[i + 5] - az;
			double e2x = triangles[i + 6] - ax, e2y = triangles[i + 7] - ay, e2z = triangles[i + 8] - az;
			double px = direction.y() * e2z - direction.z() * e2y;
			double py = direction.z() * e2x - direction.x() * e2z;
			double pz = direction.x() * e2y - direction.y() * e2x;
			double determinant = e1x * px + e1y * py + e1z * pz;
			if (Math.abs(determinant) < 1e-14)
			{
				continue;
			}
			double tx = origin.x() - ax, ty = origin.y() - ay, tz = origin.z() - az;
			double u = (tx * px + ty * py + tz * pz) / determinant;
			if (u < -1e-8 || u > 1 + 1e-8)
			{
				continue;
			}
			double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
			double v = (direction.x() * qx + direction.y() * qy + direction.z() * qz) / determinant;
			if (v < -1e-8 || u + v > 1 + 1e-8)
			{
				continue;
			}
			double distance = (e2x * qx + e2y * qy + e2z * qz) / determinant;
			if (distance >= 0)
			{
				best = Math.min(best, distance);
			}
		}
		return (float) best;
	}
}
