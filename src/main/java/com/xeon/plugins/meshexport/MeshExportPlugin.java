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
package com.xeon.plugins.meshexport;

import com.xeon.plugin.MapViewerPlugin;
import com.xeon.plugin.PluginContext;
import com.xeon.view3d.Map3DEntity;
import com.xeon.view3d.Map3DLayer;
import com.xeon.view3d.Map3DMesh;
import com.xeon.view3d.Map3DMouseEvent;
import com.xeon.view3d.Map3DObjectOverlay;
import com.xeon.view3d.Map3DOverlay;
import com.xeon.view3d.Map3DRenderContext;
import com.xeon.view3d.Map3DTextSegment;
import com.xeon.view3d.Map3DTileAction;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

public final class MeshExportPlugin implements MapViewerPlugin, Map3DLayer
{
	private PluginContext context;
	private MeshExportPanel panel;
	private final LinkedHashMap<SelectionKey, SelectedObject> selection = new LinkedHashMap<>();
	private volatile List<Map3DObjectOverlay> selectedOverlays = List.of();

	@Override
	public String id()
	{
		return "mesh-export";
	}

	@Override
	public String displayName()
	{
		return "Mesh Export";
	}

	@Override
	public void install(PluginContext context)
	{
		this.context = context;
		panel = new MeshExportPanel(context, this::clearSelection);
		SwingUtilities.invokeLater(context::showRightSidebar);
	}

	@Override
	public boolean entityPickingEnabled()
	{
		return true;
	}

	@Override
	public Color objectHoverOutlineColor()
	{
		return new Color(0x55D6BE);
	}

	@Override
	public List<Map3DTextSegment> entityHoverText(Map3DEntity.Kind kind, int id, int type)
	{
		return kind == Map3DEntity.Kind.OBJECT
			? List.of(
				new Map3DTextSegment("Object ID - ", Color.WHITE),
				new Map3DTextSegment(Integer.toString(id), Color.CYAN),
				new Map3DTextSegment(" | Type: ", Color.WHITE),
				new Map3DTextSegment(Integer.toString(type), Color.CYAN)
			)
			: List.of();
	}

	@Override
	public boolean tileHoverSelectorVisible(boolean objectHovered)
	{
		return !objectHovered;
	}

	@Override
	public JComponent rightComponent()
	{
		return panel;
	}

	@Override
	public Map3DOverlay overlay(Map3DRenderContext ignored)
	{
		return new Map3DOverlay(List.of(), List.of(), List.of(), List.of(), selectedOverlays);
	}

	@Override
	public List<Map3DTileAction> entityActions(Map3DEntity entity)
	{
		return List.of(new Map3DTileAction("Export Mesh", () -> {
			if (context == null)
			{
				return;
			}
			selection.clear();
			refreshSelectionOverlay();
			panel.select(entity);
			context.showRightSidebar();
		}));
	}

	@Override
	public boolean entityClicked(Map3DEntity entity, Map3DMouseEvent event)
	{
		if (!event.shiftDown() || event.button() != MouseEvent.BUTTON1 || entity.kind() != Map3DEntity.Kind.OBJECT)
		{
			return false;
		}
		SelectionKey key = SelectionKey.of(entity);
		SelectedObject existing = selection.get(key);
		try
		{
			if (existing != null)
			{
				List<Map3DMesh> remaining = selection.entrySet().stream()
					.filter(entry -> !entry.getKey().equals(key)).map(entry -> entry.getValue().mesh()).toList();
				if (!MeshConnectivity.connected(remaining))
				{
					context.setStatus("That object connects other selected parts; clear the selection to start over");
					return true;
				}
				selection.remove(key);
			}
			else
			{
				Map3DMesh mesh = entity.meshSource().load();
				if (mesh == null || mesh.faceCount() == 0)
				{
					throw new IllegalArgumentException("No object mesh is available");
				}
				boolean connected = selection.isEmpty() || selection.values().stream()
					.anyMatch(selected -> MeshConnectivity.touches(selected.mesh(), mesh));
				if (!connected)
				{
					context.setStatus("Object was not selected because its mesh is not connected to the current structure");
					return true;
				}
				selection.put(key, new SelectedObject(entity, mesh));
			}
			refreshSelectionOverlay();
			if (selection.isEmpty())
			{
				panel.clear();
			}
			else
			{
				panel.select(selection.values().stream().map(SelectedObject::entity).toList());
			}
			context.showRightSidebar();
			context.repaintVisible();
			context.setStatus(selection.size() + " connected object" + (selection.size() == 1 ? "" : "s") + " selected");
		}
		catch (Exception ex)
		{
			context.setStatus("Could not select object: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
		}
		return true;
	}

	private void clearSelection()
	{
		selection.clear();
		refreshSelectionOverlay();
		if (panel != null)
		{
			panel.clear();
		}
		if (context != null)
		{
			context.repaintVisible();
		}
	}

	private void refreshSelectionOverlay()
	{
		List<Map3DObjectOverlay> overlays = new ArrayList<>();
		Color fill = new Color(0x3355D6BE, true);
		Color outline = new Color(0xFF55D6BE, true);
		for (SelectedObject selected : selection.values())
		{
			Map3DEntity entity = selected.entity();
			overlays.add(new Map3DObjectOverlay(entity.tile(), entity.id(), fill, outline));
		}
		selectedOverlays = List.copyOf(overlays);
	}

	@Override
	public void uninstall()
	{
		if (panel != null)
		{
			panel.dispose();
		}
		selection.clear();
		selectedOverlays = List.of();
		panel = null;
		context = null;
	}

	private record SelectedObject(Map3DEntity entity, Map3DMesh mesh)
	{
	}

	private record SelectionKey(int id, int type, int x, int y, int plane)
	{
		static SelectionKey of(Map3DEntity entity)
		{
			return new SelectionKey(entity.id(), entity.type(), entity.tile().x, entity.tile().y, entity.tile().z);
		}
	}
}
