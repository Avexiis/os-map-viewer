package com.xeon.plugins.shortestpath.core;

import java.util.Arrays;

public final class PrimitiveIntHashMap<V> {
    private static final int MINIMUM_SIZE = 8;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private final float loadFactor;
    private int[] keys;
    private Object[] values;
    private int size;
    private int capacity;
    private int maxSize;
    private int mask;

    public PrimitiveIntHashMap(int initialSize) {
        this(initialSize, DEFAULT_LOAD_FACTOR);
    }

    public PrimitiveIntHashMap(int initialSize, float loadFactor) {
        if (loadFactor < 0.0f || loadFactor > 1.0f) {
            throw new IllegalArgumentException("Load factor must be between 0 and 1");
        }
        this.loadFactor = loadFactor;
        setNewSize(initialSize);
        recreateArrays();
    }

    public int size() {
        return size;
    }

    public int[] keys() {
        int[] result = new int[size];
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                result[index++] = keys[i];
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public V getOrDefault(int key, V defaultValue) {
        int slot = findSlot(key);
        return slot < 0 ? defaultValue : (V) values[slot];
    }

    @SuppressWarnings("unchecked")
    public V put(int key, V value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot insert a null value");
        }
        int i = (hash(key) & 0x7FFFFFFF) & mask;
        while (values[i] != null) {
            if (keys[i] == key) {
                V previous = (V) values[i];
                values[i] = value;
                return previous;
            }
            i = (i + 1) & mask;
        }
        keys[i] = key;
        values[i] = value;
        incrementSize();
        return null;
    }

    private int findSlot(int key) {
        int i = (hash(key) & 0x7FFFFFFF) & mask;
        while (values[i] != null) {
            if (keys[i] == key) {
                return i;
            }
            i = (i + 1) & mask;
        }
        return -1;
    }

    private void incrementSize() {
        size++;
        if (size >= capacity) {
            rehash();
        }
    }

    @SuppressWarnings("unchecked")
    private void rehash() {
        int[] oldKeys = keys;
        Object[] oldValues = values;
        growCapacity();
        recreateArrays();
        int oldSize = size;
        size = 0;
        for (int i = 0; i < oldValues.length; i++) {
            if (oldValues[i] != null) {
                put(oldKeys[i], (V) oldValues[i]);
            }
        }
        size = oldSize;
    }

    private void growCapacity() {
        setNewSize(maxSize);
    }

    private void recreateArrays() {
        keys = new int[maxSize];
        values = new Object[maxSize];
    }

    private void setNewSize(int requestedSize) {
        int sizeForTable = Math.max(requestedSize, MINIMUM_SIZE - 1);
        maxSize = nextPowerOfTwo(sizeForTable + 1);
        mask = maxSize - 1;
        capacity = Math.max(1, (int) (maxSize * loadFactor));
    }

    private static int nextPowerOfTwo(int value) {
        int next = 1;
        while (next < value && next > 0) {
            next <<= 1;
        }
        return next > 0 ? next : 1 << 30;
    }

    private static int hash(int value) {
        int h = value * 0x9E3779B1;
        return h ^ (h >>> 16);
    }

    @Override
    public String toString() {
        return "PrimitiveIntHashMap{size=" + size + ", keys=" + Arrays.toString(keys()) + "}";
    }
}
