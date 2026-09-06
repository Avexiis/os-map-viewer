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
import java.nio.file.Files;
import java.nio.file.Path;
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
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

final class MeshExportPanel extends JPanel
{
	private final PluginContext context;
	private final Runnable clearSelection;
	private final MeshPreviewPanel preview = new MeshPreviewPanel();
	private final JLabel name = new JLabel("Mesh Export");
	private final JLabel counts = new JLabel(" ");
	private final JTextArea status = new JTextArea(3, 20);
	private final JRadioButton originalMode = new JRadioButton("Original", true);
	private final JRadioButton compactMode = new JRadioButton("Compacted");
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
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.add(name);
		controls.add(Box.createVerticalStrut(8));
		ButtonGroup modes = new ButtonGroup();
		modes.add(originalMode);
		modes.add(compactMode);
		JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		modeRow.add(originalMode);
		modeRow.add(compactMode);
		capHeight(modeRow);
		controls.add(modeRow);
		originalMode.addActionListener(e -> showPreview());
		compactMode.addActionListener(e -> showPreview());
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
		statusScroll.setPreferredSize(new Dimension(250, 90));
		statusScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
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
		controls.add(Box.createVerticalGlue());
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
		export.addActionListener(e -> exportMesh());
		cancel.addActionListener(e -> cancelWork());
		setControlsForNoSelection();
	}

	private static void capHeight(JComponent component)
	{
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
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
		cancelWorker();
		original = null;
		compacted = null;
		Map3DEntity first = selections.get(0);
		String firstName = first.name().isBlank() || first.name().equalsIgnoreCase("null")
			? Integer.toString(first.id()) : first.name();
		String title = selections.size() == 1 ? firstName : selections.size() + " connected objects";
		outputFileName = selections.size() == 1
			? MeshExportIO.fileName(first.name(), first.id())
			: MeshExportIO.fileName("Connected structure (" + selections.size() + " objects)", 0);
		name.setText(title);
		name.setToolTipText(selections.size() == 1 ? first.kind() + " " + first.id() + ": " + title : title);
		preview.resetView();
		preview.setMessage("Loading mesh...");
		originalMode.setSelected(true);
		compactMode.setEnabled(false);
		export.setEnabled(false);
		cancel.setEnabled(true);
		clear.setEnabled(true);
		wireframe.setEnabled(false);
		size.setEnabled(false);
		counts.setText(" ");
		status.setText("Loading static model...");
		int currentRequest = request;
		SwingWorker<MeshCompactor.Result, Object> next = new SwingWorker<>()
		{
			@Override
			protected MeshCompactor.Result doInBackground() throws Exception
			{
				List<Map3DMesh> meshes = new java.util.ArrayList<>(selections.size());
				for (Map3DEntity selection : selections)
				{
					Map3DMesh mesh = selection.meshSource().load();
					if (mesh == null || mesh.faceCount() == 0)
					{
						throw new IllegalArgumentException("No static mesh is available");
					}
					meshes.add(mesh);
				}
				Map3DMesh mesh = combine(meshes);
				MeshTopology.checkCancelled();
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
					if (chunk instanceof Map3DMesh mesh)
					{
						original = mesh;
						preview.setMesh(mesh);
						counts.setText(mesh.faceCount() + " original faces");
						originalMode.setEnabled(true);
						wireframe.setEnabled(true);
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
					compactMode.setSelected(true);
					showPreview();
					counts.setText((original == null ? "" : original.faceCount() + " original / ") + compacted.mesh().faceCount() + " compacted faces");
					MeshTopology.Diagnostics d = compacted.diagnostics();
					status.setText(d.closed() ? "Closed surface."
						: d.boundaryEdges() + " open edges, " + d.nonManifoldEdges() + " non-manifold edges, " + d.inconsistentEdges() + " winding conflicts.");
					for (String note : compacted.notes())
					{
						status.append("\n" + note);
					}
					status.setCaretPosition(0);
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
					showError("Compaction failed", ex.getCause());
				}
			}
		};
		worker = next;
		next.execute();
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
		originalMode.setSelected(true);
		originalMode.setEnabled(false);
		compactMode.setEnabled(false);
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
		preview.setMesh(compactMode.isSelected() && compacted != null ? compacted.mesh() : original);
	}

	private void exportMesh()
	{
		if (compacted == null || worker != null)
		{
			return;
		}
		try
		{
			size.commitEdit();
		}
		catch (java.text.ParseException ex)
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
		chooser.setSelectedFile(new java.io.File(outputFileName + ".stl"));
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
		Map3DMesh mesh = compacted.mesh();
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

	private void showError(String title, Throwable error)
	{
		String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
		status.setText(title + ": " + message);
		context.setStatus(title + ": " + message);
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
		export.setEnabled(compacted != null);
		status.setText("Cancelled");
	}

	void dispose()
	{
		disposed = true;
		cancelWorker();
	}
}
