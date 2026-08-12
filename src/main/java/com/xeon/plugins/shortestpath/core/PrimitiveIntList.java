package com.xeon.plugins.shortestpath.core;

import java.util.Arrays;

public final class PrimitiveIntList {
    private int[] values;
    private int size;

    public PrimitiveIntList() {
        this(10);
    }

    public PrimitiveIntList(int initialCapacity) {
        values = new int[Math.max(0, initialCapacity)];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
        return values[index];
    }

    public void add(int value) {
        ensureCapacity(size + 1);
        values[size++] = value;
    }

    public void clear() {
        size = 0;
    }

    private void ensureCapacity(int required) {
        if (required <= values.length) {
            return;
        }
        int next = values.length + (values.length >> 1);
        if (next < required) {
            next = required;
        }
        if (next == 0) {
            next = 1;
        }
        values = Arrays.copyOf(values, next);
    }
}
