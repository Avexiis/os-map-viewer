/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2022-2023, dennisdev
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
package com.xeon.view3d;

import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;

final class HdosModelLoader
{
	private final ModelLoader runeliteLoader = new ModelLoader();

	ModelDefinition load(int modelId, byte[] data)
	{
		if (data == null || data.length < 2)
		{
			return null;
		}

		ModelDefinition definition;
		if (data[data.length - 1] == -1 && data[data.length - 2] == -1)
		{
			definition = new ModelDefinition();
			definition.id = modelId;
			decodeType1(definition, data);
			finish(definition);
			return definition;
		}

		definition = runeliteLoader.load(modelId, data);
		validate(definition);
		return definition;
	}

	private static void finish(ModelDefinition definition)
	{
		validate(definition);
		definition.computeNormals();
		if (definition.faceTextures != null)
		{
			definition.computeTextureUVCoordinates();
		}
		definition.computeAnimationTables();
	}

	private static void decodeType1(ModelDefinition def, byte[] data)
	{
		HdosByteBuffer buf1 = new HdosByteBuffer(data);
		HdosByteBuffer buf2 = new HdosByteBuffer(data);
		HdosByteBuffer buf3 = new HdosByteBuffer(data);
		HdosByteBuffer buf4 = new HdosByteBuffer(data);
		HdosByteBuffer buf5 = new HdosByteBuffer(data);
		HdosByteBuffer buf6 = new HdosByteBuffer(data);
		HdosByteBuffer buf7 = new HdosByteBuffer(data);
		buf1.offset = data.length - 23;
		int vertexCount = buf1.readUnsignedShort();
		int faceCount = buf1.readUnsignedShort();
		int textureFaceCount = buf1.readUnsignedByte();
		int flags = buf1.readUnsignedByte();
		boolean hasFaceRenderTypes = (flags & 0x1) == 1;
		boolean hasVersion = (flags & 0x8) == 0x8;
		int version = 1;
		if (hasVersion)
		{
			buf1.offset -= 7;
			version = buf1.readUnsignedByte();
			buf1.offset += 6;
		}

		int modelPriority = buf1.readUnsignedByte();
		int hasFaceAlpha = buf1.readUnsignedByte();
		int hasFaceSkins = buf1.readUnsignedByte();
		int hasFaceTextures = buf1.readUnsignedByte();
		int hasVertexSkins = buf1.readUnsignedByte();
		int vertexXBytes = buf1.readUnsignedShort();
		int vertexYBytes = buf1.readUnsignedShort();
		int vertexZBytes = buf1.readUnsignedShort();
		int faceIndexBytes = buf1.readUnsignedShort();
		int textureIndexBytes = buf1.readUnsignedShort();

		int simpleTextureFaceCount = 0;
		int complexTextureFaceCount = 0;
		int cubeTextureFaceCount = 0;
		if (textureFaceCount > 0)
		{
			def.textureRenderTypes = new byte[textureFaceCount];
			buf1.offset = 0;
			for (int i = 0; i < textureFaceCount; i++)
			{
				byte type = def.textureRenderTypes[i] = buf1.readByte();
				if (type == 0)
				{
					simpleTextureFaceCount++;
				}
				if (type >= 1 && type <= 3)
				{
					complexTextureFaceCount++;
				}
				if (type == 2)
				{
					cubeTextureFaceCount++;
				}
			}
		}

		int offset = textureFaceCount + vertexCount;
		int faceRenderTypesOffset = offset;
		if (hasFaceRenderTypes)
		{
			offset += faceCount;
		}
		int faceCompressionTypesOffset = offset;
		offset += faceCount;
		int facePrioritiesOffset = offset;
		if (modelPriority == 255)
		{
			offset += faceCount;
		}
		int faceSkinsOffset = offset;
		if (hasFaceSkins == 1)
		{
			offset += faceCount;
		}
		int vertexSkinsOffset = offset;
		if (hasVertexSkins == 1)
		{
			offset += vertexCount;
		}
		int faceAlphasOffset = offset;
		if (hasFaceAlpha == 1)
		{
			offset += faceCount;
		}
		int faceIndicesOffset = offset;
		offset += faceIndexBytes;
		int faceMaterialsOffset = offset;
		if (hasFaceTextures == 1)
		{
			offset += faceCount * 2;
		}
		int textureIndicesOffset = offset;
		offset += textureIndexBytes;
		int faceColorsOffset = offset;
		offset += faceCount * 2;
		int vertexXOffset = offset;
		offset += vertexXBytes;
		int vertexYOffset = offset;
		offset += vertexYBytes;
		int vertexZOffset = offset;
		offset += vertexZBytes;
		int simpleTexturesOffset = offset;
		offset += simpleTextureFaceCount * 6;
		int complexTexturesOffset = offset;
		offset += complexTextureFaceCount * 6;
		int textureBytes = version == 14 ? 7 : version >= 15 ? 9 : 6;
		int textureScalesOffset = offset;
		offset += complexTextureFaceCount * textureBytes;
		int textureRotationsOffset = offset;
		offset += complexTextureFaceCount;
		int textureDirectionsOffset = offset;
		offset += complexTextureFaceCount;
		int textureTranslationsOffset = offset;
		offset += complexTextureFaceCount + cubeTextureFaceCount * 2;

		def.vertexCount = vertexCount;
		def.faceCount = faceCount;
		def.numTextureFaces = textureFaceCount;
		def.vertexX = new int[vertexCount];
		def.vertexY = new int[vertexCount];
		def.vertexZ = new int[vertexCount];
		def.faceIndices1 = new int[faceCount];
		def.faceIndices2 = new int[faceCount];
		def.faceIndices3 = new int[faceCount];
		if (hasVertexSkins == 1)
		{
			def.packedVertexGroups = new int[vertexCount];
		}
		if (hasFaceRenderTypes)
		{
			def.faceRenderTypes = new byte[faceCount];
		}
		if (modelPriority == 255)
		{
			def.faceRenderPriorities = new byte[faceCount];
		}
		else
		{
			def.priority = (byte) modelPriority;
		}
		if (hasFaceAlpha == 1)
		{
			def.faceTransparencies = new byte[faceCount];
		}
		if (hasFaceSkins == 1)
		{
			def.packedTransparencyVertexGroups = new int[faceCount];
		}
		if (hasFaceTextures == 1)
		{
			def.faceTextures = new short[faceCount];
		}
		if (hasFaceTextures == 1 && textureFaceCount > 0)
		{
			def.textureCoords = new byte[faceCount];
		}
		def.faceColors = new short[faceCount];
		if (textureFaceCount > 0)
		{
			def.texIndices1 = new short[textureFaceCount];
			def.texIndices2 = new short[textureFaceCount];
			def.texIndices3 = new short[textureFaceCount];
		}

		readVertices(def, buf1, buf2, buf3, buf4, buf5, textureFaceCount, vertexXOffset, vertexYOffset,
			vertexZOffset, vertexSkinsOffset, hasVertexSkins == 1);
		readFaces(def, buf1, buf2, buf3, buf4, buf5, buf6, buf7, faceColorsOffset, faceRenderTypesOffset,
			facePrioritiesOffset, faceAlphasOffset, faceSkinsOffset, faceMaterialsOffset,
			textureIndicesOffset, hasFaceRenderTypes, modelPriority, hasFaceAlpha == 1, hasFaceSkins == 1,
			hasFaceTextures == 1);
		readFaceIndices(def, buf1, buf2, faceIndicesOffset, faceCompressionTypesOffset);
		if (textureFaceCount > 0)
		{
			readTextureMappings(def, buf1, buf2, buf3, buf4, buf5, buf6, simpleTexturesOffset,
				complexTexturesOffset, textureScalesOffset, textureRotationsOffset, textureDirectionsOffset,
				textureTranslationsOffset, version);
		}
		cleanupTextureReferences(def);
		if (version >= 13)
		{
			scaleDown(def, 2);
		}
	}

	private static void readVertices(
		ModelDefinition def,
		HdosByteBuffer vertexFlags,
		HdosByteBuffer vertexX,
		HdosByteBuffer vertexY,
		HdosByteBuffer vertexZ,
		HdosByteBuffer vertexSkins,
		int textureFaceCount,
		int vertexXOffset,
		int vertexYOffset,
		int vertexZOffset,
		int vertexSkinsOffset,
		boolean hasVertexSkins
	)
	{
		vertexFlags.offset = textureFaceCount;
		vertexX.offset = vertexXOffset;
		vertexY.offset = vertexYOffset;
		vertexZ.offset = vertexZOffset;
		vertexSkins.offset = vertexSkinsOffset;
		int lastX = 0;
		int lastY = 0;
		int lastZ = 0;
		for (int i = 0; i < def.vertexCount; i++)
		{
			int flag = vertexFlags.readUnsignedByte();
			int deltaX = (flag & 1) == 0 ? 0 : vertexX.readSmart2();
			int deltaY = (flag & 2) == 0 ? 0 : vertexY.readSmart2();
			int deltaZ = (flag & 4) == 0 ? 0 : vertexZ.readSmart2();
			def.vertexX[i] = lastX + deltaX;
			def.vertexY[i] = lastY + deltaY;
			def.vertexZ[i] = lastZ + deltaZ;
			lastX = def.vertexX[i];
			lastY = def.vertexY[i];
			lastZ = def.vertexZ[i];
			if (hasVertexSkins)
			{
				def.packedVertexGroups[i] = vertexSkins.readUnsignedByte();
			}
		}
	}

	private static void readFaces(
		ModelDefinition def,
		HdosByteBuffer faceColors,
		HdosByteBuffer faceRenderTypes,
		HdosByteBuffer facePriorities,
		HdosByteBuffer faceAlphas,
		HdosByteBuffer faceSkins,
		HdosByteBuffer faceMaterials,
		HdosByteBuffer textureIndices,
		int faceColorsOffset,
		int faceRenderTypesOffset,
		int facePrioritiesOffset,
		int faceAlphasOffset,
		int faceSkinsOffset,
		int faceMaterialsOffset,
		int textureIndicesOffset,
		boolean hasFaceRenderTypes,
		int modelPriority,
		boolean hasFaceAlpha,
		boolean hasFaceSkins,
		boolean hasFaceTextures
	)
	{
		faceColors.offset = faceColorsOffset;
		faceRenderTypes.offset = faceRenderTypesOffset;
		facePriorities.offset = facePrioritiesOffset;
		faceAlphas.offset = faceAlphasOffset;
		faceSkins.offset = faceSkinsOffset;
		faceMaterials.offset = faceMaterialsOffset;
		textureIndices.offset = textureIndicesOffset;
		for (int i = 0; i < def.faceCount; i++)
		{
			def.faceColors[i] = (short) faceColors.readUnsignedShort();
			if (hasFaceRenderTypes)
			{
				def.faceRenderTypes[i] = faceRenderTypes.readByte();
			}
			if (modelPriority == 255)
			{
				def.faceRenderPriorities[i] = facePriorities.readByte();
			}
			if (hasFaceAlpha)
			{
				def.faceTransparencies[i] = faceAlphas.readByte();
			}
			if (hasFaceSkins)
			{
				def.packedTransparencyVertexGroups[i] = faceSkins.readUnsignedByte();
			}
			if (hasFaceTextures)
			{
				def.faceTextures[i] = (short) (faceMaterials.readUnsignedShort() - 1);
			}
			if (def.textureCoords != null)
			{
				def.textureCoords[i] = def.faceTextures[i] == -1 ? -1 : (byte) (textureIndices.readUnsignedByte() - 1);
			}
		}
	}

	private static void readFaceIndices(
		ModelDefinition def,
		HdosByteBuffer faceIndices,
		HdosByteBuffer compressionTypes,
		int faceIndicesOffset,
		int compressionTypesOffset
	)
	{
		faceIndices.offset = faceIndicesOffset;
		compressionTypes.offset = compressionTypesOffset;
		int index1 = 0;
		int index2 = 0;
		int index3 = 0;
		int lastIndex = 0;
		for (int i = 0; i < def.faceCount; i++)
		{
			int type = compressionTypes.readUnsignedByte();
			if (type == 1)
			{
				index1 = faceIndices.readSmart2() + lastIndex;
				index2 = faceIndices.readSmart2() + index1;
				index3 = faceIndices.readSmart2() + index2;
				lastIndex = index3;
				def.faceIndices1[i] = index1;
				def.faceIndices2[i] = index2;
				def.faceIndices3[i] = index3;
			}
			else if (type == 2)
			{
				index2 = index3;
				index3 = faceIndices.readSmart2() + lastIndex;
				lastIndex = index3;
				def.faceIndices1[i] = index1;
				def.faceIndices2[i] = index2;
				def.faceIndices3[i] = index3;
			}
			else if (type == 3)
			{
				index1 = index3;
				index3 = faceIndices.readSmart2() + lastIndex;
				lastIndex = index3;
				def.faceIndices1[i] = index1;
				def.faceIndices2[i] = index2;
				def.faceIndices3[i] = index3;
			}
			else if (type == 4)
			{
				int swap = index1;
				index1 = index2;
				index2 = swap;
				index3 = faceIndices.readSmart2() + lastIndex;
				lastIndex = index3;
				def.faceIndices1[i] = index1;
				def.faceIndices2[i] = swap;
				def.faceIndices3[i] = index3;
			}
			else
			{
				throw new IllegalArgumentException("Unsupported HDOS model face compression type " + type);
			}
		}
	}

	private static void readTextureMappings(
		ModelDefinition def,
		HdosByteBuffer simpleBuffer,
		HdosByteBuffer complexBuffer,
		HdosByteBuffer scaleBuffer,
		HdosByteBuffer rotationBuffer,
		HdosByteBuffer directionBuffer,
		HdosByteBuffer translationBuffer,
		int simpleTexturesOffset,
		int complexTexturesOffset,
		int textureScalesOffset,
		int textureRotationsOffset,
		int textureDirectionsOffset,
		int textureTranslationsOffset,
		int version
	)
	{
		simpleBuffer.offset = simpleTexturesOffset;
		complexBuffer.offset = complexTexturesOffset;
		scaleBuffer.offset = textureScalesOffset;
		rotationBuffer.offset = textureRotationsOffset;
		directionBuffer.offset = textureDirectionsOffset;
		translationBuffer.offset = textureTranslationsOffset;
		for (int i = 0; i < def.numTextureFaces; i++)
		{
			int type = def.textureRenderTypes[i] & 0xFF;
			if (type == 0)
			{
				def.texIndices1[i] = (short) simpleBuffer.readUnsignedShort();
				def.texIndices2[i] = (short) simpleBuffer.readUnsignedShort();
				def.texIndices3[i] = (short) simpleBuffer.readUnsignedShort();
				continue;
			}
			if (type >= 1 && type <= 3)
			{
				def.texIndices1[i] = (short) complexBuffer.readUnsignedShort();
				def.texIndices2[i] = (short) complexBuffer.readUnsignedShort();
				def.texIndices3[i] = (short) complexBuffer.readUnsignedShort();
				if (version < 15)
				{
					scaleBuffer.readUnsignedShort();
					if (version >= 14)
					{
						scaleBuffer.readMedium();
					}
					else
					{
						scaleBuffer.readUnsignedShort();
					}
					scaleBuffer.readUnsignedShort();
				}
				else
				{
					scaleBuffer.readMedium();
					scaleBuffer.readMedium();
					scaleBuffer.readMedium();
				}
				rotationBuffer.readByte();
				directionBuffer.readByte();
				translationBuffer.readByte();
				if (type == 2)
				{
					translationBuffer.readByte();
					translationBuffer.readByte();
				}
			}
		}
	}

	private static void cleanupTextureReferences(ModelDefinition def)
	{
		boolean usesTextureCoords = false;
		boolean usesFaceTextures = false;
		if (def.textureCoords != null)
		{
			for (int i = 0; i < def.faceCount; i++)
			{
				int coord = def.textureCoords[i] & 0xFF;
				if (coord == 255)
				{
					continue;
				}
				if (coord >= def.numTextureFaces)
				{
					def.textureCoords[i] = -1;
					continue;
				}
				if (def.faceIndices1[i] == (def.texIndices1[coord] & 0xFFFF)
					&& def.faceIndices2[i] == (def.texIndices2[coord] & 0xFFFF)
					&& def.faceIndices3[i] == (def.texIndices3[coord] & 0xFFFF))
				{
					def.textureCoords[i] = -1;
				}
				else
				{
					usesTextureCoords = true;
				}
			}
			if (!usesTextureCoords)
			{
				def.textureCoords = null;
			}
		}

		if (def.faceTextures != null)
		{
			for (short texture : def.faceTextures)
			{
				if (texture != -1)
				{
					usesFaceTextures = true;
					break;
				}
			}
			if (!usesFaceTextures)
			{
				def.faceTextures = null;
			}
		}

		boolean usesFaceRenderTypes = false;
		if (def.faceRenderTypes != null)
		{
			for (byte renderType : def.faceRenderTypes)
			{
				if (renderType != 0)
				{
					usesFaceRenderTypes = true;
					break;
				}
			}
			if (!usesFaceRenderTypes)
			{
				def.faceRenderTypes = null;
			}
		}
	}

	private static void scaleDown(ModelDefinition def, int amount)
	{
		for (int i = 0; i < def.vertexCount; i++)
		{
			def.vertexX[i] >>= amount;
			def.vertexY[i] >>= amount;
			def.vertexZ[i] >>= amount;
		}
	}

	private static void validate(ModelDefinition def)
	{
		if (def == null)
		{
			throw new IllegalArgumentException("HDOS model definition is null");
		}
		if (def.vertexCount < 0 || def.faceCount < 0 || def.vertexX == null || def.faceIndices1 == null)
		{
			throw new IllegalArgumentException("HDOS model has missing geometry arrays");
		}
		for (int i = 0; i < def.faceCount; i++)
		{
			validateVertexIndex(def, def.faceIndices1[i], i);
			validateVertexIndex(def, def.faceIndices2[i], i);
			validateVertexIndex(def, def.faceIndices3[i], i);
		}
		if (def.textureCoords != null)
		{
			for (int i = 0; i < def.faceCount; i++)
			{
				int coord = def.textureCoords[i] & 0xFF;
				if (coord != 255 && coord >= def.numTextureFaces)
				{
					throw new IllegalArgumentException("HDOS model texture coord " + coord
						+ " exceeds texture face count " + def.numTextureFaces + " on face " + i);
				}
			}
		}
	}

	private static void validateVertexIndex(ModelDefinition def, int vertex, int face)
	{
		if (vertex < 0 || vertex >= def.vertexCount)
		{
			throw new IllegalArgumentException("HDOS model vertex index " + vertex
				+ " exceeds vertex count " + def.vertexCount + " on face " + face);
		}
	}
}
