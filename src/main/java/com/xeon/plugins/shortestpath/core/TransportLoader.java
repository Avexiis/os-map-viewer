package com.xeon.plugins.shortestpath.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TransportLoader {
    private static final TsvParser TSV_PARSER = new TsvParser();

    private TransportLoader() {
    }

    public static HashMap<Integer, Set<Transport>> loadAllFromResources() {
        HashMap<Integer, Set<Transport>> transports = new HashMap<>();
        for (TransportType type : TransportType.values()) {
            if (type.hasResourcePath()) {
                addTransports(transports, type.getResourcePath(), type, type.getRadiusThreshold());
            }
        }
        return transports;
    }

    private static void addTransports(Map<Integer, Set<Transport>> transports, String path,
                                      TransportType transportType, int radiusThreshold) {
        try {
            byte[] bytes = Util.readAllBytes(Objects.requireNonNull(
                    TransportLoader.class.getResourceAsStream(path),
                    "Missing resource " + path
            ));
            addTransportsFromContents(transports, new String(bytes, StandardCharsets.UTF_8), transportType, radiusThreshold);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + path, e);
        }
    }

    static void addTransportsFromContents(Map<Integer, Set<Transport>> transports, String contents,
                                          TransportType transportType, int radiusThreshold) {
        Set<Transport> newTransports = new HashSet<>();
        for (TransportRecord record : TSV_PARSER.parse(contents)) {
            newTransports.add(Transport.fromRecord(record, transportType));
        }

        Set<Transport> transportOrigins = new HashSet<>();
        Set<Transport> transportDestinations = new HashSet<>();
        for (Transport transport : newTransports) {
            int origin = transport.getOrigin();
            int destination = transport.getDestination();
            if ((origin == Transport.UNDEFINED_ORIGIN && destination == Transport.UNDEFINED_DESTINATION)
                    || (origin == Transport.LOCATION_PERMUTATION && destination == Transport.LOCATION_PERMUTATION)) {
                continue;
            } else if (origin != Transport.LOCATION_PERMUTATION
                    && origin != Transport.UNDEFINED_ORIGIN
                    && destination == Transport.LOCATION_PERMUTATION) {
                transportOrigins.add(transport);
            } else if (origin == Transport.LOCATION_PERMUTATION
                    && destination != Transport.UNDEFINED_DESTINATION) {
                transportDestinations.add(transport);
            }

            if (origin != Transport.LOCATION_PERMUTATION
                    && destination != Transport.UNDEFINED_DESTINATION
                    && destination != Transport.LOCATION_PERMUTATION
                    && (origin == Transport.UNDEFINED_ORIGIN || origin != destination)) {
                transports.computeIfAbsent(origin, ignored -> new HashSet<>()).add(transport);
            }
        }

        for (Transport origin : transportOrigins) {
            for (Transport destination : transportDestinations) {
                if (WorldPointUtil.distanceBetween2D(origin.getOrigin(), destination.getDestination()) > radiusThreshold) {
                    Transport combined = new Transport(origin, destination);
                    transports.computeIfAbsent(origin.getOrigin(), ignored -> new HashSet<>()).add(combined);
                }
            }
        }
    }
}
