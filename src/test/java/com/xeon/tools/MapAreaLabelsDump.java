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
package com.xeon.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MapAreaLabelsDump
{
	private static final Path ATLAS_PATH = Path.of("src/main/resources/com/xeon/FullWorldMap.atlas");
	private static final Path OUTPUT_DIR = Path.of("docs/map-area-labels");
	private static final String LOCATION_LABELS = "locationLabels";
	private static final Gson GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();

	private MapAreaLabelsDump()
	{
	}

	public static void main(String[] args) throws Exception
	{
		Mode mode = parseMode(args);
		if (mode == Mode.HELP)
		{
			printUsage();
			return;
		}

		Path root = findProjectRoot();
		Path atlas = root.resolve(ATLAS_PATH);
		Path outputDir = root.resolve(OUTPUT_DIR);
		JsonArray labels = readLocationLabels(atlas);

		Files.createDirectories(outputDir);
		Path output = outputDir.resolve(mode == Mode.JSON ? "labels.json" : "labels.txt");
		if (mode == Mode.JSON)
		{
			Files.writeString(output, GSON.toJson(labels) + System.lineSeparator(), StandardCharsets.UTF_8);
		}
		else
		{
			writeTextLabels(labels, output);
		}

		System.out.printf("Wrote %,d location labels to %s%n",
			labels.size(),
			root.relativize(output).toString());
	}

	private static Mode parseMode(String[] args)
	{
		Mode mode = Mode.TEXT;
		for (String arg : args)
		{
			switch (arg)
			{
				case "--json" -> mode = Mode.JSON;
				case "-h", "--help" -> mode = Mode.HELP;
				default -> throw new IllegalArgumentException("Unknown argument: " + arg);
			}
		}
		return mode;
	}

	private static void printUsage()
	{
		System.out.println("Usage: ./gradlew dumpMapAreaLabels [--args=\"--json\"]");
	}

	private static Path findProjectRoot()
	{
		Path current = Path.of("").toAbsolutePath().normalize();
		for (Path candidate = current; candidate != null; candidate = candidate.getParent())
		{
			if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))
				&& Files.isRegularFile(candidate.resolve(ATLAS_PATH)))
			{
				return candidate;
			}
		}
		return current;
	}

	private static JsonArray readLocationLabels(Path atlas) throws IOException
	{
		if (!Files.isRegularFile(atlas))
		{
			throw new IOException("Atlas file not found: " + atlas);
		}

		byte[] metadataBytes = readAtlasMetadata(atlas);
		JsonObject metadata;
		try
		{
			JsonElement parsed = new JsonParser().parse(new String(metadataBytes, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject())
			{
				throw new IllegalStateException("Atlas metadata is not a JSON object");
			}
			metadata = parsed.getAsJsonObject();
		}
		catch (JsonSyntaxException ex)
		{
			throw new IllegalStateException("Atlas metadata is not valid JSON", ex);
		}

		JsonElement labels = metadata.get(LOCATION_LABELS);
		if (labels == null || !labels.isJsonArray())
		{
			throw new IllegalStateException("Atlas metadata is missing a " + LOCATION_LABELS + " array");
		}
		return labels.getAsJsonArray();
	}

	private static byte[] readAtlasMetadata(Path atlas) throws IOException
	{
		try (RandomAccessFile raf = new RandomAccessFile(atlas.toFile(), "r"))
		{
			Header header = readHeader(raf);
			if (header.metadataOffset <= 0 || header.metadataLength <= 0)
			{
				throw new IllegalStateException("Atlas is missing v3 metadata");
			}

			byte[] bytes = new byte[header.metadataLength];
			raf.seek(header.metadataOffset);
			raf.readFully(bytes);
			return bytes;
		}
	}

	private static Header readHeader(RandomAccessFile raf) throws IOException
	{
		raf.seek(0);
		byte[] magic8 = new byte[8];
		raf.readFully(magic8);

		byte[] expected7 = new byte[]{'A', 'T', 'L', 'S', 'v', '3', 0x00};
		for (int i = 0; i < expected7.length; i++)
		{
			if (magic8[i] != expected7[i])
			{
				throw new IllegalStateException("Atlas has an invalid ATLSv3 header");
			}
		}
		if (magic8[7] != 0x00)
		{
			raf.seek(7);
		}

		int version = readU32(raf);
		if (version != 3)
		{
			throw new IllegalStateException("Unsupported atlas version " + version + "; expected 3");
		}

		readU32(raf);
		readU32(raf);
		readU32(raf);
		int numLods = readU32(raf);
		if (numLods < 0 || numLods > 64)
		{
			throw new IllegalStateException("Atlas has an invalid LOD count: " + numLods);
		}
		for (int i = 0; i < numLods; i++)
		{
			readU32(raf);
		}

		readU32(raf);
		readU32(raf);
		readU32(raf);
		readU64(raf);
		readU64(raf);
		long metadataOffset = readU64(raf);
		int metadataLength = readU32(raf);
		return new Header(metadataOffset, metadataLength);
	}

	private static int readU32(RandomAccessFile raf) throws IOException
	{
		byte[] bytes = new byte[4];
		raf.readFully(bytes);
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	private static long readU64(RandomAccessFile raf) throws IOException
	{
		byte[] bytes = new byte[8];
		raf.readFully(bytes);
		long value = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
		if (value < 0)
		{
			throw new EOFException("Atlas offset is larger than supported by this tool");
		}
		return value;
	}

	private static void writeTextLabels(JsonArray labels, Path output) throws IOException
	{
		List<String> names = new ArrayList<>();
		for (JsonElement label : labels)
		{
			String name = displayName(label);
			if (name != null)
			{
				names.add(name);
			}
		}

		String content = String.join(System.lineSeparator(), names);
		if (!content.isEmpty())
		{
			content += System.lineSeparator();
		}
		Files.writeString(output, content, StandardCharsets.UTF_8);
	}

	private static String displayName(JsonElement label)
	{
		if (label == null || !label.isJsonObject())
		{
			return null;
		}

		JsonObject object = label.getAsJsonObject();
		return firstNonBlank(
			cleanString(readString(object, "name")),
			cleanString(readString(object, "rawName")),
			cleanString(readString(object, "objectName"))
		);
	}

	private static String readString(JsonObject object, String key)
	{
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive())
		{
			return null;
		}
		return element.getAsString();
	}

	private static String cleanString(String value)
	{
		if (value == null)
		{
			return null;
		}
		String cleaned = value.replace("<br>", " ")
			.replace("<br/>", " ")
			.replace("<br />", " ")
			.trim();
		return cleaned.isEmpty() ? null : cleaned;
	}

	private static String firstNonBlank(String... values)
	{
		for (String value : values)
		{
			if (value != null && !value.isBlank())
			{
				return value;
			}
		}
		return null;
	}

	private enum Mode
	{
		TEXT,
		JSON,
		HELP
	}

	private record Header(long metadataOffset, int metadataLength)
	{
	}
}
