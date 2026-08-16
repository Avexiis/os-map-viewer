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

import java.util.Arrays;

record AnimatedObjectMesh(
	int sequenceId,
	int[] frameLengths,
	int frameStep,
	int phaseOffset,
	Frame[] frames
)
{
	AnimatedObjectMesh
	{
		frameLengths = frameLengths == null ? new int[0] : Arrays.copyOf(frameLengths, frameLengths.length);
		frames = frames == null ? new Frame[0] : Arrays.copyOf(frames, frames.length);
	}

	int frameCount()
	{
		return frames.length;
	}

	void releaseVertexData()
	{
		for (Frame frame : frames)
		{
			if (frame != null)
			{
				frame.releaseVertexData();
			}
		}
	}

	static final class Frame
	{
		private float[] vertexData;
		private final int vertexCount;

		Frame(float[] vertexData, int vertexCount)
		{
			this.vertexData = vertexData == null ? new float[0] : vertexData;
			this.vertexCount = vertexCount;
		}

		float[] rawVertexData()
		{
			return vertexData;
		}

		int vertexCount()
		{
			return vertexCount;
		}

		void releaseVertexData()
		{
			vertexData = new float[0];
		}
	}
}
