package com.xeon.plugins.shortestpath;

import com.xeon.model.Tile;
import com.xeon.plugins.shortestpath.core.PathOptions;
import com.xeon.plugins.shortestpath.core.TeleportItem;
import com.xeon.plugins.shortestpath.core.TransportType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class ShortestPathPanel extends JPanel {
    enum CollisionMapMode {
        OFF("Collision Map Off"),
        HOVERED_REGION("Hovered Region Collision Map"),
        SELECTED_REGION("Selected Region Collision Map");

        private final String label;

        CollisionMapMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum TeleportItemSortMode {
        DEFAULT("Default"),
        ENABLED_FIRST("Enabled first"),
        DISABLED_FIRST("Disabled first");

        private final String label;

        TeleportItemSortMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final JLabel startLabel = new JLabel("Start: none");
    private final JLabel stepsLabel = new JLabel("Steps: none");
    private final JLabel targetLabel = new JLabel("Target: none");
    private final JLabel statusLabel = new JLabel("Status: idle");
    private final JCheckBox includeTransports = new JCheckBox("Transports", true);
    private final JCheckBox includeTeleports = new JCheckBox("Teleports", true);
    private final JCheckBox avoidWilderness = new JCheckBox("Avoid wilderness", false);
    private final JCheckBox includePoh = new JCheckBox("POH links", true);
    private final JCheckBox avoidItemTeleports = new JCheckBox("Avoid item teleports", false);
    private final JComboBox<CollisionMapMode> collisionMapMode = new JComboBox<>(CollisionMapMode.values());
    private final JTextField profileName = new JTextField(12);
    private final JButton lookUpProfile = new JButton("Look up");
    private final JLabel profileStatus = new JLabel("Profile: None");
    private final JButton walkLineColorButton = new JButton("Walk line");
    private final JButton transportLineColorButton = new JButton("Transport line");
    private final JButton startMarkerColorButton = new JButton("Start marker");
    private final JButton stepMarkerColorButton = new JButton("Step marker");
    private final JButton targetMarkerColorButton = new JButton("End marker");
    private final JButton collisionColorButton = new JButton("Collision");
    private final JTextField teleportItemFilter = new JTextField(14);
    private final JComboBox<TeleportItemSortMode> teleportItemSort = new JComboBox<>(TeleportItemSortMode.values());
    private final TeleportItemTableModel teleportItemModel = new TeleportItemTableModel();
    private final JTable teleportItemTable = new JTable(teleportItemModel);
    private final JButton centerStart = new JButton("Center Start");
    private final JButton swap = new JButton("Swap");
    private final JButton recalculate = new JButton("Recalculate");
    private final JButton importRoute = new JButton("Import");
    private final JButton exportRoute = new JButton("Export");
    private final JButton clear = new JButton("Clear");
    private final Map<TransportType, JCheckBox> transportToggles = new EnumMap<>(TransportType.class);
    private Set<TeleportItem> enabledTeleportItems = EnumSet.allOf(TeleportItem.class);
    private Color walkLineColor = new Color(0xFF27D6C4, true);
    private Color transportLineColor = new Color(0xFFFF5C7A, true);
    private Color startMarkerColor = new Color(0xFF35D07F, true);
    private Color stepMarkerColor = new Color(0xFF65A8FF, true);
    private Color targetMarkerColor = new Color(0xFFFFC857, true);
    private Color collisionColor = new Color(0x99FFDD40, true);
    private final Color profileStatusLoadedForeground = new Color(0x35D07F);
    private final Color profileStatusMissingForeground = new Color(0xFF5C7A);

    private Runnable onOptionsChanged;
    private Runnable onCenterStart;
    private Runnable onSwap;
    private Runnable onRecalculate;
    private Runnable onImportRoute;
    private Runnable onExportRoute;
    private Runnable onClear;
    private Consumer<String> onProfileLookup;

    ShortestPathPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        addFullRow(controls, c, title("Shortest Path"));
        addFullRow(controls, c, startLabel);
        addFullRow(controls, c, stepsLabel);
        addFullRow(controls, c, targetLabel);
        addFullRow(controls, c, statusLabel);

        addFullRow(controls, c, title("WikiSync Profile"));
        JPanel profileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        profileRow.setOpaque(false);
        lookUpProfile.setIcon(loadRuneLiteIcon());
        lookUpProfile.setHorizontalTextPosition(SwingConstants.RIGHT);
        profileName.setMaximumSize(new Dimension(Integer.MAX_VALUE, profileName.getPreferredSize().height));
        profileRow.add(profileName);
        profileRow.add(lookUpProfile);
        addFullRow(controls, c, profileRow);
        addFullRow(controls, c, profileStatus);

        addFullRow(controls, c, collisionMapViewRow());

        JPanel optionGrid = new JPanel(new GridLayout(0, 1, 0, 2));
        optionGrid.setOpaque(false);
        optionGrid.add(includeTeleports);
        optionGrid.add(includeTransports);
        optionGrid.add(avoidItemTeleports);
        optionGrid.add(avoidWilderness);
        optionGrid.add(includePoh);
        addFullRow(controls, c, optionGrid);

        addFullRow(controls, c, title("Use"));
        JPanel useGrid = new JPanel(new GridLayout(0, 1, 0, 2));
        useGrid.setOpaque(false);
        addTransportToggle(useGrid, TransportType.AGILITY_SHORTCUT, "Use agility shortcuts");
        addTransportToggle(useGrid, TransportType.GRAPPLE_SHORTCUT, "Use grapple shortcuts");
        addTransportToggle(useGrid, TransportType.BOAT, "Use boats");
        addTransportToggle(useGrid, TransportType.CANOE, "Use canoes");
        addTransportToggle(useGrid, TransportType.CHARTER_SHIP, "Use charter ships");
        addTransportToggle(useGrid, TransportType.SHIP, "Use ships");
        addTransportToggle(useGrid, TransportType.FAIRY_RING, "Use fairy rings");
        addTransportToggle(useGrid, TransportType.GNOME_GLIDER, "Use gnome gliders");
        addTransportToggle(useGrid, TransportType.HOT_AIR_BALLOON, "Use hot air balloons");
        addTransportToggle(useGrid, TransportType.MAGIC_CARPET, "Use magic carpets");
        addTransportToggle(useGrid, TransportType.MAGIC_MUSHTREE, "Use magic mushtrees");
        addTransportToggle(useGrid, TransportType.MINECART, "Use minecarts");
        addTransportToggle(useGrid, TransportType.QUETZAL, "Use quetzals");
        addTransportToggle(useGrid, TransportType.QUETZAL_WHISTLE, "Use quetzal whistle");
        addTransportToggle(useGrid, TransportType.SPIRIT_TREE, "Use spirit trees");
        addTransportToggle(useGrid, TransportType.TELEPORTATION_LEVER, "Use teleportation levers");
        addTransportToggle(useGrid, TransportType.TELEPORTATION_PORTAL, "Use teleportation portals");
        addTransportToggle(useGrid, TransportType.TELEPORTATION_SPELL, "Use teleportation spells");
        addTransportToggle(useGrid, TransportType.TELEPORTATION_SPELL_HOME, "Use Home Teleport spells");
        addTransportToggle(useGrid, TransportType.TELEPORTATION_MINIGAME, "Use teleportation minigames");
        addTransportToggle(useGrid, TransportType.WILDERNESS_OBELISK, "Use wilderness obelisks");
        addFullRow(controls, c, useGrid);

        addFullRow(controls, c, title("Colors"));
        addFullRow(controls, c, stackedButtons(150,
                walkLineColorButton,
                transportLineColorButton,
                startMarkerColorButton,
                stepMarkerColorButton,
                targetMarkerColorButton,
                collisionColorButton
        ));

        addFullRow(controls, c, actionButtons());

        addFullRow(controls, c, title("Teleport Items"));
        addFullRow(controls, c, teleportItemControls());
        configureTeleportItemTable();
        refreshTeleportItemList();

        JPanel content = new WidthTrackingPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(controls, BorderLayout.NORTH);
        JScrollPane itemScroll = new JScrollPane(teleportItemTable);
        itemScroll.setBorder(BorderFactory.createTitledBorder("Teleport Items"));
        itemScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        itemScroll.getVerticalScrollBar().setUnitIncrement(40);
        content.add(itemScroll, BorderLayout.CENTER);
        JScrollPane scrollContent = new JScrollPane(content);
        scrollContent.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollContent.getVerticalScrollBar().setUnitIncrement(24);
        add(scrollContent, BorderLayout.CENTER);

        includeTransports.addActionListener(e -> emitOptionsChanged());
        includeTeleports.addActionListener(e -> emitOptionsChanged());
        avoidItemTeleports.addActionListener(e -> {
            refreshTeleportItemList();
            emitOptionsChanged();
        });
        avoidWilderness.addActionListener(e -> emitOptionsChanged());
        includePoh.addActionListener(e -> emitOptionsChanged());
        collisionMapMode.addActionListener(e -> emitOptionsChanged());
        teleportItemSort.addActionListener(e -> refreshTeleportItemList());
        teleportItemFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshTeleportItemList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshTeleportItemList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshTeleportItemList();
            }
        });
        walkLineColorButton.addActionListener(e -> chooseColor("Walk Line Color", walkLineColor, color -> walkLineColor = color));
        transportLineColorButton.addActionListener(e -> chooseColor("Transport Line Color", transportLineColor, color -> transportLineColor = color));
        startMarkerColorButton.addActionListener(e -> chooseColor("Start Marker Color", startMarkerColor, color -> startMarkerColor = color));
        stepMarkerColorButton.addActionListener(e -> chooseColor("Step Marker Color", stepMarkerColor, color -> stepMarkerColor = color));
        targetMarkerColorButton.addActionListener(e -> chooseColor("End Marker Color", targetMarkerColor, color -> targetMarkerColor = color));
        collisionColorButton.addActionListener(e -> chooseColor("Collision Map Color", collisionColor, color -> collisionColor = color));
        profileName.addActionListener(e -> emitProfileLookup());
        lookUpProfile.addActionListener(e -> emitProfileLookup());
        centerStart.addActionListener(e -> {
            if (onCenterStart != null) {
                onCenterStart.run();
            }
        });
        swap.addActionListener(e -> {
            if (onSwap != null) {
                onSwap.run();
            }
        });
        recalculate.addActionListener(e -> {
            if (onRecalculate != null) {
                onRecalculate.run();
            }
        });
        importRoute.addActionListener(e -> {
            if (onImportRoute != null) {
                onImportRoute.run();
            }
        });
        exportRoute.addActionListener(e -> {
            if (onExportRoute != null) {
                onExportRoute.run();
            }
        });
        clear.addActionListener(e -> {
            if (onClear != null) {
                onClear.run();
            }
        });
        refreshColorButtons();
        setProfileLoaded(false);
    }

    boolean includeTransports() {
        return includeTransports.isSelected();
    }

    boolean includeTeleports() {
        return includeTeleports.isSelected();
    }

    boolean avoidWilderness() {
        return avoidWilderness.isSelected();
    }

    boolean includePoh() {
        return includePoh.isSelected();
    }

    boolean avoidItemTeleports() {
        return avoidItemTeleports.isSelected();
    }

    CollisionMapMode collisionMapMode() {
        Object selected = collisionMapMode.getSelectedItem();
        return selected instanceof CollisionMapMode mode ? mode : CollisionMapMode.OFF;
    }

    Set<TransportType> enabledTransportTypes() {
        EnumSet<TransportType> types = EnumSet.noneOf(TransportType.class);
        for (Map.Entry<TransportType, JCheckBox> entry : transportToggles.entrySet()) {
            if (entry.getValue().isSelected()) {
                types.add(entry.getKey());
            }
        }
        for (TransportType type : TransportType.values()) {
            if (!type.isLeagueOnly() && !transportToggles.containsKey(type)) {
                types.add(type);
            }
        }
        return types;
    }

    Set<TeleportItem> enabledTeleportItems() {
        if (enabledTeleportItems.isEmpty()) {
            return EnumSet.noneOf(TeleportItem.class);
        }
        return EnumSet.copyOf(enabledTeleportItems);
    }

    Color walkLineColor() {
        return walkLineColor;
    }

    Color transportLineColor() {
        return transportLineColor;
    }

    Color startMarkerColor() {
        return startMarkerColor;
    }

    Color stepMarkerColor() {
        return stepMarkerColor;
    }

    Color targetMarkerColor() {
        return targetMarkerColor;
    }

    Color collisionColor() {
        return collisionColor;
    }

    void setOptions(boolean transports, boolean teleports, boolean wilderness, boolean poh, boolean avoidItems,
                    Set<TransportType> enabledTypes, CollisionMapMode collisionMode) {
        includeTransports.setSelected(transports);
        includeTeleports.setSelected(teleports);
        avoidWilderness.setSelected(wilderness);
        includePoh.setSelected(poh);
        avoidItemTeleports.setSelected(avoidItems);
        collisionMapMode.setSelectedItem(collisionMode == null ? CollisionMapMode.OFF : collisionMode);
        Set<TransportType> types = enabledTypes == null ? PathOptions.defaultEnabledTransportTypes() : enabledTypes;
        for (Map.Entry<TransportType, JCheckBox> entry : transportToggles.entrySet()) {
            entry.getValue().setSelected(types.contains(entry.getKey()));
        }
        refreshTeleportItemList();
    }

    void setColors(Color walkLine, Color transportLine, Color startMarker, Color stepMarker,
                   Color targetMarker, Color collision) {
        walkLineColor = walkLine == null ? walkLineColor : walkLine;
        transportLineColor = transportLine == null ? transportLineColor : transportLine;
        startMarkerColor = startMarker == null ? startMarkerColor : startMarker;
        stepMarkerColor = stepMarker == null ? stepMarkerColor : stepMarker;
        targetMarkerColor = targetMarker == null ? targetMarkerColor : targetMarker;
        collisionColor = collision == null ? collisionColor : collision;
        refreshColorButtons();
    }

    void setTeleportItems(Set<TeleportItem> enabledItems) {
        enabledTeleportItems = copyTeleportItems(enabledItems);
        refreshTeleportItemList();
    }

    void setTiles(Tile start, List<Tile> steps, Tile target) {
        startLabel.setText("Start: " + tileText(start));
        stepsLabel.setText("Steps: " + stepsText(steps));
        targetLabel.setText("Target: " + tileText(target));
        swap.setEnabled(start != null && (target != null || steps != null && !steps.isEmpty()));
        exportRoute.setEnabled(start != null && (target != null || steps != null && !steps.isEmpty()));
    }

    void setStatus(String status) {
        statusLabel.setText("Status: " + (status == null || status.isBlank() ? "idle" : status));
    }

    String profileName() {
        return profileName.getText();
    }

    void setProfileName(String username) {
        profileName.setText(username == null ? "" : username);
    }

    void setProfileLoaded(boolean loaded) {
        profileStatus.setText(loaded ? "Profile Loaded" : "Profile: None");
        profileStatus.setForeground(loaded ? profileStatusLoadedForeground : profileStatusMissingForeground);
    }

    void setProfileLookupRunning(boolean running) {
        profileName.setEnabled(!running);
        lookUpProfile.setEnabled(!running);
    }

    void setOnOptionsChanged(Runnable onOptionsChanged) {
        this.onOptionsChanged = onOptionsChanged;
    }

    void setOnCenterStart(Runnable onCenterStart) {
        this.onCenterStart = onCenterStart;
    }

    void setOnSwap(Runnable onSwap) {
        this.onSwap = onSwap;
    }

    void setOnRecalculate(Runnable onRecalculate) {
        this.onRecalculate = onRecalculate;
    }

    void setOnImportRoute(Runnable onImportRoute) {
        this.onImportRoute = onImportRoute;
    }

    void setOnExportRoute(Runnable onExportRoute) {
        this.onExportRoute = onExportRoute;
    }

    void setOnClear(Runnable onClear) {
        this.onClear = onClear;
    }

    void setOnProfileLookup(Consumer<String> onProfileLookup) {
        this.onProfileLookup = onProfileLookup;
    }

    private void emitOptionsChanged() {
        if (onOptionsChanged != null) {
            onOptionsChanged.run();
        }
    }

    private void addTransportToggle(JPanel panel, TransportType type, String label) {
        JCheckBox box = new JCheckBox(label, true);
        box.addActionListener(e -> emitOptionsChanged());
        transportToggles.put(type, box);
        panel.add(box);
    }

    private JPanel collisionMapViewRow() {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));

        JLabel label = new JLabel("Collision Map View");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label);
        row.add(Box.createVerticalStrut(3));

        Dimension preferred = collisionMapMode.getPreferredSize();
        Dimension size = new Dimension(210, Math.max(26, preferred.height));
        collisionMapMode.setPreferredSize(size);
        collisionMapMode.setMinimumSize(size);
        collisionMapMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
        collisionMapMode.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(collisionMapMode);
        return row;
    }

    private JPanel stackedButtons(int width, AbstractButton... buttons) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (AbstractButton button : buttons) {
            configureCompactButton(button, width);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(button);
            panel.add(Box.createVerticalStrut(4));
        }
        return panel;
    }

    private JPanel actionButtons() {
        int width = 84;
        configureCompactButton(centerStart, width);
        configureCompactButton(swap, width);
        configureCompactButton(recalculate, width);
        configureCompactButton(importRoute, width);
        configureCompactButton(exportRoute, width);
        configureCompactButton(clear, width);

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JPanel firstRow = compactButtonRow(centerStart, swap, recalculate);
        JPanel secondRow = compactButtonRow(importRoute, exportRoute, clear);
        panel.add(firstRow);
        panel.add(Box.createVerticalStrut(4));
        panel.add(secondRow);
        return panel;
    }

    private JPanel compactButtonRow(AbstractButton... buttons) {
        JPanel row = new JPanel(new GridLayout(1, buttons.length, 4, 0));
        row.setOpaque(false);
        for (AbstractButton button : buttons) {
            row.add(button);
        }
        row.setMaximumSize(row.getPreferredSize());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JPanel teleportItemControls() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints tc = new GridBagConstraints();
        tc.gridx = 0;
        tc.gridy = 0;
        tc.insets = new Insets(2, 0, 2, 6);
        tc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Filter"), tc);
        tc.gridx = 1;
        teleportItemFilter.setPreferredSize(new Dimension(150, teleportItemFilter.getPreferredSize().height));
        panel.add(teleportItemFilter, tc);

        tc.gridx = 0;
        tc.gridy++;
        panel.add(new JLabel("Sort"), tc);
        tc.gridx = 1;
        teleportItemSort.setPreferredSize(new Dimension(150, teleportItemSort.getPreferredSize().height));
        panel.add(teleportItemSort, tc);
        return panel;
    }

    private void configureTeleportItemTable() {
        teleportItemTable.setShowGrid(false);
        teleportItemTable.setIntercellSpacing(new Dimension(0, 0));
        teleportItemTable.setRowHeight(Math.max(22, teleportItemTable.getRowHeight()));
        teleportItemTable.setTableHeader(null);
        teleportItemTable.setFillsViewportHeight(true);
        teleportItemTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        teleportItemTable.setRowSelectionAllowed(false);
        teleportItemTable.setCellSelectionEnabled(false);
        teleportItemTable.setPreferredScrollableViewportSize(new Dimension(220, 280));
        teleportItemTable.getColumnModel().getColumn(0).setMinWidth(30);
        teleportItemTable.getColumnModel().getColumn(0).setMaxWidth(30);
        teleportItemTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        TeleportItemRenderer renderer = new TeleportItemRenderer();
        teleportItemTable.getColumnModel().getColumn(0).setCellRenderer(renderer);
        teleportItemTable.getColumnModel().getColumn(1).setCellRenderer(renderer);
        teleportItemTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = teleportItemTable.rowAtPoint(e.getPoint());
                int column = teleportItemTable.columnAtPoint(e.getPoint());
                if (row >= 0 && column == 0) {
                    toggleTeleportItem(teleportItemModel.itemAt(teleportItemTable.convertRowIndexToModel(row)));
                }
            }
        });
    }

    private static void configureCompactButton(AbstractButton button, int width) {
        Dimension size = new Dimension(width, Math.max(24, button.getPreferredSize().height));
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
    }

    private void chooseColor(String title, Color current, Consumer<Color> setter) {
        Color picked = JColorChooser.showDialog(this, title, current);
        if (picked == null) {
            return;
        }
        int alpha = current == null ? 255 : current.getAlpha();
        setter.accept(new Color(picked.getRed(), picked.getGreen(), picked.getBlue(), alpha));
        refreshColorButtons();
        emitOptionsChanged();
    }

    private void refreshColorButtons() {
        syncColorButton(walkLineColorButton, walkLineColor);
        syncColorButton(transportLineColorButton, transportLineColor);
        syncColorButton(startMarkerColorButton, startMarkerColor);
        syncColorButton(stepMarkerColorButton, stepMarkerColor);
        syncColorButton(targetMarkerColorButton, targetMarkerColor);
        syncColorButton(collisionColorButton, collisionColor);
    }

    private static void syncColorButton(AbstractButton button, Color color) {
        button.setOpaque(true);
        button.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue()));
        button.setForeground(contrastColor(color));
    }

    private void refreshTeleportItemList() {
        teleportItemModel.setRows(visibleTeleportItems());
    }

    private List<TeleportItem> visibleTeleportItems() {
        String filter = teleportItemFilter.getText() == null
                ? ""
                : teleportItemFilter.getText().trim().toLowerCase(Locale.ROOT);
        List<TeleportItem> items = new ArrayList<>();
        for (TeleportItem item : TeleportItem.values()) {
            if (filter.isBlank() || item.label().toLowerCase(Locale.ROOT).contains(filter)) {
                items.add(item);
            }
        }
        TeleportItemSortMode mode = (TeleportItemSortMode) teleportItemSort.getSelectedItem();
        if (mode == TeleportItemSortMode.ENABLED_FIRST) {
            items.sort(Comparator.comparing((TeleportItem item) -> !enabledTeleportItems.contains(item))
                    .thenComparingInt(Enum::ordinal));
        } else if (mode == TeleportItemSortMode.DISABLED_FIRST) {
            items.sort(Comparator.comparing((TeleportItem item) -> enabledTeleportItems.contains(item))
                    .thenComparingInt(Enum::ordinal));
        }
        return items;
    }

    private void toggleTeleportItem(TeleportItem item) {
        if (enabledTeleportItems.contains(item)) {
            enabledTeleportItems.remove(item);
        } else {
            enabledTeleportItems.add(item);
        }
        refreshTeleportItemList();
        emitOptionsChanged();
    }

    private void emitProfileLookup() {
        if (onProfileLookup != null) {
            onProfileLookup.accept(profileName());
        }
    }

    private static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        return label;
    }

    private static void addFullRow(JPanel panel, GridBagConstraints c, Component component) {
        c.gridx = 0;
        c.gridwidth = 2;
        panel.add(component, c);
        c.gridwidth = 1;
        c.gridy++;
    }

    private static Icon loadRuneLiteIcon() {
        URL resource = ShortestPathPanel.class.getResource("/com/xeon/application/data/runelite_icon.png");
        if (resource == null) {
            return null;
        }
        ImageIcon icon = new ImageIcon(resource);
        Image image = icon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }

    private static String tileText(Tile tile) {
        return tile == null ? "none" : tile.x + "," + tile.y + "," + tile.z;
    }

    private static String stepsText(List<Tile> steps) {
        if (steps == null || steps.isEmpty()) {
            return "none";
        }
        return steps.size() + " (" + tileText(steps.get(steps.size() - 1)) + ")";
    }

    private static Color contrastColor(Color color) {
        int luminance = (int) (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    private static Set<TeleportItem> copyTeleportItems(Set<TeleportItem> items) {
        if (items == null) {
            return EnumSet.allOf(TeleportItem.class);
        }
        if (items.isEmpty()) {
            return EnumSet.noneOf(TeleportItem.class);
        }
        return new LinkedHashSet<>(items);
    }

    private final class TeleportItemTableModel extends AbstractTableModel {
        private final List<TeleportItem> rows = new ArrayList<>();

        void setRows(List<TeleportItem> items) {
            rows.clear();
            if (items != null) {
                rows.addAll(items);
            }
            fireTableDataChanged();
        }

        TeleportItem itemAt(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TeleportItem item = rows.get(rowIndex);
            boolean enabled = enabledTeleportItems.contains(item);
            if (columnIndex == 0) {
                return enabled ? "-" : "+";
            }
            return item.label();
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    private final class TeleportItemRenderer extends DefaultTableCellRenderer {
        private final Color enabledBackground = new Color(0x2F6F45);
        private final Color enabledForeground = new Color(0xF2FFF5);
        private final Color plusForeground = new Color(0x168C3A);
        private final Color minusForeground = new Color(0xC93636);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            TeleportItem item = teleportItemModel.itemAt(modelRow);
            boolean enabled = enabledTeleportItems.contains(item);
            setOpaque(true);
            setBorder(new EmptyBorder(0, column == 0 ? 0 : 4, 0, 4));
            setBackground(enabled ? enabledBackground : table.getBackground());
            if (column == 0) {
                setHorizontalAlignment(SwingConstants.CENTER);
                setText(enabled ? "-" : "+");
                setForeground(enabled ? minusForeground : plusForeground);
                setFont(table.getFont().deriveFont(Font.BOLD));
            } else {
                setHorizontalAlignment(SwingConstants.LEFT);
                setText(item.label());
                setForeground(enabled ? enabledForeground : table.getForeground());
                setFont(table.getFont().deriveFont(Font.PLAIN));
            }
            return this;
        }
    }

    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(24, visibleRect.height - 24);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

}
