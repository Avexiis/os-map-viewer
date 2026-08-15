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

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
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
			gl_Position.z += aDepthBias / 128.0;
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
			vec3 lightDirection = normalize(vec3(-0.45, 0.88, 0.36));
			float diffuse = max(dot(normal, lightDirection), 0.0);
			float shade = 0.42 + diffuse * 0.62;
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
	private static final int MAX_REAL_REGION_UPLOADS_PER_FRAME = 1;
	private static final float OUTLINE_HEIGHT_OFFSET = SceneScale.SCENE_TO_WORLD * 1.5f;
	private static final float REGION_CULL_PADDING = 2.0f;

	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f view = new Matrix4f();
	private final Matrix4f mvp = new Matrix4f();
	private final FrustumIntersection frustum = new FrustumIntersection();
	private final Map<Integer, UploadedRegion> uploadedRegions = new HashMap<>();
	private final Map<Integer, TerrainMesh> pendingUploadRegions = new LinkedHashMap<>();
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
	private int terrainTextureArray;
	private int uploadedTextureLayerCount;
	private int outlineVao;
	private int outlineVbo;
	private String glVendor = "Unavailable";
	private String glRenderer = "Unavailable";
	private String glVersion = "Unavailable";
	private TerrainRenderStats renderStats = TerrainRenderStats.unavailable();
	private boolean initialized;
	private long startNanos;

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
		GL33C.glEnable(GL33C.GL_MULTISAMPLE);
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

	boolean isInitialized()
	{
		return initialized;
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
		GL33C.glViewport(0, 0, safeWidth, safeHeight);
		GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_DEPTH_BUFFER_BIT);

		RenderCounts renderCounts = RenderCounts.empty();
		int outlineDrawCalls = 0;
		if (!uploadedRegions.isEmpty())
		{
			uploadPendingRegions();
			prepareMatrices(camera, safeWidth / (float) safeHeight);
			renderCounts = renderTerrain(camera);
			outlineDrawCalls = renderHoveredTile();
		}
		else
		{
			uploadPendingRegions();
		}
		updateRenderStats(renderCounts, outlineDrawCalls);
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
		initialized = false;
	}

	private void prepareMatrices(FreeCamera camera, float aspectRatio)
	{
		projection.identity().perspective(
			SceneScale.CAMERA_FOV_RADIANS,
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
		GL33C.glUniform1f(terrainTimeLocation, (System.nanoTime() - startNanos) / 1_000_000_000.0f);
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
			if (region.vertexCount() <= 0)
			{
				continue;
			}
			if (!isVisible(region))
			{
				culledRegions++;
				continue;
			}
			GL33C.glUniform3f(terrainRegionOffsetLocation, region.offsetX(), 0.0f, region.offsetZ());
			GL33C.glBindVertexArray(region.vao());
			GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, region.vertexCount());
			drawCalls++;
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

	private void updateRenderStats(RenderCounts renderCounts, int outlineDrawCalls)
	{
		renderStats = new TerrainRenderStats(
			renderCounts.drawCalls() + outlineDrawCalls,
			renderCounts.visibleRegions(),
			renderCounts.culledRegions(),
			uploadedRegions.size(),
			pendingUploadRegions.size(),
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
			if (mesh.vertexCount() == 0)
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

			if (uploadedRegion == null || uploadedRegion.vertexCount() == 0)
			{
				pendingUploadRegions.putIfAbsent(mesh.regionId(), mesh);
			}
		}
		currentScene = scene;
	}

	private void uploadPendingRegions()
	{
		if (currentScene == null || pendingUploadRegions.isEmpty())
		{
			return;
		}

		int uploads = 0;
		while (uploads < MAX_REAL_REGION_UPLOADS_PER_FRAME && !pendingUploadRegions.isEmpty())
		{
			Map.Entry<Integer, TerrainMesh> entry = pendingUploadRegions.entrySet().iterator().next();
			pendingUploadRegions.remove(entry.getKey());
			TerrainMesh mesh = currentScene.mesh(entry.getKey());
			if (mesh == null || mesh != entry.getValue())
			{
				entry.getValue().releaseVertexData();
				continue;
			}

			UploadedRegion previous = uploadedRegions.remove(entry.getKey());
			if (previous != null)
			{
				previous.delete();
			}
			uploadedRegions.put(entry.getKey(), uploadRegion(currentScene, mesh));
			uploads++;
		}
	}

	private UploadedRegion uploadRegion(TerrainScene scene, TerrainMesh mesh)
	{
		float offsetX = scene.offsetX(mesh);
		float offsetZ = scene.offsetZ(mesh);
		if (mesh.vertexCount() == 0)
		{
			return UploadedRegion.empty(mesh.regionId(), offsetX, offsetZ);
		}

		float[] vertexData = mesh.rawVertexData();
		if (vertexData.length == 0)
		{
			return UploadedRegion.empty(mesh.regionId(), offsetX, offsetZ);
		}
		FloatBuffer buffer = MemoryUtil.memAllocFloat(vertexData.length);
		int vao = GL33C.glGenVertexArrays();
		int vbo = GL33C.glGenBuffers();
		try
		{
			buffer.put(vertexData).flip();
			GL33C.glBindVertexArray(vao);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo);
			GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_STATIC_DRAW);
			installTerrainAttributes();
			GL33C.glBindVertexArray(0);
		}
		finally
		{
			MemoryUtil.memFree(buffer);
		}

		float minY = Float.POSITIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < vertexData.length; i += TerrainMesh.FLOATS_PER_VERTEX)
		{
			minY = Math.min(minY, vertexData[i + 1]);
			maxY = Math.max(maxY, vertexData[i + 1]);
		}
		if (!Float.isFinite(minY) || !Float.isFinite(maxY))
		{
			minY = -64.0f;
			maxY = 64.0f;
		}

		float minX = offsetX - SceneScale.REGION_CENTER_TILES;
		float maxX = offsetX + SceneScale.REGION_CENTER_TILES;
		float minZ = offsetZ - SceneScale.REGION_CENTER_TILES;
		float maxZ = offsetZ + SceneScale.REGION_CENTER_TILES;
		UploadedRegion uploadedRegion = new UploadedRegion(
			mesh.regionId(),
			vao,
			vbo,
			mesh.vertexCount(),
			offsetX,
			offsetZ,
			minX,
			minY,
			minZ,
			maxX,
			maxY,
			maxZ
		);
		mesh.releaseVertexData();
		return uploadedRegion;
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

	private void deleteUploadedRegions()
	{
		for (TerrainMesh mesh : pendingUploadRegions.values())
		{
			mesh.releaseVertexData();
		}
		pendingUploadRegions.clear();
		for (UploadedRegion region : uploadedRegions.values())
		{
			region.delete();
		}
		uploadedRegions.clear();
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
		float maxZ
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
				0.0f
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
		}
	}
}
