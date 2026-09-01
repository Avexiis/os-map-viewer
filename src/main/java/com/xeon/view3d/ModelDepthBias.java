/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
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

import java.util.List;
import net.runelite.cache.definitions.ModelDefinition;

final class ModelDepthBias
{
	private static final int PACKED_RENDER_PRIORITY_MASK = 0x7;
	private static final float BASE_FACE_PRIORITY = 1.0f;
	private static final float FACE_Z_OFFSET_BIAS = 1.0f;
	private static final float RENDER_PRIORITY_BIAS = 1.0f;
	private static final float FACE_ORDER_TIE_BIAS = 0.75f;

	private ModelDepthBias()
	{
	}

	static float forFace(ModelDefinition model, int face)
	{
		return forFace(model, face, false);
	}

	static float forFace(ModelDefinition model, int face, boolean materializeDefaultPriority)
	{
		if (model == null)
		{
			return 0.0f;
		}
		return BASE_FACE_PRIORITY
			+ faceZOffset(model, face) * FACE_Z_OFFSET_BIAS
			+ renderPriority(model, face, materializeDefaultPriority) * RENDER_PRIORITY_BIAS
			+ faceOrderTieBias(model, face);
	}

	static float forObjectFace(ModelDefinition model, int face, int scenePriority, boolean materializeDefaultPriority)
	{
		return packedPriority(scenePriority) + forFace(model, face, materializeDefaultPriority);
	}

	static float forNpcFace(ModelDefinition model, int face)
	{
		return 20.0f + forFace(model, face);
	}

	static byte effectivePriority(ModelDefinition model, int face)
	{
		return (byte) renderPriority(model, face, true);
	}

	static int defaultPriority(ModelDefinition model)
	{
		return model == null || model.priority < 0 ? 0 : packedPriority(model.priority & 0xFF);
	}

	static boolean materializeDefaultPriorities(List<ModelDefinition> models)
	{
		if (models == null || models.isEmpty())
		{
			return false;
		}

		boolean hasExplicitPriorities = false;
		int sharedPriority = -1;
		for (ModelDefinition model : models)
		{
			if (model == null)
			{
				continue;
			}
			if (model.faceRenderPriorities != null)
			{
				hasExplicitPriorities = true;
				continue;
			}

			int priority = defaultPriority(model);
			if (sharedPriority < 0)
			{
				sharedPriority = priority;
			}
			else if (sharedPriority != priority)
			{
				return true;
			}
		}
		return hasExplicitPriorities;
	}

	private static int faceZOffset(ModelDefinition model, int face)
	{
		if (model.faceZOffsets != null && face >= 0 && face < model.faceZOffsets.length)
		{
			return model.faceZOffsets[face] & 0xFF;
		}
		return 0;
	}

	private static int renderPriority(ModelDefinition model, int face, boolean materializeDefaultPriority)
	{
		if (model.faceRenderPriorities != null && face >= 0 && face < model.faceRenderPriorities.length)
		{
			return packedPriority(model.faceRenderPriorities[face] & 0xFF);
		}
		return materializeDefaultPriority ? defaultPriority(model) : 0;
	}

	private static float faceOrderTieBias(ModelDefinition model, int face)
	{
		if (face <= 0 || model.faceCount <= 1)
		{
			return 0.0f;
		}
		return Math.min(face, model.faceCount - 1) * FACE_ORDER_TIE_BIAS / (model.faceCount - 1);
	}

	private static int packedPriority(int priority)
	{
		return priority & PACKED_RENDER_PRIORITY_MASK;
	}
}
