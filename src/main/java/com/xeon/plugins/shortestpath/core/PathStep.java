package com.xeon.plugins.shortestpath.core;

public final class PathStep {
    private final int packedPosition;
    private final boolean bankVisited;

    public PathStep(int packedPosition, boolean bankVisited) {
        this.packedPosition = packedPosition;
        this.bankVisited = bankVisited;
    }

    public int getPackedPosition() {
        return packedPosition;
    }

    public boolean isBankVisited() {
        return bankVisited;
    }
}
