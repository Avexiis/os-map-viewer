package com.xeon.plugins.shortestpath.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SplitFlagMap {
    public static final int REGION_SIZE = 64;

    private static final int FLAG_COUNT = 2;
    private static final int BITS_PER_PLANE = REGION_SIZE * REGION_SIZE * FLAG_COUNT;
    private static final int WORDS_PER_PLANE = BITS_PER_PLANE / Long.SIZE;
    private static final int REGION_MASK = REGION_SIZE - 1;

    private static RegionExtent regionExtents;

    private final byte[] regionMapPlaneCounts;
    private final long[] flags;
    private final int[] regionWordOffset;
    private final int widthInclusive;

    public SplitFlagMap(Map<Integer, byte[]> compressedRegions, RegionExtent extents) {
        regionExtents = Objects.requireNonNull(extents, "extents");
        widthInclusive = regionExtents.width() + 1;
        int heightInclusive = regionExtents.height() + 1;
        int regionCount = widthInclusive * heightInclusive;
        regionMapPlaneCounts = new byte[regionCount];
        regionWordOffset = new int[regionCount];
        Arrays.fill(regionWordOffset, -1);

        Map<Integer, long[]> regionWords = new HashMap<>(compressedRegions.size());
        int totalWords = 0;
        for (Map.Entry<Integer, byte[]> entry : compressedRegions.entrySet()) {
            int pos = entry.getKey();
            int index = getIndex(unpackX(pos), unpackY(pos));
            BitSet bits = BitSet.valueOf(entry.getValue());
            int planeCount = (bits.size() + BITS_PER_PLANE - 1) / BITS_PER_PLANE;
            regionMapPlaneCounts[index] = (byte) planeCount;
            regionWordOffset[index] = totalWords;
            regionWords.put(index, bits.toLongArray());
            totalWords += planeCount * WORDS_PER_PLANE;
        }

        flags = new long[totalWords];
        for (Map.Entry<Integer, long[]> entry : regionWords.entrySet()) {
            long[] words = entry.getValue();
            System.arraycopy(words, 0, flags, regionWordOffset[entry.getKey()], words.length);
        }
    }

    public static RegionExtent getRegionExtents() {
        return regionExtents;
    }

    public static int unpackX(int position) {
        return position & 0xFFFF;
    }

    public static int unpackY(int position) {
        return (position >> 16) & 0xFFFF;
    }

    public static int packPosition(int x, int y) {
        return (x & 0xFFFF) | ((y & 0xFFFF) << 16);
    }

    public static SplitFlagMap fromResources() {
        try (InputStream resource = SplitFlagMap.class.getResourceAsStream("/com/xeon/application/data/collision-map.zip")) {
            return fromZipStream(resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SplitFlagMap fromZipStream(InputStream resource) throws IOException {
        Map<Integer, byte[]> compressedRegions = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(Objects.requireNonNull(resource, "com/xeon/application/data/collision-map.zip"))) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = 0;
            int maxY = 0;

            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String[] name = entry.getName().split("_");
                int x = Integer.parseInt(name[0]);
                int y = Integer.parseInt(name[1]);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                compressedRegions.put(packPosition(x, y), Util.readAllBytes(in));
            }

            return new SplitFlagMap(compressedRegions, new RegionExtent(minX, minY, maxX, maxY));
        }
    }

    public byte getRegionPlaneCounts(int index) {
        return regionMapPlaneCounts[index];
    }

    public boolean get(int x, int y, int z, int flag) {
        int index = getIndex(x / REGION_SIZE, y / REGION_SIZE);
        if (index < 0 || index >= regionWordOffset.length) {
            return false;
        }

        int wordOffset = regionWordOffset[index];
        if (wordOffset < 0 || z < 0 || z >= regionMapPlaneCounts[index]) {
            return false;
        }

        int localBit = (z * REGION_SIZE * REGION_SIZE
                + (y & REGION_MASK) * REGION_SIZE
                + (x & REGION_MASK)) * FLAG_COUNT + flag;
        return (flags[wordOffset + (localBit >> 6)] >>> (localBit & 63) & 1L) != 0L;
    }

    private int getIndex(int regionX, int regionY) {
        return (regionX - regionExtents.minX()) + (regionY - regionExtents.minY()) * widthInclusive;
    }

    public record RegionExtent(int minX, int minY, int maxX, int maxY) {
        public int width() {
            return maxX - minX;
        }

        public int height() {
            return maxY - minY;
        }

        public int count() {
            return (width() + 1) * (height() + 1);
        }
    }
}
