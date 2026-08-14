package com.xeon.view;

@FunctionalInterface
public interface TilePredicate {
    boolean test(int x, int y, int plane);
}
