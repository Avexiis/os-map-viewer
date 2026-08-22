/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.

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

final class NpcDefinition3D
{
	static final int DEFAULT_SPAWN_DIRECTION = 6;

	final int id;
	String name = "null";
	int size = 1;
	int[] models;
	int standingAnimation = -1;
	int walkingAnimation = -1;
	int rotate180Animation = -1;
	int rotateLeftAnimation = -1;
	int rotateRightAnimation = -1;
	int runAnimation = -1;
	int runRotate180Animation = -1;
	int runRotateLeftAnimation = -1;
	int runRotateRightAnimation = -1;
	int crawlAnimation = -1;
	int crawlRotate180Animation = -1;
	int crawlRotateLeftAnimation = -1;
	int crawlRotateRightAnimation = -1;
	short[] recolorToFind;
	short[] recolorToReplace;
	short[] retextureToFind;
	short[] retextureToReplace;
	int widthScale = 128;
	int heightScale = 128;
	int ambient;
	int contrast;
	int combatLevel = -1;
	int rotationSpeed = 32;
	int[] configs;
	int varbitId = -1;
	int varpIndex = -1;
	boolean isInteractable = true;
	boolean isClickable = true;
	int loginScreenProps;
	int spawnDirection = DEFAULT_SPAWN_DIRECTION;
	int footprintSize = -1;

	NpcDefinition3D(int id)
	{
		this.id = id;
	}

	boolean hasModels()
	{
		return models != null && models.length > 0;
	}

	int idleSequenceId()
	{
		return standingAnimation;
	}

	int walkSequenceId()
	{
		return walkingAnimation;
	}

	int size()
	{
		return Math.max(1, size);
	}

	int[] modelIds()
	{
		return models == null ? new int[0] : Arrays.copyOf(models, models.length);
	}
}
