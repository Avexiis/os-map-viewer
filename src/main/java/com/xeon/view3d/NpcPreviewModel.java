/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer.
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

record NpcPreviewModel(
	int npcId,
	String name,
	int combatLevel,
	AnimatedObjectMesh.Frame[] idleFrames,
	int[] idleFrameLengths,
	AnimatedObjectMesh.Frame[] walkFrames,
	int[] walkFrameLengths,
	NpcMesh.Bounds bounds
)
{
	NpcPreviewModel
	{
		name = name == null ? "" : name;
		idleFrames = idleFrames == null ? new AnimatedObjectMesh.Frame[0] : idleFrames.clone();
		idleFrameLengths = idleFrameLengths == null ? new int[0] : idleFrameLengths.clone();
		walkFrames = walkFrames == null ? new AnimatedObjectMesh.Frame[0] : walkFrames.clone();
		walkFrameLengths = walkFrameLengths == null ? new int[0] : walkFrameLengths.clone();
		bounds = bounds == null ? NpcMesh.Bounds.fallback() : bounds;
	}

	boolean hasWalkAnimation()
	{
		return walkFrames.length > 0;
	}
}
