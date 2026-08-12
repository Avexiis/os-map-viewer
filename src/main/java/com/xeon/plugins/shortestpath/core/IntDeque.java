package com.xeon.plugins.shortestpath.core;

final class IntDeque {
    private int[] elements;
    private int head;
    private int tail;
    private int size;

    IntDeque(int initialCapacity) {
        elements = new int[Math.max(1, initialCapacity)];
    }

    boolean isEmpty() {
        return size == 0;
    }

    void addLast(int value) {
        if (size == elements.length) {
            grow();
        }
        elements[tail] = value;
        tail = increment(tail);
        size++;
    }

    void addFirst(int value) {
        if (size == elements.length) {
            grow();
        }
        head = decrement(head);
        elements[head] = value;
        size++;
    }

    int peekFirst() {
        return size == 0 ? NodeGraph.NO_NODE : elements[head];
    }

    int pollFirst() {
        if (size == 0) {
            return NodeGraph.NO_NODE;
        }
        int value = elements[head];
        head = increment(head);
        size--;
        return value;
    }

    void clear() {
        head = 0;
        tail = 0;
        size = 0;
    }

    private int increment(int index) {
        return index + 1 == elements.length ? 0 : index + 1;
    }

    private int decrement(int index) {
        return index == 0 ? elements.length - 1 : index - 1;
    }

    private void grow() {
        int oldCapacity = elements.length;
        int[] grown = new int[oldCapacity << 1];
        for (int i = 0; i < size; i++) {
            grown[i] = elements[(head + i) % oldCapacity];
        }
        elements = grown;
        head = 0;
        tail = size;
    }
}
