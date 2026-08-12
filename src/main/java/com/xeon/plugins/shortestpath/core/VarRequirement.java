package com.xeon.plugins.shortestpath.core;

import java.util.Map;
import java.util.Objects;

public final class VarRequirement {
    public enum VarType {
        VARBIT,
        VARPLAYER
    }

    enum CheckType {
        BIT_SET("&"),
        COOLDOWN_MINUTES("@"),
        EQUAL("="),
        GREATER(">"),
        SMALLER("<");

        private final String code;

        CheckType(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    private final VarType type;
    private final int id;
    private final int value;
    private final CheckType checkType;

    VarRequirement(VarType type, int id, int value, CheckType checkType) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = id;
        this.value = value;
        this.checkType = Objects.requireNonNull(checkType, "checkType");
    }

    public VarType type() {
        return type;
    }

    public int id() {
        return id;
    }

    public boolean check(Map<Integer, Integer> values) {
        Integer currentValue = values == null ? null : values.get(id);
        return currentValue != null && checkValue(currentValue);
    }

    public boolean checkValue(int currentValue) {
        return switch (checkType) {
            case EQUAL -> currentValue == value;
            case GREATER -> currentValue > value;
            case SMALLER -> currentValue < value;
            case BIT_SET -> (currentValue & value) > 0;
            case COOLDOWN_MINUTES -> ((System.currentTimeMillis() / 60_000L) - currentValue) > value;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VarRequirement that)) {
            return false;
        }
        return id == that.id && value == that.value && type == that.type && checkType == that.checkType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id, value, checkType);
    }

    @Override
    public String toString() {
        return type + "[" + id + checkType.code() + value + "]";
    }
}
