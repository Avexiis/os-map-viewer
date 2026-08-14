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

import com.xeon.io.Paths;
import com.xeon.model.Tile;
import com.xeon.plugin.MapViewerPlugin;
import com.xeon.plugin.PluginContext;
import com.xeon.util.Ui;
import com.xeon.view.MapLayer;
import com.xeon.view.MapMouseEvent;
import com.xeon.view.MapRenderContext;
import com.xeon.view.MapTool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GroundMarkerPlugin implements MapViewerPlugin, MapLayer, MapTool
{
	private static final double FULL_FOCUS_ZOOM = Double.POSITIVE_INFINITY;

	private final GroundMarkerProject project = new GroundMarkerProject();
	private final LinkedHashSet<Integer> selectedRegionIds = new LinkedHashSet<>();
	private final LinkedHashSet<Tile> selectedTiles = new LinkedHashSet<>();
	private final LinkedHashSet<GroundMarker.MarkerKey> configSourceKeys = new LinkedHashSet<>();
	private final LinkedHashSet<Tile> sessionNewTiles = new LinkedHashSet<>();

	private PluginContext context;
	private GroundMarkerToolbarPanel toolbar;
	private GroundMarkerSidebarPanel sidebar;
	private MarkerListPanel markerList;
	private int currentRegionId = -1;
	private Set<Integer> visibleRegionIds = Set.of();
	private Tile selectedTile;
	private GroundMarker selectedMarker;

	@Override
	public String id()
	{
		return "ground-markers";
	}

	@Override
	public String displayName()
	{
		return "Ground Markers";
	}

	@Override
	public void install(PluginContext context)
	{
		this.context = context;
		toolbar = new GroundMarkerToolbarPanel();
		sidebar = new GroundMarkerSidebarPanel();
		markerList = new MarkerListPanel(project);

		toolbar.setOnImportRuneLite(e -> importClientMarkers(GroundMarkerConfigImporter.Client.RUNELITE));
		toolbar.setOnImportHdos(e -> importClientMarkers(GroundMarkerConfigImporter.Client.HDOS));
		toolbar.setOnImportJson(e -> importJsonFile());
		toolbar.onExport.addListener(this::exportMarkers);

		sidebar.setOnSave(marker -> {
			if (marker == null)
			{
				return;
			}
			List<Tile> targets = selectedTiles();
			if (targets.isEmpty())
			{
				targets = List.of(marker.tile());
			}
			for (Tile tile : targets)
			{
				GroundMarker next = GroundMarker.fromTile(tile, marker.label, marker.color);
				project.upsert(next);
				rememberSessionNewTile(next);
				context.mapPanel().repaintTile(tile);
			}
			selectedMarker = selectedTile == null ? null : project.at(selectedTile).orElse(null);
			if (selectedTile != null)
			{
				currentRegionId = GroundMarker.regionId(selectedTile);
			}
			refreshPanels();
			context.setStatus("Saved " + targets.size() + " marker(s)");
		});
		sidebar.setOnDelete(this::deleteSelectedMarker);
		markerList.setOnSelect(marker -> selectMarker(marker, true));
		markerList.setOnTileSelect(tile -> selectTile(tile, true, false, FULL_FOCUS_ZOOM));

		context.mapPanel().addLayer(this);
		context.mapPanel().setActiveTool(this);
		handleVisibleAreaChanged();
		refreshPanels();
	}

	@Override
	public void uninstall()
	{
		if (context != null)
		{
			context.mapPanel().removeLayer(this);
			context.mapPanel().clearActiveTool(this);
		}
	}

	@Override
	public JPopupMenu actionMenu()
	{
		return toolbar;
	}

	@Override
	public JComponent leftComponent()
	{
		return markerList;
	}

	@Override
	public JComponent rightComponent()
	{
		return sidebar;
	}

	@Override
	public boolean mouseClicked(MapMouseEvent event)
	{
		if (event == null || event.tile() == null)
		{
			return false;
		}
		if (event.isShiftDown())
		{
			toggleExportRegion(event.regionId());
			return true;
		}
		if (!selectedRegionIds.isEmpty())
		{
			clearExportRegions();
		}
		selectTile(event.tile(), false, event.isAdditiveSelection());
		setCurrentRegion(event.regionId());
		return true;
	}

	@Override
	public boolean supportsRegionSelection()
	{
		return true;
	}

	@Override
	public boolean arrowStep(int dx, int dy)
	{
		if (context.mapPanel().isRegionSelectionActive() || (dx == 0 && dy == 0))
		{
			return false;
		}
		Tile base = selectedTile;
		if (base == null)
		{
			base = context.mapPanel().getHoverTile();
		}
		if (base == null && currentRegionId >= 0)
		{
			base = new Tile(
				GroundMarker.regionX(currentRegionId) * Paths.REGION_TILE_SIZE + Paths.REGION_TILE_SIZE / 2,
				GroundMarker.regionY(currentRegionId) * Paths.REGION_TILE_SIZE + Paths.REGION_TILE_SIZE / 2,
				context.mapPanel().getPlane()
			);
		}
		if (base == null)
		{
			return false;
		}

		int minX = Paths.MIN_RX * Paths.REGION_TILE_SIZE;
		int maxX = (Paths.MAX_RX + 1) * Paths.REGION_TILE_SIZE - 1;
		int minY = Paths.MIN_RY * Paths.REGION_TILE_SIZE;
		int maxY = (Paths.MAX_RY + 1) * Paths.REGION_TILE_SIZE - 1;
		int nextX = Math.max(minX, Math.min(maxX, base.x + dx));
		int nextY = Math.max(minY, Math.min(maxY, base.y + dy));
		Tile next = new Tile(nextX, nextY, context.mapPanel().getPlane());
		selectTile(next, true, false);
		return true;
	}

	@Override
	public void paint(MapRenderContext context, Graphics2D g, Rectangle visibleMap)
	{
		boolean showRegions = context.regionSelectionActive() || !selectedRegionIds.isEmpty();
		if (showRegions)
		{
			paintRegionOverlay(context, g, visibleMap);
		}
		paintMarkers(context, g, visibleMap);
		if (!context.regionSelectionActive())
		{
			paintTileSelection(context, g, visibleMap);
		}
	}

	@Override
	public String tooltipText(MapRenderContext context, Tile tile, int regionId)
	{
		if (tile == null)
		{
			return null;
		}
		GroundMarker marker = project.at(tile).orElse(null);
		if (marker != null)
		{
			return marker.label == null || marker.label.isBlank()
				? marker.displayLabel()
				: marker.label;
		}
		if (context.regionSelectionActive() && regionId >= 0)
		{
			return "Region " + regionId + " (" + GroundMarker.regionX(regionId) + "," + GroundMarker.regionY(regionId) + ")";
		}
		return null;
	}

	@Override
	public void planeChanged(int plane)
	{
		Tile current = selectedTile;
		if (current != null && current.z != plane)
		{
			selectTile(new Tile(current.x, current.y, plane), false, false);
		}
		handleVisibleAreaChanged();
		refreshPanels();
	}

	@Override
	public void tileFocused(Tile tile)
	{
		if (tile == null)
		{
			return;
		}
		selectTile(tile, true, false);
		setCurrentRegion(GroundMarker.regionId(tile));
	}

	@Override
	public void visibleAreaChanged()
	{
		handleVisibleAreaChanged();
	}

	private void setCurrentRegion(int regionId)
	{
		if (regionId < 0)
		{
			return;
		}
		int previous = currentRegionId;
		currentRegionId = regionId;
		refreshPanels();
		if (previous != regionId)
		{
			context.mapPanel().repaintRegion(previous);
		}
		context.mapPanel().repaintRegion(regionId);
	}

	private void selectTile(Tile tile, boolean focus, boolean additive)
	{
		selectTile(tile, focus, additive, null);
	}

	private void selectTile(Tile tile, boolean focus, boolean additive, Double targetZoom)
	{
		Tile previous = selectedTile;
		if (tile == null)
		{
			selectedTiles.clear();
			selectedTile = null;
			selectedMarker = null;
			refreshPanels();
			if (previous != null)
			{
				context.mapPanel().repaintTile(previous);
			}
			return;
		}

		Tile copy = new Tile(tile.x, tile.y, tile.z);
		if (additive)
		{
			if (!selectedTiles.add(copy))
			{
				selectedTiles.remove(copy);
			}
		}
		else
		{
			selectedTiles.clear();
			selectedTiles.add(copy);
		}
		selectedTile = selectedTiles.isEmpty() ? null : lastSelectedTile();
		selectedMarker = selectedTile == null ? null : project.at(selectedTile).orElse(null);
		if (selectedTile != null)
		{
			currentRegionId = GroundMarker.regionId(selectedTile);
			if (focus)
			{
				context.mapPanel().focusTile(selectedTile, targetZoom);
			}
		}
		refreshPanels();
		if (previous != null)
		{
			context.mapPanel().repaintTile(previous);
		}
		for (Tile selected : selectedTiles)
		{
			context.mapPanel().repaintTile(selected);
		}
	}

	private void selectMarker(GroundMarker marker, boolean focus)
	{
		if (marker == null)
		{
			clearTileSelection();
			return;
		}
		selectedMarker = marker.copy();
		selectedTile = selectedMarker.tile();
		selectedTiles.clear();
		selectedTiles.add(new Tile(selectedTile.x, selectedTile.y, selectedTile.z));
		currentRegionId = selectedMarker.regionId;
		if (focus)
		{
			context.mapPanel().focusTile(selectedTile, FULL_FOCUS_ZOOM);
		}
		refreshPanels();
		context.mapPanel().repaintTile(selectedTile);
	}

	private void clearTileSelection()
	{
		Tile previous = selectedTile;
		List<Tile> previousTiles = selectedTiles();
		selectedTiles.clear();
		selectedTile = null;
		selectedMarker = null;
		refreshPanels();
		if (previous != null)
		{
			context.mapPanel().repaintTile(previous);
		}
		for (Tile tile : previousTiles)
		{
			context.mapPanel().repaintTile(tile);
		}
	}

	private void deleteSelectedMarker()
	{
		int deleted = 0;
		for (Tile tile : selectedTiles())
		{
			GroundMarker marker = project.at(tile).orElse(null);
			if (marker != null && project.remove(marker))
			{
				deleted++;
				sessionNewTiles.remove(tile);
				context.mapPanel().repaintTile(tile);
			}
		}
		selectedMarker = selectedTile == null ? null : project.at(selectedTile).orElse(null);
		refreshPanels();
		context.setStatus("Deleted " + deleted + " marker(s)");
	}

	private void toggleExportRegion(int regionId)
	{
		if (regionId < 0)
		{
			return;
		}
		currentRegionId = regionId;
		if (!selectedRegionIds.add(regionId))
		{
			selectedRegionIds.remove(regionId);
		}
		refreshPanels();
		context.mapPanel().repaintRegion(regionId);
	}

	private void clearExportRegions()
	{
		List<Integer> previous = new ArrayList<>(selectedRegionIds);
		selectedRegionIds.clear();
		refreshPanels();
		for (int regionId : previous)
		{
			context.mapPanel().repaintRegion(regionId);
		}
		context.mapPanel().repaintVisible();
	}

	private void paintRegionOverlay(MapRenderContext context, Graphics2D g, Rectangle visibleMap)
	{
		int hoverRegion = context.hoverRegionId();
		boolean regionSelectionActive = context.regionSelectionActive();
		Stroke oldStroke = g.getStroke();
		float screenPx = Math.max(0.75f, (float) (1.0 / Math.max(context.effectiveZoom(), 0.0001)));
		g.setStroke(new BasicStroke(screenPx));

		for (int regionId : context.visibleRegionIds(visibleMap))
		{
			Rectangle rect = context.regionToRect(regionId);
			int alpha = 150;
			if (selectedRegionIds.contains(regionId))
			{
				alpha = 86;
			}
			if (regionSelectionActive && regionId == currentRegionId)
			{
				alpha = 70;
			}
			if (regionSelectionActive && regionId == hoverRegion)
			{
				alpha = 44;
			}
			g.setColor(new Color(0, 0, 0, alpha));
			g.fillRect(rect.x, rect.y, rect.width, rect.height);

			if (selectedRegionIds.contains(regionId))
			{
				g.setColor(new Color(0xD8F4D03F, true));
				g.drawRect(rect.x, rect.y, rect.width, rect.height);
			}
			else if (regionSelectionActive && regionId == hoverRegion)
			{
				g.setColor(new Color(0x88FFFFFF, true));
				g.drawRect(rect.x, rect.y, rect.width, rect.height);
			}
		}
		if (currentRegionId >= 0 && regionSelectionActive)
		{
			Rectangle current = context.regionToRect(currentRegionId);
			g.setColor(new Color(0xF003FC13, true));
			g.drawRect(current.x, current.y, current.width, current.height);
		}
		g.setStroke(oldStroke);
	}

	private void paintMarkers(MapRenderContext context, Graphics2D g, Rectangle visibleMap)
	{
		Stroke oldStroke = g.getStroke();
		float screenPx = Math.max(0.75f, (float) (1.0 / Math.max(context.effectiveZoom(), 0.0001)));
		g.setStroke(new BasicStroke(screenPx));
		for (GroundMarker marker : project.all())
		{
			if (marker.z != context.plane())
			{
				continue;
			}
			Rectangle rect = context.tileToRect(marker.tile());
			if (!rect.intersects(visibleMap))
			{
				continue;
			}
			Color color = marker.awtColor();
			int fillAlpha = Math.max(55, Math.min(150, (int) Math.round(color.getAlpha() * 0.55)));
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), fillAlpha));
			g.fillRect(rect.x, rect.y, rect.width, rect.height);
			g.setColor(color);
			g.drawRect(rect.x, rect.y, rect.width, rect.height);
		}
		g.setStroke(oldStroke);
	}

	private void paintTileSelection(MapRenderContext context, Graphics2D g, Rectangle visibleMap)
	{
		Stroke oldStroke = g.getStroke();
		float screenPx = Math.max(0.75f, (float) (1.0 / Math.max(context.effectiveZoom(), 0.0001)));
		g.setStroke(new BasicStroke(screenPx));

		for (Tile tile : selectedTiles)
		{
			if (tile.z != context.plane())
			{
				continue;
			}
			Rectangle rect = context.tileToRect(tile);
			if (rect.intersects(visibleMap))
			{
				boolean primary = selectedTile != null && selectedTile.equals(tile);
				g.setColor(primary ? new Color(0x6603FC13, true) : new Color(0x3E03FC13, true));
				g.fillRect(rect.x, rect.y, rect.width, rect.height);
				g.setColor(primary ? new Color(0xFF03FC13, true) : new Color(0xBB03FC13, true));
				g.drawRect(rect.x, rect.y, rect.width, rect.height);
			}
		}
		g.setStroke(oldStroke);
	}

	private void importClientMarkers(GroundMarkerConfigImporter.Client client)
	{
		int scanAnswer = JOptionPane.showConfirmDialog(
			context.frame(),
			"Search common " + client.displayName() + " config/profile locations for ground markers?\n"
				+ "Files are read only; OS Map Viewer never writes back to client configs.",
			"Import " + client.displayName() + " Ground Markers",
			JOptionPane.YES_NO_CANCEL_OPTION,
			JOptionPane.QUESTION_MESSAGE
		);
		if (scanAnswer == JOptionPane.CANCEL_OPTION || scanAnswer == JOptionPane.CLOSED_OPTION)
		{
			return;
		}
		if (scanAnswer == JOptionPane.NO_OPTION)
		{
			importClientPropertiesFile(client);
			return;
		}

		GroundMarkerConfigImporter.ImportResult result = GroundMarkerConfigImporter.discover(client);
		if (result.sourceCount() <= 0)
		{
			String message = "No " + client.displayName()
				+ " profiles with ground markers were found in common locations.";
			if (!result.errors().isEmpty())
			{
				message += "\n\nSome files could not be read:\n" + firstErrors(result.errors());
			}
			int chooseAnswer = JOptionPane.showConfirmDialog(
				context.frame(),
				message + "\n\nChoose a " + client.chooserLabel() + " manually?",
				"Import " + client.displayName() + " Ground Markers",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE
			);
			if (chooseAnswer == JOptionPane.YES_OPTION)
			{
				importClientPropertiesFile(client);
			}
			return;
		}

		GroundMarkerConfigImporter.MarkerSource source = chooseDetectedSource(client, result.sources());
		if (source == null)
		{
			return;
		}
		applyImportedMarkers(
			source.markers(),
			"Imported " + source.markerCount() + " " + client.displayName()
				+ " markers from " + source.profileName(),
			true
		);
	}

	private GroundMarkerConfigImporter.MarkerSource chooseDetectedSource(
		GroundMarkerConfigImporter.Client client,
		List<GroundMarkerConfigImporter.MarkerSource> sources
	)
	{
		if (sources == null || sources.isEmpty())
		{
			return null;
		}
		if (sources.size() == 1)
		{
			GroundMarkerConfigImporter.MarkerSource source = sources.get(0);
			int answer = JOptionPane.showConfirmDialog(
				context.frame(),
				"Import " + source.markerCount() + " marker(s) from this "
					+ client.displayName() + " profile?\n\n"
					+ source.displayTitle() + "\n"
					+ source.file(),
				"Import " + client.displayName() + " Ground Markers",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE
			);
			return answer == JOptionPane.YES_OPTION ? source : null;
		}

		JList<GroundMarkerConfigImporter.MarkerSource> list = new JList<>(
			sources.toArray(GroundMarkerConfigImporter.MarkerSource[]::new)
		);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(Math.min(8, sources.size()));
		list.setSelectedIndex(0);
		list.setCellRenderer(new MarkerSourceRenderer());

		JScrollPane scrollPane = new JScrollPane(list);
		scrollPane.setPreferredSize(new Dimension(620, 260));
		Object[] message = {
			"Choose the " + client.displayName() + " profile to import:",
			scrollPane
		};
		int answer = JOptionPane.showConfirmDialog(
			context.frame(),
			message,
			"Import " + client.displayName() + " Ground Markers",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.QUESTION_MESSAGE
		);
		return answer == JOptionPane.OK_OPTION ? list.getSelectedValue() : null;
	}

	private void importClientPropertiesFile(GroundMarkerConfigImporter.Client client)
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose " + client.chooserLabel());
		chooser.setFileFilter(new FileNameExtensionFilter("Properties files (*.properties)", "properties"));
		List<Path> directories = GroundMarkerConfigImporter.candidateProfileDirectories(client);
		if (!directories.isEmpty())
		{
			chooser.setCurrentDirectory(directories.get(0).toFile());
		}
		if (chooser.showOpenDialog(context.frame()) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path path = chooser.getSelectedFile().toPath();
		try
		{
			GroundMarkerConfigImporter.MarkerSource source = GroundMarkerConfigImporter.readSource(client, path);
			if (source.markerCount() <= 0)
			{
				Ui.info("No ground markers were found in that " + client.displayName() + " file.");
				return;
			}
			applyImportedMarkers(
				source.markers(),
				"Imported " + source.markerCount() + " " + client.displayName()
					+ " markers from " + source.profileName(),
				true
			);
		}
		catch (Exception ex)
		{
			Ui.error("Failed to import " + client.displayName() + " markers: " + ex.getMessage());
		}
	}

	private void importJsonFile()
	{
		File file = Ui.chooseJson(context.frame(), "Import ground marker JSON");
		if (file == null)
		{
			return;
		}
		try
		{
			List<GroundMarker> markers = GroundMarkerJson.readFile(file.toPath());
			applyImportedMarkers(markers, "Imported " + markers.size() + " markers from JSON", false);
		}
		catch (Exception ex)
		{
			Ui.error("Failed to import marker JSON: " + ex.getMessage());
		}
	}

	private String firstErrors(List<String> errors)
	{
		StringBuilder out = new StringBuilder();
		int count = Math.min(errors.size(), 4);
		for (int i = 0; i < count; i++)
		{
			if (i > 0)
			{
				out.append('\n');
			}
			out.append(errors.get(i));
		}
		if (errors.size() > count)
		{
			out.append("\n... and ").append(errors.size() - count).append(" more");
		}
		return out.toString();
	}

	private void applyImportedMarkers(List<GroundMarker> markers, String status, boolean configSource)
	{
		if (configSource)
		{
			rememberConfigSourceTiles(markers);
		}
		int changed = project.addAll(markers);
		if (currentRegionId < 0 && markers != null && !markers.isEmpty())
		{
			selectMarker(markers.get(0), true);
		}
		else
		{
			refreshPanels();
		}
		context.mapPanel().repaintVisible();
		context.setStatus(status + " (" + changed + " changed)");
	}

	private void exportMarkers(GroundMarkerToolbarPanel.ExportAction action)
	{
		List<GroundMarker> markers = markersFor(action);
		if (markers == null)
		{
			return;
		}
		if (markers.isEmpty())
		{
			Ui.info("There are no markers in that export scope.");
			return;
		}

		boolean toClipboard = action == GroundMarkerToolbarPanel.ExportAction.CURRENT_TO_CLIPBOARD
			|| action == GroundMarkerToolbarPanel.ExportAction.ALL_TO_CLIPBOARD
			|| action == GroundMarkerToolbarPanel.ExportAction.SELECTED_TO_CLIPBOARD;
		if (toClipboard)
		{
			String json = GroundMarkerJson.toCompactJson(markers);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(json), null);
			context.setStatus("Copied " + markers.size() + " markers to clipboard");
			return;
		}

		File file = Ui.saveJson(context.frame(), "Export ground marker JSON");
		if (file == null)
		{
			return;
		}
		if (!file.getName().toLowerCase().endsWith(".json"))
		{
			file = new File(file.getParentFile(), file.getName() + ".json");
		}
		try
		{
			GroundMarkerJson.writeFile(file.toPath(), markers);
			context.setStatus("Exported " + markers.size() + " markers to " + file.getName());
		}
		catch (Exception ex)
		{
			Ui.error("Failed to export marker JSON: " + ex.getMessage());
		}
	}

	private List<GroundMarker> markersFor(GroundMarkerToolbarPanel.ExportAction action)
	{
		return switch (action)
		{
			case CURRENT_TO_CLIPBOARD, CURRENT_TO_FILE ->
			{
				if (currentRegionId < 0)
				{
					Ui.info("Select a current region before exporting.");
					yield null;
				}
				yield project.inRegion(currentRegionId);
			}
			case ALL_TO_CLIPBOARD, ALL_TO_FILE -> project.all();
			case SELECTED_TO_CLIPBOARD, SELECTED_TO_FILE ->
			{
				if (selectedRegionIds.isEmpty())
				{
					Ui.info("Select one or more regions before exporting selected regions.");
					yield null;
				}
				yield project.inRegions(selectedRegionIds);
			}
		};
	}

	private void refreshPanels()
	{
		if (toolbar != null)
		{
			// Toolbar is plugin-scoped; shared map controls are owned by the controller.
		}
		if (sidebar != null)
		{
			sidebar.setSelection(selectedTiles(), selectedMarker);
		}
		if (markerList != null)
		{
			markerList.setScope(visibleRegionIds, context.mapPanel().getPlane(), newTiles());
			if (selectedMarker != null)
			{
				markerList.selectMarker(selectedMarker);
			}
			else if (selectedTile != null)
			{
				markerList.selectTile(selectedTile);
			}
		}
		context.setStatus(statusText());
	}

	private String statusText()
	{
		String region = currentRegionId < 0
			? "no region"
			: "region " + currentRegionId + " (" + project.countInRegion(currentRegionId) + " markers)";
		return region + " - " + project.size() + " total markers";
	}

	private void handleVisibleAreaChanged()
	{
		if (context == null)
		{
			return;
		}
		Set<Integer> nextVisibleRegionIds = Set.copyOf(context.mapPanel().currentVisibleRegionIds());
		int nextPlane = context.mapPanel().getPlane();
		if (nextVisibleRegionIds.equals(visibleRegionIds) && markerList != null && markerList.visiblePlane() == nextPlane)
		{
			return;
		}
		visibleRegionIds = nextVisibleRegionIds;
		if (markerList != null)
		{
			markerList.setScope(visibleRegionIds, nextPlane, newTiles());
		}
	}

	private List<Tile> selectedTiles()
	{
		List<Tile> out = new ArrayList<>();
		for (Tile tile : selectedTiles)
		{
			out.add(new Tile(tile.x, tile.y, tile.z));
		}
		return out;
	}

	private Tile lastSelectedTile()
	{
		Tile last = null;
		for (Tile tile : selectedTiles)
		{
			last = tile;
		}
		return last == null ? null : new Tile(last.x, last.y, last.z);
	}

	private List<Tile> newTiles()
	{
		List<Tile> out = new ArrayList<>();
		for (Tile tile : sessionNewTiles)
		{
			if (!isConfigSourceTile(tile) && project.at(tile).isPresent())
			{
				out.add(new Tile(tile.x, tile.y, tile.z));
			}
		}
		return out;
	}

	private void rememberConfigSourceTiles(List<GroundMarker> markers)
	{
		if (markers == null)
		{
			return;
		}
		for (GroundMarker marker : markers)
		{
			if (marker != null)
			{
				configSourceKeys.add(marker.key());
			}
		}
		sessionNewTiles.removeIf(this::isConfigSourceTile);
	}

	private void rememberSessionNewTile(GroundMarker marker)
	{
		if (marker == null)
		{
			return;
		}
		Tile tile = marker.tile();
		if (isConfigSourceTile(tile))
		{
			sessionNewTiles.remove(tile);
		}
		else
		{
			sessionNewTiles.add(tile);
		}
	}

	private boolean isConfigSourceTile(Tile tile)
	{
		return tile != null && configSourceKeys.contains(GroundMarker.MarkerKey.fromTile(tile));
	}

	private static final class MarkerSourceRenderer extends JPanel
		implements ListCellRenderer<GroundMarkerConfigImporter.MarkerSource>
	{
		private final JLabel title = new JLabel();
		private final JLabel meta = new JLabel();

		MarkerSourceRenderer()
		{
			setLayout(new BorderLayout(4, 2));
			setBorder(new EmptyBorder(6, 8, 6, 8));
			title.setFont(title.getFont().deriveFont(Font.PLAIN, 12f));
			meta.setFont(meta.getFont().deriveFont(Font.PLAIN, 11f));
			add(title, BorderLayout.NORTH);
			add(meta, BorderLayout.CENTER);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends GroundMarkerConfigImporter.MarkerSource> list,
		                                              GroundMarkerConfigImporter.MarkerSource source,
		                                              int index,
		                                              boolean isSelected,
		                                              boolean cellHasFocus)
		{
			Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
			Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
			setBackground(bg);
			title.setForeground(fg);
			meta.setForeground(isSelected ? fg : new Color(155, 155, 155));
			if (source == null)
			{
				title.setText("");
				meta.setText("");
			}
			else
			{
				title.setText(source.displayTitle() + " - " + source.markerCount() + " marker(s)");
				meta.setText(source.file().toString());
			}
			return this;
		}
	}
}
