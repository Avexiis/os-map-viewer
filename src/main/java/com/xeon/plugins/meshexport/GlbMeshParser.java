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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.xeon.view3d.Map3DMesh;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class GlbMeshParser
{
	private static final int GLB_MAGIC = 0x46546C67;
	private static final int JSON_CHUNK = 0x4E4F534A;
	private static final int BINARY_CHUNK = 0x004E4942;
	private static final int TRIANGLES = 4;
	private static final int MAX_ACCESSOR_COUNT = 5_000_000;
	private static final Gson GSON = new Gson();

	private GlbMeshParser()
	{
	}

	static Map3DMesh parse(byte[] data) throws IOException
	{
		Chunks chunks = chunks(data);
		JsonObject root;
		try
		{
			String json = new String(data, chunks.jsonOffset(), chunks.jsonLength(), StandardCharsets.UTF_8).trim();
			root = GSON.fromJson(json, JsonObject.class);
		}
		catch (JsonParseException ex)
		{
			throw new IOException("RuneProfile returned invalid GLB metadata", ex);
		}
		if (root == null || chunks.binaryOffset() < 0)
		{
			throw new IOException("RuneProfile returned an incomplete GLB model");
		}
		AccessorReader reader = new AccessorReader(data, chunks, root);
		JsonArray meshes = array(root, "meshes");
		DoubleBuilder positions = new DoubleBuilder();
		IntBuilder triangles = new IntBuilder();
		IntBuilder faceColors = new IntBuilder();
		for (JsonElement meshElement : meshes)
		{
			JsonObject mesh = object(meshElement, "GLB mesh");
			for (JsonElement primitiveElement : array(mesh, "primitives"))
			{
				JsonObject primitive = object(primitiveElement, "GLB primitive");
				if (integer(primitive, "mode", TRIANGLES) != TRIANGLES)
				{
					throw new IOException("RuneProfile GLB uses an unsupported primitive mode");
				}
				JsonObject attributes = object(primitive.get("attributes"), "GLB primitive attributes");
				int positionIndex = integer(attributes, "POSITION", -1);
				if (positionIndex < 0)
				{
					throw new IOException("RuneProfile GLB primitive has no positions");
				}
				Values sourcePositions = reader.values(positionIndex, "VEC3");
				int vertexBase = positions.size() / 3;
				for (int vertex = 0; vertex < sourcePositions.count(); vertex++)
				{
					positions.add(sourcePositions.value(vertex, 0));
					positions.add(sourcePositions.value(vertex, 1));
					positions.add(sourcePositions.value(vertex, 2));
				}
				Values colors = null;
				int colorIndex = integer(attributes, "COLOR_0", -1);
				if (colorIndex >= 0)
				{
					colors = reader.values(colorIndex, null);
					if ((colors.components() != 3 && colors.components() != 4) || colors.count() < sourcePositions.count())
					{
						throw new IOException("RuneProfile GLB contains invalid vertex colors");
					}
				}
				double[] materialColor = materialColor(root, integer(primitive, "material", -1));
				int[] indices = indices(primitive, reader, sourcePositions.count());
				if (indices.length % 3 != 0)
				{
					throw new IOException("RuneProfile GLB contains an incomplete triangle");
				}
				for (int index = 0; index < indices.length; index += 3)
				{
					int first = checkedIndex(indices[index], sourcePositions.count());
					int second = checkedIndex(indices[index + 1], sourcePositions.count());
					int third = checkedIndex(indices[index + 2], sourcePositions.count());
					triangles.add(vertexBase + first);
					triangles.add(vertexBase + second);
					triangles.add(vertexBase + third);
					faceColors.add(faceColor(colors, materialColor, first, second, third));
				}
			}
		}
		if (triangles.size() == 0)
		{
			throw new IOException("RuneProfile returned an empty GLB model");
		}
		return new Map3DMesh(positions.toArray(), triangles.toArray(), faceColors.toArray());
	}

	private static Chunks chunks(byte[] data) throws IOException
	{
		if (data.length < 20)
		{
			throw new IOException("RuneProfile returned a truncated GLB model");
		}
		ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		if (input.getInt() != GLB_MAGIC || input.getInt() != 2)
		{
			throw new IOException("RuneProfile returned an unsupported GLB model");
		}
		long declaredLength = Integer.toUnsignedLong(input.getInt());
		if (declaredLength != data.length)
		{
			throw new IOException("RuneProfile returned a truncated GLB model");
		}
		int jsonOffset = -1;
		int jsonLength = 0;
		int binaryOffset = -1;
		int binaryLength = 0;
		while (input.remaining() >= 8)
		{
			long length = Integer.toUnsignedLong(input.getInt());
			int type = input.getInt();
			if (length > input.remaining())
			{
				throw new IOException("RuneProfile returned a truncated GLB chunk");
			}
			int offset = input.position();
			if (type == JSON_CHUNK && jsonOffset < 0)
			{
				jsonOffset = offset;
				jsonLength = (int) length;
			}
			else if (type == BINARY_CHUNK && binaryOffset < 0)
			{
				binaryOffset = offset;
				binaryLength = (int) length;
			}
			input.position(offset + (int) length);
		}
		if (jsonOffset < 0 || binaryOffset < 0)
		{
			throw new IOException("RuneProfile returned an incomplete GLB model");
		}
		return new Chunks(jsonOffset, jsonLength, binaryOffset, binaryLength);
	}

	private static int[] indices(JsonObject primitive, AccessorReader reader, int vertexCount) throws IOException
	{
		int accessor = integer(primitive, "indices", -1);
		if (accessor < 0)
		{
			int[] sequential = new int[vertexCount];
			for (int i = 0; i < vertexCount; i++)
			{
				sequential[i] = i;
			}
			return sequential;
		}
		Values values = reader.values(accessor, "SCALAR");
		int[] result = new int[values.count()];
		for (int i = 0; i < result.length; i++)
		{
			double value = values.value(i, 0);
			if (!Double.isFinite(value) || value < 0 || value > Integer.MAX_VALUE || value != Math.rint(value))
			{
				throw new IOException("RuneProfile GLB contains an invalid vertex index");
			}
			result[i] = (int) value;
		}
		return result;
	}

	private static int checkedIndex(int index, int vertexCount) throws IOException
	{
		if (index < 0 || index >= vertexCount)
		{
			throw new IOException("RuneProfile GLB face references a missing vertex");
		}
		return index;
	}

	private static int faceColor(Values colors, double[] material, int first, int second, int third)
	{
		double[] channels = new double[4];
		for (int channel = 0; channel < channels.length; channel++)
		{
			double value = colors == null || channel >= colors.components()
				? 1
				: (colors.value(first, channel) + colors.value(second, channel) + colors.value(third, channel)) / 3;
			channels[channel] = Math.max(0, Math.min(1, value * material[channel]));
		}
		return (int) Math.round(channels[3] * 255) << 24
			| (int) Math.round(channels[0] * 255) << 16
			| (int) Math.round(channels[1] * 255) << 8
			| (int) Math.round(channels[2] * 255);
	}

	private static double[] materialColor(JsonObject root, int materialIndex) throws IOException
	{
		double[] result = {1, 1, 1, 1};
		JsonArray materials = optionalArray(root, "materials");
		if (materialIndex < 0 || materials == null)
		{
			return result;
		}
		if (materialIndex >= materials.size())
		{
			throw new IOException("RuneProfile GLB references a missing material");
		}
		JsonObject material = object(materials.get(materialIndex), "GLB material");
		JsonElement pbrElement = material.get("pbrMetallicRoughness");
		if (pbrElement == null || !pbrElement.isJsonObject())
		{
			return result;
		}
		JsonArray factor = optionalArray(pbrElement.getAsJsonObject(), "baseColorFactor");
		if (factor == null)
		{
			return result;
		}
		if (factor.size() != 4)
		{
			throw new IOException("RuneProfile GLB contains an invalid material color");
		}
		try
		{
			for (int channel = 0; channel < result.length; channel++)
			{
				result[channel] = factor.get(channel).getAsDouble();
			}
		}
		catch (RuntimeException ex)
		{
			throw new IOException("RuneProfile GLB contains an invalid material color", ex);
		}
		return result;
	}

	private static JsonArray array(JsonObject object, String name) throws IOException
	{
		JsonArray result = optionalArray(object, name);
		if (result == null)
		{
			throw new IOException("RuneProfile GLB has no " + name);
		}
		return result;
	}

	private static JsonArray optionalArray(JsonObject object, String name) throws IOException
	{
		JsonElement element = object.get(name);
		if (element == null)
		{
			return null;
		}
		if (!element.isJsonArray())
		{
			throw new IOException("RuneProfile GLB contains invalid " + name);
		}
		return element.getAsJsonArray();
	}

	private static JsonObject object(JsonElement element, String name) throws IOException
	{
		if (element == null || !element.isJsonObject())
		{
			throw new IOException("RuneProfile returned an invalid " + name);
		}
		return element.getAsJsonObject();
	}

	private static int integer(JsonObject object, String name, int fallback) throws IOException
	{
		JsonElement element = object.get(name);
		if (element == null)
		{
			return fallback;
		}
		try
		{
			return element.getAsInt();
		}
		catch (RuntimeException ex)
		{
			throw new IOException("RuneProfile GLB contains invalid " + name, ex);
		}
	}

	private record Chunks(int jsonOffset, int jsonLength, int binaryOffset, int binaryLength)
	{
	}

	private static final class AccessorReader
	{
		private final byte[] data;
		private final Chunks chunks;
		private final JsonArray accessors;
		private final JsonArray bufferViews;

		private AccessorReader(byte[] data, Chunks chunks, JsonObject root) throws IOException
		{
			this.data = data;
			this.chunks = chunks;
			accessors = array(root, "accessors");
			bufferViews = array(root, "bufferViews");
		}

		private Values values(int accessorIndex, String requiredType) throws IOException
		{
			if (accessorIndex < 0 || accessorIndex >= accessors.size())
			{
				throw new IOException("RuneProfile GLB references a missing accessor");
			}
			JsonObject accessor = object(accessors.get(accessorIndex), "GLB accessor");
			if (accessor.has("sparse"))
			{
				throw new IOException("RuneProfile GLB uses unsupported sparse data");
			}
			int viewIndex = integer(accessor, "bufferView", -1);
			if (viewIndex < 0 || viewIndex >= bufferViews.size())
			{
				throw new IOException("RuneProfile GLB references a missing buffer view");
			}
			JsonObject view = object(bufferViews.get(viewIndex), "GLB buffer view");
			if (integer(view, "buffer", 0) != 0)
			{
				throw new IOException("RuneProfile GLB uses an external data buffer");
			}
			String type;
			try
			{
				type = accessor.get("type").getAsString();
			}
			catch (RuntimeException ex)
			{
				throw new IOException("RuneProfile GLB contains an invalid accessor type", ex);
			}
			if (requiredType != null && !requiredType.equals(type))
			{
				throw new IOException("RuneProfile GLB contains unexpected accessor data");
			}
			int components = components(type);
			int componentType = integer(accessor, "componentType", -1);
			int componentBytes = componentBytes(componentType);
			int count = integer(accessor, "count", -1);
			if (count < 0 || count > MAX_ACCESSOR_COUNT)
			{
				throw new IOException("RuneProfile GLB accessor size is outside the supported range");
			}
			int elementBytes = Math.multiplyExact(components, componentBytes);
			int stride = integer(view, "byteStride", elementBytes);
			if (stride < elementBytes)
			{
				throw new IOException("RuneProfile GLB contains an invalid accessor stride");
			}
			int viewOffset = integer(view, "byteOffset", 0);
			int viewLength = integer(view, "byteLength", -1);
			int accessorOffset = integer(accessor, "byteOffset", 0);
			long start = (long) chunks.binaryOffset() + viewOffset + accessorOffset;
			long end = count == 0 ? start : start + (long) (count - 1) * stride + elementBytes;
			long viewEnd = (long) chunks.binaryOffset() + viewOffset + viewLength;
			long binaryEnd = (long) chunks.binaryOffset() + chunks.binaryLength();
			if (viewOffset < 0 || viewLength < 0 || accessorOffset < 0 || start < chunks.binaryOffset()
				|| end < start || end > viewEnd || viewEnd > binaryEnd || binaryEnd > data.length)
			{
				throw new IOException("RuneProfile GLB accessor is outside its data buffer");
			}
			boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();
			return new Values(data, (int) start, stride, count, components, componentType, componentBytes, normalized);
		}
	}

	private static int components(String type) throws IOException
	{
		return switch (type)
		{
			case "SCALAR" -> 1;
			case "VEC2" -> 2;
			case "VEC3" -> 3;
			case "VEC4" -> 4;
			default -> throw new IOException("RuneProfile GLB uses an unsupported accessor type");
		};
	}

	private static int componentBytes(int type) throws IOException
	{
		return switch (type)
		{
			case 5120, 5121 -> 1;
			case 5122, 5123 -> 2;
			case 5125, 5126 -> 4;
			default -> throw new IOException("RuneProfile GLB uses an unsupported component type");
		};
	}

	private record Values(byte[] data, int offset, int stride, int count, int components,
	                      int componentType, int componentBytes, boolean normalized)
	{
		double value(int element, int component)
		{
			int position = offset + element * stride + component * componentBytes;
			double value = switch (componentType)
			{
				case 5120 -> data[position];
				case 5121 -> data[position] & 0xFF;
				case 5122 -> (short) unsignedShort(data, position);
				case 5123 -> unsignedShort(data, position);
				case 5125 -> Integer.toUnsignedLong(littleEndianInt(data, position));
				case 5126 -> Float.intBitsToFloat(littleEndianInt(data, position));
				default -> throw new IllegalStateException();
			};
			if (!normalized || componentType == 5126)
			{
				return value;
			}
			return switch (componentType)
			{
				case 5120 -> Math.max(-1, value / 127);
				case 5121 -> value / 255;
				case 5122 -> Math.max(-1, value / 32767);
				case 5123 -> value / 65535;
				case 5125 -> value / 4294967295.0;
				default -> value;
			};
		}
	}

	private static int unsignedShort(byte[] data, int offset)
	{
		return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
	}

	private static int littleEndianInt(byte[] data, int offset)
	{
		return data[offset] & 0xFF
			| (data[offset + 1] & 0xFF) << 8
			| (data[offset + 2] & 0xFF) << 16
			| (data[offset + 3] & 0xFF) << 24;
	}

	private static final class DoubleBuilder
	{
		private double[] values = new double[1024];
		private int size;

		private void add(double value)
		{
			if (size == values.length)
			{
				values = Arrays.copyOf(values, values.length * 2);
			}
			values[size++] = value;
		}

		private int size()
		{
			return size;
		}

		private double[] toArray()
		{
			return Arrays.copyOf(values, size);
		}
	}

	private static final class IntBuilder
	{
		private int[] values = new int[1024];
		private int size;

		private void add(int value)
		{
			if (size == values.length)
			{
				values = Arrays.copyOf(values, values.length * 2);
			}
			values[size++] = value;
		}

		private int size()
		{
			return size;
		}

		private int[] toArray()
		{
			return Arrays.copyOf(values, size);
		}
	}
}
