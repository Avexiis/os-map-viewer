/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.

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

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

final class NpcDefinitionProvider
{
	private static final int CACHE_LIMIT = 4096;
	private static final int MAX_NPC_TRANSFORM_DEPTH = 8;
	private static final int REV_210_NPC_ARCHIVE_REV = 1493;

	private final Store store;
	private final Map<Integer, NpcDefinition3D> definitions = new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, NpcDefinition3D> eldest)
		{
			return size() > CACHE_LIMIT;
		}
	};
	private ArchiveFiles npcFiles;
	private boolean rev210HeadIcons = true;

	NpcDefinitionProvider(Store store)
	{
		this.store = store;
	}

	NpcDefinition3D definition(int npcId)
	{
		NpcDefinition3D definition = completionStateDefinition(load(npcId), new HashSet<>(), 0);
		return isDisabledGameModeNpc(definition) ? null : definition;
	}

	private NpcDefinition3D completionStateDefinition(NpcDefinition3D definition, Set<Integer> seen, int depth)
	{
		if (definition == null || depth >= MAX_NPC_TRANSFORM_DEPTH || !seen.add(definition.id))
		{
			return definition;
		}

		int[] transforms = definition.configs;
		if (transforms == null || transforms.length == 0)
		{
			return definition;
		}

		NpcDefinition3D fallback = null;
		for (int i = transforms.length - 1; i >= 0; i--)
		{
			int npcId = transforms[i];
			if (npcId < 0)
			{
				continue;
			}
			NpcDefinition3D candidate = load(npcId);
			if (candidate == null || isDisabledGameModeNpc(candidate))
			{
				continue;
			}
			if (fallback == null)
			{
				fallback = candidate;
			}

			NpcDefinition3D resolved = completionStateDefinition(candidate, seen, depth + 1);
			if (resolved != null && resolved.hasModels() && !isDisabledGameModeNpc(resolved))
			{
				return resolved;
			}
		}
		return fallback == null || isDisabledGameModeNpc(fallback) ? definition : fallback;
	}

	private static boolean isDisabledGameModeNpc(NpcDefinition3D definition)
	{
		if (definition == null)
		{
			return false;
		}
		String name = definition.name == null ? "" : definition.name.toLowerCase(Locale.ROOT);
		return name.equals("financial wizard"); //DMM banker replacement
	}

	private NpcDefinition3D load(int npcId)
	{
		if (npcId < 0)
		{
			return null;
		}
		return definitions.computeIfAbsent(npcId, this::loadUncached);
	}

	private NpcDefinition3D loadUncached(int npcId)
	{
		try
		{
			ArchiveFiles files = npcFiles();
			FSFile file = files == null ? null : files.findFile(npcId);
			if (file == null || file.getContents() == null || file.getContents().length == 0)
			{
				return null;
			}
			return decode(npcId, file.getContents());
		}
		catch (IOException | RuntimeException ex)
		{
			System.err.println("Failed to load NPC definition " + npcId + ": " + ex.getMessage());
			return null;
		}
	}

	private ArchiveFiles npcFiles() throws IOException
	{
		if (npcFiles != null)
		{
			return npcFiles;
		}

		Index index = store.getIndex(IndexType.CONFIGS);
		Archive archive = index == null ? null : index.getArchive(ConfigType.NPC.getId());
		if (archive == null)
		{
			return null;
		}
		rev210HeadIcons = archive.getRevision() >= REV_210_NPC_ARCHIVE_REV;
		byte[] archiveData = store.getStorage().loadArchive(archive);
		npcFiles = archive.getFiles(archiveData);
		return npcFiles;
	}

	private NpcDefinition3D decode(int npcId, byte[] bytes)
	{
		NpcDefinition3D definition = new NpcDefinition3D(npcId);
		InputStream stream = new InputStream(bytes);
		while (stream.remaining() > 0)
		{
			int opcode = stream.readUnsignedByte();
			if (opcode == 0)
			{
				break;
			}
			decodeOpcode(definition, opcode, stream);
		}
		if (definition.footprintSize == -1)
		{
			definition.footprintSize = (int) (0.4f * definition.size() * SceneScale.SCENE_UNITS_PER_TILE);
		}
		return definition;
	}

	private void decodeOpcode(NpcDefinition3D definition, int opcode, InputStream stream)
	{
		if (opcode == 1)
		{
			int count = stream.readUnsignedByte();
			definition.models = new int[count];
			for (int i = 0; i < count; i++)
			{
				definition.models[i] = stream.readUnsignedShort();
			}
		}
		else if (opcode == 2)
		{
			definition.name = stream.readString();
		}
		else if (opcode == 3)
		{
			stream.readString();
		}
		else if (opcode == 12)
		{
			definition.size = stream.readUnsignedByte();
		}
		else if (opcode == 13)
		{
			definition.standingAnimation = stream.readUnsignedShort();
		}
		else if (opcode == 14)
		{
			definition.walkingAnimation = stream.readUnsignedShort();
		}
		else if (opcode == 15 || opcode == 16)
		{
			int sequence = stream.readUnsignedShort();
			if (opcode == 15)
			{
				definition.rotateLeftAnimation = sequence;
			}
			else
			{
				definition.rotateRightAnimation = sequence;
			}
		}
		else if (opcode == 17)
		{
			definition.walkingAnimation = stream.readUnsignedShort();
			definition.rotate180Animation = stream.readUnsignedShort();
			definition.rotateLeftAnimation = stream.readUnsignedShort();
			definition.rotateRightAnimation = stream.readUnsignedShort();
		}
		else if (opcode == 18)
		{
			stream.readUnsignedShort();
		}
		else if (opcode >= 30 && opcode < 35)
		{
			stream.readString();
		}
		else if (opcode == 40)
		{
			int count = stream.readUnsignedByte();
			definition.recolorToFind = new short[count];
			definition.recolorToReplace = new short[count];
			for (int i = 0; i < count; i++)
			{
				definition.recolorToFind[i] = (short) stream.readUnsignedShort();
				definition.recolorToReplace[i] = (short) stream.readUnsignedShort();
			}
		}
		else if (opcode == 41)
		{
			int count = stream.readUnsignedByte();
			definition.retextureToFind = new short[count];
			definition.retextureToReplace = new short[count];
			for (int i = 0; i < count; i++)
			{
				definition.retextureToFind[i] = (short) stream.readUnsignedShort();
				definition.retextureToReplace[i] = (short) stream.readUnsignedShort();
			}
		}
		else if (opcode == 44 || opcode == 45)
		{
			stream.readUnsignedShort();
		}
		else if (opcode == 60)
		{
			skipUnsignedShortArray(stream);
		}
		else if (opcode == 61)
		{
			int count = stream.readUnsignedByte();
			definition.models = new int[count];
			for (int i = 0; i < count; i++)
			{
				definition.models[i] = stream.readInt();
			}
		}
		else if (opcode == 62)
		{
			int count = stream.readUnsignedByte();
			stream.skip(count * Integer.BYTES);
		}
		else if (opcode >= 74 && opcode <= 79)
		{
			stream.readUnsignedShort();
		}
		else if (opcode == 93 || opcode == 99 || opcode == 107 || opcode == 111 || opcode == 122
			|| opcode == 123 || opcode == 129 || opcode == 130 || opcode == 141 || opcode == 143
			|| opcode == 145 || opcode == 147 || opcode == 158 || opcode == 159 || opcode == 161
			|| opcode == 162)
		{
			if (opcode == 107)
			{
				definition.isInteractable = false;
			}
			else if (opcode == 109)
			{
				definition.isClickable = false;
			}
		}
		else if (opcode == 95 || opcode == 103 || opcode == 124 || opcode == 126 || opcode == 127
			|| opcode == 137 || opcode == 142 || opcode == 144 || opcode == 146
			|| opcode >= 170 && opcode < 176)
		{
			if (opcode == 95)
			{
				definition.combatLevel = stream.readUnsignedShort();
			}
			else if (opcode == 103)
			{
				definition.rotationSpeed = stream.readUnsignedShort();
			}
			else if (opcode == 126)
			{
				definition.footprintSize = stream.readUnsignedShort();
			}
			else
			{
				stream.readUnsignedShort();
			}
		}
		else if (opcode == 97)
		{
			definition.widthScale = stream.readUnsignedShort();
		}
		else if (opcode == 98)
		{
			definition.heightScale = stream.readUnsignedShort();
		}
		else if (opcode == 100)
		{
			definition.ambient = stream.readByte();
		}
		else if (opcode == 101)
		{
			definition.contrast = stream.readByte();
		}
		else if (opcode == 102)
		{
			skipHeadIcons(stream);
		}
		else if (opcode == 106 || opcode == 118)
		{
			decodeTransforms(definition, opcode, stream);
		}
		else if (opcode == 109)
		{
			definition.isClickable = false;
		}
		else if (opcode == 114 || opcode == 116)
		{
			int sequence = stream.readUnsignedShort();
			if (opcode == 114)
			{
				definition.runAnimation = sequence;
			}
			else
			{
				definition.crawlAnimation = sequence;
			}
		}
		else if (opcode == 115 || opcode == 117)
		{
			int first = stream.readUnsignedShort();
			int back = stream.readUnsignedShort();
			int left = stream.readUnsignedShort();
			int right = stream.readUnsignedShort();
			if (opcode == 115)
			{
				definition.runAnimation = first;
				definition.runRotate180Animation = back;
				definition.runRotateLeftAnimation = left;
				definition.runRotateRightAnimation = right;
			}
			else
			{
				definition.crawlAnimation = first;
				definition.crawlRotate180Animation = back;
				definition.crawlRotateLeftAnimation = left;
				definition.crawlRotateRightAnimation = right;
			}
		}
		else if (opcode == 119)
		{
			definition.loginScreenProps = stream.readByte();
		}
		else if (opcode == 121)
		{
			int count = stream.readUnsignedByte();
			for (int i = 0; i < count; i++)
			{
				stream.readUnsignedByte();
				stream.skip(3);
			}
		}
		else if (opcode == 125)
		{
			definition.spawnDirection = stream.readByte();
		}
		else if (opcode == 128 || opcode == 140 || opcode == 149 || opcode == 151 || opcode == 163
			|| opcode == 165 || opcode == 168)
		{
			stream.readUnsignedByte();
		}
		else if (opcode == 134)
		{
			stream.skip(Short.BYTES * 4 + 1);
		}
		else if (opcode == 135 || opcode == 136)
		{
			stream.readUnsignedByte();
			stream.readUnsignedShort();
		}
		else if (opcode == 138 || opcode == 139)
		{
			stream.readBigSmart2();
		}
		else if (opcode == 148)
		{
			stream.readUnsignedShort();
			stream.readUnsignedByte();
			stream.readUnsignedByte();
		}
		else if (opcode == 150)
		{
			stream.readUnsignedByte();
			stream.readUnsignedShort();
			stream.readUnsignedByte();
			stream.readUnsignedShort();
		}
		else if (opcode == 152)
		{
			stream.readUnsignedShort();
			stream.readUnsignedShort();
			stream.readUnsignedByte();
			stream.readUnsignedByte();
			skipUnsignedShortArray(stream);
		}
		else if (opcode == 153 || opcode == 154)
		{
			stream.readString();
		}
		else if (opcode == 155)
		{
			stream.skip(4);
		}
		else if (opcode == 160)
		{
			skipUnsignedShortArray(stream);
		}
		else if (opcode == 164)
		{
			stream.readUnsignedShort();
			stream.readUnsignedShort();
		}
		else if (opcode == 249)
		{
			stream.readParams();
		}
		else if (opcode == 251)
		{
			stream.readUnsignedByte();
			stream.readUnsignedByte();
			stream.readString();
		}
		else if (opcode == 252)
		{
			stream.readUnsignedByte();
			stream.readUnsignedShort();
			stream.readUnsignedShort();
			stream.readInt();
			stream.readInt();
			stream.readStringOrNull();
		}
		else if (opcode == 253)
		{
			stream.readUnsignedByte();
			stream.readUnsignedShort();
			stream.readUnsignedShort();
			stream.readUnsignedShort();
			stream.readInt();
			stream.readInt();
			stream.readStringOrNull();
		}
		else
		{
			throw new IllegalArgumentException("Unsupported NPC opcode " + opcode + " for " + definition.id);
		}
	}

	private void skipHeadIcons(InputStream stream)
	{
		if (!rev210HeadIcons)
		{
			stream.readUnsignedShort();
			return;
		}

		int flags = stream.readUnsignedByte();
		int count = 0;
		for (int bits = flags; bits != 0; bits >>= 1)
		{
			count++;
		}
		for (int i = 0; i < count; i++)
		{
			if ((flags & 1 << i) != 0)
			{
				stream.readBigSmart2();
				stream.readUnsignedShortSmartMinusOne();
			}
		}
	}

	private static void decodeTransforms(NpcDefinition3D definition, int opcode, InputStream stream)
	{
		definition.varbitId = normalizedUnsignedShort(stream.readUnsignedShort());
		definition.varpIndex = normalizedUnsignedShort(stream.readUnsignedShort());
		int fallback = -1;
		if (opcode == 118)
		{
			fallback = normalizedUnsignedShort(stream.readUnsignedShort());
		}

		int count = stream.readUnsignedByte();
		definition.configs = new int[count + 2];
		for (int i = 0; i <= count; i++)
		{
			definition.configs[i] = normalizedUnsignedShort(stream.readUnsignedShort());
		}
		definition.configs[count + 1] = fallback;
	}

	private static void skipUnsignedShortArray(InputStream stream)
	{
		int count = stream.readUnsignedByte();
		stream.skip(count * Short.BYTES);
	}

	private static int normalizedUnsignedShort(int value)
	{
		return value == 0xFFFF ? -1 : value;
	}
}
