/*
 * Copyright (c) 2026, Xeon <https://github.com/Avexiis>
 * Copyright (c) 2022-2023, dennisdev
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

final class HdosByteBuffer
{
	private final byte[] data;
	int offset;

	HdosByteBuffer(byte[] data)
	{
		this.data = data == null ? new byte[0] : data;
	}

	int length()
	{
		return data.length;
	}

	int remaining()
	{
		return data.length - offset;
	}

	byte readByte()
	{
		require(1);
		return data[offset++];
	}

	int readUnsignedByte()
	{
		return readByte() & 0xFF;
	}

	short readShort()
	{
		return (short) readUnsignedShort();
	}

	int readUnsignedShort()
	{
		require(2);
		return (readUnsignedByteUnchecked() << 8) | readUnsignedByteUnchecked();
	}

	int readMedium()
	{
		require(3);
		return (readUnsignedByteUnchecked() << 16) | (readUnsignedByteUnchecked() << 8) | readUnsignedByteUnchecked();
	}

	int readInt()
	{
		require(4);
		return (readUnsignedByteUnchecked() << 24)
			| (readUnsignedByteUnchecked() << 16)
			| (readUnsignedByteUnchecked() << 8)
			| readUnsignedByteUnchecked();
	}

	int readSmart2()
	{
		return peekByte() >= 0 ? readUnsignedByte() - 64 : readUnsignedShort() - 0xC000;
	}

	int peekByte()
	{
		require(1);
		return data[offset];
	}

	void skip(int amount)
	{
		require(amount);
		offset += amount;
	}

	private int readUnsignedByteUnchecked()
	{
		return data[offset++] & 0xFF;
	}

	private void require(int amount)
	{
		if (amount < 0 || offset < 0 || offset + amount > data.length)
		{
			throw new IllegalArgumentException("need=" + amount + " offset=" + offset + " remaining=" + remaining());
		}
	}
}
