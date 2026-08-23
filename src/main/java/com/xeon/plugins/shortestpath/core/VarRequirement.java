/*
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
package com.xeon.plugins.shortestpath.core;

import java.util.Map;
import java.util.Objects;

public final class VarRequirement
{
	public enum VarType
	{
		VARBIT,
		VARPLAYER
	}

	enum CheckType
	{
		BIT_SET("&"),
		COOLDOWN_MINUTES("@"),
		EQUAL("="),
		GREATER(">"),
		SMALLER("<");

		private final String code;

		CheckType(String code)
		{
			this.code = code;
		}

		String code()
		{
			return code;
		}
	}

	private final VarType type;
	private final int id;
	private final int value;
	private final CheckType checkType;

	VarRequirement(VarType type, int id, int value, CheckType checkType)
	{
		this.type = Objects.requireNonNull(type, "type");
		this.id = id;
		this.value = value;
		this.checkType = Objects.requireNonNull(checkType, "checkType");
	}

	public VarType type()
	{
		return type;
	}

	public int id()
	{
		return id;
	}

	public boolean check(Map<Integer, Integer> values)
	{
		Integer currentValue = values == null ? null : values.get(id);
		return currentValue != null && checkValue(currentValue);
	}

	public boolean checkValue(int currentValue)
	{
		return switch (checkType)
		{
			case EQUAL -> currentValue == value;
			case GREATER -> currentValue > value;
			case SMALLER -> currentValue < value;
			case BIT_SET -> (currentValue & value) > 0;
			case COOLDOWN_MINUTES -> ((System.currentTimeMillis() / 60_000L) - currentValue) > value;
		};
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof VarRequirement that))
		{
			return false;
		}
		return id == that.id && value == that.value && type == that.type && checkType == that.checkType;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(type, id, value, checkType);
	}

	@Override
	public String toString()
	{
		return type + "[" + id + checkType.code() + value + "]";
	}
}
