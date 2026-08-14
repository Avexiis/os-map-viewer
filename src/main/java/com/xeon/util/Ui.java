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
import java.awt.*;
import java.io.File;
import java.util.Enumeration;

public class Ui
{
	public static void installGlobalFont(String name, int size)
	{
		try
		{
			Font f = new Font(name, Font.PLAIN, size);
			Enumeration<Object> keys = UIManager.getDefaults().keys();
			while (keys.hasMoreElements())
			{
				Object key = keys.nextElement();
				Object val = UIManager.get(key);
				if (val instanceof Font)
				{
					UIManager.put(key, f);
				}
			}
		}
		catch (Exception ignore)
		{
		}
	}

	public static void info(String msg)
	{
		JOptionPane.showMessageDialog(null, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
	}

	public static void warn(String msg)
	{
		JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
	}

	public static void error(String msg)
	{
		JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
	}

	public static File chooseJson(Component parent, String title)
	{
		JFileChooser ch = new JFileChooser();
		ch.setDialogTitle(title);
		if (ch.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
		{
			return ch.getSelectedFile();
		}
		return null;
	}

	public static File saveJson(Component parent, String title)
	{
		JFileChooser ch = new JFileChooser();
		ch.setDialogTitle(title);
		if (ch.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION)
		{
			return ch.getSelectedFile();
		}
		return null;
	}
}
