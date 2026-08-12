package com.xeon.plugins.shortestpath.core;

import java.util.List;

public final class PathfinderResult {
    private final int start;
    private final int target;
    private final boolean reached;
    private final List<PathStep> pathSteps;
    private final int closestReachedPoint;
    private final int nodesChecked;
    private final int transportsChecked;
    private final long elapsedNanos;
    private final PathTerminationReason terminationReason;

    PathfinderResult(
            int start,
            int target,
            boolean reached,
            List<PathStep> pathSteps,
            int closestReachedPoint,
            int nodesChecked,
            int transportsChecked,
            long elapsedNanos,
            PathTerminationReason terminationReason
    ) {
        this.start = start;
        this.target = target;
        this.reached = reached;
        this.pathSteps = pathSteps;
        this.closestReachedPoint = closestReachedPoint;
        this.nodesChecked = nodesChecked;
        this.transportsChecked = transportsChecked;
        this.elapsedNanos = elapsedNanos;
        this.terminationReason = terminationReason;
    }

    public int getStart() {
        return start;
    }

    public int getTarget() {
        return target;
    }

    public boolean isReached() {
        return reached;
    }

    public List<PathStep> getPathSteps() {
        return pathSteps;
    }

    public int getClosestReachedPoint() {
        return closestReachedPoint;
    }

    public int getNodesChecked() {
        return nodesChecked;
    }

    public int getTransportsChecked() {
        return transportsChecked;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public PathTerminationReason getTerminationReason() {
        return terminationReason;
    }
}
