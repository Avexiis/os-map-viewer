package com.xeon.tools;

import com.xeon.io.Paths;
import com.xeon.plugins.shortestpath.core.CollisionMap;
import com.xeon.plugins.shortestpath.core.PathOptions;
import com.xeon.plugins.shortestpath.core.Pathfinder;
import com.xeon.plugins.shortestpath.core.PathfinderConfig;
import com.xeon.plugins.shortestpath.core.PathfinderResult;
import com.xeon.plugins.shortestpath.core.SplitFlagMap;
import com.xeon.plugins.shortestpath.core.TeleportItem;
import com.xeon.plugins.shortestpath.core.Transport;
import com.xeon.plugins.shortestpath.core.TransportType;
import com.xeon.plugins.shortestpath.core.WikiSyncClient;
import com.xeon.plugins.shortestpath.core.WikiSyncProfile;
import com.xeon.plugins.shortestpath.core.WorldPointUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ShortestPathDataCheck {
    private ShortestPathDataCheck() {
    }

    public static void main(String[] args) {
        PathfinderConfig config = new PathfinderConfig(PathOptions.defaults());
        SplitFlagMap.RegionExtent extents = SplitFlagMap.getRegionExtents();
        require(extents.minX() == Paths.MIN_RX, "collision min region X");
        require(extents.minY() == Paths.MIN_RY, "collision min region Y");
        require(extents.maxX() == Paths.MAX_RX, "collision max region X");
        require(extents.maxY() == Paths.MAX_RY, "collision max region Y");

        CollisionMap map = config.getMap();
        require(map.e(3200, 3200, 0) || map.w(3200, 3200, 0)
                || map.n(3200, 3200, 0) || map.s(3200, 3200, 0), "sample Lumbridge-area movement flags");

        int transports = 0;
        for (Transport ignored : config.visibleTransports()) {
            transports++;
        }
        require(transports > 0, "transport data");
        require(noLeagueSpecificTransports(config), "league-specific transport filtering");
        validateTeleportItems(config);
        require(config.profileRequirements().skills().contains("agility"), "skill requirements");
        require(config.profileRequirements().quests().contains("observatoryquest"), "quest requirements");

        Transport observatoryShortcut = findTransport(config,
                WorldPointUtil.packWorldPoint(2449, 3155, 0),
                WorldPointUtil.packWorldPoint(2444, 3165, 0),
                "observatoryquest");
        require(observatoryShortcut != null, "profile-gated shortcut");
        WikiSyncProfile blockedProfile = new WikiSyncProfile("tester", "STANDARD", Instant.EPOCH,
                Map.of("agility", 99, "strength", 99, "ranged", 99),
                Set.of());
        require(!blockedProfile.canUse(observatoryShortcut), "profile quest filtering");
        WikiSyncProfile allowedProfile = new WikiSyncProfile("tester", "STANDARD", Instant.EPOCH,
                Map.of("agility", 99, "strength", 99, "ranged", 99),
                Set.of("observatoryquest"));
        require(allowedProfile.canUse(observatoryShortcut), "profile allows satisfied requirements");

        WikiSyncProfile parsedProfile = parseSampleProfile(config);
        require(parsedProfile.hasLevel("Agility", 77), "WikiSync level parsing");
        require(parsedProfile.hasLevel("Sailing", 87), "WikiSync sailing level parsing");
        require(parsedProfile.hasLevel("Total", 1989), "WikiSync total level parsing");
        require(parsedProfile.hasLevel("Combat", 120), "WikiSync combat level parsing");
        require(parsedProfile.hasCompletedQuest("Observatory Quest"), "WikiSync quest parsing");
        require(parsedProfile.hasCompletedQuest("A Soul's Bane"), "WikiSync untrimmed completed quest parsing");

        Pathfinder walkPath = new Pathfinder(config,
                WorldPointUtil.packWorldPoint(3200, 3200, 0),
                Set.of(WorldPointUtil.packWorldPoint(3205, 3200, 0)),
                null);
        walkPath.run();
        PathfinderResult walkResult = walkPath.getResult();
        require(walkResult != null && walkResult.isReached(), "sample walking route");

        Pathfinder teleportPath = new Pathfinder(config,
                WorldPointUtil.packWorldPoint(3222, 3218, 0),
                Set.of(WorldPointUtil.packWorldPoint(3213, 3424, 0)),
                null);
        teleportPath.run();
        PathfinderResult teleportResult = teleportPath.getResult();
        require(teleportResult != null && teleportResult.isReached(), "sample global teleport route");

        System.out.printf("Shortest-path data OK: collision regions %d..%d x %d..%d, %,d transports%n",
                extents.minX(), extents.maxX(), extents.minY(), extents.maxY(), transports);
    }

    private static void validateTeleportItems(PathfinderConfig config) {
        EnumSet<TeleportItem> seen = EnumSet.noneOf(TeleportItem.class);
        int itemTeleports = 0;
        for (Transport transport : config.visibleTransports()) {
            if (transport.getType() != TransportType.TELEPORTATION_ITEM) {
                continue;
            }
            itemTeleports++;
            require(transport.getTeleportItem() != null,
                    "teleport item mapping for " + transport.getDisplayInfo());
            seen.add(transport.getTeleportItem());
        }
        require(itemTeleports > 0, "item teleport data");

        EnumSet<TeleportItem> missing = EnumSet.allOf(TeleportItem.class);
        missing.removeAll(seen);
        require(missing.isEmpty(), "teleport item enum coverage " + missing);

        PathfinderConfig noGamesNecklace = new PathfinderConfig(PathOptions.defaults());
        noGamesNecklace.setEnabledTeleportItems(EnumSet.complementOf(EnumSet.of(TeleportItem.GAMES_NECKLACE)));
        require(noTeleportItem(noGamesNecklace, TeleportItem.GAMES_NECKLACE),
                "disabled teleport item filtering");

        PathOptions noItemOptions = new PathOptions(true, true, false, true, true,
                PathOptions.defaultEnabledTransportTypes(), 3000L);
        PathfinderConfig noItems = new PathfinderConfig(noItemOptions);
        require(noItemTeleports(noItems), "avoid item teleports filtering");
    }

    private static boolean noTeleportItem(PathfinderConfig config, TeleportItem item) {
        for (Transport transport : config.visibleTransports()) {
            if (transport.getType() == TransportType.TELEPORTATION_ITEM
                    && transport.getTeleportItem() == item) {
                return false;
            }
        }
        return true;
    }

    private static boolean noItemTeleports(PathfinderConfig config) {
        for (Transport transport : config.visibleTransports()) {
            if (transport.getType() == TransportType.TELEPORTATION_ITEM) {
                return false;
            }
        }
        return true;
    }

    private static boolean noLeagueSpecificTransports(PathfinderConfig config) {
        for (Transport transport : config.visibleTransports()) {
            if (transport.isLeagueSpecific()) {
                return false;
            }
        }
        return true;
    }

    private static WikiSyncProfile parseSampleProfile(PathfinderConfig config) {
        String json = """
                {
                  "username": "Tester",
                  "timestamp": "2026-08-12T00:00:00.000Z",
                  "levels": {
                    "Hunter": 72,
                    "Thieving": 75,
                    "Runecraft": 67,
                    "Construction": 86,
                    "Cooking": 90,
                    "Magic": 97,
                    "Fletching": 75,
                    "Herblore": 71,
                    "Firemaking": 81,
                    "Attack": 91,
                    "Fishing": 71,
                    "Crafting": 77,
                    "Hitpoints": 99,
                    "Ranged": 99,
                    "Mining": 73,
                    "Sailing": 87,
                    "Smithing": 73,
                    "Agility": 77,
                    "Woodcutting": 74,
                    "Slayer": 90,
                    "Defence": 90,
                    "Strength": 99,
                    "Prayer": 90,
                    "Farming": 85
                  },
                  "quests": {
                    "A Soul's Bane": 2,
                    "Observatory Quest": 2,
                    "Legends' Quest": 1
                  }
                }
                """;
        try {
            return WikiSyncClient.parseProfile(json, config.profileRequirements(), "Tester");
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid WikiSync sample profile", ex);
        }
    }

    private static Transport findTransport(PathfinderConfig config, int source, int destination, String questRequirement) {
        for (Transport transport : config.visibleTransports()) {
            if (transport.getOrigin() == source
                    && transport.getDestination() == destination
                    && transport.getQuestRequirements().contains(questRequirement)) {
                return transport;
            }
        }
        return null;
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new IllegalStateException("Invalid shortest-path " + label);
        }
    }
}
