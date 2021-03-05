package com.plotsquaredmg.util;

import java.util.Objects;

public final class Vector2D implements Comparable<Vector2D> {
    private final int x;
    private final int z;

    public Vector2D(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vector2D vector2D = (Vector2D) o;
        return x == vector2D.x && z == vector2D.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return "Vector2D{" +
                "x=" + x +
                ", z=" + z +
                "}";
    }

    @Override
    public int compareTo(Vector2D other) {
        final int p1 = (int) Math.sqrt(x*x + z*z);
        final int p2 = (int) Math.sqrt(other.x*other.x + other.z*other.z);
        return Integer.compare(p1, p2);
    }
}
