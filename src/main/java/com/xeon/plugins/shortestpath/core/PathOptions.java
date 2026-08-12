package com.xeon.plugins.shortestpath.core;

import java.util.EnumSet;
import java.util.Set;

public record PathOptions(
        boolean includeTransports,
        boolean includeTeleports,
        boolean avoidWilderness,
        boolean includePoh,
        boolean avoidItemTeleports,
        Set<TransportType> enabledTransportTypes,
        long calculationCutoffMillis
) {
    public PathOptions {
        enabledTransportTypes = copyTransportTypes(enabledTransportTypes);
    }

    public static PathOptions defaults() {
        return new PathOptions(true, true, false, true, false, defaultEnabledTransportTypes(), 3000L);
    }

    public static Set<TransportType> defaultEnabledTransportTypes() {
        EnumSet<TransportType> types = EnumSet.allOf(TransportType.class);
        types.remove(TransportType.HOT_AIR_BALLOON);
        types.remove(TransportType.TELEPORTATION_MINIGAME);
        types.remove(TransportType.WILDERNESS_OBELISK);
        return types;
    }

    public static Set<TransportType> copyTransportTypes(Set<TransportType> types) {
        if (types == null) {
            return defaultEnabledTransportTypes();
        }
        if (types.isEmpty()) {
            return EnumSet.noneOf(TransportType.class);
        }
        return EnumSet.copyOf(types);
    }
}
