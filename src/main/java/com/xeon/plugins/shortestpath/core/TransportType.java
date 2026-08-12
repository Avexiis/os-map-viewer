package com.xeon.plugins.shortestpath.core;

public enum TransportType {
    TRANSPORT("/com/xeon/application/data/transports/transports.tsv", false, 0, 0),
    AGILITY_SHORTCUT("/com/xeon/application/data/transports/agility_shortcuts.tsv", false, 0, 0),
    GRAPPLE_SHORTCUT(null, false, 0, 0),
    BOAT("/com/xeon/application/data/transports/boats.tsv", false, 0, 0),
    CANOE("/com/xeon/application/data/transports/canoes.tsv", false, 0, 0),
    CHARTER_SHIP("/com/xeon/application/data/transports/charter_ships.tsv", false, 0, 0),
    SHIP("/com/xeon/application/data/transports/ships.tsv", false, 0, 0),
    FAIRY_RING("/com/xeon/application/data/transports/fairy_rings.tsv", false, 6, 0),
    GNOME_GLIDER("/com/xeon/application/data/transports/gnome_gliders.tsv", false, 6, 0),
    HOT_AIR_BALLOON("/com/xeon/application/data/transports/hot_air_balloons.tsv", false, 7, 0),
    MAGIC_CARPET("/com/xeon/application/data/transports/magic_carpets.tsv", false, 0, 0),
    MAGIC_MUSHTREE("/com/xeon/application/data/transports/magic_mushtrees.tsv", false, 5, 0),
    MINECART("/com/xeon/application/data/transports/minecarts.tsv", false, 0, 0),
    QUETZAL("/com/xeon/application/data/transports/quetzals.tsv", false, 5, 0) {
        @Override
        public TransportType sharesDestinationsWith() {
            return QUETZAL_WHISTLE;
        }
    },
    QUETZAL_WHISTLE("/com/xeon/application/data/transports/quetzal_whistle.tsv", true, 0, 0) {
        @Override
        public TransportType sharesDestinationsWith() {
            return QUETZAL;
        }

        @Override
        public int differentialCost() {
            return 15;
        }
    },
    SEASONAL_TRANSPORTS("/com/xeon/application/data/transports/seasonal_transports.tsv", false, 0, 0, true),
    SPIRIT_TREE("/com/xeon/application/data/transports/spirit_trees.tsv", false, 5, 0),
    TELEPORTATION_BOX("/com/xeon/application/data/transports/teleportation_boxes.tsv", false, 0, 0),
    TELEPORTATION_ITEM("/com/xeon/application/data/transports/teleportation_items.tsv", true, 0, 0),
    TELEPORTATION_LEVER("/com/xeon/application/data/transports/teleportation_levers.tsv", false, 0, 0),
    TELEPORTATION_MINIGAME("/com/xeon/application/data/transports/teleportation_minigames.tsv", true, 0, 0),
    TELEPORTATION_PORTAL("/com/xeon/application/data/transports/teleportation_portals.tsv", false, 0, 0),
    TELEPORTATION_PORTAL_POH("/com/xeon/application/data/transports/teleportation_portals_poh.tsv", false, 0, 0),
    TELEPORTATION_SPELL("/com/xeon/application/data/transports/teleportation_spells.tsv", true, 0, 0),
    TELEPORTATION_SPELL_HOME("/com/xeon/application/data/transports/teleportation_spells_home.tsv", true, 0, 0),
    WILDERNESS_OBELISK("/com/xeon/application/data/transports/wilderness_obelisks.tsv", false, 0, 0);

    private final String resourcePath;
    private final boolean teleport;
    private final int radiusThreshold;
    private final int additionalCost;
    private final boolean leagueOnly;

    TransportType(String resourcePath, boolean teleport, int radiusThreshold, int additionalCost) {
        this(resourcePath, teleport, radiusThreshold, additionalCost, false);
    }

    TransportType(String resourcePath, boolean teleport, int radiusThreshold, int additionalCost, boolean leagueOnly) {
        this.resourcePath = resourcePath;
        this.teleport = teleport;
        this.radiusThreshold = radiusThreshold;
        this.additionalCost = additionalCost;
        this.leagueOnly = leagueOnly;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public boolean hasResourcePath() {
        return resourcePath != null;
    }

    public boolean isTeleport() {
        return teleport;
    }

    public int getRadiusThreshold() {
        return radiusThreshold;
    }

    public int getAdditionalCost() {
        return additionalCost;
    }

    public boolean isLeagueOnly() {
        return leagueOnly;
    }

    public TransportType sharesDestinationsWith() {
        return null;
    }

    public int differentialCost() {
        return 0;
    }
}
