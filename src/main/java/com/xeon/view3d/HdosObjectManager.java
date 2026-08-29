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
package com.xeon.view3d;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.io.InputStream;

final class HdosObjectManager extends ObjectManager
{
	static final int OBJECT_INDEX = 16;
	private static final int FILE_ID_BITS = 8;
	private static final int FILE_ID_MASK = (1 << FILE_ID_BITS) - 1;

	private final Store store;
	private final Map<Integer, ObjectDefinition> objects = new HashMap<>();

	HdosObjectManager(Store store)
	{
		super(store);
		this.store = store;
	}

	@Override
	public void load() throws IOException
	{
		Index index = store.findIndex(OBJECT_INDEX);
		if (index == null)
		{
			throw new IOException("HDOS loc/object index " + OBJECT_INDEX + " is missing.");
		}
		if (!(store.getStorage() instanceof DispleeCacheStorage storage))
		{
			throw new IOException("HDOS object decoder requires Displee cache storage.");
		}

		HdosObjectLoader loader = new HdosObjectLoader();
		int failures = 0;
		String firstFailure = null;
		for (Archive archive : index.getArchives())
		{
			for (DispleeCacheStorage.DecodedFile file : storage.loadArchiveFiles(OBJECT_INDEX, archive.getArchiveId()))
			{
				if (file.data() == null || file.data().length == 0 || file.id() < 0 || file.id() > FILE_ID_MASK)
				{
					continue;
				}

				int objectId = archive.getArchiveId() << FILE_ID_BITS | file.id();
				try
				{
					objects.put(objectId, loader.load(objectId, file.data()));
				}
				catch (RuntimeException ex)
				{
					failures++;
					if (firstFailure == null)
					{
						firstFailure = "object " + objectId + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
					}
				}
			}
		}

		if (objects.isEmpty() && failures > 0)
		{
			throw new IOException("Failed to decode HDOS loc/object definitions from index " + OBJECT_INDEX
				+ "; first failure was " + firstFailure);
		}
		if (failures > 0)
		{
			System.err.println("Skipped " + failures + " HDOS loc/object definitions; first failure was " + firstFailure);
		}
	}

	@Override
	public Collection<ObjectDefinition> getObjects()
	{
		return Collections.unmodifiableCollection(objects.values());
	}

	@Override
	public ObjectDefinition getObject(int id)
	{
		return objects.get(id);
	}

	private static final class HdosObjectLoader
	{
		private ObjectDefinition load(int id, byte[] bytes)
		{
			ObjectDefinition definition = new ObjectDefinition();
			definition.setId(id);
			definition.setRandomizeAnimStart(true);

			InputStream input = new InputStream(bytes);
			while (input.remaining() > 0)
			{
				int opcode = input.readUnsignedByte();
				if (opcode == 0)
				{
					post(definition);
					return definition;
				}
				decodeOpcode(definition, opcode, input);
			}
			post(definition);
			return definition;
		}

		private void decodeOpcode(ObjectDefinition definition, int opcode, InputStream input)
		{
			switch (opcode)
			{
				case 1 -> decodeModels(definition, input, true, false);
				case 2 -> definition.setName(input.readString());
				case 3 -> input.readString();
				case 5 -> decodeModels(definition, input, false, false);
				case 6 -> decodeModels(definition, input, true, true);
				case 7 -> decodeModels(definition, input, false, true);
				case 14 -> definition.setSizeX(input.readUnsignedByte());
				case 15 -> definition.setSizeY(input.readUnsignedByte());
				case 17 ->
				{
					definition.setInteractType(0);
					definition.setBlocksProjectile(false);
				}
				case 18 -> definition.setBlocksProjectile(false);
				case 19 -> definition.setWallOrDoor(input.readUnsignedByte());
				case 21 -> definition.setContouredGround(0);
				case 22 -> definition.setMergeNormals(true);
				case 23 -> definition.setModelClipped(true);
				case 24 -> definition.setAnimationID(nullableUnsignedShort(input));
				case 25 ->
				{
				}
				case 27 -> definition.setInteractType(1);
				case 28 -> definition.setDecorDisplacement(input.readUnsignedByte());
				case 29 -> definition.setAmbient(input.readByte());
				case 39 -> definition.setContrast(input.readByte() * 25);
				case 40 -> decodeRecolors(definition, input);
				case 41 -> decodeRetextures(definition, input);
				case 44, 45 -> input.readUnsignedShort();
				case 60 -> definition.setMapAreaId(input.readUnsignedShort());
				case 61 -> definition.setCategory(input.readUnsignedShort());
				case 62 -> definition.setRotated(true);
				case 64 -> definition.setShadow(false);
				case 65 -> definition.setModelSizeX(input.readUnsignedShort());
				case 66 -> definition.setModelSizeHeight(input.readUnsignedShort());
				case 67 -> definition.setModelSizeY(input.readUnsignedShort());
				case 68 -> definition.setMapSceneID(input.readUnsignedShort());
				case 69 -> definition.setBlockingMask(input.readByte());
				case 70 -> definition.setOffsetX(input.readShort());
				case 71 -> definition.setOffsetHeight(input.readShort());
				case 72 -> definition.setOffsetY(input.readShort());
				case 73 -> definition.setObstructsGround(true);
				case 74 -> definition.setHollow(true);
				case 75 -> definition.setSupportsItems(input.readUnsignedByte());
				case 77 -> decodeTransforms(definition, input, false);
				case 78 ->
				{
					definition.setAmbientSoundId(input.readUnsignedShort());
					definition.setAmbientSoundDistance(input.readUnsignedByte());
				}
				case 79 -> decodeAmbientSounds(definition, input);
				case 81 -> definition.setContouredGround(input.readUnsignedByte() * 256);
				case 82, 88, 90, 91, 94, 96, 97, 98, 103, 105, 168, 169, 177, 189, 190, 191 ->
				{
				}
				case 89 -> definition.setRandomizeAnimStart(false);
				case 92 -> decodeTransforms(definition, input, true);
				case 93 ->
				{
					input.readShort();
					definition.setContouredGround(0);
				}
				case 95 -> definition.setContouredGround(0);
				case 99, 100 ->
				{
					input.readUnsignedByte();
					input.readUnsignedShort();
				}
				case 101 -> input.readUnsignedByte();
				case 102 -> definition.setMapSceneID(input.readUnsignedShort());
				case 104 -> input.readUnsignedByte();
				case 106 -> decodeRandomSequences(definition, input);
				case 107 -> definition.setMapAreaId(input.readUnsignedShort());
				case 160 -> skipUnsignedShortList(input);
				case 163 ->
				{
					input.readByte();
					input.readByte();
					input.readByte();
					input.readByte();
				}
				case 167 -> input.readUnsignedShort();
				case 170, 171 -> input.readUnsignedShortSmart();
				case 173 ->
				{
					input.readUnsignedShort();
					input.readUnsignedShort();
				}
				case 178 -> input.readUnsignedByte();
				case 249 -> definition.setParams(input.readParams());
				case 255 -> input.skip(input.remaining());
				default ->
				{
					if (opcode >= 30 && opcode < 39 || opcode >= 150 && opcode < 155)
					{
						decodeAction(definition.getOps(), input, opcode >= 150 ? opcode - 150 : opcode - 30);
						return;
					}
					throw new IllegalArgumentException("Unsupported HDOS loc/object opcode " + opcode
						+ " at offset " + input.getOffset() + " of " + input.getLength());
				}
			}
		}

		private static void decodeModels(
			ObjectDefinition definition,
			InputStream input,
			boolean withTypes,
			boolean largeModelIds
		)
		{
			int length = input.readUnsignedByte();
			if (length <= 0)
			{
				return;
			}

			int[] models = new int[length];
			int[] types = withTypes ? new int[length] : null;
			for (int i = 0; i < length; i++)
			{
				models[i] = largeModelIds ? input.readInt() : input.readUnsignedShort();
				if (withTypes)
				{
					types[i] = input.readUnsignedByte();
				}
			}
			definition.setObjectModels(models);
			definition.setObjectTypes(types);
		}

		private static void decodeRecolors(ObjectDefinition definition, InputStream input)
		{
			int length = input.readUnsignedByte();
			short[] find = new short[length];
			short[] replace = new short[length];
			for (int i = 0; i < length; i++)
			{
				find[i] = input.readShort();
				replace[i] = input.readShort();
			}
			definition.setRecolorToFind(find);
			definition.setRecolorToReplace(replace);
		}

		private static void decodeRetextures(ObjectDefinition definition, InputStream input)
		{
			int length = input.readUnsignedByte();
			short[] find = new short[length];
			short[] replace = new short[length];
			for (int i = 0; i < length; i++)
			{
				find[i] = input.readShort();
				replace[i] = input.readShort();
			}
			definition.setRetextureToFind(find);
			definition.setTextureToReplace(replace);
		}

		private static void decodeTransforms(ObjectDefinition definition, InputStream input, boolean hasDefault)
		{
			definition.setVarbitID(nullableUnsignedShort(input));
			definition.setVarpID(nullableUnsignedShort(input));
			int defaultTransform = -1;
			if (hasDefault)
			{
				defaultTransform = nullableUnsignedShort(input);
			}

			int length = input.readUnsignedByte();
			int[] transforms = new int[length + 2];
			for (int i = 0; i <= length; i++)
			{
				transforms[i] = nullableUnsignedShort(input);
			}
			transforms[length + 1] = defaultTransform;
			definition.setConfigChangeDest(transforms);
		}

		private static void decodeAmbientSounds(ObjectDefinition definition, InputStream input)
		{
			definition.setAmbientSoundChangeTicksMin(input.readUnsignedShort());
			definition.setAmbientSoundChangeTicksMax(input.readUnsignedShort());
			definition.setAmbientSoundDistance(input.readUnsignedByte());
			int length = input.readUnsignedByte();
			int[] ids = new int[length];
			for (int i = 0; i < length; i++)
			{
				ids[i] = input.readUnsignedShort();
			}
			definition.setAmbientSoundIds(ids);
		}

		private static void decodeRandomSequences(ObjectDefinition definition, InputStream input)
		{
			int length = input.readUnsignedByte();
			int firstSequence = -1;
			for (int i = 0; i < length; i++)
			{
				int sequenceId = nullableUnsignedShort(input);
				input.readUnsignedByte();
				if (firstSequence < 0 && sequenceId >= 0)
				{
					firstSequence = sequenceId;
				}
			}
			if (definition.getAnimationID() < 0)
			{
				definition.setAnimationID(firstSequence);
			}
		}

		private static void skipUnsignedShortList(InputStream input)
		{
			int length = input.readUnsignedByte();
			for (int i = 0; i < length; i++)
			{
				input.readUnsignedShort();
			}
		}

		private static void decodeAction(EntityOpsDefinition ops, InputStream input, int index)
		{
			String text = input.readString();
			if (!"Hidden".equalsIgnoreCase(text))
			{
				ops.setOp(index, text);
			}
		}

		private static int nullableUnsignedShort(InputStream input)
		{
			int value = input.readUnsignedShort();
			return value == 0xFFFF ? -1 : value;
		}

		private static void post(ObjectDefinition definition)
		{
			if (definition.getWallOrDoor() == -1)
			{
				definition.setWallOrDoor(0);
				if (definition.getObjectModels() != null
					&& (definition.getObjectTypes() == null || definition.getObjectTypes()[0] == 10))
				{
					definition.setWallOrDoor(1);
				}
				if (definition.getOps().ops.stream().anyMatch(op -> op != null && op.text != null))
				{
					definition.setWallOrDoor(1);
				}
			}

			if (definition.getSupportsItems() == -1)
			{
				definition.setSupportsItems(definition.getInteractType() != 0 ? 1 : 0);
			}
			if (definition.isHollow())
			{
				definition.setInteractType(0);
				definition.setBlocksProjectile(false);
			}
		}
	}
}
