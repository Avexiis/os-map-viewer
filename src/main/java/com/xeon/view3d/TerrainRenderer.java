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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
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
		uniform vec3 uCameraPosition;
		uniform vec3 uRegionOffset;
		uniform float uTimeSeconds;

		out vec3 vColor;
		out vec3 vNormal;
		out float vAlpha;
		out float vDistance;
		out vec2 vTexCoord;
		flat out float vTextureLayer;
		flat out float vTextureAlphaCutoff;

		void main()
		{
			vec3 position = aPosition + uRegionOffset;
			vec4 worldPosition = vec4(position, 1.0);
			vColor = aColor;
			vNormal = aNormal;
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
		in vec3 vNormal;
		in float vAlpha;
		in float vDistance;
		in vec2 vTexCoord;
		flat in float vTextureLayer;
		flat in float vTextureAlphaCutoff;

		uniform sampler2DArray uTextures;
		uniform float uTextureLayerCount;

		out vec4 fragColor;

		void main()
		{
			vec3 normal = normalize(vNormal);
			vec3 lightDirection = normalize(vec3(-0.36, 0.78, -0.52));
			float diffuse = max(dot(normal, lightDirection), 0.0);
			float skyFill = 0.18 * max(normal.y, 0.0);
			float groundFill = 0.08 * max(-normal.y, 0.0);
			float shade = clamp(0.50 + diffuse * 0.46 + skyFill + groundFill, 0.46, 1.08);
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
			vec3 sky = vec3(0.43, 0.53, 0.62);
			float fog = smoothstep(82.0, 138.0, vDistance);
			vec3 color = mix(baseColor * shade, sky, fog);
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
	private static final int MAX_UPLOAD_BYTES_PER_FRAME = 4 * 1024 * 1024;
	private static final int MAX_UPLOAD_FLOATS_PER_FRAME = MAX_UPLOAD_BYTES_PER_FRAME / Float.BYTES;
	private static final float OUTLINE_HEIGHT_OFFSET = SceneScale.SCENE_TO_WORLD * 1.5f;
	private static final float OVERLAY_PATH_HEIGHT_OFFSET = 0.28f;
	private static final float OVERLAY_MARKER_HEIGHT_OFFSET = 0.42f;
	private static final float OVERLAY_LABEL_HEIGHT_OFFSET = 1.15f;
	private static final float OVERLAY_DASH_LENGTH = 0.55f;
	private static final float OVERLAY_DASH_GAP = 0.34f;
	private static final float OVERLAY_LABEL_STEM_HEIGHT = 0.58f;
	private static final float OVERLAY_LABEL_FLAG_WIDTH = 0.38f;
	private static final float OVERLAY_TEXT_WORLD_HEIGHT = 0.48f;
	private static final int OVERLAY_TEXT_PADDING_X = 5;
	private static final int OVERLAY_TEXT_PADDING_Y = 3;
	private static final int OVERLAY_TEXT_ATLAS_MAX_WIDTH = 2048;
	private static final int OVERLAY_TRANSPORT_ICON_RGB = 0xFF2E3D;
	private static final float REGION_CULL_PADDING = 2.0f;

	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f view = new Matrix4f();
	private final Matrix4f mvp = new Matrix4f();
	private final FrustumIntersection frustum = new FrustumIntersection();
	private final Map<Integer, UploadedRegion> uploadedRegions = new HashMap<>();
	private final Map<Integer, TerrainMesh> pendingUploadRegions = new LinkedHashMap<>();
	private RegionUploadTask activeUploadTask;
	private TerrainScene pendingScene;
	private TerrainScene currentScene;
	private HoveredTile hoveredTile;
	private int terrainProgram;
	private int terrainMvpLocation;
	private int terrainCameraLocation;
	private int terrainRegionOffsetLocation;
	private int terrainTimeLocation;
	private int terrainTextureLocation;
	private int terrainTextureLayerCountLocation;
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
	private int overlayLineVao;
	private int overlayLineVbo;
	private int overlayLineVertexCount;
	private int overlayTextVao;
	private int overlayTextVbo;
	private int overlayTextTexture;
	private int sceneFbo;
	private int sceneColorRbo;
	private int sceneDepthRbo;
	private int sceneFboWidth = -1;
	private int sceneFboHeight = -1;
	private int sceneFboSamples = -1;
	private String glVendor = "Unavailable";
	private String glRenderer = "Unavailable";
	private String glVersion = "Unavailable";
	private TerrainRenderStats renderStats = TerrainRenderStats.unavailable();
	private Map3DOverlay pluginOverlay = Map3DOverlay.empty();
	private List<OverlayLineDraw> overlayLineDraws = List.of();
	private List<OverlayTextLabel> overlayTextLabels = List.of();
	private boolean initialized;
	private int antialiasingSamples = 4;
	private long startNanos;
	private boolean pluginOverlayDirty = true;

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
		GL33C.glClearColor(0.43f, 0.53f, 0.62f, 1.0f);

		terrainProgram = createProgram(TERRAIN_VERTEX_SHADER, TERRAIN_FRAGMENT_SHADER);
		terrainMvpLocation = GL33C.glGetUniformLocation(terrainProgram, "uMvp");
		terrainCameraLocation = GL33C.glGetUniformLocation(terrainProgram, "uCameraPosition");
		terrainRegionOffsetLocation = GL33C.glGetUniformLocation(terrainProgram, "uRegionOffset");
		terrainTimeLocation = GL33C.glGetUniformLocation(terrainProgram, "uTimeSeconds");
		terrainTextureLocation = GL33C.glGetUniformLocation(terrainProgram, "uTextures");
		terrainTextureLayerCountLocation = GL33C.glGetUniformLocation(terrainProgram, "uTextureLayerCount");
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

	boolean isInitialized()
	{
		return initialized;
	}

	void setAntialiasingSamples(int antialiasingSamples)
	{
		this.antialiasingSamples = Math.max(0, antialiasingSamples);
		sceneFboSamples = -1;
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
		int defaultFramebuffer = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
		int renderFramebuffer = prepareRenderTarget(safeWidth, safeHeight, defaultFramebuffer);
		GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, renderFramebuffer);
		GL33C.glViewport(0, 0, safeWidth, safeHeight);
		GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_DEPTH_BUFFER_BIT);

		RenderCounts renderCounts = RenderCounts.empty();
		int outlineDrawCalls = 0;
		int overlayDrawCalls = 0;
		if (!uploadedRegions.isEmpty())
		{
			uploadPendingRegions();
			prepareMatrices(camera, safeWidth / (float) safeHeight);
			renderCounts = renderTerrain(camera);
			outlineDrawCalls = renderHoveredTile();
			overlayDrawCalls = renderPluginOverlay(camera);
		}
		else
		{
			uploadPendingRegions();
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

	private RenderCounts renderTerrain(FreeCamera camera)
	{
		Vector3fc cameraPosition = camera.position();
		GL33C.glUseProgram(terrainProgram);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer matrixBuffer = stack.mallocFloat(16);
			GL33C.glUniformMatrix4fv(terrainMvpLocation, false, mvp.get(matrixBuffer));
		}
		GL33C.glUniform3f(terrainCameraLocation, cameraPosition.x(), cameraPosition.y(), cameraPosition.z());
		float timeSeconds = animationTimeSeconds();
		GL33C.glUniform1f(terrainTimeLocation, timeSeconds);
		GL33C.glUniform1f(terrainTextureLayerCountLocation, uploadedTextureLayerCount);
		GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
		GL33C.glBindTexture(GL33C.GL_TEXTURE_2D_ARRAY, terrainTextureArray);
		GL33C.glUniform1i(terrainTextureLocation, 0);
		int drawCalls = 0;
		int visibleRegions = 0;
		int culledRegions = 0;
		int verticesDrawn = 0;
		for (UploadedRegion region : uploadedRegions.values())
		{
			if (region.vertexCount() <= 0 && region.animatedObjects().isEmpty())
			{
				continue;
			}
			if (!isVisible(region))
			{
				culledRegions++;
				continue;
			}
			GL33C.glUniform3f(terrainRegionOffsetLocation, region.offsetX(), 0.0f, region.offsetZ());
			if (region.vertexCount() > 0)
			{
				GL33C.glBindVertexArray(region.vao());
				GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, region.vertexCount());
				drawCalls++;
			}
			for (UploadedAnimatedObject animatedObject : region.animatedObjects())
			{
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
			visibleRegions++;
			verticesDrawn += region.vertexCount();
		}
		GL33C.glBindVertexArray(0);
		GL33C.glBindTexture(GL33C.GL_TEXTURE_2D_ARRAY, 0);
		GL33C.glUseProgram(0);
		return new RenderCounts(drawCalls, visibleRegions, culledRegions, verticesDrawn);
	}

	private int renderHoveredTile()
	{
		if (currentScene == null || hoveredTile == null)
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
		GL33C.glUniform4f(outlineColorLocation, 1.0f, 0.92f, 0.24f, 0.95f);
		GL33C.glLineWidth(2.0f);
		GL33C.glDrawArrays(GL33C.GL_LINES, 0, OUTLINE_VERTICES);
		GL33C.glLineWidth(1.0f);
		GL33C.glBindVertexArray(0);
		GL33C.glUseProgram(0);
		return 2;
	}

	private int renderPluginOverlay(FreeCamera camera)
	{
		if (currentScene == null)
		{
			return 0;
		}
		if (pluginOverlayDirty)
		{
			rebuildPluginOverlayLines();
			pluginOverlayDirty = false;
		}
		if (overlayLineVertexCount <= 0 || overlayLineDraws.isEmpty())
		{
			return renderPluginOverlayText(camera);
		}

		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayLineVbo);
		GL33C.glUseProgram(outlineProgram);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer matrixBuffer = stack.mallocFloat(16);
			GL33C.glUniformMatrix4fv(outlineMvpLocation, false, mvp.get(matrixBuffer));
		}
		GL33C.glBindVertexArray(overlayLineVao);
		GL33C.glUniform4f(outlineColorLocation, 0.0f, 0.0f, 0.0f, 0.70f);
		GL33C.glLineWidth(5.2f);
		GL33C.glDrawArrays(GL33C.GL_LINES, 0, overlayLineVertexCount);
		int drawCalls = 1;
		for (OverlayLineDraw draw : overlayLineDraws)
		{
			GL33C.glUniform4f(outlineColorLocation, draw.red(), draw.green(), draw.blue(), draw.alpha());
			GL33C.glLineWidth(2.7f);
			GL33C.glDrawArrays(GL33C.GL_LINES, draw.startVertex(), draw.vertexCount());
			drawCalls++;
		}
		GL33C.glLineWidth(1.0f);
		GL33C.glBindVertexArray(0);
		GL33C.glUseProgram(0);
		return drawCalls + renderPluginOverlayText(camera);
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
			GL33C.glBindVertexArray(0);
		}

		Map<Integer, OverlayLineBatch> batches = new LinkedHashMap<>();
		for (Map3DPathSegment segment : pluginOverlay.segments())
		{
			if (segment == null)
			{
				continue;
			}
			Vector3f start = tileWorldPosition(segment.start(), OVERLAY_PATH_HEIGHT_OFFSET);
			Vector3f end = tileWorldPosition(segment.end(), OVERLAY_PATH_HEIGHT_OFFSET);
			if (start == null || end == null)
			{
				continue;
			}
			OverlayLineBatch batch = overlayBatch(batches, segment.color());
			if (segment.dashed())
			{
				addDashedLine(batch, start, end);
			}
			else
			{
				batch.addLine(start, end);
			}
		}
		for (Map3DMarker marker : pluginOverlay.markers())
		{
			if (marker != null)
			{
				addTileHighlightLines(batches, marker.tile(), marker.color());
			}
		}
		for (Map3DLabel label : pluginOverlay.labels())
		{
			if (label != null)
			{
				addLabelAnchorLines(batches, label.tile());
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
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, overlayLineVbo);
		if (vertices.size() == 0)
		{
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, 0L, GL33C.GL_DYNAMIC_DRAW);
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
		}
		rebuildPluginOverlayTextAtlas();
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

		Font font = new Font(Font.SANS_SERIF, Font.BOLD, 19);
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
			packed.add(new PackedOverlayText(label.position(), text, cursorX, cursorY, width, height));
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
				g.setColor(Color.WHITE);
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
		for (Map3DLabel label : pluginOverlay.labels())
		{
			if (label == null || label.text().isBlank())
			{
				continue;
			}
			Vector3f position = tileWorldPosition(label.tile(), OVERLAY_LABEL_HEIGHT_OFFSET + OVERLAY_LABEL_STEM_HEIGHT);
			if (position != null)
			{
				candidates.add(new OverlayTextCandidate(position, label.text()));
			}
		}
		return candidates;
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
		}

		GL33C.glUseProgram(textProgram);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer matrixBuffer = stack.mallocFloat(16);
			GL33C.glUniformMatrix4fv(textMvpLocation, false, mvp.get(matrixBuffer));
		}
		GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
		GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, overlayTextTexture);
		GL33C.glUniform1i(textTextureLocation, 0);
		GL33C.glBindVertexArray(overlayTextVao);
		GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, vertices.size() / OVERLAY_TEXT_FLOATS_PER_VERTEX);
		GL33C.glBindVertexArray(0);
		GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, 0);
		GL33C.glUseProgram(0);
		return 1;
	}

	private FloatList overlayTextVertices(FreeCamera camera)
	{
		Vector3f direction = camera.direction(new Vector3f());
		Vector3f right = direction.cross(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f());
		if (right.lengthSquared() < 0.0001f)
		{
			right.set(1.0f, 0.0f, 0.0f);
		}
		else
		{
			right.normalize();
		}
		Vector3f up = new Vector3f(right).cross(direction).normalize();
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

	private void addDashedLine(OverlayLineBatch batch, Vector3f start, Vector3f end)
	{
		Vector3f delta = new Vector3f(end).sub(start);
		float length = delta.length();
		if (length <= 0.0001f)
		{
			return;
		}
		Vector3f direction = delta.div(length);
		float cursor = 0.0f;
		while (cursor < length)
		{
			float dashEnd = Math.min(length, cursor + OVERLAY_DASH_LENGTH);
			Vector3f dashStart = new Vector3f(start).fma(cursor, direction);
			Vector3f dashStop = new Vector3f(start).fma(dashEnd, direction);
			batch.addLine(dashStart, dashStop);
			cursor += OVERLAY_DASH_LENGTH + OVERLAY_DASH_GAP;
		}
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

	private void addLabelAnchorLines(Map<Integer, OverlayLineBatch> batches, Tile tile)
	{
		Vector3f base = tileWorldPosition(tile, OVERLAY_LABEL_HEIGHT_OFFSET);
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

	private Vector3f tileWorldPosition(Tile tile, float heightOffset)
	{
		if (tile == null || currentScene == null)
		{
			return null;
		}
		int regionId = TerrainScene.regionId(
			Math.floorDiv(tile.x, TerrainScene.REGION_SIZE),
			Math.floorDiv(tile.y, TerrainScene.REGION_SIZE)
		);
		TerrainMesh mesh = currentScene.mesh(regionId);
		if (mesh == null)
		{
			return null;
		}
		float localX = Math.floorMod(tile.x, TerrainScene.REGION_SIZE) + 0.5f;
		float localY = Math.floorMod(tile.y, TerrainScene.REGION_SIZE) + 0.5f;
		return worldPosition(mesh, tile.z, localX, localY, heightOffset);
	}

	private TileGeometry tileGeometry(Tile tile, float heightOffset)
	{
		if (tile == null || currentScene == null)
		{
			return null;
		}
		int regionId = TerrainScene.regionId(
			Math.floorDiv(tile.x, TerrainScene.REGION_SIZE),
			Math.floorDiv(tile.y, TerrainScene.REGION_SIZE)
		);
		TerrainMesh mesh = currentScene.mesh(regionId);
		if (mesh == null)
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
		return new Vector3f(x, y, z);
	}

	private static float channel(int argb, int shift)
	{
		return (argb >> shift & 0xFF) / 255.0f;
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
			if (regionIds.contains(entry.getKey()))
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
			if (mesh.vertexCount() == 0 && mesh.animatedObjects().isEmpty())
			{
				if (uploadedRegion == null)
				{
					uploadedRegions.put(
						mesh.regionId(),
						UploadedRegion.empty(mesh.regionId(), scene.offsetX(mesh), scene.offsetZ(mesh))
					);
				}
				continue;
			}

			if (uploadedRegion == null
				|| uploadedRegion.vertexCount() == 0 && uploadedRegion.animatedObjects().isEmpty())
			{
				pendingUploadRegions.putIfAbsent(mesh.regionId(), mesh);
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

		if (activeUploadTask == null)
		{
			startNextUploadTask();
		}
		if (activeUploadTask == null)
		{
			return;
		}

		UploadBudget budget = new UploadBudget(MAX_UPLOAD_FLOATS_PER_FRAME);
		if (activeUploadTask.uploadChunk(budget))
		{
			UploadedRegion uploadedRegion = activeUploadTask.finish();
			UploadedRegion previous = uploadedRegions.put(uploadedRegion.regionId(), uploadedRegion);
			if (previous != null)
			{
				previous.delete();
			}
			activeUploadTask = null;
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
		List<UploadedAnimatedObject> animatedObjects
	)
	{
		float offsetX = scene.offsetX(mesh);
		float offsetZ = scene.offsetZ(mesh);
		float minX = offsetX - SceneScale.REGION_CENTER_TILES;
		float maxX = offsetX + SceneScale.REGION_CENTER_TILES;
		float minZ = offsetZ - SceneScale.REGION_CENTER_TILES;
		float maxZ = offsetZ + SceneScale.REGION_CENTER_TILES;
		return new UploadedRegion(
			mesh.regionId(),
			vao,
			vbo,
			mesh.vertexCount(),
			offsetX,
			offsetZ,
			minX,
			mesh.minY(),
			minZ,
			maxX,
			mesh.maxY(),
			maxZ,
			animatedObjects
		);
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
			vertices.add(start.x).add(start.y).add(start.z);
			vertices.add(end.x).add(end.y).add(end.z);
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
		int x,
		int y,
		int width,
		int height
	)
	{
	}

	private record OverlayTextCandidate(
		Vector3f position,
		String text
	)
	{
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

	private record UploadedRegion(
		int regionId,
		int vao,
		int vbo,
		int vertexCount,
		float offsetX,
		float offsetZ,
		float minX,
		float minY,
		float minZ,
		float maxX,
		float maxY,
		float maxZ,
		List<UploadedAnimatedObject> animatedObjects
	)
	{
		private static UploadedRegion empty(int regionId, float offsetX, float offsetZ)
		{
			return new UploadedRegion(
				regionId,
				0,
				0,
				0,
				offsetX,
				offsetZ,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				List.of()
			);
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
		}
	}

	private final class RegionUploadTask
	{
		private final TerrainScene scene;
		private final TerrainMesh mesh;
		private final float[] vertexData;
		private final List<AnimatedObjectUploadTask> animatedTasks = new ArrayList<>();
		private final int vao;
		private final int vbo;
		private int uploadedFloats;
		private int animatedTaskIndex;
		private boolean cancelled;

		private RegionUploadTask(TerrainScene scene, TerrainMesh mesh)
		{
			this.scene = scene;
			this.mesh = mesh;
			this.vertexData = mesh.rawVertexData();
			if (vertexData.length == 0)
			{
				vao = 0;
				vbo = 0;
			}
			else
			{
				vao = GL33C.glGenVertexArrays();
				vbo = GL33C.glGenBuffers();
				GL33C.glBindVertexArray(vao);
				GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
				GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, (long) vertexData.length * Float.BYTES, GL33C.GL_STATIC_DRAW);
				installTerrainAttributes();
				GL33C.glBindVertexArray(0);
			}
			for (AnimatedObjectMesh animatedObject : mesh.animatedObjects())
			{
				if (animatedObject.frameCount() > 0)
				{
					animatedTasks.add(new AnimatedObjectUploadTask(animatedObject));
				}
			}
		}

		private int regionId()
		{
			return mesh.regionId();
		}

		private TerrainMesh mesh()
		{
			return mesh;
		}

		private boolean uploadChunk(UploadBudget budget)
		{
			if (cancelled)
			{
				return false;
			}
			int remaining = vertexData.length - uploadedFloats;
			if (remaining <= 0)
			{
				return uploadAnimationChunk(budget);
			}

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
			return uploadedFloats >= vertexData.length && uploadAnimationChunk(budget);
		}

		private UploadedRegion finish()
		{
			List<UploadedAnimatedObject> animatedObjects = new ArrayList<>(animatedTasks.size());
			for (AnimatedObjectUploadTask task : animatedTasks)
			{
				animatedObjects.add(task.finish());
			}
			return uploadedRegion(scene, mesh, vao, vbo, List.copyOf(animatedObjects));
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
	}

	private final class AnimatedObjectUploadTask
	{
		private final AnimatedObjectMesh mesh;
		private final UploadedAnimationFrame[] uploadedFrames;
		private AnimationFrameUploadTask activeFrameTask;
		private int frameIndex;

		private AnimatedObjectUploadTask(AnimatedObjectMesh mesh)
		{
			this.mesh = mesh;
			this.uploadedFrames = new UploadedAnimationFrame[mesh.frameCount()];
		}

		private boolean uploadChunk(UploadBudget budget)
		{
			while (frameIndex < uploadedFrames.length)
			{
				if (activeFrameTask == null)
				{
					AnimatedObjectMesh.Frame frame = mesh.frames()[frameIndex];
					if (frame.vertexCount() > 0 && !budget.hasRemaining())
					{
						return false;
					}
					activeFrameTask = new AnimationFrameUploadTask(frame);
				}
				if (activeFrameTask.uploadChunk(budget))
				{
					uploadedFrames[frameIndex] = activeFrameTask.finish();
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

	private final class AnimationFrameUploadTask
	{
		private final AnimatedObjectMesh.Frame frame;
		private final float[] vertexData;
		private final int vao;
		private final int vbo;
		private int uploadedFloats;

		private AnimationFrameUploadTask(AnimatedObjectMesh.Frame frame)
		{
			this.frame = frame;
			this.vertexData = frame.rawVertexData();
			if (frame.vertexCount() <= 0 || vertexData.length == 0)
			{
				vao = 0;
				vbo = 0;
				return;
			}

			vao = GL33C.glGenVertexArrays();
			vbo = GL33C.glGenBuffers();
			GL33C.glBindVertexArray(vao);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, (long) vertexData.length * Float.BYTES, GL33C.GL_STATIC_DRAW);
			installTerrainAttributes();
			GL33C.glBindVertexArray(0);
		}

		private boolean uploadChunk(UploadBudget budget)
		{
			int remaining = vertexData.length - uploadedFloats;
			if (remaining <= 0)
			{
				return true;
			}

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
			return uploadedFloats >= vertexData.length;
		}

		private UploadedAnimationFrame finish()
		{
			return new UploadedAnimationFrame(vao, vbo, frame.vertexCount());
		}

		private void cancel(boolean releaseVertexData)
		{
			if (releaseVertexData)
			{
				frame.releaseVertexData();
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
	}

	private static final class UploadBudget
	{
		private int remainingFloats;

		private UploadBudget(int remainingFloats)
		{
			this.remainingFloats = remainingFloats;
		}

		private int take(int requestedFloats)
		{
			int floats = Math.min(requestedFloats, remainingFloats);
			remainingFloats -= floats;
			return floats;
		}

		private boolean hasRemaining()
		{
			return remainingFloats > 0;
		}
	}

	private record UploadedAnimatedObject(
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
			if (selected != null && selected.vertexCount() > 0)
			{
				return selected;
			}
			for (int i = frame - 1; i >= 0; i--)
			{
				UploadedAnimationFrame fallback = frames[i];
				if (fallback != null && fallback.vertexCount() > 0)
				{
					return fallback;
				}
			}
			for (int i = frame + 1; i < frames.length; i++)
			{
				UploadedAnimationFrame fallback = frames[i];
				if (fallback != null && fallback.vertexCount() > 0)
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
		int vertexCount
	)
	{
		private static final UploadedAnimationFrame EMPTY = new UploadedAnimationFrame(0, 0, 0);

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
