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

import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

final class TerrainRenderer
{
	private static final String TERRAIN_VERTEX_SHADER = """
		#version 330 core
		layout(location = 0) in vec3 aPosition;
		layout(location = 1) in vec3 aNormal;
		layout(location = 2) in vec3 aColor;
		layout(location = 3) in float aAlpha;
		layout(location = 4) in float aDepthBias;

		uniform mat4 uMvp;
		uniform vec3 uCameraPosition;

		out vec3 vColor;
		out vec3 vNormal;
		out float vAlpha;
		out float vDistance;

		void main()
		{
			vec4 worldPosition = vec4(aPosition, 1.0);
			vColor = aColor;
			vNormal = aNormal;
			vAlpha = aAlpha;
			vDistance = distance(aPosition, uCameraPosition);
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

		out vec4 fragColor;

		void main()
		{
			vec3 normal = normalize(vNormal);
			vec3 lightDirection = normalize(vec3(-0.45, 0.88, 0.36));
			float diffuse = max(dot(normal, lightDirection), 0.0);
			float shade = 0.42 + diffuse * 0.62;
			vec3 sky = vec3(0.43, 0.53, 0.62);
			float fog = smoothstep(82.0, 138.0, vDistance);
			vec3 color = mix(vColor * shade, sky, fog);
			if (vAlpha <= 0.01)
			{
				discard;
			}
			fragColor = vec4(color, vAlpha);
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
	private static final int OUTLINE_VERTICES = 8;
	private static final float OUTLINE_HEIGHT_OFFSET = SceneScale.SCENE_TO_WORLD * 1.5f;

	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f view = new Matrix4f();
	private final Matrix4f mvp = new Matrix4f();
	private TerrainMesh pendingMesh;
	private TerrainMesh currentMesh;
	private HoveredTile hoveredTile;
	private int terrainProgram;
	private int terrainMvpLocation;
	private int terrainCameraLocation;
	private int outlineProgram;
	private int outlineMvpLocation;
	private int outlineColorLocation;
	private int terrainVao;
	private int terrainVbo;
	private int outlineVao;
	private int outlineVbo;
	private int vertexCount;
	private boolean initialized;

	void init()
	{
		if (initialized)
		{
			return;
		}

		GL.createCapabilities();
		GL33C.glEnable(GL33C.GL_DEPTH_TEST);
		GL33C.glDepthFunc(GL33C.GL_LEQUAL);
		GL33C.glEnable(GL33C.GL_MULTISAMPLE);
		GL33C.glEnable(GL33C.GL_BLEND);
		GL33C.glBlendFunc(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA);
		GL33C.glClearColor(0.43f, 0.53f, 0.62f, 1.0f);

		terrainProgram = createProgram(TERRAIN_VERTEX_SHADER, TERRAIN_FRAGMENT_SHADER);
		terrainMvpLocation = GL33C.glGetUniformLocation(terrainProgram, "uMvp");
		terrainCameraLocation = GL33C.glGetUniformLocation(terrainProgram, "uCameraPosition");
		outlineProgram = createProgram(OUTLINE_VERTEX_SHADER, OUTLINE_FRAGMENT_SHADER);
		outlineMvpLocation = GL33C.glGetUniformLocation(outlineProgram, "uMvp");
		outlineColorLocation = GL33C.glGetUniformLocation(outlineProgram, "uColor");
		initialized = true;
	}

	void setMesh(TerrainMesh mesh)
	{
		pendingMesh = mesh;
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
		if (pendingMesh != null)
		{
			uploadMesh(pendingMesh);
			pendingMesh = null;
		}

		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		GL33C.glViewport(0, 0, safeWidth, safeHeight);
		GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_DEPTH_BUFFER_BIT);

		if (vertexCount > 0)
		{
			prepareMatrices(camera, safeWidth / (float) safeHeight);
			renderTerrain(camera);
			renderHoveredTile();
		}
	}

	void dispose()
	{
		if (!initialized)
		{
			return;
		}
		if (terrainVbo != 0)
		{
			GL33C.glDeleteBuffers(terrainVbo);
			terrainVbo = 0;
		}
		if (terrainVao != 0)
		{
			GL33C.glDeleteVertexArrays(terrainVao);
			terrainVao = 0;
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
	}

	private void renderTerrain(FreeCamera camera)
	{
		Vector3fc cameraPosition = camera.position();
		GL33C.glUseProgram(terrainProgram);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
			FloatBuffer matrixBuffer = stack.mallocFloat(16);
			GL33C.glUniformMatrix4fv(terrainMvpLocation, false, mvp.get(matrixBuffer));
		}
		GL33C.glUniform3f(terrainCameraLocation, cameraPosition.x(), cameraPosition.y(), cameraPosition.z());
		GL33C.glBindVertexArray(terrainVao);
		GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, vertexCount);
		GL33C.glBindVertexArray(0);
		GL33C.glUseProgram(0);
	}

	private void renderHoveredTile()
	{
		if (currentMesh == null || hoveredTile == null)
		{
			return;
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
		FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
		buffer.put(vertices).flip();

		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, outlineVbo);
		GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_DYNAMIC_DRAW);
		GL33C.glUseProgram(outlineProgram);
		try (MemoryStack stack = MemoryStack.stackPush())
		{
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
	}

	private void uploadMesh(TerrainMesh mesh)
	{
		if (terrainVbo != 0)
		{
			GL33C.glDeleteBuffers(terrainVbo);
		}
		if (terrainVao != 0)
		{
			GL33C.glDeleteVertexArrays(terrainVao);
		}

		float[] vertexData = mesh.vertexData();
		FloatBuffer buffer = BufferUtils.createFloatBuffer(vertexData.length);
		buffer.put(vertexData).flip();

		terrainVao = GL33C.glGenVertexArrays();
		terrainVbo = GL33C.glGenBuffers();
		GL33C.glBindVertexArray(terrainVao);
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, terrainVbo);
		GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_STATIC_DRAW);

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
		GL33C.glBindVertexArray(0);
		vertexCount = mesh.vertexCount();
		currentMesh = mesh;
	}

	private float[] outlineVertices()
	{
		float x0 = hoveredTile.localX();
		float y0 = hoveredTile.localY();
		float x1 = x0 + 1.0f;
		float y1 = y0 + 1.0f;
		float worldX0 = SceneScale.worldXFromTile(x0);
		float worldX1 = SceneScale.worldXFromTile(x1);
		float worldZ0 = SceneScale.worldZFromTile(y0);
		float worldZ1 = SceneScale.worldZFromTile(y1);
		float h00 = currentMesh.worldHeightAt(hoveredTile.plane(), x0, y0) + OUTLINE_HEIGHT_OFFSET;
		float h10 = currentMesh.worldHeightAt(hoveredTile.plane(), x1, y0) + OUTLINE_HEIGHT_OFFSET;
		float h11 = currentMesh.worldHeightAt(hoveredTile.plane(), x1, y1) + OUTLINE_HEIGHT_OFFSET;
		float h01 = currentMesh.worldHeightAt(hoveredTile.plane(), x0, y1) + OUTLINE_HEIGHT_OFFSET;

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
}
