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
package com.xeon.plugins.groundmarkers;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

public final class GroundMarkerToolbarPanel extends JPopupMenu
{
	public enum ExportAction
	{
		CURRENT_TO_CLIPBOARD("Current region to clipboard"),
		ALL_TO_CLIPBOARD("All markers to clipboard"),
		SELECTED_TO_CLIPBOARD("Selected regions to clipboard"),
		CURRENT_TO_FILE("Current region to file"),
		ALL_TO_FILE("All markers to file"),
		SELECTED_TO_FILE("Selected regions to file"),
		VISIBLE_AREA_TO_FILE("Export Visible Area");

		private final String label;

		ExportAction(String label)
		{
			this.label = label;
		}

		public String label()
		{
			return label;
		}
	}

	public final Event<ExportAction> onExport = new Event<>();

	private Consumer<ActionEvent> onImportRuneLite;
	private Consumer<ActionEvent> onImportHdos;
	private Consumer<ActionEvent> onImportJson;
	private JMenu exportMenu;
	private boolean viewer3DMode;

	public GroundMarkerToolbarPanel()
	{
		buildMenu();
	}

	public void setOnImportRuneLite(Consumer<ActionEvent> onImportRuneLite)
	{
		this.onImportRuneLite = onImportRuneLite;
	}

	public void setOnImportHdos(Consumer<ActionEvent> onImportHdos)
	{
		this.onImportHdos = onImportHdos;
	}

	public void setOnImportJson(Consumer<ActionEvent> onImportJson)
	{
		this.onImportJson = onImportJson;
	}

	private void buildMenu()
	{
		JMenuItem importRuneLite = new JMenuItem("Import from RuneLite");
		importRuneLite.addActionListener(e -> {
			if (onImportRuneLite != null)
			{
				onImportRuneLite.accept(e);
			}
		});
		JMenuItem importHdos = new JMenuItem("Import from HDOS");
		importHdos.addActionListener(e -> {
			if (onImportHdos != null)
			{
				onImportHdos.accept(e);
			}
		});
		JMenuItem importJson = new JMenuItem("Import JSON file");
		importJson.addActionListener(e -> {
			if (onImportJson != null)
			{
				onImportJson.accept(e);
			}
		});
		add(importRuneLite);
		add(importHdos);
		add(importJson);
		addSeparator();

		exportMenu = new JMenu("Export");
		rebuildExportMenu();
		add(exportMenu);
	}

	public void setViewer3DMode(boolean viewer3DMode)
	{
		if (this.viewer3DMode == viewer3DMode)
		{
			return;
		}
		this.viewer3DMode = viewer3DMode;
		rebuildExportMenu();
	}

	private void rebuildExportMenu()
	{
		if (exportMenu == null)
		{
			return;
		}
		exportMenu.removeAll();
		if (viewer3DMode)
		{
			addExportItem(ExportAction.VISIBLE_AREA_TO_FILE);
			return;
		}
		for (ExportAction action : ExportAction.values())
		{
			if (action != ExportAction.VISIBLE_AREA_TO_FILE)
			{
				addExportItem(action);
			}
		}
	}

	private void addExportItem(ExportAction action)
	{
		JMenuItem item = new JMenuItem(action.label());
		item.addActionListener(e -> onExport.emit(action));
		exportMenu.add(item);
	}

	public static final class Event<T>
	{
		private final List<Consumer<T>> listeners = new ArrayList<>();

		public void addListener(Consumer<T> listener)
		{
			listeners.add(listener);
		}

		public void emit(T value)
		{
			for (Consumer<T> listener : listeners)
			{
				listener.accept(value);
			}
		}
	}
}
