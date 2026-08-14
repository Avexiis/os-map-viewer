package com.xeon.view;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

final class TileCache extends LinkedHashMap<String, BufferedImage> {
    private static final long MIN_CACHE_BUDGET_BYTES = 512L * 1024L * 1024L;

    private long budgetBytes;
    private long liveBytes = 0L;

    TileCache(long budgetBytes) {
        super(512, 0.75f, true);
        this.budgetBytes = normalizeBudgetBytes(budgetBytes);
    }

    synchronized void setBudgetBytes(long budgetBytes) {
        this.budgetBytes = normalizeBudgetBytes(budgetBytes);
        trimToBudget();
    }

    synchronized long budgetBytes() {
        return budgetBytes;
    }

    static String key(int plane, int lod, int tx, int ty) {
        return plane + ":" + lod + ":" + tx + ":" + ty;
    }

    synchronized BufferedImage get(int plane, int lod, int tx, int ty) {
        return super.get(key(plane, lod, tx, ty));
    }

    synchronized void put(int plane, int lod, int tx, int ty, BufferedImage img) {
        BufferedImage previous = super.put(key(plane, lod, tx, ty), img);
        if (previous != null) {
            liveBytes -= approx(previous);
        }
        if (img != null) {
            liveBytes += approx(img);
        }
        trimToBudget();
    }

    private void trimToBudget() {
        while (liveBytes > budgetBytes && !isEmpty()) {
            Map.Entry<String, BufferedImage> eldest = entrySet().iterator().next();
            BufferedImage removed = super.remove(eldest.getKey());
            if (removed != null) {
                liveBytes -= approx(removed);
            }
        }
    }

    @Override
    public synchronized BufferedImage remove(Object key) {
        BufferedImage previous = super.remove(key);
        if (previous != null) {
            liveBytes -= approx(previous);
        }
        return previous;
    }

    private static long approx(BufferedImage image) {
        return image == null ? 0 : (long) image.getWidth() * (long) image.getHeight() * 4L;
    }

    private static long normalizeBudgetBytes(long budgetBytes) {
        return budgetBytes == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(MIN_CACHE_BUDGET_BYTES, budgetBytes);
    }
}
