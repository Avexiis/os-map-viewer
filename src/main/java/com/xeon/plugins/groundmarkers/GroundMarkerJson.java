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
package com.xeon.plugins.groundmarkers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GroundMarkerJson
{
	private static final Gson GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.create();
	private static final Gson PRETTY_GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();

	private GroundMarkerJson()
	{
	}

	public static List<GroundMarker> readFile(Path path) throws IOException
	{
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			return parse(GSON.fromJson(reader, JsonElement.class));
		}
	}

	public static void writeFile(Path path, List<GroundMarker> markers) throws IOException
	{
		Path parent = path.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
		{
			PRETTY_GSON.toJson(toJsonArray(markers), writer);
		}
	}

	public static List<GroundMarker> parse(String json)
	{
		return parse(GSON.fromJson(json, JsonElement.class));
	}

	public static List<GroundMarker> parse(JsonElement root)
	{
		List<GroundMarker> out = new ArrayList<>();
		if (root == null || !root.isJsonArray())
		{
			return out;
		}
		JsonArray array = root.getAsJsonArray();
		for (JsonElement element : array)
		{
			if (!element.isJsonObject())
			{
				continue;
			}
			GroundMarker marker = fromObject(element.getAsJsonObject());
			if (marker != null)
			{
				out.add(marker);
			}
		}
		return out;
	}

	public static String toCompactJson(List<GroundMarker> markers)
	{
		return GSON.toJson(toJsonArray(markers));
	}

	public static JsonArray toJsonArray(List<GroundMarker> markers)
	{
		JsonArray array = new JsonArray();
		if (markers == null)
		{
			return array;
		}
		for (GroundMarker marker : markers)
		{
			if (marker == null)
			{
				continue;
			}
			JsonObject object = new JsonObject();
			object.addProperty("regionX", marker.regionX);
			object.addProperty("z", marker.z);
			object.addProperty("regionId", marker.regionId);
			if (marker.label != null && !marker.label.isBlank())
			{
				object.addProperty("label", marker.label);
			}
			object.addProperty("regionY", marker.regionY);
			object.addProperty("color", GroundMarker.normalizeColor(marker.color));
			array.add(object);
		}
		return array;
	}

	private static GroundMarker fromObject(JsonObject object)
	{
		if (!hasNumber(object, "regionX") || !hasNumber(object, "regionY"))
		{
			return null;
		}

		int regionX = object.get("regionX").getAsInt();
		int regionY = object.get("regionY").getAsInt();
		int z = hasNumber(object, "z") ? object.get("z").getAsInt() : 0;
		int regionId = hasNumber(object, "regionId") ? object.get("regionId").getAsInt() : 0;
		String label = hasString(object, "label") ? object.get("label").getAsString() : null;
		String color = readColor(object.get("color"));
		return new GroundMarker(regionX, z, regionId, label, regionY, color);
	}

	private static String readColor(JsonElement element)
	{
		if (element == null || element.isJsonNull())
		{
			return GroundMarker.DEFAULT_COLOR;
		}
		if (element.isJsonPrimitive())
		{
			return GroundMarker.normalizeColor(element.getAsString());
		}
		if (!element.isJsonObject())
		{
			return GroundMarker.DEFAULT_COLOR;
		}

		JsonObject object = element.getAsJsonObject();
		if (hasNumber(object, "value"))
		{
			return GroundMarker.normalizeColor(String.format("#%08X", object.get("value").getAsInt()));
		}
		if (hasNumber(object, "rgb"))
		{
			return GroundMarker.normalizeColor(String.format("#%08X", object.get("rgb").getAsInt()));
		}
		return GroundMarker.DEFAULT_COLOR;
	}

	private static boolean hasNumber(JsonObject object, String name)
	{
		JsonElement element = object.get(name);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
	}

	private static boolean hasString(JsonObject object, String name)
	{
		JsonElement element = object.get(name);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
	}
}
