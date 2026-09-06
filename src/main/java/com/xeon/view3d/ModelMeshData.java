package com.xeon.view3d;

final class ModelMeshData
{
	private ModelMeshData()
	{
	}

	static Map3DMesh fromFrame(AnimatedObjectMesh.Frame frame)
	{
		float[] data = frame.rawVertexData();
		int stride = TerrainMesh.FLOATS_PER_VERTEX;
		int count = data.length / stride;
		double[] positions = new double[count * 3];
		int[] indices = new int[count];
		int[] colors = new int[count / 3];
		for (int v = 0; v < count; v++)
		{
			for (int axis = 0; axis < 3; axis++)
			{
				positions[v * 3 + axis] = data[v * stride + axis];
			}
			indices[v] = v;
		}
		for (int f = 0; f < colors.length; f++)
		{
			int color = 0;
			for (int channel = 0; channel < 4; channel++)
			{
				float sum = 0;
				for (int v = 0; v < 3; v++)
				{
					sum += data[(f * 3 + v) * stride + 6 + channel];
				}
				int value = Math.max(channel == 3 ? 25 : 0, Math.min(255, Math.round(sum * 255 / 3)));
				color |= value << (channel == 3 ? 24 : 16 - channel * 8);
			}
			colors[f] = color;
		}
		return new Map3DMesh(positions, indices, colors);
	}
}
