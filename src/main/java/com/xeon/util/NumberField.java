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
package com.xeon.util;

import javax.swing.*;
import javax.swing.text.*;
import java.text.ParseException;

public class NumberField extends JFormattedTextField
{
	public NumberField(int columns)
	{
		super(new NumberFormatter());
		setColumns(columns);
	}

	public Integer getInt(Integer def)
	{
		try
		{
			commitEdit();
		}
		catch (ParseException ignore)
		{
		}
		Object v = getValue();
		if (v instanceof Number)
		{
			return ((Number) v).intValue();
		}
		try
		{
			return Integer.parseInt(getText().trim());
		}
		catch (Exception e)
		{
			return def;
		}
	}

	public void setInt(Integer v)
	{
		if (v == null)
		{
			setText("");
		}
		else
		{
			setValue(v);
		}
	}

	private static class NumberFormatter extends DefaultFormatter
	{
		@Override
		public Object stringToValue(String string) throws ParseException
		{
			if (string == null || string.trim().isEmpty())
			{
				return null;
			}
			try
			{
				return Integer.parseInt(string.trim());
			}
			catch (NumberFormatException e)
			{
				throw new ParseException("Not a number", 0);
			}
		}

		@Override
		public String valueToString(Object value) throws ParseException
		{
			if (value == null)
			{
				return "";
			}
			return String.valueOf(value);
		}
	}
}
