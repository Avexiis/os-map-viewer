package com.xeon.plugins.shortestpath.core;

public final class CollisionMap {
    private static final OrdinalDirection[] ORDINAL_VALUES = OrdinalDirection.values();

    private final SplitFlagMap collisionData;
    private final PrimitiveIntList neighbors = new PrimitiveIntList(16);
    private final boolean[] traversable = new boolean[8];

    public CollisionMap(SplitFlagMap collisionData) {
        this.collisionData = collisionData;
    }

    public byte getRegionPlaneCounts(int regionIndex) {
        return collisionData.getRegionPlaneCounts(regionIndex);
    }

    public boolean n(int x, int y, int z) {
        return get(x, y, z, 0);
    }

    public boolean s(int x, int y, int z) {
        return n(x, y - 1, z);
    }

    public boolean e(int x, int y, int z) {
        return get(x, y, z, 1);
    }

    public boolean w(int x, int y, int z) {
        return e(x - 1, y, z);
    }

    public boolean isBlocked(int x, int y, int z) {
        return !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z);
    }

    PrimitiveIntList getNeighbors(int node, VisitedTiles visited, PathfinderConfig config, int wildernessLevel,
                                  boolean targetInWilderness, NodeGraph graph) {
        if (graph.isTile(node)) {
            return getTileNeighbors(node, visited, config, wildernessLevel, graph);
        }
        return getAbstractNodeNeighbors(node, visited, config, targetInWilderness, graph);
    }

    private boolean get(int x, int y, int z, int flag) {
        return collisionData.get(x, y, z, flag);
    }

    private boolean ne(int x, int y, int z) {
        return n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z);
    }

    private boolean nw(int x, int y, int z) {
        return n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z);
    }

    private boolean se(int x, int y, int z) {
        return s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z);
    }

    private boolean sw(int x, int y, int z) {
        return s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z);
    }

    private PrimitiveIntList getTileNeighbors(int node, VisitedTiles visited, PathfinderConfig config,
                                              int wildernessLevel, NodeGraph graph) {
        int packedPosition = graph.packedPosition(node);
        int x = WorldPointUtil.unpackWorldX(packedPosition);
        int y = WorldPointUtil.unpackWorldY(packedPosition);
        int z = WorldPointUtil.unpackWorldPlane(packedPosition);

        neighbors.clear();
        boolean pathBankVisited = graph.bankVisited(node)
                || (config.isBankPathEnabled() && config.bankAccessible(packedPosition));

        Transport[] transports = config.getTransportsPacked(pathBankVisited)
                .getOrDefault(packedPosition, TransportAvailability.EMPTY_TRANSPORTS);
        int inheritedDifferential = graph.isTransport(node) && graph.isDelayedVisit(node)
                ? graph.differentialCost(node)
                : 0;
        for (Transport transport : transports) {
            boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
            if (!delayedVisit && visited.get(transport.getDestination(), pathBankVisited)) {
                continue;
            }
            int chainPenalty = delayedVisit && inheritedDifferential > 0 ? inheritedDifferential : 0;
            neighbors.add(graph.createTransport(
                    transport.getDestination(),
                    node,
                    transport.getDuration(),
                    config.getAdditionalTransportCost(transport) + chainPenalty,
                    pathBankVisited,
                    delayedVisit,
                    delayedVisit ? config.getDifferentialCost(transport) : 0
            ));
        }

        AbstractNodeKind abstractKind = AbstractNodeKind.fromWildernessLevel(wildernessLevel);
        if (!visited.getAbstract(abstractKind, pathBankVisited)) {
            neighbors.add(graph.createAbstract(abstractKind, node, pathBankVisited));
        }

        if (isBlocked(x, y, z)) {
            boolean westBlocked = isBlocked(x - 1, y, z);
            boolean eastBlocked = isBlocked(x + 1, y, z);
            boolean southBlocked = isBlocked(x, y - 1, z);
            boolean northBlocked = isBlocked(x, y + 1, z);
            boolean southWestBlocked = isBlocked(x - 1, y - 1, z);
            boolean southEastBlocked = isBlocked(x + 1, y - 1, z);
            boolean northWestBlocked = isBlocked(x - 1, y + 1, z);
            boolean northEastBlocked = isBlocked(x + 1, y + 1, z);
            traversable[0] = !westBlocked;
            traversable[1] = !eastBlocked;
            traversable[2] = !southBlocked;
            traversable[3] = !northBlocked;
            traversable[4] = !southWestBlocked && !westBlocked && !southBlocked;
            traversable[5] = !southEastBlocked && !eastBlocked && !southBlocked;
            traversable[6] = !northWestBlocked && !westBlocked && !northBlocked;
            traversable[7] = !northEastBlocked && !eastBlocked && !northBlocked;
        } else {
            traversable[0] = w(x, y, z);
            traversable[1] = e(x, y, z);
            traversable[2] = s(x, y, z);
            traversable[3] = n(x, y, z);
            traversable[4] = sw(x, y, z);
            traversable[5] = se(x, y, z);
            traversable[6] = nw(x, y, z);
            traversable[7] = ne(x, y, z);
        }

        for (int i = 0; i < traversable.length; i++) {
            OrdinalDirection direction = ORDINAL_VALUES[i];
            int neighborPacked = packedPointFromOrdinal(packedPosition, direction);
            if (visited.get(neighborPacked, pathBankVisited)) {
                continue;
            }

            if (traversable[i]) {
                neighbors.add(graph.createTile(neighborPacked, node, pathBankVisited));
            } else if (Math.abs(direction.x + direction.y) == 1 && isBlocked(x + direction.x, y + direction.y, z)) {
                Transport[] neighborTransports = config.getTransportsPacked(pathBankVisited)
                        .getOrDefault(neighborPacked, TransportAvailability.EMPTY_TRANSPORTS);
                for (Transport transport : neighborTransports) {
                    if (transport.getOrigin() == Transport.UNDEFINED_ORIGIN
                            || !transport.isUsableAtWildernessLevel(wildernessLevel)
                            || visited.get(transport.getOrigin(), pathBankVisited)) {
                        continue;
                    }
                    neighbors.add(graph.createTile(transport.getOrigin(), node, pathBankVisited));
                }
            }
        }

        return neighbors;
    }

    private PrimitiveIntList getAbstractNodeNeighbors(int node, VisitedTiles visited, PathfinderConfig config,
                                                      boolean targetInWilderness, NodeGraph graph) {
        neighbors.clear();
        int sourceTile = graph.getClosestTilePosition(node);
        boolean bankVisited = graph.bankVisited(node);
        int maxWildernessLevel = graph.abstractKind(node).maxWildernessLevel();
        for (Transport transport : config.getUsableTeleports(bankVisited)) {
            boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
            if (!delayedVisit && visited.get(transport.getDestination(), bankVisited)) {
                continue;
            }
            if (!transport.isUsableAtWildernessLevel(maxWildernessLevel)) {
                continue;
            }
            if (config.avoidWilderness(sourceTile, transport.getDestination(), targetInWilderness)) {
                continue;
            }
            int differentialCost = delayedVisit ? config.getDifferentialCost(transport) : 0;
            neighbors.add(graph.createTransport(
                    transport.getDestination(),
                    node,
                    transport.getDuration(),
                    config.getAdditionalTransportCost(transport),
                    bankVisited,
                    delayedVisit,
                    differentialCost
            ));
        }
        return neighbors;
    }

    private static int packedPointFromOrdinal(int startPacked, OrdinalDirection direction) {
        int x = WorldPointUtil.unpackWorldX(startPacked);
        int y = WorldPointUtil.unpackWorldY(startPacked);
        int plane = WorldPointUtil.unpackWorldPlane(startPacked);
        return WorldPointUtil.packWorldPoint(x + direction.x, y + direction.y, plane);
    }
}
