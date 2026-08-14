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

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class FreeCamera
{
	private static final Vector3f WORLD_UP = new Vector3f(0.0f, 1.0f, 0.0f);
	private static final float MOVE_SPEED = 22.0f;
	private static final float MOUSE_SENSITIVITY = 0.12f;
	private static final float MIN_PITCH = -88.0f;
	private static final float MAX_PITCH = 88.0f;

	private final Vector3f position = new Vector3f();
	private boolean forward;
	private boolean backward;
	private boolean left;
	private boolean right;
	private boolean raise;
	private boolean lower;
	private float yaw;
	private float pitch;

	FreeCamera()
	{
		reset(0.0f, 22.0f, -76.0f);
	}

	void reset(float x, float y, float z)
	{
		position.set(x, y, z);
		yaw = 0.0f;
		pitch = -12.0f;
		forward = false;
		backward = false;
		left = false;
		right = false;
		raise = false;
		lower = false;
	}

	void update(float deltaSeconds)
	{
		float distance = MOVE_SPEED * deltaSeconds;
		Vector3f direction = direction(new Vector3f());
		Vector3f rightVector = direction.cross(WORLD_UP, new Vector3f()).normalize();

		if (forward)
		{
			position.fma(distance, direction);
		}
		if (backward)
		{
			position.fma(-distance, direction);
		}
		if (right)
		{
			position.fma(distance, rightVector);
		}
		if (left)
		{
			position.fma(-distance, rightVector);
		}
		if (raise)
		{
			position.y += distance;
		}
		if (lower)
		{
			position.y -= distance;
		}
	}

	void rotate(float deltaX, float deltaY)
	{
		yaw -= deltaX * MOUSE_SENSITIVITY;
		pitch -= deltaY * MOUSE_SENSITIVITY;
		if (pitch < MIN_PITCH)
		{
			pitch = MIN_PITCH;
		}
		else if (pitch > MAX_PITCH)
		{
			pitch = MAX_PITCH;
		}
	}

	Matrix4f viewMatrix(Matrix4f destination)
	{
		Vector3f target = direction(new Vector3f()).add(position);
		return destination.identity().lookAt(position, target, WORLD_UP);
	}

	Vector3fc position()
	{
		return position;
	}

	void setForward(boolean value)
	{
		forward = value;
	}

	void setBackward(boolean value)
	{
		backward = value;
	}

	void setLeft(boolean value)
	{
		left = value;
	}

	void setRight(boolean value)
	{
		right = value;
	}

	void setRaise(boolean value)
	{
		raise = value;
	}

	void setLower(boolean value)
	{
		lower = value;
	}

	private Vector3f direction(Vector3f destination)
	{
		float yawRadians = (float) Math.toRadians(yaw);
		float pitchRadians = (float) Math.toRadians(pitch);
		float pitchCos = (float) Math.cos(pitchRadians);
		destination.x = (float) Math.sin(yawRadians) * pitchCos;
		destination.y = (float) Math.sin(pitchRadians);
		destination.z = (float) Math.cos(yawRadians) * pitchCos;
		return destination.normalize();
	}
}
