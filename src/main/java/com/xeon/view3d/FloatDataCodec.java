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

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class FloatDataCodec
{
	private static final int INPUT_CHUNK_BYTES = 64 * 1024;
	private static final int OUTPUT_CHUNK_BYTES = 32 * 1024;

	private FloatDataCodec()
	{
	}

	static byte[] deflate(float[] values)
	{
		return deflate(values, null);
	}

	static byte[] deflate(float[] values, Runnable pause)
	{
		if (values == null || values.length == 0)
		{
			return new byte[0];
		}

		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		byte[] input = new byte[INPUT_CHUNK_BYTES];
		byte[] output = new byte[OUTPUT_CHUNK_BYTES];
		int initialCapacity = (int) Math.min((long) values.length * 2L, 1024L * 1024L);
		ByteArrayOutputStream compressed = new ByteArrayOutputStream(initialCapacity);
		try
		{
			int index = 0;
			while (index < values.length)
			{
				pauseIfRequested(pause);
				int floats = Math.min(input.length / Float.BYTES, values.length - index);
				int inputBytes = floats * Float.BYTES;
				for (int i = 0, out = 0; i < floats; i++)
				{
					int bits = Float.floatToRawIntBits(values[index + i]);
					input[out++] = (byte) bits;
					input[out++] = (byte) (bits >>> 8);
					input[out++] = (byte) (bits >>> 16);
					input[out++] = (byte) (bits >>> 24);
				}
				deflater.setInput(input, 0, inputBytes);
				while (!deflater.needsInput())
				{
					pauseIfRequested(pause);
					int written = deflater.deflate(output);
					if (written > 0)
					{
						compressed.write(output, 0, written);
					}
				}
				index += floats;
			}

			deflater.finish();
			while (!deflater.finished())
			{
				pauseIfRequested(pause);
				int written = deflater.deflate(output);
				if (written > 0)
				{
					compressed.write(output, 0, written);
				}
			}
			return compressed.toByteArray();
		}
		finally
		{
			deflater.end();
		}
	}

	private static void pauseIfRequested(Runnable pause)
	{
		if (pause != null)
		{
			pause.run();
		}
	}

	static float[] inflate(byte[] compressed, int expectedFloats)
	{
		if (compressed == null || compressed.length == 0 || expectedFloats <= 0)
		{
			return new float[0];
		}

		byte[] inflated = new byte[Math.multiplyExact(expectedFloats, Float.BYTES)];
		Inflater inflater = new Inflater();
		try
		{
			inflater.setInput(compressed);
			int offset = 0;
			while (!inflater.finished() && offset < inflated.length)
			{
				int read = inflater.inflate(inflated, offset, inflated.length - offset);
				if (read <= 0)
				{
					if (inflater.needsInput() || inflater.needsDictionary())
					{
						break;
					}
					throw new IllegalStateException("Compressed vertex data could not be inflated.");
				}
				offset += read;
			}
			if (offset != inflated.length)
			{
				throw new IllegalStateException("Compressed vertex data length mismatch.");
			}
		}
		catch (DataFormatException ex)
		{
			throw new IllegalStateException("Compressed vertex data is invalid.", ex);
		}
		finally
		{
			inflater.end();
		}

		float[] values = new float[expectedFloats];
		for (int i = 0, in = 0; i < values.length; i++)
		{
			int bits = (inflated[in++] & 0xFF)
				| (inflated[in++] & 0xFF) << 8
				| (inflated[in++] & 0xFF) << 16
				| (inflated[in++] & 0xFF) << 24;
			values[i] = Float.intBitsToFloat(bits);
		}
		return values;
	}
}
