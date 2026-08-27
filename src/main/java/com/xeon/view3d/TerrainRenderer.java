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

import com.xeon.io.Viewer3DState;
import com.xeon.model.Tile;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.LockSupport;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

final class TerrainRenderer
{
	private static final String TERRAIN_VERTEX_SHADER = """
		#version 330 core
		layout(location = 0) in vec3 aPosition;
		layout(location = 1) in vec3 aNormal;
		layout(location = 2) in vec3 aColor;
		layout(location = 3) in float aAlpha;
		layout(location = 4) in float aDepthBias;
		layout(location = 5) in vec2 aTexCoord;
		layout(location = 6) in float aTextureLayer;
		layout(location = 7) in float aTextureAnimU;
		layout(location = 8) in float aTextureAnimV;
		layout(location = 9) in float aTextureAlphaCutoff;

		uniform mat4 uMvp;
		uniform mat4 uModelMatrix;
		uniform vec3 uCameraPosition;
		uniform vec3 uRegionOffset;
		uniform float uTimeSeconds;

		out vec3 vColor;
		out float vAlpha;
		out float vDistance;
		out vec2 vTexCoord;
		flat out float vTextureLayer;
		flat out float vTextureAlphaCutoff;

		void main()
		{
			vec3 modelPosition = (uModelMatrix * vec4(aPosition, 1.0)).xyz;
			vec3 position = modelPosition + uRegionOffset;
			vec4 worldPosition = vec4(position, 1.0);
			vColor = aColor;
			vAlpha = aAlpha;
			vDistance = distance(position, uCameraPosition);
			vec2 animation = vec2(aTextureAnimU, aTextureAnimV);
			vTexCoord = aTexCoord + mod(uTimeSeconds * animation / 2.56, 1.0);
			vTextureLayer = aTextureLayer;
			vTextureAlphaCutoff = aTextureAlphaCutoff;
			gl_Position = uMvp * worldPosition;
			gl_Position.z -= aDepthBias / 2048.0;
		}
		""";
	private static final String TERRAIN_FRAGMENT_SHADER = """
		#version 330 core
		in vec3 vColor;
		in float vAlpha;
		in float vDistance;
		in vec2 vTexCoord;
		flat in float vTextureLayer;
		flat in float vTextureAlphaCutoff;
		uniform sampler2DArray uTextures;
		uniform float uTextureLayerCount;
		uniform float uFogStart;
		uniform float uFogEnd;
		uniform vec3 uFogColor;

		out vec4 fragColor;

		vec3 sceneColor(vec3 color)
		{
			float luminance = dot(color, vec3(0.299, 0.587, 0.114));
			color = mix(vec3(luminance), color, 0.88);
			color = mix(vec3(0.5), color, 0.96);
			return clamp(color, 0.0, 1.0);
		}

		void main()
		{
			vec3 baseColor = vColor;
			float alpha = vAlpha;
			if (vTextureLayer > 0.5 && vTextureLayer < uTextureLayerCount)
			{
				vec4 textureColor = texture(uTextures, vec3(vTexCoord, vTextureLayer));
				if (textureColor.a < vTextureAlphaCutoff)
				{
					discard;
				}
				baseColor *= textureColor.rgb;
				alpha *= textureColor.a;
			}
			float fog = smoothstep(uFogStart, uFogEnd, vDistance);
			vec3 color = mix(sceneColor(baseColor), uFogColor, fog);
			if (alpha <= 0.01)
			{
				discard;
			}
			fragColor = vec4(color, alpha);
		}
		""";
	private static final String OUTLINE_VERTEX_SHADER = """
		#version 330 core
		layout(location = 0) in vec3 aPosition;

		uniform mat4 uMvp;

		void main()
		{
			gl_Position = uMvp * vec4(aPosition, 1.0);
		}
		""";
	private static final String OUTLINE_FRAGMENT_SHADER = """
		#version 330 core
		uniform vec4 uColor;

		out vec4 fragColor;

		void main()
		{
			fragColor = uColor;
		}
		""";
	private static final String TEXT_VERTEX_SHADER = """
		#version 330 core
		layout(location = 0) in vec3 aPosition;
		layout(location = 1) in vec2 aTexCoord;

		uniform mat4 uMvp;

		out vec2 vTexCoord;

		void main()
		{
			vTexCoord = aTexCoord;
			gl_Position = uMvp * vec4(aPosition, 1.0);
		}
		""";
	private static final String TEXT_FRAGMENT_SHADER = """
		#version 330 core
		in vec2 vTexCoord;

		uniform sampler2D uText;

		out vec4 fragColor;

		void main()
		{
			vec4 color = texture(uText, vTexCoord);
			if (color.a <= 0.01)
			{
				discard;
			}
			fragColor = color;
		}
		""";
	private static final int POSITION_FLOATS = 3;
	private static final int NORMAL_FLOATS = 3;
	private static final int COLOR_FLOATS = 3;
	private static final int ALPHA_FLOATS = 1;
	private static final int DEPTH_BIAS_FLOATS = 1;
	private static final int UV_FLOATS = 2;
	private static final int TEXTURE_LAYER_FLOATS = 1;
	private static final int TEXTURE_ANIMATION_FLOATS = 1;
	private static final int TEXTURE_ALPHA_CUTOFF_FLOATS = 1;
	private static final int OUTLINE_VERTICES = 8;
	private static final int OVERLAY_LINE_POSITION_FLOATS = 3;
	private static final int OVERLAY_TEXT_UV_FLOATS = 2;
	private static final int OVERLAY_TEXT_FLOATS_PER_VERTEX = OVERLAY_LINE_POSITION_FLOATS + OVERLAY_TEXT_UV_FLOATS;
	private static final int MAX_UPLOAD_BYTES_PER_FRAME = 3 * 1024 * 1024;
	private static final int MAX_UPLOAD_FLOATS_PER_FRAME = MAX_UPLOAD_BYTES_PER_FRAME / Float.BYTES;
	private static final int MAX_UPLOAD_CHUNK_BYTES = 512 * 1024;
	private static final int MAX_UPLOAD_CHUNK_FLOATS = MAX_UPLOAD_CHUNK_BYTES / Float.BYTES;
	private static final long MAX_UPLOAD_NANOS_PER_FRAME = 2_000_000L;
	private static final long COMPACTION_DEFER_NANOS = 750_000_000L;
	private static final long COMPACTION_PAUSE_SLICE_NANOS = 2_000_000L;
	private static final float OUTLINE_HEIGHT_OFFSET = SceneScale.SCENE_TO_WORLD * 1.5f;
	private static final float OVERLAY_PATH_HEIGHT_OFFSET = 0.28f;
	private static final float OVERLAY_MARKER_HEIGHT_OFFSET = OUTLINE_HEIGHT_OFFSET;
	private static final float OVERLAY_LABEL_HEIGHT_OFFSET = 1.15f;
	private static final float OVERLAY_LABEL_STACK_SPACING = 0.62f;
	private static final double OVERLAY_TRANSPORT_ARROW_LENGTH_TILES = 1.35;
	private static final double OVERLAY_TRANSPORT_ARROW_HEAD_LENGTH_TILES = 0.42;
	private static final double OVERLAY_TRANSPORT_ARROW_HEAD_WIDTH_TILES = 0.30;
	private static final float OVERLAY_LABEL_STEM_HEIGHT = 0.58f;
	private static final float OVERLAY_LABEL_FLAG_WIDTH = 0.38f;
	private static final float OVERLAY_TEXT_WORLD_HEIGHT = 0.54f;
	private static final int OVERLAY_TEXT_PADDING_X = 5;
	private static final int OVERLAY_TEXT_PADDING_Y = 3;
	private static final int OVERLAY_TEXT_ATLAS_MAX_WIDTH = 2048;
	private static final int OVERLAY_TRANSPORT_ICON_RGB = 0xFF2E3D;
	private static final int MAX_OVERLAY_PATH_INTERVAL_SAMPLES = 384;
	private static final int MAX_OVERLAY_LINE_VERTICES_PER_BATCH = 120_000;
	private static final int MAX_VISIBLE_PLANE = 3;
	private static final float REGION_CULL_PADDING = 2.0f;
	private static final float NPC_PICK_PADDING = 0.10f;
	private static final int MAX_NPC_OUTLINE_PIXELS = 500_000;
	private static final int NPC_OUTLINE_STAMP_RADIUS = 1;
	private static final float NPC_OUTLINE_DEPTH_BIAS = 0.0005f;

	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f view = new Matrix4f();
	private final Matrix4f mvp = new Matrix4f();
	private final Matrix4f modelMatrix = new Matrix4f();
	private final Matrix4f npcOutlineMvp = new Matrix4f();
	private final FrustumIntersection frustum = new FrustumIntersection();
	private final Map<Integer, UploadedRegion> uploadedRegions = new HashMap<>();
	private final Map<Integer, TerrainMesh> pendingUploadRegions = new LinkedHashMap<>();
	private final ExecutorService retainedDataCompactor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "3D retained mesh compactor");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});
	private RegionUploadTask activeUploadTask;
	private TerrainScene pendingScene;
	private TerrainScene currentScene;
	private volatile long retainedDataCompactionDeferredUntilNanos;
	private HoveredTile hoveredTile;
	private int terrainProgram;
	private int terrainMvpLocation;
	private int terrainModelMatrixLocation;
	private int terrainCameraLocation;
	private int terrainRegionOffsetLocation;
	private int terrainTimeLocation;
	private int terrainTextureLocation;
	private int terrainTextureLayerCountLocation;
	private int terrainFogStartLocation;
	private int terrainFogEndLocation;
	private int terrainFogColorLocation;
	private int outlineProgram;
	private int outlineMvpLocation;
	private int outlineColorLocation;
	private int textProgram;
	private int textMvpLocation;
	private int textTextureLocation;
	private int terrainTextureArray;
	private int uploadedTextureLayerCount;
	private int outlineVao;
	private int outlineVbo;
	private int npcOutlineEdgeVao;
	private int npcOutlineEdgeVbo;
	private int overlayLineVao;
	private int overlayLineVbo;
	private int overlayLineVertexCount;
	private int overlayObjectOutlineVao;
	private int overlayObjectOutlineVbo;
	private int overlayObjectOutlineVertexCount;
	private int overlayTileVao;
	private int overlayTileVbo;
	private int overlayTileVertexCount;
	private int overlayTextVao;
	private int overlayTextVbo;
	private int overlayTextTexture;
	private int sceneFbo;
	private int sceneColorRbo;
	private int sceneDepthRbo;
	private int sceneFboWidth = -1;
	private int sceneFboHeight = -1;
	private int sceneFboSamples = -1;
	private int renderWidth = 1;
	private int renderHeight = 1;
	private String glVendor = "Unavailable";
	private String glRenderer = "Unavailable";
	private String glVersion = "Unavailable";
	private TerrainRenderStats renderStats = TerrainRenderStats.unavailable();
	private Map3DOverlay pluginOverlay = Map3DOverlay.empty();
	private List<OverlayLineDraw> overlayLineDraws = List.of();
	private List<OverlayLineDraw> overlayObjectOutlineDraws = List.of();
	private List<OverlayLineDraw> overlayTileDraws = List.of();
	private List<OverlayTextLabel> overlayTextLabels = List.of();
	private volatile List<NpcMapDot> npcMapDots = List.of();
	private HoverRay hoverRay;
	private HoveredNpcDraw hoveredNpcDraw;
	private NpcHoverInfo hoveredNpcInfo;
	private boolean initialized;
	private int antialiasingSamples = 4;
	private int viewDistanceRegions = 3;
	private int maxVisiblePlane = 0;
	private long startNanos;
	private boolean pluginOverlayDirty = true;
	private boolean pluginOverlayOnTop;
	private boolean npcsVisible = true;
	private boolean npcOutlinesEnabled = true;
	private boolean npcHoverTextEnabled = true;
	private boolean npcPickingEnabled;
	private float backgroundRed = 0.0f;
	private float backgroundGreen = 0.0f;
	private float backgroundBlue = 0.0f;
	private Color npcOutlineColor = new Color(Viewer3DState.DEFAULT_NPC_OUTLINE_COLOR_ARGB, true);
	private Color tileHoverSelectorColor = new Color(Viewer3DState.DEFAULT_TILE_HOVER_COLOR_ARGB, true);

	void init()
	{
		if (initialized)
		{
			return;
		}

		GL.createCapabilities();
		glVendor = glString(GL33C.GL_VENDOR);
		glRenderer = glString(GL33C.GL_RENDERER);
		glVersion = glString(GL33C.GL_VERSION);
		GL33C.glEnable(GL33C.GL_DEPTH_TEST);
		GL33C.glDepthFunc(GL33C.GL_LEQUAL);
		if (antialiasingSamples > 0)
		{
			GL33C.glEnable(GL33C.GL_MULTISAMPLE);
		}
		else
		{
			GL33C.glDisable(GL33C.GL_MULTISAMPLE);
		}
		GL33C.glEnable(GL33C.GL_BLEND);
		GL33C.glBlendFunc(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA);
		GL33C.glClearColor(backgroundRed, backgroundGreen, backgroundBlue, 1.0f);

		terrainProgram = createProgram(TERRAIN_VERTEX_SHADER, TERRAIN_FRAGMENT_SHADER);
		terrainMvpLocation = GL33C.glGetUniformLocation(terrainProgram, "uMvp");
		terrainModelMatrixLocation = GL33C.glGetUniformLocation(terrainProgram, "uModelMatrix");
		terrainCameraLocation = GL33C.glGetUniformLocation(terrainProgram, "uCameraPosition");
		terrainRegionOffsetLocation = GL33C.glGetUniformLocation(terrainProgram, "uRegionOffset");
		terrainTimeLocation = GL33C.glGetUniformLocation(terrainProgram, "uTimeSeconds");
		terrainTextureLocation = GL33C.glGetUniformLocation(terrainProgram, "uTextures");
		terrainTextureLayerCountLocation = GL33C.glGetUniformLocation(terrainProgram, "uTextureLayerCount");
		terrainFogStartLocation = GL33C.glGetUniformLocation(terrainProgram, "uFogStart");
		terrainFogEndLocation = GL33C.glGetUniformLocation(terrainProgram, "uFogEnd");
		terrainFogColorLocation = GL33C.glGetUniformLocation(terrainProgram, "uFogColor");
		outlineProgram = createProgram(OUTLINE_VERTEX_SHADER, OUTLINE_FRAGMENT_SHADER);
		outlineMvpLocation = GL33C.glGetUniformLocation(outlineProgram, "uMvp");
		outlineColorLocation = GL33C.glGetUniformLocation(outlineProgram, "uColor");
		textProgram = createProgram(TEXT_VERTEX_SHADER, TEXT_FRAGMENT_SHADER);
		textMvpLocation = GL33C.glGetUniformLocation(textProgram, "uMvp");
		textTextureLocation = GL33C.glGetUniformLocation(textProgram, "uText");
		startNanos = System.nanoTime();
		initialized = true;
	}

	TerrainRenderStats renderStats()
	{
		return renderStats;
	}

	RegionUploadProgress regionUploadProgress()
	{
		RegionUploadTask task = activeUploadTask;
		if (task == null)
		{
			return RegionUploadProgress.none(pendingUploadRegions.size());
		}
		return new RegionUploadProgress(
			task.regionId(),
			task.stage(),
			task.uploadedFloats(),
			task.totalFloats(),
			pendingUploadRegions.size()
		);
	}

	boolean hasUploadedDrawableRegion(int regionId)
	{
		UploadedRegion region = uploadedRegions.get(regionId);
		return region != null && region.hasDrawableGeometry();
	}

	void setMesh(TerrainMesh mesh)
	{
		setScene(TerrainScene.single(mesh));
	}

	void setScene(TerrainScene scene)
	{
		pendingScene = scene;
	}

	void setHoveredTile(HoveredTile hoveredTile)
	{
		this.hoveredTile = hoveredTile;
	}

	void setPluginOverlay(Map3DOverlay overlay)
	{
		pluginOverlay = overlay == null ? Map3DOverlay.empty() : overlay;
		pluginOverlayDirty = true;
	}

	void setPluginOverlayOnTop(boolean pluginOverlayOnTop)
	{
		this.pluginOverlayOnTop = pluginOverlayOnTop;
	}

	void setMaxVisiblePlane(int maxVisiblePlane)
	{
		this.maxVisiblePlane = clamp(maxVisiblePlane, 0, MAX_VISIBLE_PLANE);
		pluginOverlayDirty = true;
		if (hoveredTile != null && !isPlaneVisible(hoveredTile.plane()))
		{
			hoveredTile = null;
		}
		if (hoveredNpcDraw != null && !isPlaneVisible(hoveredNpcDraw.instance().plane()))
		{
			hoveredNpcDraw = null;
			hoveredNpcInfo = null;
		}
	}

	void setHoverRay(Vector3fc origin, Vector3fc direction)
	{
		if (origin == null || direction == null || direction.lengthSquared() <= 0.000001f)
		{
			hoverRay = null;
			return;
		}
		hoverRay = new HoverRay(new Vector3f(origin), new Vector3f(direction).normalize());
	}

	NpcHoverInfo hoveredNpcInfo()
	{
		return hoveredNpcInfo;
	}

	NpcHoverInfo pickHoveredNpcInfo()
	{
		updateHoveredNpc(animationTimeSeconds());
		return hoveredNpcInfo;
	}

	List<NpcMapDot> npcMapDots()
	{
		return npcMapDots;
	}

	void setNpcsVisible(boolean npcsVisible)
	{
		this.npcsVisible = npcsVisible;
		if (!npcsVisible)
		{
			hoveredNpcDraw = null;
			hoveredNpcInfo = null;
		}
	}

	void setNpcOutlinesEnabled(boolean npcOutlinesEnabled)
	{
		this.npcOutlinesEnabled = npcOutlinesEnabled;
	}

	void setNpcHoverTextEnabled(boolean npcHoverTextEnabled)
	{
		this.npcHoverTextEnabled = npcHoverTextEnabled;
		if (!npcHoverTextEnabled && !npcPickingEnabled)
		{
			hoveredNpcInfo = null;
		}
	}

	void setNpcPickingEnabled(boolean npcPickingEnabled)
	{
		this.npcPickingEnabled = npcPickingEnabled;
		if (!npcPickingEnabled && !npcHoverTextEnabled)
		{
			hoveredNpcInfo = null;
		}
	}

	void setNpcOutlineColor(Color color)
	{
		npcOutlineColor = color == null
			? new Color(Viewer3DState.DEFAULT_NPC_OUTLINE_COLOR_ARGB, true)
			: color;
	}

	void setTileHoverSelectorColor(Color color)
	{
		tileHoverSelectorColor = color == null
			? new Color(Viewer3DState.DEFAULT_TILE_HOVER_COLOR_ARGB, true)
			: color;
	}

	void setBackgroundColor(Color color)
	{
		Color background = color == null ? Color.BLACK : color;
		backgroundRed = background.getRed() / 255.0f;
		backgroundGreen = background.getGreen() / 255.0f;
		backgroundBlue = background.getBlue() / 255.0f;
	}

	void invalidatePluginOverlayGeometry()
	{
		pluginOverlayDirty = true;
	}

	boolean isInitialized()
	{
		return initialized;
	}

	void setAntialiasingSamples(int antialiasingSamples)
	{
		this.antialiasingSamples = Math.max(0, antialiasingSamples);
		sceneFboSamples = -1;
	}

	void setViewDistanceRegions(int viewDistanceRegions)
	{
		this.viewDistanceRegions = Math.max(2, Math.min(5, viewDistanceRegions));
	}

	void deferRetainedDataCompaction()
	{
		deferRetainedDataCompactionUntil(System.nanoTime() + COMPACTION_DEFER_NANOS);
	}

	float animationTimeSeconds()
	{
		return (System.nanoTime() - startNanos) / 1_000_000_000.0f;
	}

	void render(FreeCamera camera, int width, int height)
	{
		init();
		if (pendingScene != null)
		{
			applyScene(pendingScene);
			pendingScene = null;
		}

		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		if (safeWidth != renderWidth || safeHeight != renderHeight)
		{
			pluginOverlayDirty = true;
		}
		renderWidth = safeWidth;
		renderHeight = safeHeight;
		int defaultFramebuffer = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
		int renderFramebuffer = prepareRenderTarget(safeWidth, safeHeight, defaultFramebuffer);
		GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, renderFramebuffer);
		GL33C.glViewport(0, 0, safeWidth, safeHeight);
		GL33C.glClearColor(backgroundRed, backgroundGreen, backgroundBlue, 1.0f);
		GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_DEPTH_BUFFER_BIT);

		RenderCounts renderCounts = RenderCounts.empty();
		int outlineDrawCalls = 0;
		int overlayDrawCalls = 0;
		if (!uploadedRegions.isEmpty())
		{
			uploadPendingRegions();
			prepareMatrices(camera, safeWidth / (float) safeHeight);
			float timeSeconds = animationTimeSeconds();
			renderCounts = renderTerrain(camera, timeSeconds);
			updateHoveredNpc(timeSeconds);
			updateNpcMapDots(timeSeconds);
			overlayDrawCalls = renderSceneOverlays(camera);
		}
		else
		{
			uploadPendingRegions();
			npcMapDots = List.of();
		}
		updateRenderStats(renderCounts, outlineDrawCalls + overlayDrawCalls);
		if (renderFramebuffer != defaultFramebuffer)
		{
			blitRenderTarget(defaultFramebuffer, safeWidth, safeHeight);
		}
	}

	void dispose()
	{
		if (!initialized)
		{
			return;
		}
		deleteUploadedRegions();
		if (terrainTextureArray != 0)
		{
			GL33C.glDeleteTextures(terrainTextureArray);
			terrainTextureArray = 0;
			uploadedTextureLayerCount = 0;
		}
		if (outlineVbo != 0)
		{
			GL33C.glDeleteBuffers(outlineVbo);
			outlineVbo = 0;
		}
		if (outlineVao != 0)
		{
			GL33C.glDeleteVertexArrays(outlineVao);
			outlineVao = 0;
		}
		if (npcOutlineEdgeVbo != 0)
		{
			GL33C.glDeleteBuffers(npcOutlineEdgeVbo);
			npcOutlineEdgeVbo = 0;
		}
		if (npcOutlineEdgeVao != 0)
		{
			GL33C.glDeleteVertexArrays(npcOutlineEdgeVao);
			npcOutlineEdgeVao = 0;
		}
		if (overlayLineVbo != 0)
		{
			GL33C.glDeleteBuffers(overlayLineVbo);
			overlayLineVbo = 0;
		}
		if (overlayLineVao != 0)
		{
			GL33C.glDeleteVertexArrays(overlayLineVao);
			overlayLineVao = 0;
		}
		if (overlayObjectOutlineVbo != 0)
		{
			GL33C.glDeleteBuffers(overlayObjectOutlineVbo);
			overlayObjectOutlineVbo = 0;
		}
		if (overlayObjectOutlineVao != 0)
		{
			GL33C.glDeleteVertexArrays(overlayObjectOutlineVao);
			overlayObjectOutlineVao = 0;
		}
		if (overlayTileVbo != 0)
		{
			GL33C.glDeleteBuffers(overlayTileVbo);
			overlayTileVbo = 0;
		}
		if (overlayTileVao != 0)
		{
			GL33C.glDeleteVertexArrays(overlayTileVao);
			overlayTileVao = 0;
		}
		if (overlayTextVbo != 0)
		{
			GL33C.glDeleteBuffers(overlayTextVbo);
			overlayTextVbo = 0;
		}
		if (overlayTextVao != 0)
		{
			GL33C.glDeleteVertexArrays(overlayTextVao);
			overlayTextVao = 0;
		}
		deleteOverlayTextTexture();
		if (terrainProgram != 0)
		{
			GL33C.glDeleteProgram(terrainProgram);
			terrainProgram = 0;
		}
		if (outlineProgram != 0)
		{
			GL33C.glDeleteProgram(outlineProgram);
			outlineProgram = 0;
		}
		if (textProgram != 0)
		{
			GL33C.glDeleteProgram(textProgram);
			textProgram = 0;
		}
		deleteSceneFbo();
		initialized = false;
	}

	void shutdown()
	{
		retainedDataCompactor.shutdownNow();
	}

	private void prepareMatrices(FreeCamera camera, float aspectRatio)
	{
		projection.identity().perspective(
			camera.fovRadians(),
			aspectRatio,
			SceneScale.CAMERA_NEAR_PLANE,
			SceneScale.CAMERA_FAR_PLANE
		);
		camera.viewMatrix(view);
		projection.mul(view, mvp);
		frustum.set(mvp);
	}

	private RenderCounts renderTerrain(FreeCamera camera, float timeSeconds)
	{
		Vector3fc cameraPosition = camera.position();
		GL33C.glUseProgram(terrainProgram);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer matrixBuffer = stack.mallocFloat(16);
			GL33C.glUniformMatrix4fv(terrainMvpLocation, false, mvp.get(matrixBuffer));
		}
		GL33C.glUniform3f(terrainCameraLocation, cameraPosition.x(), cameraPosition.y(), cameraPosition.z());
		GL33C.glUniform1f(terrainTimeLocation, timeSeconds);
		GL33C.glUniform1f(terrainTextureLayerCountLocation, uploadedTextureLayerCount);
		float fogEnd = fogEndDistance();
		GL33C.glUniform1f(terrainFogStartLocation, Math.max(18.0f, fogEnd - 56.0f));
		GL33C.glUniform1f(terrainFogEndLocation, fogEnd);
		GL33C.glUniform3f(terrainFogColorLocation, backgroundRed, backgroundGreen, backgroundBlue);
		GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
		GL33C.glBindTexture(GL33C.GL_TEXTURE_2D_ARRAY, terrainTextureArray);
		GL33C.glUniform1i(terrainTextureLocation, 0);
		int drawCalls = 0;
		int visibleRegions = 0;
		int culledRegions = 0;
		int verticesDrawn = 0;
		List<UploadedRegion> visibleRegionList = new ArrayList<>();
		for (UploadedRegion region : uploadedRegions.values())
		{
			if (region.vertexCount() <= 0 && region.animatedObjects().isEmpty() && region.npcMeshes().isEmpty())
			{
				continue;
			}
			if (!isVisible(region))
			{
				culledRegions++;
				continue;
			}
			visibleRegionList.add(region);
		}
		visibleRegions = visibleRegionList.size();

		for (UploadedRegion region : visibleRegionList)
		{
			GL33C.glUniform3f(terrainRegionOffsetLocation, region.offsetX(), 0.0f, region.offsetZ());
			setTerrainIdentityModelMatrix();
			if (region.vertexCount() > 0)
			{
				GL33C.glBindVertexArray(region.vao());
				for (int plane = 0; plane <= maxVisiblePlane; plane++)
				{
					int vertexCount = region.planeVertexCount(plane);
					if (vertexCount <= 0)
					{
						continue;
					}
					GL33C.glDrawArrays(GL33C.GL_TRIANGLES, region.planeStartVertex(plane), vertexCount);
					drawCalls++;
					verticesDrawn += vertexCount;
				}
			}
			for (UploadedAnimatedObject animatedObject : region.animatedObjects())
			{
				if (!isPlaneVisible(animatedObject.plane()))
				{
					continue;
				}
				UploadedAnimationFrame frame = animatedObject.frameAt(timeSeconds);
				if (frame.vertexCount() <= 0)
				{
					continue;
				}
				GL33C.glBindVertexArray(frame.vao());
				GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, frame.vertexCount());
				drawCalls++;
				verticesDrawn += frame.vertexCount();
			}
		}

		if (npcsVisible)
		{
			for (UploadedRegion region : visibleRegionList)
			{
				GL33C.glUniform3f(terrainRegionOffsetLocation, region.offsetX(), 0.0f, region.offsetZ());
				for (UploadedNpcMesh npcMesh : region.npcMeshes())
				{
					for (NpcMesh.Instance instance : npcMesh.instances())
					{
						if (!isPlaneVisible(instance.plane()))
						{
							continue;
						}
						NpcMesh.Transform transform = instance.transformAt(timeSeconds);
						if (npcMesh.walkingAnimation() != transform.walking())
						{
							continue;
						}
						UploadedAnimationFrame frame = npcMesh.frameAt(timeSeconds, instance.phaseOffset());
						if (frame.vertexCount() <= 0)
						{
							continue;
						}
						setTerrainModelMatrix(transform);
						GL33C.glBindVertexArray(frame.vao());
						GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, frame.vertexCount());
						drawCalls++;
						verticesDrawn += frame.vertexCount();
					}
				}
			}
		}

		boolean depthMask = GL33C.glGetBoolean(GL33C.GL_DEPTH_WRITEMASK);
		GL33C.glDepthMask(false);
		try
		{
			for (UploadedRegion region : visibleRegionList)
			{
				GL33C.glUniform3f(terrainRegionOffsetLocation, region.offsetX(), 0.0f, region.offsetZ());
				setTerrainIdentityModelMatrix();
				if (region.vertexCount() > 0)
				{
					GL33C.glBindVertexArray(region.vao());
					for (int plane = 0; plane <= maxVisiblePlane; plane++)
					{
						int vertexCount = region.planeTransparentVertexCount(plane);
						if (vertexCount <= 0)
						{
							continue;
						}
						GL33C.glDrawArrays(GL33C.GL_TRIANGLES, region.planeTransparentStartVertex(plane), vertexCount);
						drawCalls++;
						verticesDrawn += vertexCount;
					}
				}
				for (UploadedAnimatedObject animatedObject : region.animatedObjects())
				{
					if (!isPlaneVisible(animatedObject.plane()))
					{
						continue;
					}
					UploadedAnimationFrame frame = animatedObject.frameAt(timeSeconds);
					if (frame.transparentVertexCount() <= 0)
					{
						continue;
					}
					GL33C.glBindVertexArray(frame.vao());
					GL33C.glDrawArrays(GL33C.GL_TRIANGLES, frame.transparentStartVertex(), frame.transparentVertexCount());
					drawCalls++;
					verticesDrawn += frame.transparentVertexCount();
				}
			}
		}
		finally
		{
			GL33C.glDepthMask(depthMask);
		}
		setTerrainIdentityModelMatrix();
		GL33C.glBindVertexArray(0);
		GL33C.glBindTexture(GL33C.GL_TEXTURE_2D_ARRAY, 0);
		GL33C.glUseProgram(0);
		return new RenderCounts(drawCalls, visibleRegions, culledRegions, verticesDrawn);
	}

	private void updateHoveredNpc(float timeSeconds)
	{
		HoverRay ray = hoverRay;
		if (!npcsVisible || ray == null || (!npcOutlinesEnabled && !npcHoverTextEnabled && !npcPickingEnabled))
		{
			hoveredNpcDraw = null;
			hoveredNpcInfo = null;
			return;
		}

		HoveredNpcDraw best = null;
		float bestDistance = Float.POSITIVE_INFINITY;
		for (UploadedRegion region : uploadedRegions.values())
		{
			if (region.npcMeshes().isEmpty() || !isVisible(region))
			{
				continue;
			}
			for (UploadedNpcMesh npcMesh : region.npcMeshes())
			{
				for (NpcMesh.Instance instance : npcMesh.instances())
				{
					if (!isPlaneVisible(instance.plane()))
					{
						continue;
					}
					NpcMesh.Transform transform = instance.transformAt(timeSeconds);
					if (npcMesh.walkingAnimation() != transform.walking())
					{
						continue;
					}
					UploadedAnimationFrame frame = npcMesh.frameAt(timeSeconds, instance.phaseOffset());
					if (frame.vertexCount() <= 0)
					{
						continue;
					}
					float distance = npcIntersectionDistance(region, npcMesh.bounds(), transform, ray);
					if (distance < bestDistance)
					{
						bestDistance = distance;
						best = new HoveredNpcDraw(region, npcMesh, instance, transform, frame, distance);
					}
				}
			}
		}
		hoveredNpcDraw = best;
		NpcMesh.SpawnMetadata spawn = best == null ? null : best.instance().spawn();
		hoveredNpcInfo = best == null || (!npcHoverTextEnabled && !npcPickingEnabled)
			? null
			: new NpcHoverInfo(
				best.mesh().name(),
				best.mesh().combatLevel(),
				best.mesh().npcId(),
				spawn.name(),
				spawn.worldX(),
				spawn.worldY(),
				spawn.plane(),
				spawn.faceDirection(),
				spawn.walkEnabled(),
				best.instance().moving(),
				spawn.source()
			);
	}

	private void updateNpcMapDots(float timeSeconds)
	{
		if (!npcsVisible)
		{
			npcMapDots = List.of();
			return;
		}
		List<NpcMapDot> dots = new ArrayList<>();
		for (UploadedRegion region : uploadedRegions.values())
		{
			if (region.npcMeshes().isEmpty())
			{
				continue;
			}
			for (UploadedNpcMesh npcMesh : region.npcMeshes())
			{
				for (NpcMesh.Instance instance : npcMesh.instances())
				{
					if (!isPlaneVisible(instance.plane()))
					{
						continue;
					}
					NpcMesh.Transform transform = instance.transformAt(timeSeconds);
					if (npcMesh.walkingAnimation() != transform.walking())
					{
						continue;
					}
					dots.add(new NpcMapDot(
						TerrainScene.regionX(region.regionId()) * (double) TerrainScene.REGION_SIZE
							+ SceneScale.tileXFromWorld(transform.x()),
						TerrainScene.regionY(region.regionId()) * (double) TerrainScene.REGION_SIZE
							+ SceneScale.tileYFromWorld(transform.z()),
						instance.plane()
					));
				}
			}
		}
		npcMapDots = dots.isEmpty() ? List.of() : List.copyOf(dots);
	}

	private static float npcIntersectionDistance(UploadedRegion region, NpcMesh.Bounds bounds,
	                                             NpcMesh.Transform transform, HoverRay ray)
	{
		if (region == null || bounds == null || !bounds.valid() || transform == null || ray == null)
		{
			return Float.POSITIVE_INFINITY;
		}

		float yaw = transform.yawRadians();
		float cos = (float) Math.cos(yaw);
		float sin = (float) Math.sin(yaw);
		float originX = ray.origin().x() - (region.offsetX() + transform.x());
		float originY = ray.origin().y() - transform.y();
		float originZ = ray.origin().z() - (region.offsetZ() + transform.z());
		float localOriginX = cos * originX - sin * originZ;
		float localOriginZ = sin * originX + cos * originZ;
		float localDirectionX = cos * ray.direction().x() - sin * ray.direction().z();
		float localDirectionZ = sin * ray.direction().x() + cos * ray.direction().z();
		return rayAabbIntersection(
			localOriginX,
			originY,
			localOriginZ,
			localDirectionX,
			ray.direction().y(),
			localDirectionZ,
			bounds.minX() - NPC_PICK_PADDING,
			bounds.minY() - NPC_PICK_PADDING,
			bounds.minZ() - NPC_PICK_PADDING,
			bounds.maxX() + NPC_PICK_PADDING,
			bounds.maxY() + NPC_PICK_PADDING,
			bounds.maxZ() + NPC_PICK_PADDING
		);
	}

	private static float rayAabbIntersection(float originX, float originY, float originZ,
	                                         float directionX, float directionY, float directionZ,
	                                         float minX, float minY, float minZ,
	                                         float maxX, float maxY, float maxZ)
	{
		float tMin = 0.0f;
		float tMax = Float.POSITIVE_INFINITY;

		float dir = directionX;
		if (Math.abs(dir) < 0.000001f)
		{
			if (originX < minX || originX > maxX)
			{
				return Float.POSITIVE_INFINITY;
			}
		}
		else
		{
			float t1 = (minX - originX) / dir;
			float t2 = (maxX - originX) / dir;
			if (t1 > t2)
			{
				float tmp = t1;
				t1 = t2;
				t2 = tmp;
			}
			tMin = Math.max(tMin, t1);
			tMax = Math.min(tMax, t2);
			if (tMin > tMax)
			{
				return Float.POSITIVE_INFINITY;
			}
		}

		dir = directionY;
		if (Math.abs(dir) < 0.000001f)
		{
			if (originY < minY || originY > maxY)
			{
				return Float.POSITIVE_INFINITY;
			}
		}
		else
		{
			float t1 = (minY - originY) / dir;
			float t2 = (maxY - originY) / dir;
			if (t1 > t2)
			{
				float tmp = t1;
				t1 = t2;
				t2 = tmp;
			}
			tMin = Math.max(tMin, t1);
			tMax = Math.min(tMax, t2);
			if (tMin > tMax)
			{
				return Float.POSITIVE_INFINITY;
			}
		}

		dir = directionZ;
		if (Math.abs(dir) < 0.000001f)
		{
			if (originZ < minZ || originZ > maxZ)
			{
				return Float.POSITIVE_INFINITY;
			}
		}
		else
		{
			float t1 = (minZ - originZ) / dir;
			float t2 = (maxZ - originZ) / dir;
			if (t1 > t2)
			{
				float tmp = t1;
				t1 = t2;
				t2 = tmp;
			}
			tMin = Math.max(tMin, t1);
			tMax = Math.min(tMax, t2);
		}

		return tMin <= tMax ? tMin : Float.POSITIVE_INFINITY;
	}

	private boolean isPlaneVisible(int plane)
	{
		return plane >= 0 && plane <= maxVisiblePlane;
	}

	private void setTerrainIdentityModelMatrix()
	{
		modelMatrix.identity();
		uploadTerrainModelMatrix(modelMatrix);
	}

	private void setTerrainModelMatrix(NpcMesh.Transform transform)
	{
		modelMatrix
			.identity()
			.translation(transform.x(), transform.y(), transform.z())
			.rotateY(transform.yawRadians());
		uploadTerrainModelMatrix(modelMatrix);
	}

	private void uploadTerrainModelMatrix(Matrix4f matrix)
	{
		if (terrainModelMatrixLocation < 0)
		{
			return;
		}
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer matrixBuffer = stack.mallocFloat(16);
			GL33C.glUniformMatrix4fv(terrainModelMatrixLocation, false, matrix.get(matrixBuffer));
		}
	}

	private int renderHoveredTile()
	{
		if (currentScene == null || hoveredTile == null || !isPlaneVisible(hoveredTile.plane()))
		{
			return 0;
		}
		if (outlineVao == 0)
		{
			outlineVao = GL33C.glGenVertexArrays();
			outlineVbo = GL33C.glGenBuffers();
			GL33C.glBindVertexArray(outlineVao);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, outlineVbo);
			GL33C.glEnableVertexAttribArray(0);
			GL33C.glVertexAttribPointer(
				0,
				POSITION_FLOATS,
				GL33C.GL_FLOAT,
				false,
				POSITION_FLOATS * Float.BYTES,
				0L
			);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			GL33C.glBindVertexArray(0);
		}

		float[] vertices = outlineVertices();
		if (vertices.length == 0)
		{
			return 0;
		}
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, outlineVbo);
		GL33C.glUseProgram(outlineProgram);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer buffer = stack.mallocFloat(vertices.length);
			buffer.put(vertices).flip();
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_DYNAMIC_DRAW);
			FloatBuffer matrixBuffer = stack.mallocFloat(16);
			GL33C.glUniformMatrix4fv(outlineMvpLocation, false, mvp.get(matrixBuffer));
		}
		GL33C.glBindVertexArray(outlineVao);
		GL33C.glUniform4f(outlineColorLocation, 0.0f, 0.0f, 0.0f, 0.78f);
		GL33C.glLineWidth(4.0f);
		GL33C.glDrawArrays(GL33C.GL_LINES, 0, OUTLINE_VERTICES);
		uploadOutlineColor(tileHoverSelectorColor, 0.95f);
		GL33C.glLineWidth(2.0f);
		GL33C.glDrawArrays(GL33C.GL_LINES, 0, OUTLINE_VERTICES);
		GL33C.glLineWidth(1.0f);
		GL33C.glBindVertexArray(0);
		GL33C.glUseProgram(0);
		return 2;
	}

	private int renderSceneOverlays(FreeCamera camera)
	{
		if (currentScene == null)
		{
			return 0;
		}
		boolean previousDepthEnabled = GL33C.glIsEnabled(GL33C.GL_DEPTH_TEST);
		if (pluginOverlayOnTop)
		{
			GL33C.glDisable(GL33C.GL_DEPTH_TEST);
		}
		else
		{
			GL33C.glEnable(GL33C.GL_DEPTH_TEST);
		}
		try
		{
			int hoverDrawCalls = hoveredTileCoveredByPluginOverlay() ? 0 : renderHoveredTile();
			int pluginDrawCalls = renderPluginOverlay(camera);
			return hoverDrawCalls + pluginDrawCalls + renderHoveredNpcOutline(camera);
		}
		finally
		{
			if (previousDepthEnabled)
			{
				GL33C.glEnable(GL33C.GL_DEPTH_TEST);
			}
			else
			{
				GL33C.glDisable(GL33C.GL_DEPTH_TEST);
			}
		}
	}

	private int renderHoveredNpcOutline(FreeCamera camera)
	{
		HoveredNpcDraw hovered = hoveredNpcDraw;
		if (!npcsVisible || !npcOutlinesEnabled || hovered == null || hovered.frame().outlineGeometry().isEmpty())
		{
			return 0;
		}

		FloatList vertices = npcOutlineLineVertices(hovered);
		int vertexCount = vertices.size() / OVERLAY_LINE_POSITION_FLOATS;
		if (vertexCount <= 0 || vertexCount * OVERLAY_LINE_POSITION_FLOATS != vertices.size())
		{
			return 0;
		}

		ensureNpcOutlineEdgeBuffers();
		float[] data = vertices.array();
		FloatBuffer buffer = MemoryUtil.memAllocFloat(data.length);
		boolean cullEnabled = GL33C.glIsEnabled(GL33C.GL_CULL_FACE);
		boolean depthMask = GL33C.glGetBoolean(GL33C.GL_DEPTH_WRITEMASK);
		GL33C.glDepthMask(false);
		GL33C.glDisable(GL33C.GL_CULL_FACE);
		try
		{
			buffer.put(data).flip();
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, npcOutlineEdgeVbo);
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_DYNAMIC_DRAW);
			GL33C.glUseProgram(outlineProgram);
			uploadScreenOutlineMatrix();
			GL33C.glBindVertexArray(npcOutlineEdgeVao);
				GL33C.glUniform4f(outlineColorLocation, 0.0f, 0.0f, 0.0f, 0.80f);
				GL33C.glLineWidth(4.0f);
				GL33C.glDrawArrays(GL33C.GL_LINES, 0, vertexCount);
				uploadOutlineColor(npcOutlineColor, 0.98f);
				GL33C.glLineWidth(2.0f);
				GL33C.glDrawArrays(GL33C.GL_LINES, 0, vertexCount);
			return 2;
		}
		finally
		{
			MemoryUtil.memFree(buffer);
			GL33C.glLineWidth(1.0f);
			GL33C.glBindVertexArray(0);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			GL33C.glUseProgram(0);
			GL33C.glDepthMask(depthMask);
			if (cullEnabled)
			{
				GL33C.glEnable(GL33C.GL_CULL_FACE);
			}
		}
	}

	private void uploadOutlineColor(Color color, float fallbackAlpha)
	{
		Color c = color == null ? Color.WHITE : color;
		float alpha = c.getAlpha() / 255.0f;
		if (alpha <= 0.0f)
		{
			alpha = fallbackAlpha;
		}
		GL33C.glUniform4f(outlineColorLocation,
			c.getRed() / 255.0f,
			c.getGreen() / 255.0f,
			c.getBlue() / 255.0f,
			Math.max(0.05f, Math.min(1.0f, alpha)));
	}

	private FloatList npcOutlineLineVertices(HoveredNpcDraw hovered)
	{
		NpcOutlineGeometry geometry = hovered.frame().outlineGeometry();
		float[] triangleData = geometry.triangleData();
		if (triangleData.length == 0 || renderWidth <= 0 || renderHeight <= 0)
		{
			return new FloatList();
		}
		return outlineLineVertices(projectedNpcOutline(hovered, triangleData));
	}

	private FloatList objectOutlineLineVertices(MatchedObjectOverlay match)
	{
		if (match == null || match.region() == null || match.mesh() == null || renderWidth <= 0 || renderHeight <= 0)
		{
			return new FloatList();
		}
		float[] triangleData = match.mesh().rawVertexData();
		if (triangleData.length == 0)
		{
			return new FloatList();
		}
		return outlineLineVertices(projectedObjectOutline(match, triangleData));
	}

	private FloatList outlineLineVertices(ProjectedOutline outline)
	{
		FloatList vertices = new FloatList();
		if (outline == null || outline.coverage().length == 0)
		{
			return vertices;
		}

		boolean[] coverage = outline.coverage();
		float[] depth = outline.depth();
		int originX = outline.originX();
		int originY = outline.originY();
		int width = outline.width();
		int height = outline.height();
		boolean[] outlineMask = new boolean[coverage.length];
		float[] outlineDepth = new float[coverage.length];
		java.util.Arrays.fill(outlineDepth, Float.POSITIVE_INFINITY);
		for (int y = 0; y < height; y++)
		{
			int rowOffset = y * width;
			for (int x = 0; x < width; x++)
			{
				int index = rowOffset + x;
				if (!coverage[index])
				{
					continue;
				}
				if (isNpcOutlineBoundaryPixel(coverage, x, y, width, height))
				{
					applyNpcOutlineStamp(outlineMask, outlineDepth, coverage, x, y, Math.min(1.0f, depth[index] + NPC_OUTLINE_DEPTH_BIAS), width, height);
				}
			}
		}
		addNpcOutlineMaskSpans(vertices, outlineMask, outlineDepth, originX, originY, width, height);
		return vertices;
	}

	private ProjectedOutline projectedNpcOutline(HoveredNpcDraw hovered, float[] triangleData)
	{
		Matrix4f transform = new Matrix4f()
			.translation(hovered.region().offsetX(), 0.0f, hovered.region().offsetZ())
			.translate(hovered.transform().x(), hovered.transform().y(), hovered.transform().z())
			.rotateY(hovered.transform().yawRadians());
		Matrix4f projectionMatrix = new Matrix4f(mvp).mul(transform);
		return projectedOutline(projectionMatrix, triangleData);
	}

	private ProjectedOutline projectedObjectOutline(MatchedObjectOverlay match, float[] triangleData)
	{
		Matrix4f transform = new Matrix4f()
			.translation(match.region().offsetX(), 0.0f, match.region().offsetZ());
		Matrix4f projectionMatrix = new Matrix4f(mvp).mul(transform);
		return projectedOutline(projectionMatrix, triangleData);
	}

	private ProjectedOutline projectedOutline(Matrix4f projectionMatrix, float[] triangleData)
	{
		if (projectionMatrix == null || triangleData == null || triangleData.length == 0)
		{
			return null;
		}
		List<ProjectedTriangle> triangles = new ArrayList<>(Math.max(16, triangleData.length / 9));
		Vector4f clipA = new Vector4f();
		Vector4f clipB = new Vector4f();
		Vector4f clipC = new Vector4f();
		float minProjectedX = Float.POSITIVE_INFINITY;
		float minProjectedY = Float.POSITIVE_INFINITY;
		float maxProjectedX = Float.NEGATIVE_INFINITY;
		float maxProjectedY = Float.NEGATIVE_INFINITY;
		for (int offset = 0; offset + 8 < triangleData.length; offset += 9)
		{
			if (!projectOutlineVertex(projectionMatrix, triangleData, offset, clipA)
				|| !projectOutlineVertex(projectionMatrix, triangleData, offset + 3, clipB)
				|| !projectOutlineVertex(projectionMatrix, triangleData, offset + 6, clipC))
			{
				continue;
			}
			float ax = screenX(clipA.x);
			float ay = screenY(clipA.y);
			float bx = screenX(clipB.x);
			float by = screenY(clipB.y);
			float cx = screenX(clipC.x);
			float cy = screenY(clipC.y);
			float determinant = ax * (by - cy) + bx * (cy - ay) + cx * (ay - by);
			if (!Float.isFinite(determinant) || Math.abs(determinant) <= 0.01f)
			{
				continue;
			}
			ProjectedTriangle triangle = new ProjectedTriangle(ax, ay, clipA.z, bx, by, clipB.z, cx, cy, clipC.z);
			triangles.add(triangle);
			minProjectedX = Math.min(minProjectedX, triangle.minX());
			minProjectedY = Math.min(minProjectedY, triangle.minY());
			maxProjectedX = Math.max(maxProjectedX, triangle.maxX());
			maxProjectedY = Math.max(maxProjectedY, triangle.maxY());
		}
		if (triangles.isEmpty() || !Float.isFinite(minProjectedX) || !Float.isFinite(minProjectedY)
			|| !Float.isFinite(maxProjectedX) || !Float.isFinite(maxProjectedY))
		{
			return null;
		}
		int originX = clamp((int) Math.floor(minProjectedX) - 2, 0, renderWidth - 1);
		int originY = clamp((int) Math.floor(minProjectedY) - 2, 0, renderHeight - 1);
		int maxX = clamp((int) Math.ceil(maxProjectedX) + 2, 0, renderWidth - 1);
		int maxY = clamp((int) Math.ceil(maxProjectedY) + 2, 0, renderHeight - 1);
		if (originX > maxX || originY > maxY)
		{
			return null;
		}
		int width = maxX - originX + 1;
		int height = maxY - originY + 1;
		long pixelCountLong = (long) width * height;
		if (pixelCountLong <= 0L || pixelCountLong > MAX_NPC_OUTLINE_PIXELS)
		{
			return null;
		}
		int pixelCount = (int) pixelCountLong;
		float[] depth = new float[pixelCount];
		boolean[] coverage = new boolean[pixelCount];
		java.util.Arrays.fill(depth, Float.POSITIVE_INFINITY);
		boolean hasCoverage = false;
		for (ProjectedTriangle triangle : triangles)
		{
			if (rasterizeProjectedTriangle(triangle, depth, coverage, originX, originY, width, height))
			{
				hasCoverage = true;
			}
		}
		return hasCoverage ? new ProjectedOutline(originX, originY, width, height, depth, coverage) : null;
	}

	private boolean projectOutlineVertex(Matrix4f matrix, float[] triangleData, int offset, Vector4f clip)
	{
		clip.set(triangleData[offset], triangleData[offset + 1], triangleData[offset + 2], 1.0f);
		matrix.transform(clip);
		if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || !Float.isFinite(clip.z) || !Float.isFinite(clip.w)
			|| clip.w <= 0.0001f)
		{
			return false;
		}
		float invW = 1.0f / clip.w;
		clip.set(clip.x * invW, clip.y * invW, clip.z * invW, 1.0f);
		return Float.isFinite(clip.x) && Float.isFinite(clip.y) && Float.isFinite(clip.z)
			&& clip.z >= -1.0f && clip.z <= 1.0f;
	}

	private boolean rasterizeProjectedTriangle(ProjectedTriangle triangle, float[] depth, boolean[] coverage,
	                                           int originX, int originY, int width, int height)
	{
		int minY = clamp((int) Math.ceil(triangle.minY() - 0.5f), originY, originY + height - 1);
		int maxY = clamp((int) Math.floor(triangle.maxY() - 0.5f), originY, originY + height - 1);
		if (minY > maxY)
		{
			return false;
		}
		boolean wrote = false;
		float[] intersections = new float[3];
		for (int y = minY; y <= maxY; y++)
		{
			float sampleY = y + 0.5f;
			int count = 0;
			count = addScanlineIntersection(sampleY, triangle.ax(), triangle.ay(), triangle.bx(), triangle.by(), intersections, count);
			count = addScanlineIntersection(sampleY, triangle.bx(), triangle.by(), triangle.cx(), triangle.cy(), intersections, count);
			count = addScanlineIntersection(sampleY, triangle.cx(), triangle.cy(), triangle.ax(), triangle.ay(), intersections, count);
			if (count < 2)
			{
				continue;
			}
			float left = intersections[0];
			float right = intersections[0];
			for (int i = 1; i < count; i++)
			{
				left = Math.min(left, intersections[i]);
				right = Math.max(right, intersections[i]);
			}
			int minX = clamp((int) Math.ceil(left - 0.5f), originX, originX + width - 1);
			int maxX = clamp((int) Math.floor(right - 0.5f), originX, originX + width - 1);
			if (minX > maxX)
			{
				continue;
			}
			float sampleX = minX + 0.5f;
			float z = triangle.depthAt(sampleX, sampleY);
			int index = (y - originY) * width + (minX - originX);
			for (int x = minX; x <= maxX; x++)
			{
				if (z < depth[index])
				{
					depth[index] = z;
					coverage[index] = true;
					wrote = true;
				}
				z += triangle.depthPlaneX();
				index++;
			}
		}
		return wrote;
	}

	private static int addScanlineIntersection(float sampleY, float x1, float y1, float x2, float y2,
	                                           float[] intersections, int count)
	{
		if (count >= intersections.length || y1 == y2)
		{
			return count;
		}
		float minY = Math.min(y1, y2);
		float maxY = Math.max(y1, y2);
		if (sampleY < minY || sampleY >= maxY)
		{
			return count;
		}
		float t = (sampleY - y1) / (y2 - y1);
		intersections[count++] = x1 + (x2 - x1) * t;
		return count;
	}

	private static boolean isNpcOutlineBoundaryPixel(boolean[] coverage, int x, int y, int width, int height)
	{
		for (int dy = -1; dy <= 1; dy++)
		{
			for (int dx = -1; dx <= 1; dx++)
			{
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				int neighborX = x + dx;
				int neighborY = y + dy;
				if (neighborX < 0 || neighborY < 0 || neighborX >= width || neighborY >= height)
				{
					return true;
				}
				if (!coverage[neighborY * width + neighborX])
				{
					return true;
				}
			}
		}
		return false;
	}

	private static void applyNpcOutlineStamp(boolean[] outlineMask, float[] outlineDepth, boolean[] coverage,
	                                         int centerX, int centerY, float z, int width, int height)
	{
		for (int dy = -NPC_OUTLINE_STAMP_RADIUS; dy <= NPC_OUTLINE_STAMP_RADIUS; dy++)
		{
			for (int dx = -NPC_OUTLINE_STAMP_RADIUS; dx <= NPC_OUTLINE_STAMP_RADIUS; dx++)
			{
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				int x = centerX + dx;
				int y = centerY + dy;
				if (x < 0 || y < 0 || x >= width || y >= height)
				{
					continue;
				}
				int index = y * width + x;
				if (coverage[index])
				{
					continue;
				}
				outlineMask[index] = true;
				outlineDepth[index] = Math.min(outlineDepth[index], z);
			}
		}
	}

	private void addNpcOutlineMaskSpans(FloatList vertices, boolean[] outlineMask, float[] outlineDepth,
	                                    int originX, int originY, int width, int height)
	{
		for (int y = 0; y < height; y++)
		{
			int rowOffset = y * width;
			int x = 0;
			while (x < width)
			{
				if (!outlineMask[rowOffset + x])
				{
					x++;
					continue;
				}
				int startX = x;
				float z = outlineDepth[rowOffset + x];
				while (x < width && outlineMask[rowOffset + x])
				{
					z = Math.min(z, outlineDepth[rowOffset + x]);
					x++;
				}
				if (Float.isFinite(z))
				{
					addScreenLine(vertices, originX + startX, originY + y + 0.5f, originX + x, originY + y + 0.5f, z);
				}
			}
		}
	}

	private void addScreenLine(FloatList vertices, float x1, float y1, float x2, float y2, float z)
	{
		if (vertices.size() / OVERLAY_LINE_POSITION_FLOATS + 2 > MAX_OVERLAY_LINE_VERTICES_PER_BATCH)
		{
			return;
		}
		vertices
			.add(ndcX(x1)).add(ndcY(y1)).add(z)
			.add(ndcX(x2)).add(ndcY(y2)).add(z);
	}

	private float screenX(float ndcX)
	{
		return (ndcX * 0.5f + 0.5f) * renderWidth;
	}

	private float screenY(float ndcY)
	{
		return (1.0f - (ndcY * 0.5f + 0.5f)) * renderHeight;
	}

	private float ndcX(float screenX)
	{
		return screenX / Math.max(1.0f, renderWidth) * 2.0f - 1.0f;
	}

	private float ndcY(float screenY)
	{
		return 1.0f - screenY / Math.max(1.0f, renderHeight) * 2.0f;
	}

	private void ensureNpcOutlineEdgeBuffers()
	{
		if (npcOutlineEdgeVao != 0 && npcOutlineEdgeVbo != 0)
		{
			return;
		}
		npcOutlineEdgeVao = GL33C.glGenVertexArrays();
		npcOutlineEdgeVbo = GL33C.glGenBuffers();
		GL33C.glBindVertexArray(npcOutlineEdgeVao);
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, npcOutlineEdgeVbo);
		GL33C.glEnableVertexAttribArray(0);
		GL33C.glVertexAttribPointer(0, OVERLAY_LINE_POSITION_FLOATS, GL33C.GL_FLOAT, false,
			OVERLAY_LINE_POSITION_FLOATS * Float.BYTES, 0L);
		GL33C.glBindVertexArray(0);
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
	}

	private void ensurePluginObjectOutlineBuffers()
	{
		if (overlayObjectOutlineVao != 0 && overlayObjectOutlineVbo != 0)
		{
			return;
		}
		overlayObjectOutlineVao = GL33C.glGenVertexArrays();
		overlayObjectOutlineVbo = GL33C.glGenBuffers();
		GL33C.glBindVertexArray(overlayObjectOutlineVao);
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayObjectOutlineVbo);
		GL33C.glEnableVertexAttribArray(0);
		GL33C.glVertexAttribPointer(0, OVERLAY_LINE_POSITION_FLOATS, GL33C.GL_FLOAT, false,
			OVERLAY_LINE_POSITION_FLOATS * Float.BYTES, 0L);
		GL33C.glBindVertexArray(0);
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
	}

	private void uploadScreenOutlineMatrix()
	{
		npcOutlineMvp.identity();
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer matrixBuffer = stack.mallocFloat(16);
			GL33C.glUniformMatrix4fv(outlineMvpLocation, false, npcOutlineMvp.get(matrixBuffer));
		}
	}

	private int renderPluginOverlay(FreeCamera camera)
	{
		if (pluginOverlayDirty)
		{
			rebuildPluginOverlayLines();
			pluginOverlayDirty = false;
		}
		int drawCalls = renderPluginOverlayTiles();
		drawCalls += renderPluginOverlayLines();
		drawCalls += renderPluginObjectOverlayOutlines();
		return drawCalls + renderPluginOverlayText(camera);
	}

	private int renderPluginOverlayLines()
	{
		if (overlayLineVertexCount <= 0 || overlayLineDraws.isEmpty())
		{
			return 0;
		}

		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayLineVbo);
		GL33C.glUseProgram(outlineProgram);
		int drawCalls = 0;
		try
		{
			try (MemoryStack stack = MemoryStack.stackPush())
			{
				FloatBuffer matrixBuffer = stack.mallocFloat(16);
				GL33C.glUniformMatrix4fv(outlineMvpLocation, false, mvp.get(matrixBuffer));
			}
			GL33C.glBindVertexArray(overlayLineVao);
			if (validDrawRange(0, overlayLineVertexCount, overlayLineVertexCount, 2))
			{
				GL33C.glUniform4f(outlineColorLocation, 0.0f, 0.0f, 0.0f, 0.70f);
				GL33C.glLineWidth(5.2f);
				GL33C.glDrawArrays(GL33C.GL_LINES, 0, overlayLineVertexCount);
				drawCalls++;
			}
			for (OverlayLineDraw draw : overlayLineDraws)
			{
				if (!validDrawRange(draw, overlayLineVertexCount, 2))
				{
					continue;
				}
				GL33C.glUniform4f(outlineColorLocation, draw.red(), draw.green(), draw.blue(), draw.alpha());
				GL33C.glLineWidth(2.7f);
				GL33C.glDrawArrays(GL33C.GL_LINES, draw.startVertex(), draw.vertexCount());
				drawCalls++;
			}
		}
		finally
		{
			GL33C.glLineWidth(1.0f);
			GL33C.glBindVertexArray(0);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			GL33C.glUseProgram(0);
		}
		return drawCalls;
	}

	private int renderPluginObjectOverlayOutlines()
	{
		if (overlayObjectOutlineVertexCount <= 0 || overlayObjectOutlineDraws.isEmpty())
		{
			return 0;
		}

		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayObjectOutlineVbo);
		GL33C.glUseProgram(outlineProgram);
		boolean cullEnabled = GL33C.glIsEnabled(GL33C.GL_CULL_FACE);
		boolean depthMask = GL33C.glGetBoolean(GL33C.GL_DEPTH_WRITEMASK);
		GL33C.glDepthMask(false);
		GL33C.glDisable(GL33C.GL_CULL_FACE);
		int drawCalls = 0;
		try
		{
			uploadScreenOutlineMatrix();
			GL33C.glBindVertexArray(overlayObjectOutlineVao);
			if (validDrawRange(0, overlayObjectOutlineVertexCount, overlayObjectOutlineVertexCount, 2))
			{
				GL33C.glUniform4f(outlineColorLocation, 0.0f, 0.0f, 0.0f, 0.80f);
				GL33C.glLineWidth(4.0f);
				GL33C.glDrawArrays(GL33C.GL_LINES, 0, overlayObjectOutlineVertexCount);
				drawCalls++;
			}
			for (OverlayLineDraw draw : overlayObjectOutlineDraws)
			{
				if (!validDrawRange(draw, overlayObjectOutlineVertexCount, 2))
				{
					continue;
				}
				GL33C.glUniform4f(outlineColorLocation, draw.red(), draw.green(), draw.blue(), draw.alpha());
				GL33C.glLineWidth(2.0f);
				GL33C.glDrawArrays(GL33C.GL_LINES, draw.startVertex(), draw.vertexCount());
				drawCalls++;
			}
		}
		finally
		{
			GL33C.glLineWidth(1.0f);
			GL33C.glBindVertexArray(0);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			GL33C.glUseProgram(0);
			GL33C.glDepthMask(depthMask);
			if (cullEnabled)
			{
				GL33C.glEnable(GL33C.GL_CULL_FACE);
			}
		}
		return drawCalls;
	}

	private boolean hoveredTileCoveredByPluginOverlay()
	{
		if (hoveredTile == null || pluginOverlay.tileOverlays().isEmpty())
		{
			return false;
		}
		for (Map3DTileOverlay overlay : pluginOverlay.tileOverlays())
		{
			if (overlay == null)
			{
				continue;
			}
			Tile tile = overlay.tile();
			if (tile != null
				&& tile.x == hoveredTile.worldX()
				&& tile.y == hoveredTile.worldY()
				&& tile.z == hoveredTile.plane())
			{
				return true;
			}
		}
		return false;
	}

	private int renderPluginOverlayTiles()
	{
		if (overlayTileVertexCount <= 0 || overlayTileDraws.isEmpty())
		{
			return 0;
		}

		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayTileVbo);
		GL33C.glUseProgram(outlineProgram);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer matrixBuffer = stack.mallocFloat(16);
			GL33C.glUniformMatrix4fv(outlineMvpLocation, false, mvp.get(matrixBuffer));
		}

		boolean depthMask = GL33C.glGetBoolean(GL33C.GL_DEPTH_WRITEMASK);
		GL33C.glDepthMask(false);
		int drawCalls = 0;
		try
		{
			GL33C.glBindVertexArray(overlayTileVao);
			for (OverlayLineDraw draw : overlayTileDraws)
			{
				if (!validDrawRange(draw, overlayTileVertexCount, 3))
				{
					continue;
				}
				GL33C.glUniform4f(outlineColorLocation, draw.red(), draw.green(), draw.blue(), draw.alpha());
				GL33C.glDrawArrays(GL33C.GL_TRIANGLES, draw.startVertex(), draw.vertexCount());
				drawCalls++;
			}
		}
		finally
		{
			GL33C.glBindVertexArray(0);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			GL33C.glDepthMask(depthMask);
			GL33C.glUseProgram(0);
		}
		return drawCalls;
	}

	private void rebuildPluginOverlayLines()
	{
		if (overlayLineVao == 0)
		{
			overlayLineVao = GL33C.glGenVertexArrays();
			overlayLineVbo = GL33C.glGenBuffers();
			GL33C.glBindVertexArray(overlayLineVao);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayLineVbo);
			GL33C.glEnableVertexAttribArray(0);
			GL33C.glVertexAttribPointer(
				0,
				OVERLAY_LINE_POSITION_FLOATS,
				GL33C.GL_FLOAT,
				false,
				OVERLAY_LINE_POSITION_FLOATS * Float.BYTES,
				0L
			);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			GL33C.glBindVertexArray(0);
		}
		if (overlayTileVao == 0)
		{
			overlayTileVao = GL33C.glGenVertexArrays();
			overlayTileVbo = GL33C.glGenBuffers();
			GL33C.glBindVertexArray(overlayTileVao);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayTileVbo);
			GL33C.glEnableVertexAttribArray(0);
			GL33C.glVertexAttribPointer(
				0,
				OVERLAY_LINE_POSITION_FLOATS,
				GL33C.GL_FLOAT,
				false,
				OVERLAY_LINE_POSITION_FLOATS * Float.BYTES,
				0L
			);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			GL33C.glBindVertexArray(0);
		}

		Map<Integer, OverlayLineBatch> batches = new LinkedHashMap<>();
		Map<Integer, OverlayLineBatch> tileBatches = new LinkedHashMap<>();
		Map<Integer, OverlayLineBatch> objectOutlineBatches = new LinkedHashMap<>();
		for (Map3DPathSegment segment : pluginOverlay.segments())
		{
			if (segment == null || !isTilePlaneVisible(segment.start()) || !isTilePlaneVisible(segment.end()))
			{
				continue;
			}
			OverlayLineBatch batch = overlayBatch(batches, segment.color());
			addPathSegmentLine(batch, segment);
		}
		for (Map3DMarker marker : pluginOverlay.markers())
		{
			if (marker != null && isTilePlaneVisible(marker.tile()))
			{
				addTileHighlightLines(batches, marker.tile(), marker.color());
			}
		}
		for (Map3DTileOverlay overlay : pluginOverlay.tileOverlays())
		{
			if (overlay == null || !isTilePlaneVisible(overlay.tile()))
			{
				continue;
			}
			addTileFill(tileBatches, overlay.tile(), overlay.fillColor());
			addTileHighlightLines(batches, overlay.tile(), overlay.outlineColor());
		}
		for (Map3DObjectOverlay overlay : pluginOverlay.objectOverlays())
		{
			addObjectOverlay(objectOutlineBatches, tileBatches, overlay);
		}
		Set<Long> labelFlagTiles = new HashSet<>();
		for (Map3DLabel label : pluginOverlay.labels())
		{
			if (label != null && isTilePlaneVisible(label.tile()) && labelFlagTiles.add(labelStackKey(label.tile())))
			{
				addLabelAnchorLines(batches, label.tile(), 0);
			}
		}

		FloatList vertices = new FloatList();
		List<OverlayLineDraw> draws = new ArrayList<>();
		int startVertex = 0;
		for (OverlayLineBatch batch : batches.values())
		{
			int vertexCount = batch.vertexCount();
			if (vertexCount <= 0)
			{
				continue;
			}
			draws.add(new OverlayLineDraw(
				startVertex,
				vertexCount,
				channel(batch.argb(), 16),
				channel(batch.argb(), 8),
				channel(batch.argb(), 0),
				channel(batch.argb(), 24)
			));
			vertices.addAll(batch.vertices());
			startVertex += vertexCount;
		}

		overlayLineDraws = List.copyOf(draws);
		overlayLineVertexCount = startVertex;
		uploadPluginOverlayTiles(tileBatches);
		uploadPluginObjectOverlayOutlines(objectOutlineBatches);
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayLineVbo);
		if (vertices.size() == 0)
		{
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, 0L, GL33C.GL_DYNAMIC_DRAW);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			rebuildPluginOverlayTextAtlas();
			return;
		}
		FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.size());
		try
		{
			buffer.put(vertices.array(), 0, vertices.size()).flip();
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_DYNAMIC_DRAW);
		}
		finally
		{
			MemoryUtil.memFree(buffer);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
		}
		rebuildPluginOverlayTextAtlas();
	}

	private void uploadPluginOverlayTiles(Map<Integer, OverlayLineBatch> batches)
	{
		FloatList vertices = new FloatList();
		List<OverlayLineDraw> draws = new ArrayList<>();
		int startVertex = 0;
		for (OverlayLineBatch batch : batches.values())
		{
			int vertexCount = batch.vertexCount();
			if (vertexCount <= 0)
			{
				continue;
			}
			draws.add(new OverlayLineDraw(
				startVertex,
				vertexCount,
				channel(batch.argb(), 16),
				channel(batch.argb(), 8),
				channel(batch.argb(), 0),
				channel(batch.argb(), 24)
			));
			vertices.addAll(batch.vertices());
			startVertex += vertexCount;
		}

		overlayTileDraws = List.copyOf(draws);
		overlayTileVertexCount = startVertex;
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayTileVbo);
		if (vertices.size() == 0)
		{
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, 0L, GL33C.GL_DYNAMIC_DRAW);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			return;
		}
		FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.size());
		try
		{
			buffer.put(vertices.array(), 0, vertices.size()).flip();
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_DYNAMIC_DRAW);
		}
		finally
		{
			MemoryUtil.memFree(buffer);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
		}
	}

	private void uploadPluginObjectOverlayOutlines(Map<Integer, OverlayLineBatch> batches)
	{
		FloatList vertices = new FloatList();
		List<OverlayLineDraw> draws = new ArrayList<>();
		int startVertex = 0;
		for (OverlayLineBatch batch : batches.values())
		{
			int vertexCount = batch.vertexCount();
			if (vertexCount <= 0)
			{
				continue;
			}
			draws.add(new OverlayLineDraw(
				startVertex,
				vertexCount,
				channel(batch.argb(), 16),
				channel(batch.argb(), 8),
				channel(batch.argb(), 0),
				channel(batch.argb(), 24)
			));
			vertices.addAll(batch.vertices());
			startVertex += vertexCount;
		}

		overlayObjectOutlineDraws = List.copyOf(draws);
		overlayObjectOutlineVertexCount = startVertex;
		ensurePluginObjectOutlineBuffers();
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayObjectOutlineVbo);
		if (vertices.size() == 0)
		{
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, 0L, GL33C.GL_DYNAMIC_DRAW);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
			return;
		}
		FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.size());
		try
		{
			buffer.put(vertices.array(), 0, vertices.size()).flip();
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_DYNAMIC_DRAW);
		}
		finally
		{
			MemoryUtil.memFree(buffer);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
		}
	}

	private void rebuildPluginOverlayTextAtlas()
	{
		List<OverlayTextCandidate> candidates = overlayTextCandidates();
		if (candidates.isEmpty())
		{
			deleteOverlayTextTexture();
			overlayTextLabels = List.of();
			return;
		}

		Font font = new Font(Font.SANS_SERIF, Font.BOLD, 21);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D probeGraphics = probe.createGraphics();
		FontMetrics metrics;
		try
		{
			probeGraphics.setFont(font);
			metrics = probeGraphics.getFontMetrics();
		}
		finally
		{
			probeGraphics.dispose();
		}

		List<PackedOverlayText> packed = new ArrayList<>();
		int cursorX = 0;
		int cursorY = 0;
		int rowHeight = 0;
		int atlasWidth = 1;
		int maxTextureSize = Math.max(256, GL33C.glGetInteger(GL33C.GL_MAX_TEXTURE_SIZE));
		int atlasMaxWidth = Math.min(OVERLAY_TEXT_ATLAS_MAX_WIDTH, maxTextureSize);
		for (OverlayTextCandidate label : candidates)
		{
			String text = fitOverlayText(label.text(), metrics, atlasMaxWidth - OVERLAY_TEXT_PADDING_X * 2);
			int width = Math.max(1, metrics.stringWidth(text) + OVERLAY_TEXT_PADDING_X * 2);
			int height = Math.max(1, metrics.getHeight() + OVERLAY_TEXT_PADDING_Y * 2);
			if (cursorX > 0 && cursorX + width > atlasMaxWidth)
			{
				cursorY += rowHeight;
				cursorX = 0;
				rowHeight = 0;
			}
			if (cursorY + height > maxTextureSize)
			{
				break;
			}
			packed.add(new PackedOverlayText(label.position(), text, label.color(), cursorX, cursorY, width, height));
			cursorX += width;
			rowHeight = Math.max(rowHeight, height);
			atlasWidth = Math.max(atlasWidth, cursorX);
		}
		if (packed.isEmpty())
		{
			deleteOverlayTextTexture();
			overlayTextLabels = List.of();
			return;
		}

		int atlasHeight = Math.max(1, cursorY + rowHeight);
		BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = atlas.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(font);
			metrics = g.getFontMetrics();
			for (PackedOverlayText label : packed)
			{
				int baseline = label.y() + OVERLAY_TEXT_PADDING_Y + metrics.getAscent();
				g.setColor(Color.BLACK);
				int x = label.x() + OVERLAY_TEXT_PADDING_X;
				for (int dx = -2; dx <= 2; dx++)
				{
					for (int dy = -2; dy <= 2; dy++)
					{
						if (dx == 0 && dy == 0)
						{
							continue;
						}
						g.drawString(label.text(), x + dx, baseline + dy);
					}
				}
				g.setColor(label.color());
				g.drawString(label.text(), label.x() + OVERLAY_TEXT_PADDING_X, baseline);
			}
		}
		finally
		{
			g.dispose();
		}

		uploadOverlayTextAtlas(atlas);
		List<OverlayTextLabel> labels = new ArrayList<>();
		for (PackedOverlayText label : packed)
		{
			labels.add(new OverlayTextLabel(
				new Vector3f(label.position()),
				label.width(),
				label.height(),
				label.x() / (float) atlasWidth,
				label.y() / (float) atlasHeight,
				(label.x() + label.width()) / (float) atlasWidth,
				(label.y() + label.height()) / (float) atlasHeight
			));
		}
		overlayTextLabels = List.copyOf(labels);
	}

	private static String fitOverlayText(String text, FontMetrics metrics, int maxTextWidth)
	{
		if (metrics.stringWidth(text) <= maxTextWidth)
		{
			return text;
		}
		String suffix = "...";
		int suffixWidth = metrics.stringWidth(suffix);
		int end = text.length();
		while (end > 1 && metrics.stringWidth(text.substring(0, end)) + suffixWidth > maxTextWidth)
		{
			end--;
		}
		return text.substring(0, Math.max(1, end)) + suffix;
	}

	private List<OverlayTextCandidate> overlayTextCandidates()
	{
		List<OverlayTextCandidate> candidates = new ArrayList<>();
		Map<Long, Integer> labelStacks = new HashMap<>();
		for (Map3DLabel label : pluginOverlay.labels())
		{
			if (label == null || label.text().isBlank() || !isTilePlaneVisible(label.tile()))
			{
				continue;
			}
			int stackIndex = labelStackIndex(labelStacks, label.tile());
			float heightOffset = OVERLAY_LABEL_HEIGHT_OFFSET
				+ OVERLAY_LABEL_STEM_HEIGHT
				+ stackIndex * OVERLAY_LABEL_STACK_SPACING;
			Vector3f position = tileWorldPosition(label.tile(), heightOffset);
			if (position != null)
			{
				candidates.add(new OverlayTextCandidate(position, label.text(), label.color()));
			}
		}
		for (Map3DTileOverlay overlay : pluginOverlay.tileOverlays())
		{
			if (overlay == null || overlay.label().isBlank() || !isTilePlaneVisible(overlay.tile()))
			{
				continue;
			}
			int stackIndex = labelStackIndex(labelStacks, overlay.tile());
			float heightOffset = OVERLAY_LABEL_HEIGHT_OFFSET + stackIndex * OVERLAY_LABEL_STACK_SPACING;
			Vector3f position = tileWorldPosition(overlay.tile(), heightOffset);
			if (position != null)
			{
				candidates.add(new OverlayTextCandidate(position, overlay.label(), overlay.outlineColor()));
			}
		}
		for (Map3DObjectOverlay overlay : pluginOverlay.objectOverlays())
		{
			if (overlay == null || overlay.label().isBlank() || !isTilePlaneVisible(overlay.tile()))
			{
				continue;
			}
			int stackIndex = labelStackIndex(labelStacks, overlay.tile());
			for (MatchedObjectOverlay match : matchingObjectOverlays(overlay))
			{
				Vector3f position = objectOverlayLabelPosition(match, stackIndex);
				if (position != null)
				{
					candidates.add(new OverlayTextCandidate(position, overlay.label(), overlay.outlineColor()));
					break;
				}
			}
		}
		return candidates;
	}

	private Vector3f objectOverlayLabelPosition(MatchedObjectOverlay match, int stackIndex)
	{
		if (match == null || match.region() == null || match.mesh() == null)
		{
			return null;
		}
		float x = match.region().offsetX() + match.mesh().centerX();
		float y = match.mesh().maxY()
			+ OVERLAY_LABEL_HEIGHT_OFFSET
			+ stackIndex * OVERLAY_LABEL_STACK_SPACING;
		float z = match.region().offsetZ() + match.mesh().centerZ();
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
		{
			return null;
		}
		return new Vector3f(x, y, z);
	}

	private void uploadOverlayTextAtlas(BufferedImage atlas)
	{
		deleteOverlayTextTexture();
		int width = atlas.getWidth();
		int height = atlas.getHeight();
		int[] pixelsArgb = atlas.getRGB(0, 0, width, height, null, 0, width);
		ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
		try
		{
			for (int argb : pixelsArgb)
			{
				pixels.put((byte) (argb >> 16 & 0xFF));
				pixels.put((byte) (argb >> 8 & 0xFF));
				pixels.put((byte) (argb & 0xFF));
				pixels.put((byte) (argb >> 24 & 0xFF));
			}
			pixels.flip();

			overlayTextTexture = GL33C.glGenTextures();
			GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, overlayTextTexture);
			GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, 1);
			GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
			GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
			GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
			GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
			GL33C.glTexImage2D(
				GL33C.GL_TEXTURE_2D,
				0,
				GL33C.GL_RGBA8,
				width,
				height,
				0,
				GL33C.GL_RGBA,
				GL33C.GL_UNSIGNED_BYTE,
				pixels
			);
			GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, 0);
		}
		finally
		{
			MemoryUtil.memFree(pixels);
		}
	}

	private int renderPluginOverlayText(FreeCamera camera)
	{
		if (overlayTextTexture == 0 || overlayTextLabels.isEmpty())
		{
			return 0;
		}
		if (overlayTextVao == 0)
		{
			overlayTextVao = GL33C.glGenVertexArrays();
			overlayTextVbo = GL33C.glGenBuffers();
			GL33C.glBindVertexArray(overlayTextVao);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayTextVbo);
			int stride = OVERLAY_TEXT_FLOATS_PER_VERTEX * Float.BYTES;
			GL33C.glEnableVertexAttribArray(0);
			GL33C.glVertexAttribPointer(0, OVERLAY_LINE_POSITION_FLOATS, GL33C.GL_FLOAT, false, stride, 0L);
			GL33C.glEnableVertexAttribArray(1);
			GL33C.glVertexAttribPointer(
				1,
				OVERLAY_TEXT_UV_FLOATS,
				GL33C.GL_FLOAT,
				false,
				stride,
				OVERLAY_LINE_POSITION_FLOATS * Float.BYTES
			);
			GL33C.glBindVertexArray(0);
		}

		FloatList vertices = overlayTextVertices(camera);
		if (vertices.size() == 0)
		{
			return 0;
		}
		FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.size());
		try
		{
			buffer.put(vertices.array(), 0, vertices.size()).flip();
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayTextVbo);
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_DYNAMIC_DRAW);
		}
		finally
		{
			MemoryUtil.memFree(buffer);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
		}

		GL33C.glUseProgram(textProgram);
		try
		{
			try (MemoryStack stack = MemoryStack.stackPush())
			{
				FloatBuffer matrixBuffer = stack.mallocFloat(16);
				GL33C.glUniformMatrix4fv(textMvpLocation, false, mvp.get(matrixBuffer));
			}
			GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
			GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, overlayTextTexture);
			GL33C.glUniform1i(textTextureLocation, 0);
			GL33C.glBindVertexArray(overlayTextVao);
			int vertexCount = vertices.size() / OVERLAY_TEXT_FLOATS_PER_VERTEX;
			if (vertexCount > 0 && vertexCount * OVERLAY_TEXT_FLOATS_PER_VERTEX == vertices.size())
			{
				GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, vertexCount);
			}
		}
		finally
		{
			GL33C.glBindVertexArray(0);
			GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, 0);
			GL33C.glUseProgram(0);
		}
		return 1;
	}

	private FloatList overlayTextVertices(FreeCamera camera)
	{
		Vector3f direction = camera.direction(new Vector3f());
		if (!isFinite(direction))
		{
			return new FloatList();
		}
		Vector3f right = direction.cross(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f());
		if (!isFinite(right) || right.lengthSquared() < 0.0001f)
		{
			right.set(1.0f, 0.0f, 0.0f);
		}
		else
		{
			right.normalize();
		}
		Vector3f up = new Vector3f(right).cross(direction).normalize();
		if (!isFinite(up))
		{
			return new FloatList();
		}
		FloatList vertices = new FloatList();
		for (OverlayTextLabel label : overlayTextLabels)
		{
			float height = OVERLAY_TEXT_WORLD_HEIGHT;
			float width = height * label.pixelWidth() / Math.max(1.0f, label.pixelHeight());
			Vector3f center = new Vector3f(label.position()).add(0.0f, height * 0.55f, 0.0f);
			Vector3f halfRight = new Vector3f(right).mul(width * 0.5f);
			Vector3f halfUp = new Vector3f(up).mul(height * 0.5f);
			Vector3f bottomLeft = new Vector3f(center).sub(halfRight).sub(halfUp);
			Vector3f bottomRight = new Vector3f(center).add(halfRight).sub(halfUp);
			Vector3f topRight = new Vector3f(center).add(halfRight).add(halfUp);
			Vector3f topLeft = new Vector3f(center).sub(halfRight).add(halfUp);
			if (!isFinite(bottomLeft) || !isFinite(bottomRight) || !isFinite(topRight) || !isFinite(topLeft))
			{
				continue;
			}
			addTextVertex(vertices, bottomLeft, label.u0(), label.v1());
			addTextVertex(vertices, bottomRight, label.u1(), label.v1());
			addTextVertex(vertices, topRight, label.u1(), label.v0());
			addTextVertex(vertices, bottomLeft, label.u0(), label.v1());
			addTextVertex(vertices, topRight, label.u1(), label.v0());
			addTextVertex(vertices, topLeft, label.u0(), label.v0());
		}
		return vertices;
	}

	private void addTextVertex(FloatList vertices, Vector3f position, float u, float v)
	{
		vertices
			.add(position.x).add(position.y).add(position.z)
			.add(u).add(v);
	}

	private void deleteOverlayTextTexture()
	{
		if (overlayTextTexture != 0)
		{
			GL33C.glDeleteTextures(overlayTextTexture);
			overlayTextTexture = 0;
		}
	}

	private OverlayLineBatch overlayBatch(Map<Integer, OverlayLineBatch> batches, Color color)
	{
		int argb = color == null ? 0xFFFFFFFF : color.getRGB();
		return batches.computeIfAbsent(argb, OverlayLineBatch::new);
	}

	private void addPathSegmentLine(OverlayLineBatch batch, Map3DPathSegment segment)
	{
		Tile start = segment.start();
		Tile end = segment.end();
		if (start == null || end == null)
		{
			return;
		}
		double startX = start.x + 0.5;
		double startY = start.y + 0.5;
		double deltaX = end.x - start.x;
		double deltaY = end.y - start.y;
		double tileLength = Math.hypot(deltaX, deltaY);
		if (tileLength <= 0.0001)
		{
			return;
		}
		if (segment.dashed())
		{
			addTransportDirectionArrow(batch, start, end, startX, startY, deltaX, deltaY, tileLength);
			return;
		}
		addSolidPathInterval(batch, start, end, startX, startY, deltaX, deltaY, new LineInterval(0.0, 1.0));
	}

	private void addTransportDirectionArrow(OverlayLineBatch batch, Tile start, Tile end,
	                                        double startX, double startY, double deltaX, double deltaY,
	                                        double tileLength)
	{
		double arrowT = Math.min(1.0, OVERLAY_TRANSPORT_ARROW_LENGTH_TILES / tileLength);
		if (arrowT <= 0.0001)
		{
			return;
		}
		if (tileWorldPosition(start, OVERLAY_PATH_HEIGHT_OFFSET) != null
			&& addTransportDirectionArrow(batch, startX, startY, deltaX, deltaY, tileLength, start.z, 0.0, arrowT))
		{
			return;
		}
		if (tileWorldPosition(end, OVERLAY_PATH_HEIGHT_OFFSET) != null)
		{
			addTransportDirectionArrow(batch, startX, startY, deltaX, deltaY, tileLength, end.z, 1.0 - arrowT, 1.0);
		}
	}

	private boolean addTransportDirectionArrow(OverlayLineBatch batch,
	                                           double startX, double startY,
	                                           double deltaX, double deltaY,
	                                           double tileLength,
	                                           int plane,
	                                           double tailT,
	                                           double tipT)
	{
		Vector3f tail = pathSampleOnPlane(startX, startY, deltaX, deltaY, tailT, plane);
		Vector3f tip = pathSampleOnPlane(startX, startY, deltaX, deltaY, tipT, plane);
		if (tail == null || tip == null)
		{
			return false;
		}

		batch.addLine(tail, tip);
		double directionX = deltaX / tileLength;
		double directionY = deltaY / tileLength;
		double perpendicularX = -directionY;
		double perpendicularY = directionX;
		double tipX = startX + deltaX * tipT;
		double tipY = startY + deltaY * tipT;
		double wingCenterX = tipX - directionX * OVERLAY_TRANSPORT_ARROW_HEAD_LENGTH_TILES;
		double wingCenterY = tipY - directionY * OVERLAY_TRANSPORT_ARROW_HEAD_LENGTH_TILES;
		Vector3f leftWing = worldPositionForWorldTile(
			wingCenterX + perpendicularX * OVERLAY_TRANSPORT_ARROW_HEAD_WIDTH_TILES,
			wingCenterY + perpendicularY * OVERLAY_TRANSPORT_ARROW_HEAD_WIDTH_TILES,
			plane,
			OVERLAY_PATH_HEIGHT_OFFSET
		);
		Vector3f rightWing = worldPositionForWorldTile(
			wingCenterX - perpendicularX * OVERLAY_TRANSPORT_ARROW_HEAD_WIDTH_TILES,
			wingCenterY - perpendicularY * OVERLAY_TRANSPORT_ARROW_HEAD_WIDTH_TILES,
			plane,
			OVERLAY_PATH_HEIGHT_OFFSET
		);
		if (leftWing != null)
		{
			batch.addLine(tip, leftWing);
		}
		if (rightWing != null)
		{
			batch.addLine(tip, rightWing);
		}
		return true;
	}

	private void addSolidPathInterval(OverlayLineBatch batch, Tile start, Tile end,
	                                  double startX, double startY, double deltaX, double deltaY,
	                                  LineInterval interval)
	{
		int steps = pathIntervalSamples(deltaX, deltaY, interval);
		Vector3f previous = null;
		for (int i = 0; i <= steps; i++)
		{
			double t = interpolate(interval.start(), interval.end(), i / (double) steps);
			Vector3f point = pathSample(start, end, startX, startY, deltaX, deltaY, t);
			if (previous != null && point != null)
			{
				batch.addLine(previous, point);
			}
			previous = point;
		}
	}

	private Vector3f pathSample(Tile start, Tile end, double startX, double startY,
	                            double deltaX, double deltaY, double t)
	{
		int plane = t >= 1.0 ? end.z : start.z;
		return pathSampleOnPlane(startX, startY, deltaX, deltaY, t, plane);
	}

	private Vector3f pathSampleOnPlane(double startX, double startY, double deltaX, double deltaY, double t, int plane)
	{
		return worldPositionForWorldTile(
			startX + deltaX * t,
			startY + deltaY * t,
			plane,
			OVERLAY_PATH_HEIGHT_OFFSET
		);
	}

	private int pathIntervalSamples(double deltaX, double deltaY, LineInterval interval)
	{
		double tileDistance = Math.hypot(deltaX, deltaY) * Math.max(0.0, interval.end() - interval.start());
		return Math.max(1, Math.min(MAX_OVERLAY_PATH_INTERVAL_SAMPLES, (int) Math.ceil(tileDistance)));
	}

	private static double interpolate(double start, double end, double amount)
	{
		return start + (end - start) * amount;
	}

	private void addTileHighlightLines(Map<Integer, OverlayLineBatch> batches, Tile tile, Color color)
	{
		TileGeometry geometry = tileGeometry(tile, OVERLAY_MARKER_HEIGHT_OFFSET);
		if (geometry == null)
		{
			return;
		}
		OverlayLineBatch batch = overlayBatch(batches, color);
		batch.addLine(geometry.northWest(), geometry.northEast());
		batch.addLine(geometry.northEast(), geometry.southEast());
		batch.addLine(geometry.southEast(), geometry.southWest());
		batch.addLine(geometry.southWest(), geometry.northWest());
	}

	private void addTileFill(Map<Integer, OverlayLineBatch> batches, Tile tile, Color color)
	{
		Color fill = color == null ? new Color(0x55FFFFFF, true) : color;
		if (fill.getAlpha() <= 0)
		{
			return;
		}
		TileGeometry geometry = tileGeometry(tile, OVERLAY_MARKER_HEIGHT_OFFSET);
		if (geometry == null)
		{
			return;
		}
		OverlayLineBatch batch = overlayBatch(batches, fill);
		batch.addTriangle(geometry.northWest(), geometry.southEast(), geometry.northEast());
		batch.addTriangle(geometry.northWest(), geometry.southWest(), geometry.southEast());
	}

	private void addObjectOverlay(
		Map<Integer, OverlayLineBatch> objectOutlineBatches,
		Map<Integer, OverlayLineBatch> fillBatches,
		Map3DObjectOverlay overlay
	)
	{
		if (overlay == null || !isTilePlaneVisible(overlay.tile()))
		{
			return;
		}
		List<MatchedObjectOverlay> matches = matchingObjectOverlays(overlay);
		if (matches.isEmpty())
		{
			return;
		}
		Color fill = overlay.fillColor();
		if (fill != null && fill.getAlpha() > 0)
		{
			OverlayLineBatch batch = overlayBatch(fillBatches, fill);
			for (MatchedObjectOverlay match : matches)
			{
				addObjectOverlayTriangles(batch, match.region(), match.mesh().rawVertexData());
			}
		}
		Color outline = overlay.outlineColor();
		if (outline != null && outline.getAlpha() > 0)
		{
			OverlayLineBatch batch = overlayBatch(objectOutlineBatches, outline);
			for (MatchedObjectOverlay match : matches)
			{
				batch.addAll(objectOutlineLineVertices(match));
			}
		}
	}

	private List<MatchedObjectOverlay> matchingObjectOverlays(Map3DObjectOverlay overlay)
	{
		if (overlay == null || overlay.tile() == null || !isTilePlaneVisible(overlay.tile()))
		{
			return List.of();
		}
		UploadedRegion region = uploadedRegions.get(regionIdForTile(overlay.tile()));
		if (region == null || !isVisible(region))
		{
			return List.of();
		}

		List<MatchedObjectOverlay> matches = new ArrayList<>();
		for (ObjectOverlayMesh mesh : region.mesh().objectOverlays())
		{
			if (mesh != null && mesh.matches(overlay) && isTilePlaneVisible(mesh.tile()))
			{
				matches.add(new MatchedObjectOverlay(region, mesh));
			}
		}
		return matches.isEmpty() ? List.of() : matches;
	}

	private static int regionIdForTile(Tile tile)
	{
		return TerrainScene.regionId(
			Math.floorDiv(tile.x, TerrainScene.REGION_SIZE),
			Math.floorDiv(tile.y, TerrainScene.REGION_SIZE)
		);
	}

	private static void addObjectOverlayTriangles(OverlayLineBatch batch, UploadedRegion region, float[] vertices)
	{
		for (int i = 0; i + 8 < vertices.length; i += 9)
		{
			batch.addTriangle(
				region.offsetX() + vertices[i],
				vertices[i + 1],
				region.offsetZ() + vertices[i + 2],
				region.offsetX() + vertices[i + 3],
				vertices[i + 4],
				region.offsetZ() + vertices[i + 5],
				region.offsetX() + vertices[i + 6],
				vertices[i + 7],
				region.offsetZ() + vertices[i + 8]
			);
		}
	}

	private void addLabelAnchorLines(Map<Integer, OverlayLineBatch> batches, Tile tile, int stackIndex)
	{
		Vector3f base = tileWorldPosition(tile, OVERLAY_LABEL_HEIGHT_OFFSET + stackIndex * OVERLAY_LABEL_STACK_SPACING);
		if (base == null)
		{
			return;
		}
		OverlayLineBatch batch = overlayBatch(batches, new Color(OVERLAY_TRANSPORT_ICON_RGB));
		Vector3f top = new Vector3f(base).add(0.0f, OVERLAY_LABEL_STEM_HEIGHT, 0.0f);
		Vector3f flagA = new Vector3f(top).add(OVERLAY_LABEL_FLAG_WIDTH, -OVERLAY_LABEL_STEM_HEIGHT * 0.18f, 0.0f);
		Vector3f flagB = new Vector3f(top).add(0.0f, -OVERLAY_LABEL_STEM_HEIGHT * 0.36f, 0.0f);
		batch.addLine(base, top);
		batch.addLine(top, flagA);
		batch.addLine(flagA, flagB);
		batch.addLine(flagB, top);
	}

	private static int labelStackIndex(Map<Long, Integer> labelStacks, Tile tile)
	{
		if (tile == null)
		{
			return 0;
		}
		long key = labelStackKey(tile);
		int index = labelStacks.getOrDefault(key, 0);
		labelStacks.put(key, index + 1);
		return index;
	}

	private static long labelStackKey(Tile tile)
	{
		long x = tile.x & 0x1FFFFFL;
		long y = tile.y & 0x1FFFFFL;
		long z = tile.z & 0x3L;
		return z << 42 | x << 21 | y;
	}

	private Vector3f tileWorldPosition(Tile tile, float heightOffset)
	{
		if (tile == null || currentScene == null || !isTilePlaneVisible(tile))
		{
			return null;
		}
		return worldPositionForWorldTile(tile.x + 0.5, tile.y + 0.5, tile.z, heightOffset);
	}

	private Vector3f worldPositionForWorldTile(double worldTileX, double worldTileY, int plane, float heightOffset)
	{
		if (currentScene == null || !isPlaneVisible(plane))
		{
			return null;
		}
		int tileX = (int) Math.floor(worldTileX);
		int tileY = (int) Math.floor(worldTileY);
		int regionId = TerrainScene.regionId(
			Math.floorDiv(tileX, TerrainScene.REGION_SIZE),
			Math.floorDiv(tileY, TerrainScene.REGION_SIZE)
		);
		TerrainMesh mesh = currentScene.mesh(regionId);
		if (mesh == null || !isUploadedRenderableRegionVisible(regionId))
		{
			return null;
		}
		float localX = Math.floorMod(tileX, TerrainScene.REGION_SIZE) + (float) (worldTileX - tileX);
		float localY = Math.floorMod(tileY, TerrainScene.REGION_SIZE) + (float) (worldTileY - tileY);
		return worldPosition(mesh, plane, localX, localY, heightOffset);
	}

	private TileGeometry tileGeometry(Tile tile, float heightOffset)
	{
		if (tile == null || currentScene == null || !isTilePlaneVisible(tile))
		{
			return null;
		}
		int regionId = TerrainScene.regionId(
			Math.floorDiv(tile.x, TerrainScene.REGION_SIZE),
			Math.floorDiv(tile.y, TerrainScene.REGION_SIZE)
		);
		TerrainMesh mesh = currentScene.mesh(regionId);
		if (mesh == null || !isUploadedRenderableRegionVisible(regionId))
		{
			return null;
		}
		float localX = Math.floorMod(tile.x, TerrainScene.REGION_SIZE);
		float localY = Math.floorMod(tile.y, TerrainScene.REGION_SIZE);
		int plane = Math.max(0, Math.min(3, tile.z));
		return new TileGeometry(
			worldPosition(mesh, plane, localX, localY, heightOffset),
			worldPosition(mesh, plane, localX + 1.0f, localY, heightOffset),
			worldPosition(mesh, plane, localX + 1.0f, localY + 1.0f, heightOffset),
			worldPosition(mesh, plane, localX, localY + 1.0f, heightOffset)
		);
	}

	private Vector3f worldPosition(TerrainMesh mesh, int plane, float localX, float localY, float heightOffset)
	{
		int clampedPlane = Math.max(0, Math.min(3, plane));
		float x = currentScene.offsetX(mesh) + SceneScale.worldXFromTile(localX);
		float z = currentScene.offsetZ(mesh) + SceneScale.worldZFromTile(localY);
		float y = mesh.worldHeightAt(clampedPlane, localX, localY) + heightOffset;
		if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
		{
			return null;
		}
		return new Vector3f(x, y, z);
	}

	private boolean isUploadedRenderableRegionVisible(int regionId)
	{
		UploadedRegion region = uploadedRegions.get(regionId);
		return region != null
			&& (region.vertexCount() > 0 || !region.animatedObjects().isEmpty() || !region.npcMeshes().isEmpty())
			&& isVisible(region);
	}

	private boolean isTilePlaneVisible(Tile tile)
	{
		return tile != null && isPlaneVisible(tile.z);
	}

	private static float channel(int argb, int shift)
	{
		return (argb >> shift & 0xFF) / 255.0f;
	}

	private static boolean isFinite(Vector3f vector)
	{
		return vector != null
			&& Float.isFinite(vector.x)
			&& Float.isFinite(vector.y)
			&& Float.isFinite(vector.z);
	}

	private static boolean isFinite(float x, float y, float z)
	{
		return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
	}

	private static NpcOutlineGeometry buildNpcOutlineGeometry(float[] vertexData, int vertexCount)
	{
		if (vertexData == null || vertexData.length == 0 || vertexCount < 3)
		{
			return NpcOutlineGeometry.EMPTY;
		}
		FloatList data = new FloatList();
		int stride = TerrainMesh.FLOATS_PER_VERTEX;
		int maxFloats = Math.min(vertexData.length, vertexCount * stride);
		for (int offset = 0; offset + stride * 3 <= maxFloats; offset += stride * 3)
		{
			int a = offset;
			int b = offset + stride;
			int c = offset + stride * 2;
			if (!finiteVertex(vertexData, a) || !finiteVertex(vertexData, b) || !finiteVertex(vertexData, c))
			{
				continue;
			}
			data
				.add(vertexData[a]).add(vertexData[a + 1]).add(vertexData[a + 2])
				.add(vertexData[b]).add(vertexData[b + 1]).add(vertexData[b + 2])
				.add(vertexData[c]).add(vertexData[c + 1]).add(vertexData[c + 2]);
		}
		return data.size() == 0 ? NpcOutlineGeometry.EMPTY : new NpcOutlineGeometry(data.array());
	}

	private static boolean finiteVertex(float[] vertexData, int offset)
	{
		return offset >= 0
			&& offset + 2 < vertexData.length
			&& Float.isFinite(vertexData[offset])
			&& Float.isFinite(vertexData[offset + 1])
			&& Float.isFinite(vertexData[offset + 2]);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static boolean validDrawRange(OverlayLineDraw draw, int vertexCount, int primitiveVertices)
	{
		return draw != null
			&& validDrawRange(draw.startVertex(), draw.vertexCount(), vertexCount, primitiveVertices);
	}

	private static boolean validDrawRange(int startVertex, int drawVertexCount, int vertexCount, int primitiveVertices)
	{
		return primitiveVertices > 0
			&& startVertex >= 0
			&& drawVertexCount > 0
			&& drawVertexCount % primitiveVertices == 0
			&& startVertex <= vertexCount
			&& drawVertexCount <= vertexCount - startVertex;
	}

	private float fogEndDistance()
	{
		return Math.min(SceneScale.CAMERA_FAR_PLANE - 12.0f, 74.0f + viewDistanceRegions * 22.0f);
	}

	private void updateRenderStats(RenderCounts renderCounts, int outlineDrawCalls)
	{
		renderStats = new TerrainRenderStats(
			renderCounts.drawCalls() + outlineDrawCalls,
			renderCounts.visibleRegions(),
			renderCounts.culledRegions(),
			uploadedRegions.size(),
			pendingUploadCount(),
			renderCounts.verticesDrawn(),
			uploadedTextureLayerCount,
			glVendor,
			glRenderer,
			glVersion
		);
	}

	private void applyScene(TerrainScene scene)
	{
		Set<Integer> regionIds = scene.regionIds();
		if (activeUploadTask != null
			&& (!regionIds.contains(activeUploadTask.regionId())
				|| scene.mesh(activeUploadTask.regionId()) != activeUploadTask.mesh()))
		{
			activeUploadTask.cancel(true);
			activeUploadTask = null;
		}
		pendingUploadRegions.entrySet().removeIf(entry -> {
			TerrainMesh currentMesh = scene.mesh(entry.getKey());
			if (currentMesh == entry.getValue())
			{
				return false;
			}
			entry.getValue().releaseVertexData();
			return true;
		});
		uploadedRegions.entrySet().removeIf(entry -> {
			if (regionIds.contains(entry.getKey()))
			{
				return false;
			}
			entry.getValue().delete();
			return true;
		});

		int sceneLayerCount = scene.textureSet().layerCount();
		if (terrainTextureArray == 0 || uploadedTextureLayerCount <= 1 && sceneLayerCount > 1)
		{
			if (terrainTextureArray != 0)
			{
				GL33C.glDeleteTextures(terrainTextureArray);
				terrainTextureArray = 0;
				uploadedTextureLayerCount = 0;
			}
			uploadTextureSet(scene.textureSet());
		}

		for (TerrainMesh mesh : scene.meshes())
		{
			UploadedRegion uploadedRegion = uploadedRegions.get(mesh.regionId());
			if (mesh.vertexCount() == 0 && mesh.animatedObjects().isEmpty() && mesh.npcMeshes().isEmpty())
			{
				if (uploadedRegion == null)
				{
					uploadedRegions.put(
						mesh.regionId(),
						UploadedRegion.empty(mesh, scene.offsetX(mesh), scene.offsetZ(mesh))
					);
				}
				continue;
			}

			if (uploadedRegion == null
				|| uploadedRegion.mesh() != mesh
				|| uploadedRegion.vertexCount() == 0
				&& uploadedRegion.animatedObjects().isEmpty()
				&& uploadedRegion.npcMeshes().isEmpty())
			{
				pendingUploadRegions.put(mesh.regionId(), mesh);
			}
		}
		currentScene = scene;
		pluginOverlayDirty = true;
	}

	private void uploadPendingRegions()
	{
		if (currentScene == null)
		{
			return;
		}
		if (activeUploadTask != null || !pendingUploadRegions.isEmpty())
		{
			deferRetainedDataCompaction();
		}

		if (activeUploadTask == null)
		{
			startNextUploadTask();
		}
		if (activeUploadTask == null)
		{
			return;
		}

		UploadBudget budget = new UploadBudget(
			MAX_UPLOAD_FLOATS_PER_FRAME,
			MAX_UPLOAD_CHUNK_FLOATS,
			System.nanoTime() + MAX_UPLOAD_NANOS_PER_FRAME
		);
		if (activeUploadTask.uploadChunk(budget))
		{
			UploadedRegion uploadedRegion = activeUploadTask.finish();
			scheduleStaticCompaction(activeUploadTask.mesh());
			UploadedRegion previous = uploadedRegions.put(uploadedRegion.regionId(), uploadedRegion);
			if (previous != null)
			{
				previous.delete();
			}
			pluginOverlayDirty = true;
			activeUploadTask = null;
		}
	}

	private void scheduleStaticCompaction(TerrainMesh mesh)
	{
		if (mesh == null)
		{
			return;
		}
		scheduleRetainedDataCompaction(() -> mesh.compactStaticVertexData(this::waitForRetainedDataCompactionSlot));
	}

	private void scheduleFrameCompaction(AnimatedObjectMesh.Frame frame)
	{
		if (frame == null)
		{
			return;
		}
		scheduleRetainedDataCompaction(() -> frame.compactVertexData(this::waitForRetainedDataCompactionSlot));
	}

	private void scheduleRetainedDataCompaction(Runnable task)
	{
		if (task == null || retainedDataCompactor.isShutdown())
		{
			return;
		}
		try
		{
			retainedDataCompactor.execute(() -> {
				try
				{
					waitForRetainedDataCompactionSlot();
					task.run();
				}
				catch (RetainedDataCompactionCancelled ignored)
				{
					// Shutdown or an interrupted worker abandoned opportunistic retained-data compaction.
				}
			});
		}
		catch (RejectedExecutionException ignored)
		{
			// Renderer shutdown won the race; the mesh will be released by normal scene disposal.
		}
	}

	private void deferRetainedDataCompactionUntil(long nanos)
	{
		long current = retainedDataCompactionDeferredUntilNanos;
		if (nanos > current)
		{
			retainedDataCompactionDeferredUntilNanos = nanos;
		}
	}

	private void waitForRetainedDataCompactionSlot()
	{
		while (true)
		{
			if (Thread.currentThread().isInterrupted() || retainedDataCompactor.isShutdown())
			{
				throw new RetainedDataCompactionCancelled();
			}

			long pauseNanos = retainedDataCompactionDeferredUntilNanos - System.nanoTime();
			if (pauseNanos <= 0L)
			{
				return;
			}
			LockSupport.parkNanos(Math.min(pauseNanos, COMPACTION_PAUSE_SLICE_NANOS));
		}
	}

	private void startNextUploadTask()
	{
		while (!pendingUploadRegions.isEmpty())
		{
			Map.Entry<Integer, TerrainMesh> entry = pendingUploadRegions.entrySet().iterator().next();
			pendingUploadRegions.remove(entry.getKey());
			TerrainMesh mesh = currentScene.mesh(entry.getKey());
			if (mesh == null || mesh != entry.getValue())
			{
				entry.getValue().releaseVertexData();
				continue;
			}

			activeUploadTask = new RegionUploadTask(currentScene, mesh);
			return;
		}
	}

	private UploadedRegion uploadedRegion(
		TerrainScene scene,
		TerrainMesh mesh,
		int vao,
		int vbo,
		List<UploadedAnimatedObject> animatedObjects,
		List<UploadedNpcMesh> npcMeshes
	)
	{
		float offsetX = scene.offsetX(mesh);
		float offsetZ = scene.offsetZ(mesh);
		float minX = offsetX - SceneScale.REGION_CENTER_TILES;
		float maxX = offsetX + SceneScale.REGION_CENTER_TILES;
		float minZ = offsetZ - SceneScale.REGION_CENTER_TILES;
		float maxZ = offsetZ + SceneScale.REGION_CENTER_TILES;
		return new UploadedRegion(
			mesh,
			mesh.regionId(),
			vao,
			vbo,
			mesh.vertexCount(),
			planeStartVertices(mesh),
			planeVertexCounts(mesh),
			planeTransparentStartVertices(mesh),
			planeTransparentVertexCounts(mesh),
			offsetX,
			offsetZ,
			minX,
			mesh.minY(),
			minZ,
			maxX,
			mesh.maxY(),
			maxZ,
			animatedObjects,
			npcMeshes
		);
	}

	private static int[] planeStartVertices(TerrainMesh mesh)
	{
		int[] starts = new int[MAX_VISIBLE_PLANE + 1];
		for (int plane = 0; plane < starts.length; plane++)
		{
			starts[plane] = mesh.planeStartVertex(plane);
		}
		return starts;
	}

	private static int[] planeVertexCounts(TerrainMesh mesh)
	{
		int[] counts = new int[MAX_VISIBLE_PLANE + 1];
		for (int plane = 0; plane < counts.length; plane++)
		{
			counts[plane] = mesh.planeVertexCount(plane);
		}
		return counts;
	}

	private static int[] planeTransparentStartVertices(TerrainMesh mesh)
	{
		int[] starts = new int[MAX_VISIBLE_PLANE + 1];
		for (int plane = 0; plane < starts.length; plane++)
		{
			starts[plane] = mesh.planeTransparentStartVertex(plane);
		}
		return starts;
	}

	private static int[] planeTransparentVertexCounts(TerrainMesh mesh)
	{
		int[] counts = new int[MAX_VISIBLE_PLANE + 1];
		for (int plane = 0; plane < counts.length; plane++)
		{
			counts[plane] = mesh.planeTransparentVertexCount(plane);
		}
		return counts;
	}

	private static int[] normalizedPlaneArray(int[] values)
	{
		int[] out = new int[MAX_VISIBLE_PLANE + 1];
		if (values != null)
		{
			System.arraycopy(values, 0, out, 0, Math.min(out.length, values.length));
		}
		return out;
	}

	private void installTerrainAttributes()
	{
		int stride = TerrainMesh.FLOATS_PER_VERTEX * Float.BYTES;
		GL33C.glEnableVertexAttribArray(0);
		GL33C.glVertexAttribPointer(0, POSITION_FLOATS, GL33C.GL_FLOAT, false, stride, 0L);
		GL33C.glEnableVertexAttribArray(1);
		GL33C.glVertexAttribPointer(
			1,
			NORMAL_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			POSITION_FLOATS * Float.BYTES
		);
		GL33C.glEnableVertexAttribArray(2);
		GL33C.glVertexAttribPointer(
			2,
			COLOR_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			(POSITION_FLOATS + NORMAL_FLOATS) * Float.BYTES
		);
		GL33C.glEnableVertexAttribArray(3);
		GL33C.glVertexAttribPointer(
			3,
			ALPHA_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			(POSITION_FLOATS + NORMAL_FLOATS + COLOR_FLOATS) * Float.BYTES
		);
		GL33C.glEnableVertexAttribArray(4);
		GL33C.glVertexAttribPointer(
			4,
			DEPTH_BIAS_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			(POSITION_FLOATS + NORMAL_FLOATS + COLOR_FLOATS + ALPHA_FLOATS) * Float.BYTES
		);
		GL33C.glEnableVertexAttribArray(5);
		GL33C.glVertexAttribPointer(
			5,
			UV_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			(POSITION_FLOATS + NORMAL_FLOATS + COLOR_FLOATS + ALPHA_FLOATS + DEPTH_BIAS_FLOATS) * Float.BYTES
		);
		GL33C.glEnableVertexAttribArray(6);
		GL33C.glVertexAttribPointer(
			6,
			TEXTURE_LAYER_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			(POSITION_FLOATS + NORMAL_FLOATS + COLOR_FLOATS + ALPHA_FLOATS + DEPTH_BIAS_FLOATS + UV_FLOATS)
				* Float.BYTES
		);
		GL33C.glEnableVertexAttribArray(7);
		GL33C.glVertexAttribPointer(
			7,
			TEXTURE_ANIMATION_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			(POSITION_FLOATS
				+ NORMAL_FLOATS
				+ COLOR_FLOATS
				+ ALPHA_FLOATS
				+ DEPTH_BIAS_FLOATS
				+ UV_FLOATS
				+ TEXTURE_LAYER_FLOATS) * Float.BYTES
		);
		GL33C.glEnableVertexAttribArray(8);
		GL33C.glVertexAttribPointer(
			8,
			TEXTURE_ANIMATION_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			(POSITION_FLOATS
				+ NORMAL_FLOATS
				+ COLOR_FLOATS
				+ ALPHA_FLOATS
				+ DEPTH_BIAS_FLOATS
				+ UV_FLOATS
				+ TEXTURE_LAYER_FLOATS
				+ TEXTURE_ANIMATION_FLOATS) * Float.BYTES
		);
		GL33C.glEnableVertexAttribArray(9);
		GL33C.glVertexAttribPointer(
			9,
			TEXTURE_ALPHA_CUTOFF_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			(POSITION_FLOATS
				+ NORMAL_FLOATS
				+ COLOR_FLOATS
				+ ALPHA_FLOATS
				+ DEPTH_BIAS_FLOATS
				+ UV_FLOATS
				+ TEXTURE_LAYER_FLOATS
				+ TEXTURE_ANIMATION_FLOATS
				+ TEXTURE_ANIMATION_FLOATS) * Float.BYTES
		);
	}

	private void uploadTextureSet(SceneTextureSet textureSet)
	{
		int requestedLayers = Math.max(1, textureSet.layerCount());
		int maxLayers = GL33C.glGetInteger(GL33C.GL_MAX_ARRAY_TEXTURE_LAYERS);
		int layers = Math.min(requestedLayers, maxLayers);
		if (layers < requestedLayers)
		{
			System.err.println(
				"3D texture layers capped at " + layers + " by GL_MAX_ARRAY_TEXTURE_LAYERS; extra textures use color fallback."
			);
		}

		int textureSize = SceneTextureSet.TEXTURE_SIZE;
		int[] pixelsArgb = textureSet.pixelsArgb();
		ByteBuffer pixels = MemoryUtil.memAlloc(textureSize * textureSize * layers * 4);
		try
		{
			for (int i = 0; i < textureSize * textureSize * layers; i++)
			{
				int argb = pixelsArgb[i];
				pixels.put((byte) (argb >> 16 & 0xFF));
				pixels.put((byte) (argb >> 8 & 0xFF));
				pixels.put((byte) (argb & 0xFF));
				pixels.put((byte) (argb >> 24 & 0xFF));
			}
			pixels.flip();

			terrainTextureArray = GL33C.glGenTextures();
			GL33C.glBindTexture(GL33C.GL_TEXTURE_2D_ARRAY, terrainTextureArray);
			GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, 1);
			GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D_ARRAY, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
			GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D_ARRAY, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
			GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D_ARRAY, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_REPEAT);
			GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D_ARRAY, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_REPEAT);
			GL33C.glTexImage3D(
				GL33C.GL_TEXTURE_2D_ARRAY,
				0,
				GL33C.GL_RGBA8,
				textureSize,
				textureSize,
				layers,
				0,
				GL33C.GL_RGBA,
				GL33C.GL_UNSIGNED_BYTE,
				pixels
			);
			GL33C.glBindTexture(GL33C.GL_TEXTURE_2D_ARRAY, 0);
			uploadedTextureLayerCount = layers;
		}
		finally
		{
			MemoryUtil.memFree(pixels);
		}
	}

	private int prepareRenderTarget(int width, int height, int defaultFramebuffer)
	{
		int requestedSamples = Math.max(0, antialiasingSamples);
		int maxSamples = Math.max(0, GL33C.glGetInteger(GL33C.GL_MAX_SAMPLES));
		int samples = Math.min(requestedSamples, maxSamples);
		if (samples <= 0)
		{
			deleteSceneFbo();
			GL33C.glDisable(GL33C.GL_MULTISAMPLE);
			return defaultFramebuffer;
		}

		GL33C.glEnable(GL33C.GL_MULTISAMPLE);
		if (sceneFbo != 0 && sceneFboWidth == width && sceneFboHeight == height && sceneFboSamples == samples)
		{
			return sceneFbo;
		}

		deleteSceneFbo();
		sceneFbo = GL33C.glGenFramebuffers();
		sceneColorRbo = GL33C.glGenRenderbuffers();
		sceneDepthRbo = GL33C.glGenRenderbuffers();

		GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, sceneFbo);
		GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, sceneColorRbo);
		GL33C.glRenderbufferStorageMultisample(GL33C.GL_RENDERBUFFER, samples, GL33C.GL_RGBA8, width, height);
		GL33C.glFramebufferRenderbuffer(
			GL33C.GL_FRAMEBUFFER,
			GL33C.GL_COLOR_ATTACHMENT0,
			GL33C.GL_RENDERBUFFER,
			sceneColorRbo
		);

		GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, sceneDepthRbo);
		GL33C.glRenderbufferStorageMultisample(
			GL33C.GL_RENDERBUFFER,
			samples,
			GL33C.GL_DEPTH_COMPONENT24,
			width,
			height
		);
		GL33C.glFramebufferRenderbuffer(
			GL33C.GL_FRAMEBUFFER,
			GL33C.GL_DEPTH_ATTACHMENT,
			GL33C.GL_RENDERBUFFER,
			sceneDepthRbo
		);
		GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, 0);

		int status = GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER);
		if (status != GL33C.GL_FRAMEBUFFER_COMPLETE)
		{
			System.err.println("3D multisample framebuffer incomplete: " + status + ". Rendering without AA.");
			deleteSceneFbo();
			GL33C.glDisable(GL33C.GL_MULTISAMPLE);
			antialiasingSamples = 0;
			GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, defaultFramebuffer);
			return defaultFramebuffer;
		}

		sceneFboWidth = width;
		sceneFboHeight = height;
		sceneFboSamples = samples;
		return sceneFbo;
	}

	private void blitRenderTarget(int defaultFramebuffer, int width, int height)
	{
		GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, sceneFbo);
		GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, defaultFramebuffer);
		GL33C.glBlitFramebuffer(
			0,
			0,
			width,
			height,
			0,
			0,
			width,
			height,
			GL33C.GL_COLOR_BUFFER_BIT,
			GL33C.GL_NEAREST
		);
		GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, defaultFramebuffer);
		GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, defaultFramebuffer);
	}

	private void deleteSceneFbo()
	{
		if (sceneFbo != 0)
		{
			GL33C.glDeleteFramebuffers(sceneFbo);
			sceneFbo = 0;
		}
		if (sceneColorRbo != 0)
		{
			GL33C.glDeleteRenderbuffers(sceneColorRbo);
			sceneColorRbo = 0;
		}
		if (sceneDepthRbo != 0)
		{
			GL33C.glDeleteRenderbuffers(sceneDepthRbo);
			sceneDepthRbo = 0;
		}
		sceneFboWidth = -1;
		sceneFboHeight = -1;
		sceneFboSamples = -1;
	}

	private void deleteUploadedRegions()
	{
		if (activeUploadTask != null)
		{
			activeUploadTask.cancel(false);
			activeUploadTask = null;
		}
		pendingUploadRegions.clear();
		for (UploadedRegion region : uploadedRegions.values())
		{
			region.delete();
		}
		uploadedRegions.clear();
	}

	private int pendingUploadCount()
	{
		return pendingUploadRegions.size() + (activeUploadTask == null ? 0 : 1);
	}

	private boolean isVisible(UploadedRegion region)
	{
		return frustum.testAab(
			region.minX() - REGION_CULL_PADDING,
			region.minY() - REGION_CULL_PADDING,
			region.minZ() - REGION_CULL_PADDING,
			region.maxX() + REGION_CULL_PADDING,
			region.maxY() + REGION_CULL_PADDING,
			region.maxZ() + REGION_CULL_PADDING
		);
	}

	private float[] outlineVertices()
	{
		TerrainMesh mesh = currentScene.mesh(hoveredTile.regionId());
		if (mesh == null)
		{
			return new float[0];
		}

		float x0 = hoveredTile.localX();
		float y0 = hoveredTile.localY();
		float x1 = x0 + 1.0f;
		float y1 = y0 + 1.0f;
		float offsetX = currentScene.offsetX(mesh);
		float offsetZ = currentScene.offsetZ(mesh);
		float worldX0 = offsetX + SceneScale.worldXFromTile(x0);
		float worldX1 = offsetX + SceneScale.worldXFromTile(x1);
		float worldZ0 = offsetZ + SceneScale.worldZFromTile(y0);
		float worldZ1 = offsetZ + SceneScale.worldZFromTile(y1);
		float h00 = mesh.worldHeightAt(hoveredTile.plane(), x0, y0) + OUTLINE_HEIGHT_OFFSET;
		float h10 = mesh.worldHeightAt(hoveredTile.plane(), x1, y0) + OUTLINE_HEIGHT_OFFSET;
		float h11 = mesh.worldHeightAt(hoveredTile.plane(), x1, y1) + OUTLINE_HEIGHT_OFFSET;
		float h01 = mesh.worldHeightAt(hoveredTile.plane(), x0, y1) + OUTLINE_HEIGHT_OFFSET;

		return new float[]{
			worldX0, h00, worldZ0,
			worldX1, h10, worldZ0,
			worldX1, h10, worldZ0,
			worldX1, h11, worldZ1,
			worldX1, h11, worldZ1,
			worldX0, h01, worldZ1,
			worldX0, h01, worldZ1,
			worldX0, h00, worldZ0
		};
	}

	private static final class OverlayLineBatch
	{
		private final int argb;
		private final FloatList vertices = new FloatList();

		private OverlayLineBatch(int argb)
		{
			this.argb = argb;
		}

		private int argb()
		{
			return argb;
		}

		private float[] vertices()
		{
			return vertices.array();
		}

		private int vertexCount()
		{
			return vertices.size() / OVERLAY_LINE_POSITION_FLOATS;
		}

		private void addLine(Vector3f start, Vector3f end)
		{
			if (!isFinite(start) || !isFinite(end) || vertexCount() + 2 > MAX_OVERLAY_LINE_VERTICES_PER_BATCH)
			{
				return;
			}
			addLine(start.x, start.y, start.z, end.x, end.y, end.z);
		}

		private void addLine(float startX, float startY, float startZ, float endX, float endY, float endZ)
		{
			if (!isFinite(startX, startY, startZ) || !isFinite(endX, endY, endZ)
				|| vertexCount() + 2 > MAX_OVERLAY_LINE_VERTICES_PER_BATCH)
			{
				return;
			}
			vertices.add(startX).add(startY).add(startZ);
			vertices.add(endX).add(endY).add(endZ);
		}

		private void addTriangle(Vector3f a, Vector3f b, Vector3f c)
		{
			if (!isFinite(a) || !isFinite(b) || !isFinite(c)
				|| vertexCount() + 3 > MAX_OVERLAY_LINE_VERTICES_PER_BATCH)
			{
				return;
			}
			vertices.add(a.x).add(a.y).add(a.z);
			vertices.add(b.x).add(b.y).add(b.z);
			vertices.add(c.x).add(c.y).add(c.z);
		}

		private void addTriangle(
			float ax,
			float ay,
			float az,
			float bx,
			float by,
			float bz,
			float cx,
			float cy,
			float cz
		)
		{
			if (!isFinite(ax, ay, az) || !isFinite(bx, by, bz) || !isFinite(cx, cy, cz)
				|| vertexCount() + 3 > MAX_OVERLAY_LINE_VERTICES_PER_BATCH)
			{
				return;
			}
			vertices.add(ax).add(ay).add(az);
			vertices.add(bx).add(by).add(bz);
			vertices.add(cx).add(cy).add(cz);
		}

		private void addAll(FloatList source)
		{
			if (source == null || source.size() == 0)
			{
				return;
			}
			int sourceVertexCount = source.size() / OVERLAY_LINE_POSITION_FLOATS;
			if (sourceVertexCount <= 0
				|| sourceVertexCount * OVERLAY_LINE_POSITION_FLOATS != source.size()
				|| vertexCount() + sourceVertexCount > MAX_OVERLAY_LINE_VERTICES_PER_BATCH)
			{
				return;
			}
			vertices.addAll(source.array());
		}
	}

	private static final class FloatList
	{
		private float[] values = new float[128];
		private int size;

		private FloatList add(float value)
		{
			ensureCapacity(size + 1);
			values[size++] = value;
			return this;
		}

		private void addAll(float[] source)
		{
			ensureCapacity(size + source.length);
			System.arraycopy(source, 0, values, size, source.length);
			size += source.length;
		}

		private float[] array()
		{
			float[] copy = new float[size];
			System.arraycopy(values, 0, copy, 0, size);
			return copy;
		}

		private int size()
		{
			return size;
		}

		private void ensureCapacity(int minimum)
		{
			if (minimum <= values.length)
			{
				return;
			}
			int next = values.length;
			while (next < minimum)
			{
				next *= 2;
			}
			float[] expanded = new float[next];
			System.arraycopy(values, 0, expanded, 0, size);
			values = expanded;
		}
	}

	private record OverlayLineDraw(
		int startVertex,
		int vertexCount,
		float red,
		float green,
		float blue,
		float alpha
	)
	{
	}

	private record MatchedObjectOverlay(
		UploadedRegion region,
		ObjectOverlayMesh mesh
	)
	{
	}

	private record ProjectedTriangle(
		float ax,
		float ay,
		float az,
		float bx,
		float by,
		float bz,
		float cx,
		float cy,
		float cz,
		float minX,
		float minY,
		float maxX,
		float maxY,
		float depthPlaneX,
		float depthPlaneY,
		float depthPlaneOffset
	)
	{
		private ProjectedTriangle(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz)
		{
			this(
				ax,
				ay,
				az,
				bx,
				by,
				bz,
				cx,
				cy,
				cz,
				Math.min(ax, Math.min(bx, cx)),
				Math.min(ay, Math.min(by, cy)),
				Math.max(ax, Math.max(bx, cx)),
				Math.max(ay, Math.max(by, cy)),
				depthPlaneX(ax, ay, az, bx, by, bz, cx, cy, cz),
				depthPlaneY(ax, ay, az, bx, by, bz, cx, cy, cz),
				depthPlaneOffset(ax, ay, az, bx, by, bz, cx, cy, cz)
			);
		}

		private float depthAt(float x, float y)
		{
			return depthPlaneX * x + depthPlaneY * y + depthPlaneOffset;
		}

		private static float determinant(float ax, float ay, float bx, float by, float cx, float cy)
		{
			return ax * (by - cy) + bx * (cy - ay) + cx * (ay - by);
		}

		private static float depthPlaneX(float ax, float ay, float az, float bx, float by, float bz,
		                                 float cx, float cy, float cz)
		{
			float inverse = 1.0f / determinant(ax, ay, bx, by, cx, cy);
			return (az * (by - cy) + bz * (cy - ay) + cz * (ay - by)) * inverse;
		}

		private static float depthPlaneY(float ax, float ay, float az, float bx, float by, float bz,
		                                 float cx, float cy, float cz)
		{
			float inverse = 1.0f / determinant(ax, ay, bx, by, cx, cy);
			return (az * (cx - bx) + bz * (ax - cx) + cz * (bx - ax)) * inverse;
		}

		private static float depthPlaneOffset(float ax, float ay, float az, float bx, float by, float bz,
		                                      float cx, float cy, float cz)
		{
			float inverse = 1.0f / determinant(ax, ay, bx, by, cx, cy);
			return (az * (bx * cy - cx * by) + bz * (cx * ay - ax * cy) + cz * (ax * by - bx * ay)) * inverse;
		}
	}

	private record ProjectedOutline(
		int originX,
		int originY,
		int width,
		int height,
		float[] depth,
		boolean[] coverage
	)
	{
	}

	private record NpcOutlineGeometry(float[] triangleData)
	{
		private static final NpcOutlineGeometry EMPTY = new NpcOutlineGeometry(new float[0]);

		private NpcOutlineGeometry
		{
			triangleData = triangleData == null ? new float[0] : triangleData;
		}

		private boolean isEmpty()
		{
			return triangleData.length == 0;
		}
	}

	private record HoverRay(
		Vector3f origin,
		Vector3f direction
	)
	{
	}

	record NpcHoverInfo(
		String name,
		int combatLevel,
		int npcId,
		String spawnName,
		int spawnWorldX,
		int spawnWorldY,
		int spawnPlane,
		Integer faceDirection,
		Boolean walkEnabled,
		boolean currentlyWalkingEnabled,
		NpcSpawnIndex.SpawnSource source
	)
	{
		boolean hasCombatLevel()
		{
			return combatLevel > 0;
		}

		boolean customSource()
		{
			return source == NpcSpawnIndex.SpawnSource.TSV;
		}
	}

	record NpcMapDot(
		double worldTileX,
		double worldTileY,
		int plane
	)
	{
	}

	private record HoveredNpcDraw(
		UploadedRegion region,
		UploadedNpcMesh mesh,
		NpcMesh.Instance instance,
		NpcMesh.Transform transform,
		UploadedAnimationFrame frame,
		float distance
	)
	{
	}

	private record LineInterval(
		double start,
		double end
	)
	{
	}

	private record TileGeometry(
		Vector3f northWest,
		Vector3f northEast,
		Vector3f southEast,
		Vector3f southWest
	)
	{
	}

	private record PackedOverlayText(
		Vector3f position,
		String text,
		Color color,
		int x,
		int y,
		int width,
		int height
	)
	{
		private PackedOverlayText
		{
			color = color == null ? Color.WHITE : color;
		}
	}

	private record OverlayTextCandidate(
		Vector3f position,
		String text,
		Color color
	)
	{
		private OverlayTextCandidate
		{
			color = color == null ? Color.WHITE : color;
		}
	}

	private record OverlayTextLabel(
		Vector3f position,
		int pixelWidth,
		int pixelHeight,
		float u0,
		float v0,
		float u1,
		float v1
	)
	{
	}

	private static int createProgram(String vertexSource, String fragmentSource)
	{
		int vertexShader = compileShader(GL33C.GL_VERTEX_SHADER, vertexSource);
		int fragmentShader = compileShader(GL33C.GL_FRAGMENT_SHADER, fragmentSource);
		int program = GL33C.glCreateProgram();
		GL33C.glAttachShader(program, vertexShader);
		GL33C.glAttachShader(program, fragmentShader);
		GL33C.glLinkProgram(program);
		if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == GL33C.GL_FALSE)
		{
			String log = GL33C.glGetProgramInfoLog(program);
			GL33C.glDeleteProgram(program);
			GL33C.glDeleteShader(vertexShader);
			GL33C.glDeleteShader(fragmentShader);
			throw new IllegalStateException("Failed to link shader program: " + log);
		}
		GL33C.glDetachShader(program, vertexShader);
		GL33C.glDetachShader(program, fragmentShader);
		GL33C.glDeleteShader(vertexShader);
		GL33C.glDeleteShader(fragmentShader);
		return program;
	}

	private static int compileShader(int type, String source)
	{
		int shader = GL33C.glCreateShader(type);
		GL33C.glShaderSource(shader, source);
		GL33C.glCompileShader(shader);
		if (GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == GL33C.GL_FALSE)
		{
			String log = GL33C.glGetShaderInfoLog(shader);
			GL33C.glDeleteShader(shader);
			throw new IllegalStateException("Failed to compile shader: " + log);
		}
		return shader;
	}

	private static String glString(int name)
	{
		String value = GL33C.glGetString(name);
		return value == null || value.isBlank() ? "Unavailable" : value;
	}

	private record RenderCounts(
		int drawCalls,
		int visibleRegions,
		int culledRegions,
		int verticesDrawn
	)
	{
		private static RenderCounts empty()
		{
			return new RenderCounts(0, 0, 0, 0);
		}
	}

	record RegionUploadProgress(
		int regionId,
		String stage,
		int uploadedFloats,
		int totalFloats,
		int queuedRegionCount
	)
	{
		RegionUploadProgress
		{
			stage = stage == null || stage.isBlank() ? "Uploading region geometry" : stage;
			uploadedFloats = Math.max(0, uploadedFloats);
			totalFloats = Math.max(0, totalFloats);
			queuedRegionCount = Math.max(0, queuedRegionCount);
		}

		private static RegionUploadProgress none(int queuedRegionCount)
		{
			return new RegionUploadProgress(-1, "", 0, 0, queuedRegionCount);
		}

		boolean active()
		{
			return regionId >= 0;
		}

		int percent()
		{
			if (totalFloats <= 0)
			{
				return active() ? 100 : 0;
			}
			return Math.max(0, Math.min(100, (int) Math.round(uploadedFloats * 100.0 / totalFloats)));
		}
	}

	private record UploadedRegion(
		TerrainMesh mesh,
		int regionId,
		int vao,
		int vbo,
		int vertexCount,
		int[] planeStartVertices,
		int[] planeVertexCounts,
		int[] planeTransparentStartVertices,
		int[] planeTransparentVertexCounts,
		float offsetX,
		float offsetZ,
		float minX,
		float minY,
		float minZ,
		float maxX,
		float maxY,
		float maxZ,
		List<UploadedAnimatedObject> animatedObjects,
		List<UploadedNpcMesh> npcMeshes
	)
	{
		private static UploadedRegion empty(TerrainMesh mesh, float offsetX, float offsetZ)
		{
			return new UploadedRegion(
				mesh,
				mesh.regionId(),
				0,
				0,
				0,
				new int[MAX_VISIBLE_PLANE + 1],
				new int[MAX_VISIBLE_PLANE + 1],
				new int[MAX_VISIBLE_PLANE + 1],
				new int[MAX_VISIBLE_PLANE + 1],
				offsetX,
				offsetZ,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				List.of(),
				List.of()
			);
		}

		private UploadedRegion
		{
			planeStartVertices = normalizedPlaneArray(planeStartVertices);
			planeVertexCounts = normalizedPlaneArray(planeVertexCounts);
			planeTransparentStartVertices = normalizedPlaneArray(planeTransparentStartVertices);
			planeTransparentVertexCounts = normalizedPlaneArray(planeTransparentVertexCounts);
			animatedObjects = animatedObjects == null ? List.of() : List.copyOf(animatedObjects);
			npcMeshes = npcMeshes == null ? List.of() : List.copyOf(npcMeshes);
		}

		private int planeStartVertex(int plane)
		{
			return planeStartVertices[clamp(plane, 0, MAX_VISIBLE_PLANE)];
		}

		private int planeVertexCount(int plane)
		{
			return planeVertexCounts[clamp(plane, 0, MAX_VISIBLE_PLANE)];
		}

		private int planeTransparentStartVertex(int plane)
		{
			return planeTransparentStartVertices[clamp(plane, 0, MAX_VISIBLE_PLANE)];
		}

			private int planeTransparentVertexCount(int plane)
			{
				return planeTransparentVertexCounts[clamp(plane, 0, MAX_VISIBLE_PLANE)];
			}

			private boolean hasDrawableGeometry()
			{
				return vertexCount > 0 || !animatedObjects.isEmpty() || !npcMeshes.isEmpty();
			}

			private void delete()
			{
			if (vbo != 0)
			{
				GL33C.glDeleteBuffers(vbo);
			}
			if (vao != 0)
			{
				GL33C.glDeleteVertexArrays(vao);
			}
			for (UploadedAnimatedObject animatedObject : animatedObjects)
			{
				animatedObject.delete();
			}
			for (UploadedNpcMesh npcMesh : npcMeshes)
			{
				npcMesh.delete();
			}
		}
	}

	private final class RegionUploadTask
	{
		private final TerrainScene scene;
		private final TerrainMesh mesh;
		private final float[] vertexData;
		private final List<AnimatedObjectUploadTask> animatedTasks = new ArrayList<>();
		private final List<NpcMeshUploadTask> npcTasks = new ArrayList<>();
		private final int totalFloats;
		private int vao;
		private int vbo;
		private int uploadedFloats;
		private int animatedTaskIndex;
		private int npcTaskIndex;
		private boolean cancelled;

		private RegionUploadTask(TerrainScene scene, TerrainMesh mesh)
		{
			this.scene = scene;
			this.mesh = mesh;
			this.vertexData = mesh.rawVertexData();
			for (AnimatedObjectMesh animatedObject : mesh.animatedObjects())
			{
				if (animatedObject.frameCount() > 0)
				{
					animatedTasks.add(new AnimatedObjectUploadTask(animatedObject));
				}
			}
			for (NpcMesh npcMesh : mesh.npcMeshes())
			{
				if (npcMesh.frameCount() > 0 && !npcMesh.instances().isEmpty())
				{
					npcTasks.add(new NpcMeshUploadTask(npcMesh));
				}
			}
			int total = vertexData.length;
			for (AnimatedObjectUploadTask task : animatedTasks)
			{
				total += task.totalFloats();
			}
			for (NpcMeshUploadTask task : npcTasks)
			{
				total += task.totalFloats();
			}
			totalFloats = total;
		}

		private int regionId()
		{
			return mesh.regionId();
		}

		private TerrainMesh mesh()
		{
			return mesh;
		}

		private int totalFloats()
		{
			return totalFloats;
		}

		private int uploadedFloats()
		{
			int total = Math.min(uploadedFloats, vertexData.length);
			for (AnimatedObjectUploadTask task : animatedTasks)
			{
				total += task.uploadedFloats();
			}
			for (NpcMeshUploadTask task : npcTasks)
			{
				total += task.uploadedFloats();
			}
			return Math.min(total, totalFloats);
		}

		private String stage()
		{
			if (vertexData.length > 0 && uploadedFloats < vertexData.length)
			{
				return "Uploading terrain geometry";
			}
			if (animatedTaskIndex < animatedTasks.size())
			{
				return "Uploading object animations";
			}
			if (npcTaskIndex < npcTasks.size())
			{
				return "Uploading NPC models";
			}
			return "Finalizing GPU upload";
		}

		private boolean uploadChunk(UploadBudget budget)
		{
			if (cancelled)
			{
				return false;
			}
			if (!ensureStaticBuffer(budget))
			{
				return false;
			}
			while (uploadedFloats < vertexData.length)
			{
				int remaining = vertexData.length - uploadedFloats;
				int floats = budget.take(remaining);
				if (floats <= 0)
				{
					return false;
				}
				FloatBuffer buffer = MemoryUtil.memAllocFloat(floats);
				try
				{
					buffer.put(vertexData, uploadedFloats, floats).flip();
					GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
					GL33C.glBufferSubData(GL33C.GL_ARRAY_BUFFER, (long) uploadedFloats * Float.BYTES, buffer);
				}
				finally
				{
					MemoryUtil.memFree(buffer);
				}
				uploadedFloats += floats;
			}

			return uploadAnimationChunk(budget)
				&& uploadNpcChunk(budget);
		}

		private boolean ensureStaticBuffer(UploadBudget budget)
		{
			if (vertexData.length == 0 || vbo != 0)
			{
				return true;
			}
			if (!budget.hasRemaining())
			{
				return false;
			}
			vao = GL33C.glGenVertexArrays();
			vbo = GL33C.glGenBuffers();
			GL33C.glBindVertexArray(vao);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, (long) vertexData.length * Float.BYTES, GL33C.GL_STATIC_DRAW);
			installTerrainAttributes();
			GL33C.glBindVertexArray(0);
			budget.reserveSetup(vertexData.length);
			return budget.hasRemaining();
		}

		private UploadedRegion finish()
		{
			List<UploadedAnimatedObject> animatedObjects = new ArrayList<>(animatedTasks.size());
			for (AnimatedObjectUploadTask task : animatedTasks)
			{
				animatedObjects.add(task.finish());
			}
			List<UploadedNpcMesh> npcMeshes = new ArrayList<>(npcTasks.size());
			for (NpcMeshUploadTask task : npcTasks)
			{
				npcMeshes.add(task.finish());
			}
			return uploadedRegion(scene, mesh, vao, vbo, List.copyOf(animatedObjects), List.copyOf(npcMeshes));
		}

		private void cancel(boolean releaseVertexData)
		{
			cancelled = true;
			if (releaseVertexData)
			{
				mesh.releaseVertexData();
			}
			for (AnimatedObjectUploadTask task : animatedTasks)
			{
				task.cancel(releaseVertexData);
			}
			for (NpcMeshUploadTask task : npcTasks)
			{
				task.cancel(releaseVertexData);
			}
			if (vbo != 0)
			{
				GL33C.glDeleteBuffers(vbo);
			}
			if (vao != 0)
			{
				GL33C.glDeleteVertexArrays(vao);
			}
		}

		private boolean uploadAnimationChunk(UploadBudget budget)
		{
			while (animatedTaskIndex < animatedTasks.size())
			{
				if (!budget.hasRemaining())
				{
					return false;
				}
				AnimatedObjectUploadTask task = animatedTasks.get(animatedTaskIndex);
				if (task.uploadChunk(budget))
				{
					animatedTaskIndex++;
					continue;
				}
				return false;
			}
			return true;
		}

		private boolean uploadNpcChunk(UploadBudget budget)
		{
			while (npcTaskIndex < npcTasks.size())
			{
				if (!budget.hasRemaining())
				{
					return false;
				}
				NpcMeshUploadTask task = npcTasks.get(npcTaskIndex);
				if (task.uploadChunk(budget))
				{
					npcTaskIndex++;
					continue;
				}
				return false;
			}
			return true;
		}
	}

	private final class AnimatedObjectUploadTask
	{
		private final AnimatedObjectMesh mesh;
		private final UploadedAnimationFrame[] uploadedFrames;
		private final int totalFloats;
		private AnimationFrameUploadTask activeFrameTask;
		private int completedFrameFloats;
		private int frameIndex;

		private AnimatedObjectUploadTask(AnimatedObjectMesh mesh)
		{
			this.mesh = mesh;
			this.uploadedFrames = new UploadedAnimationFrame[mesh.frameCount()];
			this.totalFloats = frameUploadFloats(mesh.frames());
		}

		private int totalFloats()
		{
			return totalFloats;
		}

		private int uploadedFloats()
		{
			int activeFloats = activeFrameTask == null ? 0 : activeFrameTask.uploadedFloats();
			return Math.min(totalFloats, completedFrameFloats + activeFloats);
		}

		private boolean uploadChunk(UploadBudget budget)
		{
			while (frameIndex < uploadedFrames.length)
			{
				if (activeFrameTask == null)
				{
					AnimatedObjectMesh.Frame frame = mesh.frames()[frameIndex];
					if (frame == null)
					{
						uploadedFrames[frameIndex] = UploadedAnimationFrame.EMPTY;
						frameIndex++;
						continue;
					}
					if ((frame.vertexCount() > 0 || frame.transparentVertexCount() > 0) && !budget.hasRemaining())
					{
						return false;
					}
					activeFrameTask = new AnimationFrameUploadTask(frame, false, true);
				}
				if (activeFrameTask.uploadChunk(budget))
				{
					uploadedFrames[frameIndex] = activeFrameTask.finish();
					completedFrameFloats += activeFrameTask.totalFloats();
					activeFrameTask = null;
					frameIndex++;
					continue;
				}
				return false;
			}
			return true;
		}

		private UploadedAnimatedObject finish()
		{
			return new UploadedAnimatedObject(
				mesh.plane(),
				mesh.sequenceId(),
				mesh.frameLengths(),
				mesh.frameStep(),
				mesh.phaseOffset(),
				uploadedFrames
			);
		}

		private void cancel(boolean releaseVertexData)
		{
			if (activeFrameTask != null)
			{
				activeFrameTask.cancel(releaseVertexData);
				activeFrameTask = null;
			}
			for (UploadedAnimationFrame frame : uploadedFrames)
			{
				if (frame != null)
				{
					frame.delete();
				}
			}
		}
	}

	private final class NpcMeshUploadTask
	{
		private final NpcMesh mesh;
		private final UploadedAnimationFrame[] uploadedFrames;
		private final int totalFloats;
		private AnimationFrameUploadTask activeFrameTask;
		private int completedFrameFloats;
		private int frameIndex;

		private NpcMeshUploadTask(NpcMesh mesh)
		{
			this.mesh = mesh;
			this.uploadedFrames = new UploadedAnimationFrame[mesh.frameCount()];
			this.totalFloats = frameUploadFloats(mesh.frames());
		}

		private int totalFloats()
		{
			return totalFloats;
		}

		private int uploadedFloats()
		{
			int activeFloats = activeFrameTask == null ? 0 : activeFrameTask.uploadedFloats();
			return Math.min(totalFloats, completedFrameFloats + activeFloats);
		}

		private boolean uploadChunk(UploadBudget budget)
		{
			while (frameIndex < uploadedFrames.length)
			{
				if (activeFrameTask == null)
				{
					AnimatedObjectMesh.Frame frame = mesh.frames()[frameIndex];
					if (frame == null)
					{
						uploadedFrames[frameIndex] = UploadedAnimationFrame.EMPTY;
						frameIndex++;
						continue;
					}
					if ((frame.vertexCount() > 0 || frame.transparentVertexCount() > 0) && !budget.hasRemaining())
					{
						return false;
					}
					activeFrameTask = new AnimationFrameUploadTask(frame, true, false);
				}
				if (activeFrameTask.uploadChunk(budget))
				{
					uploadedFrames[frameIndex] = activeFrameTask.finish();
					completedFrameFloats += activeFrameTask.totalFloats();
					activeFrameTask = null;
					frameIndex++;
					continue;
				}
				return false;
			}
			return true;
		}

		private UploadedNpcMesh finish()
		{
			return new UploadedNpcMesh(
				mesh.npcId(),
				mesh.name(),
				mesh.combatLevel(),
				mesh.sequenceId(),
				mesh.walkingAnimation(),
				mesh.frameLengths(),
				mesh.frameStep(),
				uploadedFrames,
				mesh.bounds(),
				mesh.instances()
			);
		}

		private void cancel(boolean releaseVertexData)
		{
			if (activeFrameTask != null)
			{
				activeFrameTask.cancel(releaseVertexData);
				activeFrameTask = null;
			}
			for (UploadedAnimationFrame frame : uploadedFrames)
			{
				if (frame != null)
				{
					frame.delete();
				}
			}
		}
	}

	private final class AnimationFrameUploadTask
	{
		private final AnimatedObjectMesh.Frame frame;
		private final float[] vertexData;
		private final NpcOutlineGeometry outlineGeometry;
		private final boolean compactAfterUpload;
		private final int opaqueVertexCount;
		private final int transparentVertexCount;
		private int vao;
		private int vbo;
		private int uploadedFloats;

		private AnimationFrameUploadTask(AnimatedObjectMesh.Frame frame, boolean buildOutlineEdges, boolean compactAfterUpload)
		{
			this.frame = frame;
			this.opaqueVertexCount = frame.vertexCount();
			this.transparentVertexCount = frame.transparentVertexCount();
			float[] opaqueVertexData = frame.rawVertexData();
			this.vertexData = combinedFrameVertexData(opaqueVertexData, opaqueVertexCount,
				frame.rawTransparentVertexData(), transparentVertexCount);
			this.outlineGeometry = buildOutlineEdges
				? buildNpcOutlineGeometry(opaqueVertexData, opaqueVertexCount)
				: NpcOutlineGeometry.EMPTY;
			this.compactAfterUpload = compactAfterUpload;
		}

		private int totalFloats()
		{
			return vertexData.length;
		}

		private int uploadedFloats()
		{
			return Math.min(uploadedFloats, vertexData.length);
		}

		private boolean uploadChunk(UploadBudget budget)
		{
			if (!ensureBuffer(budget))
			{
				return false;
			}
			while (uploadedFloats < vertexData.length)
			{
				int remaining = vertexData.length - uploadedFloats;
				int floats = budget.take(remaining);
				if (floats <= 0)
				{
					return false;
				}
				FloatBuffer buffer = MemoryUtil.memAllocFloat(floats);
				try
				{
					buffer.put(vertexData, uploadedFloats, floats).flip();
					GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
					GL33C.glBufferSubData(GL33C.GL_ARRAY_BUFFER, (long) uploadedFloats * Float.BYTES, buffer);
				}
				finally
				{
					MemoryUtil.memFree(buffer);
				}
				uploadedFloats += floats;
			}
			return true;
		}

		private boolean ensureBuffer(UploadBudget budget)
		{
			if (opaqueVertexCount + transparentVertexCount <= 0 || vertexData.length == 0 || vbo != 0)
			{
				return true;
			}
			if (!budget.hasRemaining())
			{
				return false;
			}
			vao = GL33C.glGenVertexArrays();
			vbo = GL33C.glGenBuffers();
			GL33C.glBindVertexArray(vao);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, (long) vertexData.length * Float.BYTES, GL33C.GL_STATIC_DRAW);
			installTerrainAttributes();
			GL33C.glBindVertexArray(0);
			budget.reserveSetup(vertexData.length);
			return budget.hasRemaining();
		}

		private UploadedAnimationFrame finish()
		{
			UploadedAnimationFrame uploadedFrame = new UploadedAnimationFrame(
				vao,
				vbo,
				opaqueVertexCount,
				opaqueVertexCount,
				transparentVertexCount,
				outlineGeometry
			);
			if (compactAfterUpload)
			{
				scheduleFrameCompaction(frame);
			}
			return uploadedFrame;
		}

		private float[] combinedFrameVertexData(
			float[] opaqueVertexData,
			int opaqueVertexCount,
			float[] transparentVertexData,
			int transparentVertexCount
		)
		{
			int opaqueFloats = Math.max(0, opaqueVertexCount) * TerrainMesh.FLOATS_PER_VERTEX;
			int transparentFloats = Math.max(0, transparentVertexCount) * TerrainMesh.FLOATS_PER_VERTEX;
			float[] combined = new float[opaqueFloats + transparentFloats];
			if (opaqueVertexData != null)
			{
				System.arraycopy(opaqueVertexData, 0, combined, 0, Math.min(opaqueVertexData.length, opaqueFloats));
			}
			if (transparentVertexData != null)
			{
				System.arraycopy(
					transparentVertexData,
					0,
					combined,
					opaqueFloats,
					Math.min(transparentVertexData.length, transparentFloats)
				);
			}
			return combined;
		}

		private void cancel(boolean releaseVertexData)
		{
			if (vbo != 0)
			{
				GL33C.glDeleteBuffers(vbo);
			}
			if (vao != 0)
			{
				GL33C.glDeleteVertexArrays(vao);
			}
		}
	}

	private static int frameUploadFloats(AnimatedObjectMesh.Frame[] frames)
	{
		if (frames == null || frames.length == 0)
		{
			return 0;
		}
		int total = 0;
		for (AnimatedObjectMesh.Frame frame : frames)
		{
			total += frameUploadFloats(frame);
		}
		return total;
	}

	private static int frameUploadFloats(AnimatedObjectMesh.Frame frame)
	{
		if (frame == null)
		{
			return 0;
		}
		int vertices = Math.max(0, frame.vertexCount()) + Math.max(0, frame.transparentVertexCount());
		return vertices * TerrainMesh.FLOATS_PER_VERTEX;
	}

	private static final class UploadBudget
	{
		private final int maxChunkFloats;
		private final long deadlineNanos;
		private int remainingFloats;

		private UploadBudget(int remainingFloats, int maxChunkFloats, long deadlineNanos)
		{
			this.remainingFloats = remainingFloats;
			this.maxChunkFloats = maxChunkFloats;
			this.deadlineNanos = deadlineNanos;
		}

		private int take(int requestedFloats)
		{
			if (!hasRemaining())
			{
				return 0;
			}
			int floats = Math.min(Math.min(requestedFloats, remainingFloats), maxChunkFloats);
			remainingFloats -= floats;
			return floats;
		}

		private void reserveSetup(int requestedFloats)
		{
			if (remainingFloats <= 0)
			{
				return;
			}
			remainingFloats -= Math.min(Math.min(requestedFloats, remainingFloats), maxChunkFloats);
		}

		private boolean hasRemaining()
		{
			return remainingFloats > 0 && System.nanoTime() < deadlineNanos;
		}
	}

	private static final class RetainedDataCompactionCancelled extends RuntimeException
	{
	}

	private record UploadedAnimatedObject(
		int plane,
		int sequenceId,
		int[] frameLengths,
		int frameStep,
		int phaseOffset,
		UploadedAnimationFrame[] frames
	)
	{
		private UploadedAnimatedObject
		{
			frameLengths = frameLengths == null ? new int[0] : frameLengths.clone();
			frames = frames == null ? new UploadedAnimationFrame[0] : frames.clone();
		}

		private UploadedAnimationFrame frameAt(float timeSeconds)
		{
			if (frames.length == 0)
			{
				return UploadedAnimationFrame.EMPTY;
			}
			return frame(AnimatedObjectMesh.frameIndexAt(frames.length, frameLengths, frameStep, phaseOffset, timeSeconds));
		}

		private UploadedAnimationFrame frame(int frame)
		{
			UploadedAnimationFrame selected = frames[frame];
			if (selected != null && selected.hasGeometry())
			{
				return selected;
			}
			for (int i = frame - 1; i >= 0; i--)
			{
				UploadedAnimationFrame fallback = frames[i];
				if (fallback != null && fallback.hasGeometry())
				{
					return fallback;
				}
			}
			for (int i = frame + 1; i < frames.length; i++)
			{
				UploadedAnimationFrame fallback = frames[i];
				if (fallback != null && fallback.hasGeometry())
				{
					return fallback;
				}
			}
			return UploadedAnimationFrame.EMPTY;
		}

		private void delete()
		{
			for (UploadedAnimationFrame frame : frames)
			{
				if (frame != null)
				{
					frame.delete();
				}
			}
		}
	}

	private record UploadedNpcMesh(
		int npcId,
		String name,
		int combatLevel,
		int sequenceId,
		boolean walkingAnimation,
		int[] frameLengths,
		int frameStep,
		UploadedAnimationFrame[] frames,
		NpcMesh.Bounds bounds,
		List<NpcMesh.Instance> instances
	)
	{
		private UploadedNpcMesh
		{
			frameLengths = frameLengths == null ? new int[0] : frameLengths.clone();
			frames = frames == null ? new UploadedAnimationFrame[0] : frames.clone();
			bounds = bounds == null ? NpcMesh.Bounds.fallback() : bounds;
			instances = instances == null ? List.of() : List.copyOf(instances);
		}

		private UploadedAnimationFrame frameAt(float timeSeconds, int phaseOffset)
		{
			if (frames.length == 0)
			{
				return UploadedAnimationFrame.EMPTY;
			}
			int frame = AnimatedObjectMesh.frameIndexAt(frames.length, frameLengths, frameStep, phaseOffset, timeSeconds);
			if (frame < 0 || frame >= frames.length)
			{
				frame = 0;
			}
			return frame(frame);
		}

		private UploadedAnimationFrame frame(int frame)
		{
			UploadedAnimationFrame selected = frames[frame];
			if (selected != null && selected.hasGeometry())
			{
				return selected;
			}
			for (int i = frame - 1; i >= 0; i--)
			{
				UploadedAnimationFrame fallback = frames[i];
				if (fallback != null && fallback.hasGeometry())
				{
					return fallback;
				}
			}
			for (int i = frame + 1; i < frames.length; i++)
			{
				UploadedAnimationFrame fallback = frames[i];
				if (fallback != null && fallback.hasGeometry())
				{
					return fallback;
				}
			}
			return UploadedAnimationFrame.EMPTY;
		}

		private void delete()
		{
			for (UploadedAnimationFrame frame : frames)
			{
				if (frame != null)
				{
					frame.delete();
				}
			}
		}
	}

	private record UploadedAnimationFrame(
		int vao,
		int vbo,
		int vertexCount,
		int transparentStartVertex,
		int transparentVertexCount,
		NpcOutlineGeometry outlineGeometry
	)
	{
		private static final UploadedAnimationFrame EMPTY = new UploadedAnimationFrame(0, 0, 0, 0, 0, NpcOutlineGeometry.EMPTY);

		private UploadedAnimationFrame
		{
			outlineGeometry = outlineGeometry == null ? NpcOutlineGeometry.EMPTY : outlineGeometry;
		}

		private boolean hasGeometry()
		{
			return vertexCount > 0 || transparentVertexCount > 0;
		}

		private void delete()
		{
			if (vbo != 0)
			{
				GL33C.glDeleteBuffers(vbo);
			}
			if (vao != 0)
			{
				GL33C.glDeleteVertexArrays(vao);
			}
		}
	}
}
