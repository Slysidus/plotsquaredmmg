package com.plotsquaredmg.world;

import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Objects;

public class Chunk {
    private final Map<Vector, Material> data;
    private final int chunkX, chunkY;

    public Chunk(Map<Vector, Material> data, int chunkX, int chunkY) {
        this.data = data;
        this.chunkX = chunkX;
        this.chunkY = chunkY;
    }

    public Map<Vector, Material> getData() {
        return data;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkY() {
        return chunkY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chunk chunk = (Chunk) o;
        return chunkX == chunk.chunkX && chunkY == chunk.chunkY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkX, chunkY);
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "chunkX=" + chunkX +
                ", chunkY=" + chunkY +
                '}';
    }
}
