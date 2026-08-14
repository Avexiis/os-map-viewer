package com.xeon.view;

import com.xeon.model.Tile;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class MapLegendDialog extends JDialog {
    private static final String DESTINATION_ROOT = "/com/xeon/application/data/destinations/game_features/";
    private static final String TRANSPORT_ROOT = "/com/xeon/application/data/transports/";
    private static final int MERGE_RADIUS_TILES = 2;
    private static final int POH_MIN_X = 1856;
    private static final int POH_MAX_X = 2047;
    private static final int POH_MIN_Y = 5696;
    private static final int POH_MAX_Y = 5759;
    private static final List<LegendSourceSpec> SOURCE_SPECS = List.of(
            destination("Altar", "altar.tsv"),
            destination("Bank", "bank.tsv"),
            transport("Boats", "boats.tsv"),
            transport("Canoes", "canoes.tsv"),
            transport("Charter Ships", "charter_ships.tsv"),
            transport("Fairy Rings", "fairy_rings.tsv"),
            transport("Gnome Gliders", "gnome_gliders.tsv"),
            transport("Hot Air Balloons", "hot_air_balloons.tsv"),
            transport("Magic Carpets", "magic_carpets.tsv"),
            transport("Magic Mushtrees", "magic_mushtrees.tsv"),
            transport("Minecarts", "minecarts.tsv"),
            transport("Quetzal Whistle", "quetzal_whistle.tsv"),
            transport("Quetzals", "quetzals.tsv"),
            transport("Ships", "ships.tsv"),
            transport("Spirit Trees", "spirit_trees.tsv"),
            transport("Teleportation Levers", "teleportation_levers.tsv"),
            transport("Teleportation Minigames", "teleportation_minigames.tsv"),
            transport("Teleportation Portals", "teleportation_portals.tsv"),
            transport("Teleportation Portals (POH)", "teleportation_portals_poh.tsv"),
            transport("Teleportation Spells", "teleportation_spells.tsv"),
            transport("Home Teleport Spells", "teleportation_spells_home.tsv"),
            transport("Wilderness Obelisks", "wilderness_obelisks.tsv")
    );

    private final DefaultListModel<LegendSource> sourceModel = new DefaultListModel<>();
    private final JList<LegendSource> sourceList = new JList<>(sourceModel);
    private final LegendTableModel tableModel = new LegendTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<LegendTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JTextField filterField = new JTextField();
    private final JLabel title = new JLabel("Map Legend");
    private final Consumer<Tile> onTileSelected;

    public MapLegendDialog(Window owner, Consumer<Tile> onTileSelected) {
        super(owner, "Map Legend", ModalityType.MODELESS);
        this.onTileSelected = onTileSelected == null ? tile -> {
        } : onTileSelected;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        configureSources();
        configureTable();
        add(buildSourcePane(), BorderLayout.WEST);
        add(buildTablePane(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        for (LegendSource source : loadSources()) {
            sourceModel.addElement(source);
        }
        if (!sourceModel.isEmpty()) {
            sourceList.setSelectedIndex(0);
        }

        setSize(880, 560);
        setMinimumSize(new Dimension(680, 420));
        setLocationRelativeTo(owner);
    }

    private void configureSources() {
        sourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceList.setCellRenderer(new SourceRenderer());
        sourceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectSource(sourceList.getSelectedValue());
            }
        });
    }

    private void configureTable() {
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(105);
        table.getColumnModel().getColumn(3).setPreferredWidth(360);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    focusSelectedEntry();
                }
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "focusEntry");
        table.getActionMap().put("focusEntry", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                focusSelectedEntry();
            }
        });

        filterField.putClientProperty("JTextField.placeholderText", "Filter");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
    }

    private JComponent buildSourcePane() {
        JScrollPane scroll = new JScrollPane(sourceList);
        scroll.setPreferredSize(new Dimension(230, 0));
        scroll.setBorder(BorderFactory.createTitledBorder("Categories"));
        return scroll;
    }

    private JComponent buildTablePane() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JPanel header = new JPanel(new BorderLayout(8, 0));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        header.add(title, BorderLayout.WEST);
        filterField.setPreferredSize(new Dimension(220, 28));
        header.add(filterField, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.setFocusable(false);
        close.addActionListener(e -> dispose());
        buttons.add(close);
        return buttons;
    }

    private void selectSource(LegendSource source) {
        List<LegendEntry> entries = source == null ? List.of() : source.entries();
        tableModel.setEntries(entries);
        filterField.setText("");
        title.setText(source == null
                ? "Map Legend"
                : source.label() + " (" + entries.size() + ")");
    }

    private void applyFilter() {
        String text = filterField.getText();
        if (text == null || text.isBlank()) {
            sorter.setRowFilter(null);
            return;
        }
        sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text.trim())));
    }

    private void focusSelectedEntry() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(row);
        LegendEntry entry = tableModel.entryAt(modelRow);
        if (entry != null) {
            Tile tile = entry.tile();
            onTileSelected.accept(new Tile(tile.x, tile.y, tile.z));
        }
    }

    private static List<LegendSource> loadSources() {
        List<LegendSource> sources = new ArrayList<>(SOURCE_SPECS.size());
        for (LegendSourceSpec spec : SOURCE_SPECS) {
            sources.add(new LegendSource(spec, loadEntries(spec)));
        }
        return List.copyOf(sources);
    }

    private static List<LegendEntry> loadEntries(LegendSourceSpec spec) {
        String contents = readResource(spec.resourcePath());
        if (contents.isBlank()) {
            return List.of();
        }
        List<ParsedRow> rows = parseRows(contents);
        List<MutableLegendEntry> grouped = new ArrayList<>();
        for (ParsedRow row : rows) {
            if (spec.kind() == SourceKind.DESTINATION) {
                addDestinationEntry(grouped, spec, row);
            } else {
                addTransportEntries(grouped, spec, row);
            }
        }
        List<LegendEntry> entries = grouped.stream()
                .map(MutableLegendEntry::toEntry)
                .sorted(Comparator
                        .comparing(LegendEntry::location, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(LegendEntry::role, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(entry -> entry.tile().z)
                        .thenComparingInt(entry -> entry.tile().x)
                        .thenComparingInt(entry -> entry.tile().y))
                .toList();
        return List.copyOf(entries);
    }

    private static void addDestinationEntry(List<MutableLegendEntry> grouped,
                                            LegendSourceSpec spec, ParsedRow row) {
        Tile destination = parseTile(row.value("Destination"));
        if (!isVisibleLegendTile(destination)) {
            return;
        }
        String label = firstNonBlank(row.value("Info"), row.section(), spec.label());
        addEntry(grouped, spec, label, destination, "Location", details(row, null, destination));
    }

    private static void addTransportEntries(List<MutableLegendEntry> grouped,
                                            LegendSourceSpec spec, ParsedRow row) {
        Tile origin = parseTile(row.value("Origin"));
        Tile destination = parseTile(row.value("Destination"));
        Tile visibleOrigin = isVisibleLegendTile(origin) ? origin : null;
        Tile visibleDestination = isVisibleLegendTile(destination) ? destination : null;
        if (visibleOrigin == null && visibleDestination == null) {
            return;
        }

        String action = row.value("menuOption menuTarget objectID");
        String display = row.value("Display info");
        if (visibleOrigin != null) {
            String label = firstNonBlank(row.section(), display, action, spec.label());
            addEntry(grouped, spec, label, visibleOrigin, "Origin",
                    details(row, visibleOrigin, visibleDestination));
        }
        if (visibleDestination != null) {
            String label = firstNonBlank(display, row.section(), action, spec.label());
            addEntry(grouped, spec, label, visibleDestination, "Destination",
                    details(row, visibleOrigin, visibleDestination));
        }
    }

    private static void addEntry(List<MutableLegendEntry> grouped, LegendSourceSpec spec,
                                 String label, Tile tile, String role, String details) {
        if (tile == null) {
            return;
        }
        String cleanLabel = firstNonBlank(label, spec.label());
        MutableLegendEntry existing = findMergeTarget(grouped, spec.resourcePath(), cleanLabel, tile);
        if (existing == null) {
            existing = new MutableLegendEntry(spec.resourcePath(), cleanLabel, tile);
            grouped.add(existing);
        }
        existing.add(role, details, cleanLabel);
    }

    private static MutableLegendEntry findMergeTarget(List<MutableLegendEntry> entries, String source,
                                                      String label, Tile tile) {
        String normalizedLabel = normalizeLegendLabel(label);
        int regionId = regionId(tile);
        MutableLegendEntry nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (MutableLegendEntry entry : entries) {
            if (!entry.source.equals(source) || entry.tile.z != tile.z) {
                continue;
            }
            int distance = chebyshevDistance(entry.tile, tile);
            boolean nearby = distance <= MERGE_RADIUS_TILES;
            boolean sameNamedRegion = entry.regionId == regionId
                    && entry.normalizedLabels.contains(normalizedLabel);
            if (!nearby && !sameNamedRegion) {
                continue;
            }
            if (distance < nearestDistance) {
                nearest = entry;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static String details(ParsedRow row, Tile origin, Tile destination) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, row.value("menuOption menuTarget objectID"));
        addIfPresent(parts, requirementText("Skills", row.value("Skills")));
        addIfPresent(parts, requirementText("Items", row.value("Items")));
        addIfPresent(parts, requirementText("Quests", row.value("Quests")));
        addIfPresent(parts, routeText(origin, destination));
        return String.join(" | ", parts);
    }

    private static String requirementText(String label, String value) {
        return value == null || value.isBlank() ? null : label + ": " + value.trim();
    }

    private static String routeText(Tile origin, Tile destination) {
        if (origin == null && destination == null) {
            return null;
        }
        if (origin == null) {
            return "Destination " + tileText(destination);
        }
        if (destination == null) {
            return "Origin " + tileText(origin);
        }
        return tileText(origin) + " -> " + tileText(destination);
    }

    private static List<ParsedRow> parseRows(String contents) {
        List<ParsedRow> rows = new ArrayList<>();
        String[] headers = null;
        String section = null;
        for (String rawLine : contents.split("\\R")) {
            if (rawLine == null || rawLine.isBlank()) {
                continue;
            }
            if (rawLine.startsWith("#")) {
                String comment = rawLine.substring(1).trim();
                if (headers == null) {
                    headers = splitTsv(comment);
                } else if (!comment.isBlank()) {
                    section = clean(comment);
                }
                continue;
            }
            if (headers == null) {
                headers = splitTsv(rawLine);
                continue;
            }

            String[] fields = splitTsv(rawLine);
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                values.put(headers[i], i < fields.length ? clean(fields[i]) : null);
            }
            rows.add(new ParsedRow(values, section));
        }
        return rows;
    }

    private static String[] splitTsv(String line) {
        return line.split("\t", -1);
    }

    private static Tile parseTile(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            if (x < 0 || y < 0 || z < 0) {
                return null;
            }
            return new Tile(x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isVisibleLegendTile(Tile tile) {
        return tile != null && !isPohInterior(tile);
    }

    private static boolean isPohInterior(Tile tile) {
        return tile.z == 0
                && tile.x >= POH_MIN_X && tile.x <= POH_MAX_X
                && tile.y >= POH_MIN_Y && tile.y <= POH_MAX_Y;
    }

    private static String readResource(String resourcePath) {
        try (InputStream in = MapLegendDialog.class.getResourceAsStream(resourcePath)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static LegendSourceSpec destination(String label, String fileName) {
        return new LegendSourceSpec(label, fileName, DESTINATION_ROOT + fileName, SourceKind.DESTINATION);
    }

    private static LegendSourceSpec transport(String label, String fileName) {
        return new LegendSourceSpec(label, fileName, TRANSPORT_ROOT + fileName, SourceKind.TRANSPORT);
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return "Location";
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("<br>", " ")
                .replace("<br/>", " ")
                .replace("<br />", " ")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeLegendLabel(String value) {
        String cleaned = cleanLegendLabel(value);
        return normalize(cleaned);
    }

    private static String cleanLegendLabel(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return "Location";
        }
        String withoutRoutePrefix = cleaned.replaceFirst("^[A-Za-z0-9]+:\\s+", "");
        return withoutRoutePrefix.isBlank() ? cleaned : withoutRoutePrefix;
    }

    private static int chebyshevDistance(Tile first, Tile second) {
        return Math.max(Math.abs(first.x - second.x), Math.abs(first.y - second.y));
    }

    private static int regionId(Tile tile) {
        return (Math.floorDiv(tile.x, 64) << 8) | Math.floorDiv(tile.y, 64);
    }

    private static int labelScore(String label) {
        String normalized = normalize(label);
        int score = 10;
        if (normalized.matches(".*\\b\\d{3,}\\b.*")) {
            score -= 5;
        }
        if (normalized.matches("^(activate|configure|enter|exit|pull|travel|use|open|climb|walk-through)\\b.*")) {
            score -= 4;
        }
        if (label != null && label.matches("^[A-Za-z0-9]+:\\s+.+")) {
            score -= 1;
        }
        return score;
    }

    private static String tileText(Tile tile) {
        return tile == null ? "" : tile.x + ", " + tile.y + ", " + tile.z;
    }

    private enum SourceKind {
        DESTINATION,
        TRANSPORT
    }

    private record LegendSourceSpec(String label, String fileName, String resourcePath, SourceKind kind) {
    }

    private record LegendSource(LegendSourceSpec spec, List<LegendEntry> entries) {
        String label() {
            return spec.label();
        }

        String fileName() {
            return spec.fileName();
        }

        @Override
        public String toString() {
            return label();
        }
    }

    private record LegendEntry(String location, Tile tile, String role, String details) {
    }

    private record ParsedRow(Map<String, String> values, String section) {
        String value(String key) {
            return values.get(key);
        }
    }

    private static final class MutableLegendEntry {
        private final String source;
        private String location;
        private int labelScore;
        private final Tile tile;
        private final int regionId;
        private final Set<String> normalizedLabels = new LinkedHashSet<>();
        private final Set<String> roles = new LinkedHashSet<>();
        private final Set<String> details = new LinkedHashSet<>();
        private int count;

        MutableLegendEntry(String source, String location, Tile tile) {
            this.source = source;
            this.location = cleanLegendLabel(location);
            this.labelScore = labelScore(this.location);
            this.tile = new Tile(tile.x, tile.y, tile.z);
            this.regionId = regionId(tile);
            normalizedLabels.add(normalizeLegendLabel(location));
        }

        void add(String role, String detail, String label) {
            count++;
            normalizedLabels.add(normalizeLegendLabel(label));
            String cleanedLabel = cleanLegendLabel(label);
            int nextScore = labelScore(cleanedLabel);
            if (nextScore > labelScore
                    || nextScore == labelScore && cleanedLabel.length() < location.length()) {
                location = cleanedLabel;
                labelScore = nextScore;
            }
            if (role != null && !role.isBlank()) {
                roles.add(role);
            }
            if (detail != null && !detail.isBlank()) {
                details.add(detail);
            }
        }

        LegendEntry toEntry() {
            String detail = details.isEmpty() ? "" : details.iterator().next();
            if (count > 1) {
                detail = count + " entries" + (detail.isBlank() ? "" : " | " + detail);
            }
            return new LegendEntry(location, tile, roleText(), detail);
        }

        private String roleText() {
            if (roles.contains("Origin") && roles.contains("Destination")) {
                return "Origin/Destination";
            }
            if (roles.contains("Origin")) {
                return "Origin";
            }
            if (roles.contains("Destination")) {
                return "Destination";
            }
            return "Location";
        }
    }

    private static final class LegendTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Location", "Tile", "Type", "Details"};
        private List<LegendEntry> entries = List.of();

        void setEntries(List<LegendEntry> entries) {
            this.entries = entries == null ? List.of() : List.copyOf(entries);
            fireTableDataChanged();
        }

        LegendEntry entryAt(int row) {
            return row < 0 || row >= entries.size() ? null : entries.get(row);
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LegendEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.location();
                case 1 -> tileText(entry.tile());
                case 2 -> entry.role();
                case 3 -> entry.details();
                default -> "";
            };
        }
    }

    private static final class SourceRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof LegendSource source) {
                label.setText(source.label() + " (" + source.entries().size() + ")");
                label.setToolTipText(source.fileName());
            }
            return label;
        }
    }
}
