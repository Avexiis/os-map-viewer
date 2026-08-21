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
package com.xeon.io;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xeon.config.ConfigManager;

import java.awt.Color;

public final class ViewerSettings
{
	public static final int MEMORY_BUDGET_UNLIMITED_MB = -1;

	private static final String CORE = ConfigManager.CORE_NAMESPACE;
	private static final String KEY_PLUGINS_ENABLED = "plugins.enabledOnStartup";
	private static final String KEY_ACTIVE_PLUGIN_ID = "plugins.activePluginId";
	private static final String KEY_BACKGROUND = "map.background";
	private static final String KEY_MAP_FEATURE_TOOLTIPS = "map.featureTooltips";
	private static final String KEY_JUMP_TO_LAST_REGION = "map.jumpToLastRegionOnStart";
	private static final String KEY_LAST_REGION_ID = "map.last.regionId";
	private static final String KEY_LAST_PLANE = "map.last.plane";
	private static final String KEY_LAST_ZOOM = "map.last.zoom";
	private static final String KEY_MEMORY_BUDGET_MB = "map.memoryBudgetMb";
	private static final String KEY_3D_STATE = "viewer3d.state";
	private static final String KEY_3D_STATE_VALID = "viewer3d.state.valid";
	private static final String KEY_3D_WORLD_TILE_X = "viewer3d.state.worldTileX";
	private static final String KEY_3D_WORLD_TILE_Y = "viewer3d.state.worldTileY";
	private static final String KEY_3D_CAMERA_Y = "viewer3d.state.cameraY";
	private static final String KEY_3D_PLANE = "viewer3d.state.plane";
	private static final String KEY_3D_YAW = "viewer3d.state.yawDegrees";
	private static final String KEY_3D_PITCH = "viewer3d.state.pitchDegrees";
	private static final String KEY_3D_FOV = "viewer3d.state.fovDegrees";
	private static final String KEY_3D_AA_SAMPLES = "viewer3d.state.antialiasingSamples";
	private static final String KEY_3D_VIEW_DISTANCE_REGIONS = "viewer3d.state.viewDistanceRegions";
	private static final String KEY_3D_PLUGIN_OVERLAYS_ON_TOP = "viewer3d.state.pluginOverlaysOnTop";
	private static final String KEY_3D_CACHE_ASK_ON_OPEN = "viewer3d.cache.askOnOpen";
	private static final String KEY_3D_CACHE_AUTO_DETECT = "viewer3d.cache.autoDetect";
	private static final String FIELD_WORLD_TILE_X = "worldTileX";
	private static final String FIELD_WORLD_TILE_Y = "worldTileY";
	private static final String FIELD_CAMERA_Y = "cameraY";
	private static final String FIELD_PLANE = "plane";
	private static final String FIELD_YAW = "yawDegrees";
	private static final String FIELD_PITCH = "pitchDegrees";
	private static final String FIELD_FOV = "fovDegrees";
	private static final String FIELD_AA_SAMPLES = "antialiasingSamples";
	private static final String FIELD_VIEW_DISTANCE_REGIONS = "viewDistanceRegions";
	private static final String FIELD_PLUGIN_OVERLAYS_ON_TOP = "pluginOverlaysOnTop";
	private static final String DEFAULT_BACKGROUND = "#000000";
	private static final int DEFAULT_MEMORY_BUDGET_MB = 512;

	private final ConfigManager configManager;

	private ViewerSettings(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public static ViewerSettings load(ConfigManager configManager)
	{
		if (configManager == null)
		{
			throw new IllegalArgumentException("configManager must not be null");
		}
		return new ViewerSettings(configManager);
	}

	public boolean pluginsEnabledOnStartup()
	{
		return configManager.getBoolean(CORE, KEY_PLUGINS_ENABLED, false);
	}

	public void setPluginsEnabledOnStartup(boolean value)
	{
		configManager.setBoolean(CORE, KEY_PLUGINS_ENABLED, value);
	}

	public String activePluginId()
	{
		String value = configManager.getString(CORE, KEY_ACTIVE_PLUGIN_ID, null);
		return value == null || value.isBlank() ? null : value.trim();
	}

	public void setActivePluginId(String pluginId)
	{
		if (pluginId == null || pluginId.isBlank())
		{
			configManager.remove(CORE, KEY_ACTIVE_PLUGIN_ID);
			return;
		}
		configManager.setString(CORE, KEY_ACTIVE_PLUGIN_ID, pluginId.trim());
	}

	public Color mapBackgroundColor()
	{
		return parseColor(configManager.getString(CORE, KEY_BACKGROUND, DEFAULT_BACKGROUND), Color.BLACK);
	}

	public void setMapBackgroundColor(Color color)
	{
		if (color == null)
		{
			return;
		}
		configManager.setString(CORE, KEY_BACKGROUND, colorToHex(color));
	}

	public boolean mapFeatureTooltips()
	{
		return configManager.getBoolean(CORE, KEY_MAP_FEATURE_TOOLTIPS, true);
	}

	public void setMapFeatureTooltips(boolean value)
	{
		configManager.setBoolean(CORE, KEY_MAP_FEATURE_TOOLTIPS, value);
	}

	public boolean jumpToLastRegionOnStart()
	{
		return configManager.getBoolean(CORE, KEY_JUMP_TO_LAST_REGION, false);
	}

	public void setJumpToLastRegionOnStart(boolean value)
	{
		configManager.setBoolean(CORE, KEY_JUMP_TO_LAST_REGION, value);
	}

	public int lastRegionId()
	{
		return configManager.getInt(CORE, KEY_LAST_REGION_ID, -1);
	}

	public int lastPlane()
	{
		return configManager.getInt(CORE, KEY_LAST_PLANE, 0);
	}

	public double lastZoom()
	{
		return configManager.getDouble(CORE, KEY_LAST_ZOOM, 1.0);
	}

	public void setLastView(int regionId, int plane, double zoom)
	{
		if (regionId < 0)
		{
			return;
		}
		configManager.setInt(CORE, KEY_LAST_REGION_ID, regionId);
		configManager.setInt(CORE, KEY_LAST_PLANE, Math.max(0, plane));
		configManager.setDouble(CORE, KEY_LAST_ZOOM, Math.max(1.0, zoom));
	}

	public int memoryBudgetMb()
	{
		int value = configManager.getInt(CORE, KEY_MEMORY_BUDGET_MB, DEFAULT_MEMORY_BUDGET_MB);
		if (value == MEMORY_BUDGET_UNLIMITED_MB)
		{
			return MEMORY_BUDGET_UNLIMITED_MB;
		}
		return Math.max(DEFAULT_MEMORY_BUDGET_MB, value);
	}

	public void setMemoryBudgetMb(int value)
	{
		configManager.setInt(CORE, KEY_MEMORY_BUDGET_MB,
			value == MEMORY_BUDGET_UNLIMITED_MB ? MEMORY_BUDGET_UNLIMITED_MB : Math.max(DEFAULT_MEMORY_BUDGET_MB, value));
	}

	public Viewer3DState viewer3DState()
	{
		Viewer3DState objectState = viewer3DStateObject();
		if (objectState != null)
		{
			return objectState;
		}

		if (!configManager.getBoolean(CORE, KEY_3D_STATE_VALID, false))
		{
			return null;
		}

		Viewer3DState state = new Viewer3DState(
			configManager.getDouble(CORE, KEY_3D_WORLD_TILE_X, Double.NaN),
			configManager.getDouble(CORE, KEY_3D_WORLD_TILE_Y, Double.NaN),
			configManager.getDouble(CORE, KEY_3D_CAMERA_Y, Double.NaN),
			configManager.getInt(CORE, KEY_3D_PLANE, 0),
			(float) configManager.getDouble(CORE, KEY_3D_YAW, 0.0),
			(float) configManager.getDouble(CORE, KEY_3D_PITCH, -12.0),
			(float) configManager.getDouble(CORE, KEY_3D_FOV, 68.0),
			configManager.getInt(CORE, KEY_3D_AA_SAMPLES, 4),
			configManager.getInt(CORE, KEY_3D_VIEW_DISTANCE_REGIONS, Viewer3DState.DEFAULT_VIEW_DISTANCE_REGIONS),
			configManager.getBoolean(CORE, KEY_3D_PLUGIN_OVERLAYS_ON_TOP, false)
		);
		return state.isValid() ? state : null;
	}

	public void setViewer3DState(Viewer3DState state)
	{
		if (state == null || !state.isValid())
		{
			configManager.remove(CORE, KEY_3D_STATE);
			configManager.setBoolean(CORE, KEY_3D_STATE_VALID, false);
			return;
		}

		if (configManager.getBoolean(CORE, KEY_3D_STATE_VALID, false))
		{
			configManager.setBoolean(CORE, KEY_3D_STATE_VALID, false);
		}
		JsonObject object = new JsonObject();
		object.addProperty(FIELD_WORLD_TILE_X, state.worldTileX());
		object.addProperty(FIELD_WORLD_TILE_Y, state.worldTileY());
		object.addProperty(FIELD_CAMERA_Y, state.cameraY());
		object.addProperty(FIELD_PLANE, Math.max(0, Math.min(3, state.plane())));
		object.addProperty(FIELD_YAW, state.yawDegrees());
		object.addProperty(FIELD_PITCH, state.pitchDegrees());
		object.addProperty(FIELD_FOV, state.fovDegrees());
		object.addProperty(FIELD_AA_SAMPLES, Math.max(0, state.antialiasingSamples()));
		object.addProperty(FIELD_VIEW_DISTANCE_REGIONS,
			Viewer3DState.clampViewDistanceRegions(state.viewDistanceRegions()));
		object.addProperty(FIELD_PLUGIN_OVERLAYS_ON_TOP, state.pluginOverlaysOnTop());
		configManager.setElement(CORE, KEY_3D_STATE, object);
	}

	public boolean viewer3DCacheAskOnOpen()
	{
		return configManager.getBoolean(CORE, KEY_3D_CACHE_ASK_ON_OPEN, true);
	}

	public boolean viewer3DCacheAutoDetect()
	{
		return configManager.getBoolean(CORE, KEY_3D_CACHE_AUTO_DETECT, true);
	}

	public void setViewer3DCachePrompt(boolean askOnOpen, boolean autoDetect)
	{
		configManager.setBoolean(CORE, KEY_3D_CACHE_ASK_ON_OPEN, askOnOpen);
		configManager.setBoolean(CORE, KEY_3D_CACHE_AUTO_DETECT, autoDetect);
	}

	private Viewer3DState viewer3DStateObject()
	{
		JsonElement element = configManager.getElement(CORE, KEY_3D_STATE);
		if (element == null || !element.isJsonObject())
		{
			return null;
		}

		JsonObject object = element.getAsJsonObject();
		Viewer3DState state = new Viewer3DState(
			jsonDouble(object, FIELD_WORLD_TILE_X, Double.NaN),
			jsonDouble(object, FIELD_WORLD_TILE_Y, Double.NaN),
			jsonDouble(object, FIELD_CAMERA_Y, Double.NaN),
			jsonInt(object, FIELD_PLANE, 0),
			(float) jsonDouble(object, FIELD_YAW, 0.0),
			(float) jsonDouble(object, FIELD_PITCH, -12.0),
			(float) jsonDouble(object, FIELD_FOV, 68.0),
			jsonInt(object, FIELD_AA_SAMPLES, 4),
			jsonInt(object, FIELD_VIEW_DISTANCE_REGIONS, Viewer3DState.DEFAULT_VIEW_DISTANCE_REGIONS),
			jsonBoolean(object, FIELD_PLUGIN_OVERLAYS_ON_TOP, false)
		);
		return state.isValid() ? state : null;
	}

	private static double jsonDouble(JsonObject object, String key, double defaultValue)
	{
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive())
		{
			return defaultValue;
		}
		try
		{
			return element.getAsDouble();
		}
		catch (RuntimeException ex)
		{
			return defaultValue;
		}
	}

	private static int jsonInt(JsonObject object, String key, int defaultValue)
	{
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive())
		{
			return defaultValue;
		}
		try
		{
			return element.getAsInt();
		}
		catch (RuntimeException ex)
		{
			return defaultValue;
		}
	}

	private static boolean jsonBoolean(JsonObject object, String key, boolean defaultValue)
	{
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive())
		{
			return defaultValue;
		}
		try
		{
			return element.getAsBoolean();
		}
		catch (RuntimeException ex)
		{
			return defaultValue;
		}
	}

	private static String colorToHex(Color color)
	{
		return String.format("#%06X", color.getRGB() & 0x00FFFFFF);
	}

	private static String normalizeColor(String raw, String fallback)
	{
		if (raw == null || raw.isBlank())
		{
			return fallback;
		}
		String value = raw.trim();
		if (value.startsWith("#"))
		{
			value = value.substring(1);
		}
		if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}"))
		{
			return fallback;
		}
		return "#" + value.toUpperCase();
	}

	private static Color parseColor(String raw, Color fallback)
	{
		String normalized = normalizeColor(raw, null);
		if (normalized == null)
		{
			return fallback;
		}
		return new Color(Integer.parseInt(normalized.substring(1), 16));
	}
}
