package com.xeon.view;

@FunctionalInterface
public interface RegionChangeListener {
    void regionChanged(int previousRegionId, int currentRegionId);
}
