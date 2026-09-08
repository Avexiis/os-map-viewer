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

import com.xeon.plugin.PluginContext;
import com.xeon.view3d.Map3DEntity;
import com.xeon.view3d.Map3DMesh;
import com.xeon.view3d.MeshPreviewPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

final class MeshExportPanel extends JPanel
{
	private final PluginContext context;
	private final Runnable clearSelection;
	private final RuneProfileModelClient runeProfileClient = new RuneProfileModelClient();
	private final MeshPreviewPanel preview = new MeshPreviewPanel();
	private final JTextField playerUsername = new JTextField();
	private final JButton fetchPlayer = new JButton("Fetch Player");
	private final JButton useWikiSync = new JButton("Use WikiSync");
	private final JButton npcId = new JButton("NPC ID");
	private final JButton objectId = new JButton("Object ID");
	private final JLabel name = new JLabel("Mesh Export");
	private final JLabel counts = new JLabel(" ");
	private final JTextArea status = new JTextArea(3, 20);
	private final JRadioButton rawMode = new JRadioButton("Raw", true);
	private final JRadioButton compactMode = new JRadioButton("Compacted");
	private final JRadioButton repairedMode = new JRadioButton("Repaired");
	private final JCheckBox wireframe = new JCheckBox("Face edges", true);
	private final JSpinner size = new JSpinner(new SpinnerNumberModel(100.0, 0.1, 10000.0, 1.0));
	private final JButton export = new JButton("Export Mesh...");
	private final JButton cancel = new JButton("Cancel");
	private final JButton clear = new JButton("Clear Selection");
	private String outputFileName = "mesh";
	private Map3DMesh original;
	private MeshCompactor.Result compacted;
	private SwingWorker<?, ?> worker;
	private int request;
	private boolean disposed;

	MeshExportPanel(PluginContext context, Runnable clearSelection)
	{
		super(new GridLayout(2, 1, 0, 8));
		this.context = context;
		this.clearSelection = clearSelection == null ? this::clear : clearSelection;
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.putClientProperty("html.disable", true);
		preview.setMessage("No Mesh Selected");
		add(preview);
		JPanel controls = new ScrollablePanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		JLabel idLabel = new JLabel("Model By ID");
		idLabel.setFont(idLabel.getFont().deriveFont(Font.BOLD));
		controls.add(idLabel);
		controls.add(Box.createVerticalStrut(4));
		JPanel idRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		idRow.add(npcId);
		idRow.add(objectId);
		capHeight(idRow);
		controls.add(idRow);
		controls.add(Box.createVerticalStrut(8));
		JLabel playerLabel = new JLabel("RuneProfile Player");
		playerLabel.setLabelFor(playerUsername);
		playerLabel.setFont(playerLabel.getFont().deriveFont(Font.BOLD));
		controls.add(playerLabel);
		controls.add(Box.createVerticalStrut(4));
		JPanel playerRow = new JPanel(new BorderLayout(6, 0));
		playerRow.add(playerUsername, BorderLayout.CENTER);
		playerRow.add(fetchPlayer, BorderLayout.EAST);
		capHeight(playerRow);
		controls.add(playerRow);
		JPanel wikiSyncRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
		wikiSyncRow.add(useWikiSync);
		capHeight(wikiSyncRow);
		controls.add(wikiSyncRow);
		JSeparator separator = new JSeparator();
		separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, separator.getPreferredSize().height));
		controls.add(separator);
		controls.add(Box.createVerticalStrut(8));
		controls.add(name);
		controls.add(Box.createVerticalStrut(8));
		ButtonGroup modes = new ButtonGroup();
		modes.add(rawMode);
		modes.add(compactMode);
		modes.add(repairedMode);
		JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		modeRow.add(rawMode);
		modeRow.add(compactMode);
		modeRow.add(repairedMode);
		capHeight(modeRow);
		controls.add(modeRow);
		rawMode.addActionListener(e -> showPreview());
		compactMode.addActionListener(e -> showPreview());
		repairedMode.addActionListener(e -> showPreview());
		wireframe.addActionListener(e -> preview.setWireframe(wireframe.isSelected()));
		controls.add(wireframe);
		controls.add(counts);
		controls.add(Box.createVerticalStrut(8));
		JPanel sizeRow = new JPanel(new BorderLayout(8, 0));
		JLabel sizeLabel = new JLabel("Longest side (mm)");
		sizeLabel.setLabelFor(size);
		size.setPreferredSize(new Dimension(90, size.getPreferredSize().height));
		sizeRow.add(sizeLabel, BorderLayout.CENTER);
		sizeRow.add(size, BorderLayout.EAST);
		capHeight(sizeRow);
		controls.add(sizeRow);
		status.setEditable(false);
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		status.setOpaque(false);
		status.setFont(UIManager.getFont("Label.font"));
		JScrollPane statusScroll = new JScrollPane(status);
		statusScroll.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
		statusScroll.setMinimumSize(new Dimension(0, 100));
		statusScroll.setPreferredSize(new Dimension(250, 140));
		statusScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		controls.add(statusScroll);
		JPanel commands = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		export.setIcon(UIManager.getIcon("FileView.floppyDriveIcon"));
		commands.add(export);
		commands.add(cancel);
		capHeight(commands);
		controls.add(commands);
		clear.addActionListener(e -> this.clearSelection.run());
		controls.add(Box.createVerticalStrut(6));
		controls.add(clear);
		for (Component component : controls.getComponents())
		{
			if (component instanceof JComponent item)
			{
				item.setAlignmentX(Component.LEFT_ALIGNMENT);
			}
		}
		JScrollPane controlsScroll = new JScrollPane(controls);
		controlsScroll.setBorder(BorderFactory.createEmptyBorder());
		controlsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		controlsScroll.getVerticalScrollBar().setUnitIncrement(12);
		add(controlsScroll);
		String initialUsername = context == null ? "" : cleanUsername(context.wikiSyncUsername());
		playerUsername.setText(initialUsername);
		playerUsername.setToolTipText("RuneProfile username; this does not change the saved WikiSync profile");
		fetchPlayer.addActionListener(e -> fetchPlayerModel());
		playerUsername.addActionListener(e -> fetchPlayerModel());
		useWikiSync.addActionListener(e -> restoreWikiSyncUsername());
		npcId.addActionListener(e -> promptEntityId(Map3DEntity.Kind.NPC));
		objectId.addActionListener(e -> promptEntityId(Map3DEntity.Kind.OBJECT));
		useWikiSync.setEnabled(context != null);
		export.addActionListener(e -> exportMesh());
		cancel.addActionListener(e -> cancelWork());
		setControlsForNoSelection();
	}

	private static void capHeight(JComponent component)
	{
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
	}

	private static final class ScrollablePanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 12;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(12, visibleRect.height - 12);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
		}
	}

	void select(Map3DEntity selection)
	{
		select(List.of(selection));
	}

	void select(List<Map3DEntity> selections)
	{
		if (selections == null || selections.isEmpty())
		{
			clear();
			return;
		}
		Map3DEntity first = selections.get(0);
		String firstName = first.name().isBlank() || first.name().equalsIgnoreCase("null")
			? Integer.toString(first.id()) : first.name();
		String title = selections.size() == 1 ? firstName : selections.size() + " connected objects";
		String fileName = selections.size() == 1
			? MeshExportIO.fileName(first.name(), first.id())
			: MeshExportIO.fileName("Connected structure (" + selections.size() + " objects)", 0);
		String tooltip = selections.size() == 1 ? first.kind() + " " + first.id() + ": " + title : title;
		loadMesh(title, tooltip, fileName, "Loading static model...", progress -> {
			List<Map3DMesh> meshes = new ArrayList<>(selections.size());
			for (Map3DEntity selection : selections)
			{
				Map3DMesh mesh = selection.meshSource().load();
				if (mesh == null || mesh.faceCount() == 0)
				{
					throw new IllegalArgumentException("No static mesh is available");
				}
				meshes.add(mesh);
			}
			return combine(meshes);
		});
	}

	private void promptEntityId(Map3DEntity.Kind kind)
	{
		String type = kind == Map3DEntity.Kind.NPC ? "NPC" : "Object";
		String value = JOptionPane.showInputDialog(this, "Enter " + type + " ID:", type + " ID",
			JOptionPane.PLAIN_MESSAGE);
		if (value == null)
		{
			return;
		}
		int id;
		try
		{
			id = Integer.parseInt(value.trim());
			if (id < 0)
			{
				throw new NumberFormatException();
			}
		}
		catch (NumberFormatException ex)
		{
			showMessage("Enter a non-negative numeric " + type + " ID");
			return;
		}
		if (context == null)
		{
			showMessage("The 3D model cache is not available");
			return;
		}
		clearSelection.run();
		String fallbackTitle = type + " " + id;
		loadMesh(fallbackTitle, fallbackTitle, MeshExportIO.fileName("", id), "Loading static model...", progress -> {
			Map3DEntity entity = context.load3DEntity(kind, id);
			if (entity == null)
			{
				throw new IllegalArgumentException("No static mesh is available for " + fallbackTitle);
			}
			String entityName = entity.name().isBlank() || entity.name().equalsIgnoreCase("null")
				? fallbackTitle : entity.name();
			progress.accept(new SelectionDetails(
				entityName,
				type + " " + id + ": " + entityName,
				MeshExportIO.fileName(entity.name(), id)
			));
			return entity.meshSource().load();
		});
	}

	private void loadMesh(String title, String tooltip, String fileName, String initialStatus, MeshLoader loader)
	{
		cancelWorker();
		original = null;
		compacted = null;
		outputFileName = fileName;
		name.setText(title);
		name.setToolTipText(tooltip);
		preview.resetView();
		preview.setMessage("Loading mesh...");
		rawMode.setSelected(true);
		rawMode.setEnabled(false);
		compactMode.setEnabled(false);
		repairedMode.setEnabled(false);
		export.setEnabled(false);
		cancel.setEnabled(true);
		clear.setEnabled(true);
		wireframe.setEnabled(false);
		size.setEnabled(false);
		counts.setText(" ");
		status.setText(initialStatus);
		int currentRequest = request;
		SwingWorker<MeshCompactor.Result, Object> next = new SwingWorker<>()
		{
			private boolean meshLoaded;

			@Override
			protected MeshCompactor.Result doInBackground() throws Exception
			{
				Map3DMesh mesh = loader.load(message -> publish(message));
				if (mesh == null || mesh.faceCount() == 0)
				{
					throw new IllegalArgumentException("No mesh is available");
				}
				MeshTopology.checkCancelled();
				meshLoaded = true;
				publish(mesh);
				return MeshCompactor.compact(mesh, message -> publish(message));
			}

			@Override
			protected void process(List<Object> chunks)
			{
				if (disposed || request != currentRequest || isCancelled())
				{
					return;
				}
				for (Object chunk : chunks)
				{
					if (chunk instanceof SelectionDetails details)
					{
						name.setText(details.title());
						name.setToolTipText(details.tooltip());
						outputFileName = details.fileName();
					}
					else if (chunk instanceof Map3DMesh mesh)
					{
						original = mesh;
						preview.setMesh(mesh);
						counts.setText(mesh.faceCount() + " raw faces");
						rawMode.setEnabled(true);
						wireframe.setEnabled(true);
						size.setEnabled(true);
						export.setEnabled(true);
					}
					else
					{
						status.setText(chunk.toString());
					}
				}
			}

			@Override
			protected void done()
			{
				if (disposed || request != currentRequest)
				{
					return;
				}
				worker = null;
				cancel.setEnabled(false);
				try
				{
					compacted = get();
					compactMode.setEnabled(true);
					repairedMode.setEnabled(compacted.repairApplied());
					if (compacted.repairApplied())
					{
						repairedMode.setSelected(true);
					}
					else
					{
						compactMode.setSelected(true);
					}
					showPreview();
					counts.setText(faceCountSummary(compacted));
					export.setEnabled(true);
					size.setEnabled(true);
				}
				catch (CancellationException ignored)
				{
					status.setText("Cancelled");
				}
				catch (InterruptedException ex)
				{
					Thread.currentThread().interrupt();
					status.setText("Interrupted");
				}
				catch (ExecutionException ex)
				{
					showError(meshLoaded ? "Compaction failed" : "Mesh load failed", ex.getCause());
				}
			}
		};
		worker = next;
		next.execute();
	}

	private void fetchPlayerModel()
	{
		String username = cleanUsername(playerUsername.getText());
		if (username.isBlank())
		{
			showMessage("Enter a RuneProfile username");
			return;
		}
		clearSelection.run();
		loadMesh(username, "RuneProfile player: " + username, MeshExportIO.fileName(username, 0),
			"Fetching RuneProfile player model...", progress -> {
				progress.accept("Fetching RuneProfile player model...");
				byte[] data = runeProfileClient.fetch(username);
				MeshTopology.checkCancelled();
				progress.accept("Reading RuneProfile player model...");
				return RuneProfileModelParser.parse(data);
			});
	}

	private void restoreWikiSyncUsername()
	{
		String username = context == null ? "" : cleanUsername(context.wikiSyncUsername());
		if (username.isBlank())
		{
			showMessage("No WikiSync username is saved");
			return;
		}
		playerUsername.setText(username);
		playerUsername.requestFocusInWindow();
		playerUsername.selectAll();
	}

	private void showMessage(String message)
	{
		status.setText(message);
		if (context != null)
		{
			context.setStatus(message);
		}
	}

	private static String cleanUsername(String username)
	{
		return username == null ? "" : username.trim();
	}

	void clear()
	{
		cancelWorker();
		original = null;
		compacted = null;
		outputFileName = "mesh";
		name.setText("Mesh Export");
		name.setToolTipText(null);
		preview.setMessage("No Mesh Selected");
		counts.setText(" ");
		status.setText("");
		setControlsForNoSelection();
	}

	private void setControlsForNoSelection()
	{
		rawMode.setSelected(true);
		rawMode.setEnabled(false);
		compactMode.setEnabled(false);
		repairedMode.setEnabled(false);
		wireframe.setEnabled(false);
		size.setEnabled(false);
		export.setEnabled(false);
		cancel.setEnabled(false);
		clear.setEnabled(false);
	}

	static Map3DMesh combine(List<Map3DMesh> meshes)
	{
		int vertexCount = meshes.stream().mapToInt(Map3DMesh::vertexCount).sum();
		int faceCount = meshes.stream().mapToInt(Map3DMesh::faceCount).sum();
		double[] positions = new double[vertexCount * 3];
		int[] triangles = new int[faceCount * 3];
		int vertexOffset = 0, faceOffset = 0;
		for (Map3DMesh mesh : meshes)
		{
			System.arraycopy(mesh.positions(), 0, positions, vertexOffset * 3, mesh.vertexCount() * 3);
			for (int index : mesh.triangles())
			{
				triangles[faceOffset++] = index + vertexOffset;
			}
			vertexOffset += mesh.vertexCount();
		}
		return new Map3DMesh(positions, triangles);
	}

	private void showPreview()
	{
		if (compacted == null || rawMode.isSelected())
		{
			preview.setMesh(original);
		}
		else if (repairedMode.isSelected())
		{
			preview.setMesh(compacted.repairedMesh());
		}
		else
		{
			preview.setMesh(compacted.compactedMesh());
		}
		updateModeStatus();
	}

	private void updateModeStatus()
	{
		if (compacted == null)
		{
			return;
		}
		if (rawMode.isSelected())
		{
			setRawWarning();
		}
		else if (repairedMode.isSelected())
		{
			status.setText("Manifold repair produced a watertight surface.");
		}
		else if (compacted.compactedWatertight())
		{
			status.setText("The compacted mesh is watertight; manifold repair was not needed.");
		}
		else
		{
			status.setText("Warning: The compacted mesh is not watertight and will need repair before 3D printing.");
		}
		if (!rawMode.isSelected())
		{
			for (String note : compacted.notes())
			{
				status.append("\n" + note);
			}
		}
		status.setCaretPosition(0);
	}

	private void setRawWarning()
	{
		status.setText("Warning: Raw meshes almost always contain manifold problems. "
			+ "Use raw export for external repair or digital uses where watertight geometry is not required.");
	}

	private String faceCountSummary(MeshCompactor.Result result)
	{
		String compactFaces = result.compactedMesh().faceCount() + " compacted faces";
		String originalFaces = original == null ? compactFaces : original.faceCount() + " raw / " + compactFaces;
		return result.repairApplied()
			? "<html>" + originalFaces + "<br>" + result.repairedMesh().faceCount() + " faces after manifold repair</html>"
			: originalFaces;
	}

	private void exportMesh()
	{
		boolean rawExport = rawMode.isSelected();
		if (original == null || !rawExport && (compacted == null || worker != null))
		{
			return;
		}
		Map3DMesh mesh = rawExport ? original : selectedMesh();
		if (rawExport
			&& JOptionPane.showConfirmDialog(this,
				"Raw meshes almost always contain manifold problems and may not be watertight.\n"
					+ "Use modeling or repair software before 3D printing.\n\nExport the raw mesh anyway?",
				"Raw Mesh", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
		{
			return;
		}
		if (!rawExport && !selectedMeshWatertight()
			&& JOptionPane.showConfirmDialog(this,
				"This mesh is not watertight and will need repair before 3D printing.\n"
					+ "Your slicer or modeling software may be able to repair it.\n\nExport it anyway?",
				"Non-Watertight Mesh", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
		{
			return;
		}
		try
		{
			size.commitEdit();
		}
		catch (ParseException ex)
		{
			showError("Invalid size", ex);
			return;
		}
		JFileChooser chooser = new JFileChooser(context.config().getString("directory", null));
		chooser.setDialogTitle("Export Mesh");
		chooser.setAcceptAllFileFilterUsed(false);
		FileNameExtensionFilter stl = new FileNameExtensionFilter(MeshExportIO.Format.STL.description, "stl");
		FileNameExtensionFilter obj = new FileNameExtensionFilter(MeshExportIO.Format.OBJ.description, "obj");
		chooser.addChoosableFileFilter(stl);
		chooser.addChoosableFileFilter(obj);
		chooser.setFileFilter(stl);
		chooser.setSelectedFile(new File(outputFileName + ".stl"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		MeshExportIO.Format format = chooser.getFileFilter() == obj ? MeshExportIO.Format.OBJ : MeshExportIO.Format.STL;
		Path target = MeshExportIO.withExtension(chooser.getSelectedFile().toPath(), format).toAbsolutePath();
		boolean replace = Files.exists(target);
		if (replace && JOptionPane.showConfirmDialog(this, "Replace " + target.getFileName() + "?", "Export Mesh",
			JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
		{
			return;
		}
		context.config().setString("directory", target.getParent().toString());
		if (rawExport && worker != null)
		{
			cancelWorker();
		}
		double millimeters = ((Number) size.getValue()).doubleValue();
		int currentRequest = request;
		export.setEnabled(false);
		cancel.setEnabled(true);
		status.setText("Saving " + target.getFileName() + "...");
		SwingWorker<Void, Void> next = new SwingWorker<>()
		{
			@Override
			protected Void doInBackground() throws Exception
			{
				MeshExportIO.save(target, format, mesh, millimeters, replace);
				return null;
			}

			@Override
			protected void done()
			{
				if (disposed || currentRequest != request)
				{
					return;
				}
				worker = null;
				cancel.setEnabled(false);
				export.setEnabled(true);
				try
				{
					get();
					status.setText("Saved " + target.getFileName());
					context.setStatus("Exported mesh: " + target);
				}
				catch (CancellationException ignored)
				{
					status.setText("Cancelled");
				}
				catch (InterruptedException ex)
				{
					Thread.currentThread().interrupt();
					status.setText("Interrupted");
				}
				catch (ExecutionException ex)
				{
					showError("Export failed", ex.getCause());
				}
			}
		};
		worker = next;
		next.execute();
	}

	private Map3DMesh selectedMesh()
	{
		if (rawMode.isSelected())
		{
			return original;
		}
		if (repairedMode.isSelected() && compacted.repairedMesh() != null)
		{
			return compacted.repairedMesh();
		}
		return compacted.compactedMesh();
	}

	private boolean selectedMeshWatertight()
	{
		return repairedMode.isSelected() ? compacted.watertight() : compacted.compactedWatertight();
	}

	private void showError(String title, Throwable error)
	{
		String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
		status.setText(title + ": " + message);
		if (context != null)
		{
			context.setStatus(title + ": " + message);
		}
	}

	private void cancelWorker()
	{
		request++;
		if (worker != null)
		{
			worker.cancel(true);
		}
		worker = null;
	}

	private void cancelWork()
	{
		cancelWorker();
		cancel.setEnabled(false);
		export.setEnabled(original != null);
		size.setEnabled(original != null);
		if (original == null)
		{
			status.setText("Cancelled");
		}
		else if (compacted == null)
		{
			rawMode.setSelected(true);
			setRawWarning();
		}
		else
		{
			updateModeStatus();
		}
	}

	void dispose()
	{
		disposed = true;
		cancelWorker();
	}

	@FunctionalInterface
	private interface MeshLoader
	{
		Map3DMesh load(Consumer<Object> progress) throws Exception;
	}

	private record SelectionDetails(String title, String tooltip, String fileName)
	{
	}
}
