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
import java.util.List;

final class MeshConnectivity
{
	static final double TOLERANCE = 1e-5;
	private static final double TOLERANCE_SQUARED = TOLERANCE * TOLERANCE;

	private MeshConnectivity()
	{
	}

	static boolean touches(Map3DMesh first, Map3DMesh second)
	{
		if (!boundsTouch(first, second))
		{
			return false;
		}
		for (int a = 0; a < first.faceCount(); a++)
		{
			MeshTopology.checkCancelled();
			Vec3[] ta = triangle(first, a);
			for (int b = 0; b < second.faceCount(); b++)
			{
				Vec3[] tb = triangle(second, b);
				if (!boundsTouch(ta, tb))
				{
					continue;
				}
				if (triangleDistanceSquared(ta, tb) <= TOLERANCE_SQUARED)
				{
					return true;
				}
			}
		}
		return false;
	}

	static boolean connected(List<Map3DMesh> meshes)
	{
		if (meshes.size() < 2)
		{
			return true;
		}
		boolean[] reached = new boolean[meshes.size()];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		reached[0] = true;
		queue.add(0);
		int count = 1;
		while (!queue.isEmpty())
		{
			int current = queue.remove();
			for (int candidate = 0; candidate < meshes.size(); candidate++)
			{
				if (reached[candidate] || !touches(meshes.get(current), meshes.get(candidate)))
				{
					continue;
				}
				reached[candidate] = true;
				queue.add(candidate);
				count++;
			}
		}
		return count == meshes.size();
	}

	private static boolean boundsTouch(Map3DMesh a, Map3DMesh b)
	{
		double[][] bounds = {bounds(a), bounds(b)};
		for (int axis = 0; axis < 3; axis++)
		{
			if (bounds[0][axis + 3] + TOLERANCE < bounds[1][axis]
				|| bounds[1][axis + 3] + TOLERANCE < bounds[0][axis])
			{
				return false;
			}
		}
		return true;
	}

	private static double[] bounds(Map3DMesh mesh)
	{
		double[] result = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		for (int v = 0; v < mesh.vertexCount(); v++)
		{
			for (int axis = 0; axis < 3; axis++)
			{
				result[axis] = Math.min(result[axis], mesh.coordinate(v, axis));
				result[axis + 3] = Math.max(result[axis + 3], mesh.coordinate(v, axis));
			}
		}
		return result;
	}

	private static boolean boundsTouch(Vec3[] a, Vec3[] b)
	{
		for (int axis = 0; axis < 3; axis++)
		{
			double aMin = Math.min(a[0].get(axis), Math.min(a[1].get(axis), a[2].get(axis)));
			double aMax = Math.max(a[0].get(axis), Math.max(a[1].get(axis), a[2].get(axis)));
			double bMin = Math.min(b[0].get(axis), Math.min(b[1].get(axis), b[2].get(axis)));
			double bMax = Math.max(b[0].get(axis), Math.max(b[1].get(axis), b[2].get(axis)));
			if (aMax + TOLERANCE < bMin || bMax + TOLERANCE < aMin)
			{
				return false;
			}
		}
		return true;
	}

	private static double triangleDistanceSquared(Vec3[] a, Vec3[] b)
	{
		for (int edge = 0; edge < 3; edge++)
		{
			if (segmentIntersectsTriangle(a[edge], a[(edge + 1) % 3], b)
				|| segmentIntersectsTriangle(b[edge], b[(edge + 1) % 3], a))
			{
				return 0;
			}
		}
		double best = Double.POSITIVE_INFINITY;
		for (Vec3 point : a)
		{
			best = Math.min(best, pointTriangleDistanceSquared(point, b));
		}
		for (Vec3 point : b)
		{
			best = Math.min(best, pointTriangleDistanceSquared(point, a));
		}
		for (int ae = 0; ae < 3; ae++)
		{
			for (int be = 0; be < 3; be++)
			{
				best = Math.min(best, segmentDistanceSquared(a[ae], a[(ae + 1) % 3], b[be], b[(be + 1) % 3]));
			}
		}
		return best;
	}

	private static boolean segmentIntersectsTriangle(Vec3 start, Vec3 end, Vec3[] triangle)
	{
		Vec3 direction = end.minus(start);
		Vec3 edge1 = triangle[1].minus(triangle[0]), edge2 = triangle[2].minus(triangle[0]);
		Vec3 p = direction.cross(edge2);
		double determinant = edge1.dot(p);
		if (Math.abs(determinant) < 1e-14)
		{
			return false;
		}
		double inverse = 1 / determinant;
		Vec3 t = start.minus(triangle[0]);
		double u = t.dot(p) * inverse;
		if (u < -1e-12 || u > 1 + 1e-12)
		{
			return false;
		}
		Vec3 q = t.cross(edge1);
		double v = direction.dot(q) * inverse;
		if (v < -1e-12 || u + v > 1 + 1e-12)
		{
			return false;
		}
		double distance = edge2.dot(q) * inverse;
		return distance >= -1e-12 && distance <= 1 + 1e-12;
	}

	private static double pointTriangleDistanceSquared(Vec3 point, Vec3[] triangle)
	{
		Vec3 a = triangle[0], ab = triangle[1].minus(a), ac = triangle[2].minus(a), ap = point.minus(a);
		double d1 = ab.dot(ap), d2 = ac.dot(ap);
		if (d1 <= 0 && d2 <= 0)
		{
			return ap.lengthSquared();
		}
		Vec3 bp = point.minus(triangle[1]);
		double d3 = ab.dot(bp), d4 = ac.dot(bp);
		if (d3 >= 0 && d4 <= d3)
		{
			return bp.lengthSquared();
		}
		double vc = d1 * d4 - d3 * d2;
		if (vc <= 0 && d1 >= 0 && d3 <= 0)
		{
			return point.minus(a.plus(ab.times(d1 / (d1 - d3)))).lengthSquared();
		}
		Vec3 cp = point.minus(triangle[2]);
		double d5 = ab.dot(cp), d6 = ac.dot(cp);
		if (d6 >= 0 && d5 <= d6)
		{
			return cp.lengthSquared();
		}
		double vb = d5 * d2 - d1 * d6;
		if (vb <= 0 && d2 >= 0 && d6 <= 0)
		{
			return point.minus(a.plus(ac.times(d2 / (d2 - d6)))).lengthSquared();
		}
		double va = d3 * d6 - d5 * d4;
		if (va <= 0 && d4 - d3 >= 0 && d5 - d6 >= 0)
		{
			Vec3 bc = triangle[2].minus(triangle[1]);
			return point.minus(triangle[1].plus(bc.times((d4 - d3) / ((d4 - d3) + (d5 - d6))))).lengthSquared();
		}
		double denominator = 1 / (va + vb + vc);
		return point.minus(a.plus(ab.times(vb * denominator)).plus(ac.times(vc * denominator))).lengthSquared();
	}

	private static double segmentDistanceSquared(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2)
	{
		Vec3 d1 = q1.minus(p1), d2 = q2.minus(p2), r = p1.minus(p2);
		double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r), s, t;
		if (a <= 1e-20 && e <= 1e-20)
		{
			return r.lengthSquared();
		}
		if (a <= 1e-20)
		{
			s = 0;
			t = clamp(f / e);
		}
		else
		{
			double c = d1.dot(r);
			if (e <= 1e-20)
			{
				t = 0;
				s = clamp(-c / a);
			}
			else
			{
				double b = d1.dot(d2), denominator = a * e - b * b;
				s = denominator == 0 ? 0 : clamp((b * f - c * e) / denominator);
				t = (b * s + f) / e;
				if (t < 0)
				{
					t = 0;
					s = clamp(-c / a);
				}
				else if (t > 1)
				{
					t = 1;
					s = clamp((b - c) / a);
				}
			}
		}
		return p1.plus(d1.times(s)).minus(p2.plus(d2.times(t))).lengthSquared();
	}

	private static double clamp(double value)
	{
		return Math.max(0, Math.min(1, value));
	}

	private static Vec3[] triangle(Map3DMesh mesh, int face)
	{
		Vec3[] result = new Vec3[3];
		for (int corner = 0; corner < 3; corner++)
		{
			int vertex = mesh.vertexIndex(face, corner);
			result[corner] = new Vec3(mesh.coordinate(vertex, 0), mesh.coordinate(vertex, 1), mesh.coordinate(vertex, 2));
		}
		return result;
	}

	private record Vec3(double x, double y, double z)
	{
		double get(int axis)
		{
			return axis == 0 ? x : axis == 1 ? y : z;
		}

		Vec3 plus(Vec3 value)
		{
			return new Vec3(x + value.x, y + value.y, z + value.z);
		}

		Vec3 minus(Vec3 value)
		{
			return new Vec3(x - value.x, y - value.y, z - value.z);
		}

		Vec3 times(double value)
		{
			return new Vec3(x * value, y * value, z * value);
		}

		double dot(Vec3 value)
		{
			return x * value.x + y * value.y + z * value.z;
		}

		Vec3 cross(Vec3 value)
		{
			return new Vec3(y * value.z - z * value.y, z * value.x - x * value.z, x * value.y - y * value.x);
		}

		double lengthSquared()
		{
			return dot(this);
		}
	}
}
