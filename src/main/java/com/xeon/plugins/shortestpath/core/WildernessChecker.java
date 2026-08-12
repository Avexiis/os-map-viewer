package com.xeon.plugins.shortestpath.core;

import java.util.Set;

import com.xeon.plugins.shortestpath.core.WorldPointUtil.WorldArea;

public final class WildernessChecker {
    private static final WorldArea WILDERNESS_ABOVE_GROUND = new WorldArea(2944, 3525, 448, 448);
    private static final WorldArea WILDERNESS_UNDERGROUND = new WorldArea(2944, 9918, 518, 458);

    private static final WorldArea FEROX_ENCLAVE_1 = new WorldArea(3123, 3622, 2, 10);
    private static final WorldArea FEROX_ENCLAVE_2 = new WorldArea(3125, 3617, 16, 23);
    private static final WorldArea FEROX_ENCLAVE_3 = new WorldArea(3138, 3636, 18, 10);
    private static final WorldArea FEROX_ENCLAVE_4 = new WorldArea(3141, 3625, 14, 11);
    private static final WorldArea FEROX_ENCLAVE_5 = new WorldArea(3141, 3619, 7, 6);

    private static final WorldArea NOT_WILDERNESS_1 = new WorldArea(2997, 3525, 34, 9);
    private static final WorldArea NOT_WILDERNESS_2 = new WorldArea(3005, 3534, 21, 10);
    private static final WorldArea NOT_WILDERNESS_3 = new WorldArea(3000, 3534, 5, 5);
    private static final WorldArea NOT_WILDERNESS_4 = new WorldArea(3031, 3525, 2, 2);

    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_20 = new WorldArea(2944, 3680, 448, 448);
    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_30 = new WorldArea(2944, 3760, 448, 448);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_20 = new WorldArea(2944, 10075, 518, 301);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_30 = new WorldArea(2944, 10155, 518, 221);

    private WildernessChecker() {
    }

    public static boolean isInWilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_ABOVE_GROUND) == 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_1) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_2) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_3) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_4) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_5) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_1) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_2) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_3) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_4) != 0
                || WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_UNDERGROUND) == 0;
    }

    public static boolean isInWilderness(Set<Integer> packedPoints) {
        for (int packedPoint : packedPoints) {
            if (isInWilderness(packedPoint)) {
                return true;
            }
        }
        return false;
    }

    static boolean isInLevel20Wilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_20) == 0
                || WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_20) == 0;
    }

    static boolean isInLevel30Wilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_30) == 0
                || WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_30) == 0;
    }
}
