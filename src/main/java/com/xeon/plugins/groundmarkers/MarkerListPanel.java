package com.xeon.plugins.groundmarkers;

import com.xeon.model.Tile;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class MarkerListPanel extends JPanel {
    private final GroundMarkerProject project;
    private final JTextField tfSearch = new JTextField(18);
    private final JLabel lbCount = new JLabel("0 markers");
    private final JTabbedPane tabs = new JTabbedPane();
    private final JList<Row> listAll = new JList<>();
    private final JList<Row> listVisible = new JList<>();
    private final JList<Row> listNewTiles = new JList<>();
    private final DefaultListModel<Row> modelAll = new DefaultListModel<>();
    private final DefaultListModel<Row> modelVisible = new DefaultListModel<>();
    private final DefaultListModel<Row> modelNewTiles = new DefaultListModel<>();

    private Set<Integer> visibleRegionIds = Set.of();
    private int visiblePlane = 0;
    private List<Tile> newTiles = List.of();
    private Consumer<GroundMarker> onSelect;
    private Consumer<Tile> onTileSelect;
    private boolean suppressSelectionEvents = false;
    private final Timer searchTimer = new Timer(160, e -> refresh());

    public MarkerListPanel(GroundMarkerProject project) {
        this.project = project;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 0, 0, 0));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setBorder(new EmptyBorder(8, 8, 8, 8));
        top.add(new JLabel("Markers"), BorderLayout.NORTH);
        top.add(tfSearch, BorderLayout.CENTER);
        top.add(lbCount, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        RowRenderer renderer = new RowRenderer();
        configureList(listAll, modelAll, renderer);
        configureList(listVisible, modelVisible, renderer);
        configureList(listNewTiles, modelNewTiles, renderer);

        tabs.addTab("All", new JScrollPane(listAll));
        tabs.addTab("Visible Area", new JScrollPane(listVisible));
        tabs.addTab("New Tiles", new JScrollPane(listNewTiles));
        add(tabs, BorderLayout.CENTER);

        installActivation(listAll);
        installActivation(listVisible);
        installActivation(listNewTiles);

        searchTimer.setRepeats(false);
        tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchTimer.restart();
            }
        });
    }

    public void setOnSelect(Consumer<GroundMarker> onSelect) {
        this.onSelect = onSelect;
    }

    public void setOnTileSelect(Consumer<Tile> onTileSelect) {
        this.onTileSelect = onTileSelect;
    }

    public void setScope(Set<Integer> visibleRegionIds, int visiblePlane, List<Tile> newTiles) {
        this.visibleRegionIds = visibleRegionIds == null ? Set.of() : Set.copyOf(visibleRegionIds);
        this.visiblePlane = visiblePlane;
        this.newTiles = newTiles == null ? List.of() : List.copyOf(newTiles);
        refresh();
    }

    public int visiblePlane() {
        return visiblePlane;
    }

    public void refresh() {
        refillMarkers(modelAll, project.all(), false);
        refillMarkers(modelVisible, project.all(), true);
        refillTiles(modelNewTiles, newTiles);
        lbCount.setText(project.size() + " markers");
    }

    public void selectMarker(GroundMarker marker) {
        suppressSelectionEvents = true;
        try {
            if (marker == null) {
                clearSelections();
                return;
            }
            if (selectInList(listAll, modelAll, Row.marker(marker))
                    || selectInList(listVisible, modelVisible, Row.marker(marker))) {
                return;
            }
            clearSelections();
        } finally {
            suppressSelectionEvents = false;
        }
    }

    public void selectTile(Tile tile) {
        suppressSelectionEvents = true;
        try {
            if (tile == null) {
                clearSelections();
                return;
            }
            if (selectInList(listNewTiles, modelNewTiles, Row.tile(tile))) {
                return;
            }
            clearSelections();
        } finally {
            suppressSelectionEvents = false;
        }
    }

    private void clearSelections() {
        listAll.clearSelection();
        listVisible.clearSelection();
        listNewTiles.clearSelection();
    }

    private void configureList(JList<Row> list, DefaultListModel<Row> model, RowRenderer renderer) {
        list.setModel(model);
        list.setCellRenderer(renderer);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private boolean selectInList(JList<Row> list, DefaultListModel<Row> model, Row row) {
        for (int i = 0; i < model.getSize(); i++) {
            if (model.getElementAt(i).sameTarget(row)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                tabs.setSelectedComponent(SwingUtilities.getAncestorOfClass(JScrollPane.class, list));
                return true;
            }
        }
        return false;
    }

    private void refillMarkers(DefaultListModel<Row> model, List<GroundMarker> markers, boolean visibleOnly) {
        String query = query();
        model.clear();
        for (GroundMarker marker : markers) {
            if (visibleOnly && (marker.z != visiblePlane || !visibleRegionIds.contains(marker.regionId))) {
                continue;
            }
            Row row = Row.marker(marker);
            if (query.isEmpty() || row.searchText().contains(query)) {
                model.addElement(row);
            }
        }
    }

    private void refillTiles(DefaultListModel<Row> model, List<Tile> tiles) {
        String query = query();
        model.clear();
        for (Tile tile : tiles) {
            Row row = Row.tile(tile);
            if (query.isEmpty() || row.searchText().contains(query)) {
                model.addElement(row);
            }
        }
    }

    private String query() {
        return tfSearch.getText() == null ? "" : tfSearch.getText().trim().toLowerCase(Locale.ROOT);
    }

    private void installActivation(JList<Row> list) {
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || suppressSelectionEvents) {
                return;
            }
            Row row = list.getSelectedValue();
            if (row == null) {
                return;
            }
            if (row.marker != null && onSelect != null) {
                onSelect.accept(row.marker.copy());
            } else if (row.tile != null && onTileSelect != null) {
                onTileSelect.accept(new Tile(row.tile.x, row.tile.y, row.tile.z));
            }
        });
    }

    private static final class Row {
        final GroundMarker marker;
        final Tile tile;

        private Row(GroundMarker marker, Tile tile) {
            this.marker = marker == null ? null : marker.copy();
            this.tile = tile == null ? null : new Tile(tile.x, tile.y, tile.z);
        }

        static Row marker(GroundMarker marker) {
            return new Row(marker, null);
        }

        static Row tile(Tile tile) {
            return new Row(null, tile);
        }

        boolean sameTarget(Row other) {
            if (other == null) {
                return false;
            }
            if (marker != null && other.marker != null) {
                return marker.key().equals(other.marker.key());
            }
            if (tile != null && other.tile != null) {
                return tile.equals(other.tile);
            }
            return false;
        }

        String title() {
            if (marker != null) {
                return marker.displayLabel();
            }
            return "New marker tile";
        }

        String meta() {
            Tile target = marker != null ? marker.tile() : tile;
            if (target == null) {
                return "";
            }
            int regionId = GroundMarker.regionId(target);
            return "r" + regionId + " | x" + target.x + ", y" + target.y + ", z" + target.z;
        }

        String searchText() {
            return (title() + " " + meta()).toLowerCase(Locale.ROOT);
        }

        Color color() {
            return marker == null ? new Color(0x5503FC13, true) : marker.awtColor();
        }
    }

    private static final class RowRenderer extends JPanel implements ListCellRenderer<Row> {
        private final JPanel swatch = new JPanel();
        private final JLabel text = new JLabel();
        private final JLabel meta = new JLabel();

        RowRenderer() {
            setLayout(new BorderLayout(6, 0));
            setBorder(new EmptyBorder(4, 6, 4, 6));
            swatch.setPreferredSize(new Dimension(18, 18));
            swatch.setBorder(BorderFactory.createLineBorder(new Color(75, 75, 75)));

            JPanel labels = new JPanel(new GridLayout(0, 1));
            labels.setOpaque(false);
            text.setFont(text.getFont().deriveFont(Font.PLAIN, 12f));
            meta.setFont(meta.getFont().deriveFont(Font.PLAIN, 11f));
            labels.add(text);
            labels.add(meta);
            add(swatch, BorderLayout.WEST);
            add(labels, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Row> list,
                                                      Row row,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            setBackground(bg);
            text.setForeground(fg);
            meta.setForeground(isSelected ? fg : new Color(155, 155, 155));
            if (row == null) {
                text.setText("");
                meta.setText("");
                swatch.setBackground(Color.BLACK);
            } else {
                text.setText(row.title());
                meta.setText(row.meta());
                swatch.setBackground(row.color());
            }
            return this;
        }
    }
}
