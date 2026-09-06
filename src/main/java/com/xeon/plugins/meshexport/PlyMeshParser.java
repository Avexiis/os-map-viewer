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
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class PlyMeshParser
{
	private static final int MAX_HEADER_BYTES = 1024 * 1024;
	private static final int MAX_ELEMENT_COUNT = 5_000_000;

	private PlyMeshParser()
	{
	}

	static Map3DMesh parse(byte[] data) throws IOException
	{
		Header header = parseHeader(data);
		ByteBuffer input = ByteBuffer.wrap(data, header.dataOffset(), data.length - header.dataOffset()).order(header.byteOrder());
		double[] sourcePositions = null;
		int[] vertexColors = null;
		List<int[]> faces = new ArrayList<>();
		try
		{
			for (Element element : header.elements())
			{
				boolean vertices = "vertex".equals(element.name());
				boolean face = "face".equals(element.name());
				if (vertices)
				{
					sourcePositions = new double[Math.multiplyExact(element.count(), 3)];
					vertexColors = new int[element.count()];
					Arrays.fill(vertexColors, 0xFFB9BEC6);
				}
				for (int row = 0; row < element.count(); row++)
				{
					double x = 0;
					double y = 0;
					double z = 0;
					int red = 185;
					int green = 190;
					int blue = 198;
					int alpha = 255;
					boolean hasX = false;
					boolean hasY = false;
					boolean hasZ = false;
					boolean hasColor = false;
					int[] faceIndices = null;
					for (Property property : element.properties())
					{
						if (property.list())
						{
							int count = checkedCount(property.countType().readInteger(input));
							if (face && isVertexIndexProperty(property.name()))
							{
								faceIndices = new int[count];
								for (int i = 0; i < count; i++)
								{
									long index = property.type().readInteger(input);
									if (index < 0 || index > Integer.MAX_VALUE)
									{
										throw new IOException("PLY face contains an invalid vertex index");
									}
									faceIndices[i] = (int) index;
								}
							}
							else
							{
								for (int i = 0; i < count; i++)
								{
									property.type().read(input);
								}
							}
							continue;
						}

						double value = property.type().read(input);
						if (!vertices)
						{
							continue;
						}
						switch (property.name())
						{
							case "x" ->
							{
								x = value;
								hasX = true;
							}
							case "y" ->
							{
								y = value;
								hasY = true;
							}
							case "z" ->
							{
								z = value;
								hasZ = true;
							}
							case "red", "r" ->
							{
								red = color(value, property.type());
								hasColor = true;
							}
							case "green", "g" ->
							{
								green = color(value, property.type());
								hasColor = true;
							}
							case "blue", "b" ->
							{
								blue = color(value, property.type());
								hasColor = true;
							}
							case "alpha", "a" -> alpha = color(value, property.type());
							default ->
							{
							}
						}
					}
					if (vertices)
					{
						if (!hasX || !hasY || !hasZ)
						{
							throw new IOException("PLY vertex data has no XYZ position");
						}
						sourcePositions[row * 3] = x;
						sourcePositions[row * 3 + 1] = y;
						sourcePositions[row * 3 + 2] = z;
						if (hasColor)
						{
							vertexColors[row] = alpha << 24 | red << 16 | green << 8 | blue;
						}
					}
					if (face && faceIndices != null && faceIndices.length >= 3)
					{
						faces.add(faceIndices);
					}
				}
			}
		}
		catch (ArithmeticException | BufferUnderflowException | IndexOutOfBoundsException ex)
		{
			throw new IOException("RuneProfile returned truncated PLY data", ex);
		}
		if (sourcePositions == null || vertexColors == null || faces.isEmpty())
		{
			throw new IOException("RuneProfile returned an empty PLY model");
		}

		double[] positions = new double[sourcePositions.length];
		for (int vertex = 0; vertex < sourcePositions.length / 3; vertex++)
		{
			positions[vertex * 3] = sourcePositions[vertex * 3];
			positions[vertex * 3 + 1] = sourcePositions[vertex * 3 + 2];
			positions[vertex * 3 + 2] = -sourcePositions[vertex * 3 + 1];
		}
		List<Integer> triangles = new ArrayList<>();
		List<Integer> faceColors = new ArrayList<>();
		for (int[] polygon : faces)
		{
			for (int index : polygon)
			{
				if (index < 0 || index >= vertexColors.length)
				{
					throw new IOException("PLY face references a missing vertex");
				}
			}
			for (int corner = 1; corner + 1 < polygon.length; corner++)
			{
				triangles.add(polygon[0]);
				triangles.add(polygon[corner]);
				triangles.add(polygon[corner + 1]);
				faceColors.add(averageColor(vertexColors[polygon[0]], vertexColors[polygon[corner]], vertexColors[polygon[corner + 1]]));
			}
		}
		return new Map3DMesh(positions, integers(triangles), integers(faceColors));
	}

	private static Header parseHeader(byte[] data) throws IOException
	{
		List<Element> elements = new ArrayList<>();
		Element current = null;
		ByteOrder byteOrder = null;
		int dataOffset = -1;
		int lineStart = 0;
		for (int index = 0; index < data.length && index < MAX_HEADER_BYTES; index++)
		{
			if (data[index] != '\n')
			{
				continue;
			}
			int lineEnd = index > lineStart && data[index - 1] == '\r' ? index - 1 : index;
			String line = new String(data, lineStart, lineEnd - lineStart, StandardCharsets.US_ASCII).trim();
			lineStart = index + 1;
			if ("end_header".equals(line))
			{
				dataOffset = index + 1;
				break;
			}
			if (line.startsWith("format "))
			{
				if (line.startsWith("format binary_little_endian "))
				{
					byteOrder = ByteOrder.LITTLE_ENDIAN;
				}
				else if (line.startsWith("format binary_big_endian "))
				{
					byteOrder = ByteOrder.BIG_ENDIAN;
				}
				else
				{
					throw new IOException("RuneProfile returned an unsupported PLY format");
				}
			}
			else if (line.startsWith("element "))
			{
				String[] parts = line.split("\\s+");
				if (parts.length != 3)
				{
					throw new IOException("PLY contains an invalid element declaration");
				}
				int count;
				try
				{
					count = Integer.parseInt(parts[2]);
				}
				catch (NumberFormatException ex)
				{
					throw new IOException("PLY contains an invalid element count", ex);
				}
				if (count < 0 || count > MAX_ELEMENT_COUNT)
				{
					throw new IOException("PLY element count is outside the supported range");
				}
				current = new Element(parts[1].toLowerCase(Locale.ROOT), count, new ArrayList<>());
				elements.add(current);
			}
			else if (line.startsWith("property "))
			{
				if (current == null)
				{
					throw new IOException("PLY property has no element");
				}
				String[] parts = line.split("\\s+");
				if (parts.length == 3)
				{
					current.properties().add(new Property(parts[2].toLowerCase(Locale.ROOT), type(parts[1]), null));
				}
				else if (parts.length == 5 && "list".equals(parts[1]))
				{
					current.properties().add(new Property(parts[4].toLowerCase(Locale.ROOT), type(parts[3]), type(parts[2])));
				}
				else
				{
					throw new IOException("PLY contains an invalid property declaration");
				}
			}
		}
		if (dataOffset < 0 || byteOrder == null || elements.isEmpty())
		{
			throw new IOException("RuneProfile returned an invalid PLY header");
		}
		return new Header(byteOrder, dataOffset, elements);
	}

	private static PlyType type(String name) throws IOException
	{
		try
		{
			return PlyType.valueOf(name.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex)
		{
			return switch (name.toLowerCase(Locale.ROOT))
			{
				case "char" -> PlyType.INT8;
				case "uchar" -> PlyType.UINT8;
				case "short" -> PlyType.INT16;
				case "ushort" -> PlyType.UINT16;
				case "int" -> PlyType.INT32;
				case "uint" -> PlyType.UINT32;
				case "float" -> PlyType.FLOAT32;
				case "double" -> PlyType.FLOAT64;
				default -> throw new IOException("PLY uses an unsupported property type: " + name);
			};
		}
	}

	private static int checkedCount(long value) throws IOException
	{
		if (value < 0 || value > MAX_ELEMENT_COUNT)
		{
			throw new IOException("PLY list size is outside the supported range");
		}
		return (int) value;
	}

	private static boolean isVertexIndexProperty(String name)
	{
		return "vertex_indices".equals(name) || "vertex_index".equals(name);
	}

	private static int color(double value, PlyType type)
	{
		double scaled = type.floating && value >= 0 && value <= 1 ? value * 255 : value;
		return (int) Math.max(0, Math.min(255, Math.round(scaled)));
	}

	private static int averageColor(int first, int second, int third)
	{
		int alpha = (((first >>> 24) & 0xFF) + ((second >>> 24) & 0xFF) + ((third >>> 24) & 0xFF)) / 3;
		int red = (((first >>> 16) & 0xFF) + ((second >>> 16) & 0xFF) + ((third >>> 16) & 0xFF)) / 3;
		int green = (((first >>> 8) & 0xFF) + ((second >>> 8) & 0xFF) + ((third >>> 8) & 0xFF)) / 3;
		int blue = ((first & 0xFF) + (second & 0xFF) + (third & 0xFF)) / 3;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int[] integers(List<Integer> values)
	{
		int[] result = new int[values.size()];
		for (int i = 0; i < values.size(); i++)
		{
			result[i] = values.get(i);
		}
		return result;
	}

	private record Header(ByteOrder byteOrder, int dataOffset, List<Element> elements)
	{
	}

	private record Element(String name, int count, List<Property> properties)
	{
	}

	private record Property(String name, PlyType type, PlyType countType)
	{
		boolean list()
		{
			return countType != null;
		}
	}

	private enum PlyType
	{
		INT8(false), UINT8(false), INT16(false), UINT16(false), INT32(false), UINT32(false), FLOAT32(true), FLOAT64(true);

		private final boolean floating;

		PlyType(boolean floating)
		{
			this.floating = floating;
		}

		double read(ByteBuffer input)
		{
			return switch (this)
			{
				case INT8 -> input.get();
				case UINT8 -> input.get() & 0xFF;
				case INT16 -> input.getShort();
				case UINT16 -> input.getShort() & 0xFFFF;
				case INT32 -> input.getInt();
				case UINT32 -> Integer.toUnsignedLong(input.getInt());
				case FLOAT32 -> input.getFloat();
				case FLOAT64 -> input.getDouble();
			};
		}

		long readInteger(ByteBuffer input) throws IOException
		{
			if (floating)
			{
				throw new IOException("PLY uses a floating-point list index");
			}
			return (long) read(input);
		}
	}
}
