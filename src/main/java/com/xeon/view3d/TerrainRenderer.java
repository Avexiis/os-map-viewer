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

		uniform mat4 uMvp;
		uniform vec3 uCameraPosition;

		out vec3 vColor;
		out vec3 vNormal;
		out float vDistance;

		void main()
		{
			vec4 worldPosition = vec4(aPosition, 1.0);
			vColor = aColor;
			vNormal = aNormal;
			vDistance = distance(aPosition, uCameraPosition);
			gl_Position = uMvp * worldPosition;
		}
		""";
	private static final String TERRAIN_FRAGMENT_SHADER = """
		#version 330 core
		in vec3 vColor;
		in vec3 vNormal;
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
			fragColor = vec4(color, 1.0);
		}
		""";
	private static final String CROSSHAIR_VERTEX_SHADER = """
		#version 330 core
		layout(location = 0) in vec2 aPosition;

		void main()
		{
			gl_Position = vec4(aPosition, 0.0, 1.0);
		}
		""";
	private static final String CROSSHAIR_FRAGMENT_SHADER = """
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
	private static final int CROSSHAIR_VERTICES = 8;

	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f view = new Matrix4f();
	private final Matrix4f mvp = new Matrix4f();
	private TerrainMesh pendingMesh;
	private int terrainProgram;
	private int terrainMvpLocation;
	private int terrainCameraLocation;
	private int crosshairProgram;
	private int crosshairColorLocation;
	private int terrainVao;
	private int terrainVbo;
	private int crosshairVao;
	private int crosshairVbo;
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
		GL33C.glEnable(GL33C.GL_MULTISAMPLE);
		GL33C.glClearColor(0.43f, 0.53f, 0.62f, 1.0f);

		terrainProgram = createProgram(TERRAIN_VERTEX_SHADER, TERRAIN_FRAGMENT_SHADER);
		terrainMvpLocation = GL33C.glGetUniformLocation(terrainProgram, "uMvp");
		terrainCameraLocation = GL33C.glGetUniformLocation(terrainProgram, "uCameraPosition");
		crosshairProgram = createProgram(CROSSHAIR_VERTEX_SHADER, CROSSHAIR_FRAGMENT_SHADER);
		crosshairColorLocation = GL33C.glGetUniformLocation(crosshairProgram, "uColor");
		initialized = true;
	}

	void setMesh(TerrainMesh mesh)
	{
		pendingMesh = mesh;
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
			renderTerrain(camera, safeWidth / (float) safeHeight);
		}
		renderCrosshair(safeWidth, safeHeight);
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
		if (crosshairVbo != 0)
		{
			GL33C.glDeleteBuffers(crosshairVbo);
			crosshairVbo = 0;
		}
		if (crosshairVao != 0)
		{
			GL33C.glDeleteVertexArrays(crosshairVao);
			crosshairVao = 0;
		}
		if (terrainProgram != 0)
		{
			GL33C.glDeleteProgram(terrainProgram);
			terrainProgram = 0;
		}
		if (crosshairProgram != 0)
		{
			GL33C.glDeleteProgram(crosshairProgram);
			crosshairProgram = 0;
		}
		initialized = false;
	}

	private void renderTerrain(FreeCamera camera, float aspectRatio)
	{
		Vector3fc cameraPosition = camera.position();
		projection.identity().perspective((float) Math.toRadians(68.0), aspectRatio, 0.1f, 260.0f);
		camera.viewMatrix(view);
		projection.mul(view, mvp);

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

	private void renderCrosshair(int width, int height)
	{
		if (crosshairVao == 0)
		{
			crosshairVao = GL33C.glGenVertexArrays();
			crosshairVbo = GL33C.glGenBuffers();
			GL33C.glBindVertexArray(crosshairVao);
			GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, crosshairVbo);
			GL33C.glEnableVertexAttribArray(0);
			GL33C.glVertexAttribPointer(0, 2, GL33C.GL_FLOAT, false, 2 * Float.BYTES, 0L);
			GL33C.glBindVertexArray(0);
		}

		float gapX = 5.0f * 2.0f / width;
		float gapY = 5.0f * 2.0f / height;
		float lengthX = 16.0f * 2.0f / width;
		float lengthY = 16.0f * 2.0f / height;
		float[] vertices = new float[]{
			-lengthX, 0.0f,
			-gapX, 0.0f,
			gapX, 0.0f,
			lengthX, 0.0f,
			0.0f, -lengthY,
			0.0f, -gapY,
			0.0f, gapY,
			0.0f, lengthY
		};

		FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
		buffer.put(vertices).flip();

		GL33C.glDisable(GL33C.GL_DEPTH_TEST);
		GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, crosshairVbo);
		GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_DYNAMIC_DRAW);
		GL33C.glUseProgram(crosshairProgram);
		GL33C.glBindVertexArray(crosshairVao);
		GL33C.glUniform4f(crosshairColorLocation, 0.0f, 0.0f, 0.0f, 0.75f);
		GL33C.glLineWidth(4.0f);
		GL33C.glDrawArrays(GL33C.GL_LINES, 0, CROSSHAIR_VERTICES);
		GL33C.glUniform4f(crosshairColorLocation, 1.0f, 1.0f, 1.0f, 0.95f);
		GL33C.glLineWidth(2.0f);
		GL33C.glDrawArrays(GL33C.GL_LINES, 0, CROSSHAIR_VERTICES);
		GL33C.glBindVertexArray(0);
		GL33C.glUseProgram(0);
		GL33C.glEnable(GL33C.GL_DEPTH_TEST);
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
		GL33C.glVertexAttribPointer(1, NORMAL_FLOATS, GL33C.GL_FLOAT, false, stride, POSITION_FLOATS * Float.BYTES);
		GL33C.glEnableVertexAttribArray(2);
		GL33C.glVertexAttribPointer(
			2,
			COLOR_FLOATS,
			GL33C.GL_FLOAT,
			false,
			stride,
			(POSITION_FLOATS + NORMAL_FLOATS) * Float.BYTES
		);
		GL33C.glBindVertexArray(0);
		vertexCount = mesh.vertexCount();
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
