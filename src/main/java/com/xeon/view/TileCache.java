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
package com.xeon.view;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

final class TileCache extends LinkedHashMap<String, BufferedImage>
{
	private static final long MIN_CACHE_BUDGET_BYTES = 512L * 1024L * 1024L;

	private long budgetBytes;
	private long liveBytes = 0L;

	TileCache(long budgetBytes)
	{
		super(512, 0.75f, true);
		this.budgetBytes = normalizeBudgetBytes(budgetBytes);
	}

	synchronized void setBudgetBytes(long budgetBytes)
	{
		this.budgetBytes = normalizeBudgetBytes(budgetBytes);
		trimToBudget();
	}

	synchronized long budgetBytes()
	{
		return budgetBytes;
	}

	static String key(int plane, int lod, int tx, int ty)
	{
		return plane + ":" + lod + ":" + tx + ":" + ty;
	}

	synchronized BufferedImage get(int plane, int lod, int tx, int ty)
	{
		return super.get(key(plane, lod, tx, ty));
	}

	synchronized void put(int plane, int lod, int tx, int ty, BufferedImage img)
	{
		BufferedImage previous = super.put(key(plane, lod, tx, ty), img);
		if (previous != null)
		{
			liveBytes -= approx(previous);
		}
		if (img != null)
		{
			liveBytes += approx(img);
		}
		trimToBudget();
	}

	private void trimToBudget()
	{
		while (liveBytes > budgetBytes && !isEmpty())
		{
			Map.Entry<String, BufferedImage> eldest = entrySet().iterator().next();
			BufferedImage removed = super.remove(eldest.getKey());
			if (removed != null)
			{
				liveBytes -= approx(removed);
			}
		}
	}

	@Override
	public synchronized BufferedImage remove(Object key)
	{
		BufferedImage previous = super.remove(key);
		if (previous != null)
		{
			liveBytes -= approx(previous);
		}
		return previous;
	}

	private static long approx(BufferedImage image)
	{
		return image == null ? 0 : (long) image.getWidth() * (long) image.getHeight() * 4L;
	}

	private static long normalizeBudgetBytes(long budgetBytes)
	{
		return budgetBytes == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(MIN_CACHE_BUDGET_BYTES, budgetBytes);
	}
}
