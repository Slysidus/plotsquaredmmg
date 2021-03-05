package com.plotsquaredmg.world;

import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.DoubleTag;
import com.mojang.nbt.ListTag;
import com.plotsquaredmg.util.Vector2D;
import net.minecraft.world.level.chunk.DataLayer;

import java.util.*;

public class PlotData implements Cloneable {
    private final Map<Vector2D, PlotDataColumn> columns;
    private final PlotEntities entities;
    private final PlotEntities tileEntities;

    public PlotData() {
        this.columns = new HashMap<>();
        this.entities = new PlotEntities(PlotEntitiesType.ENTITIES);
        this.tileEntities = new PlotEntities(PlotEntitiesType.TILE_ENTITIES);
    }

    private PlotData(Map<Vector2D, PlotDataColumn> columns, PlotEntities entities, PlotEntities tileEntities) {
        this.columns = columns;
        this.entities = entities;
        this.tileEntities = tileEntities;
    }

    public void setBlockNBTData(int x, int y, int z, BlockNBTData blockData) {
        final Vector2D columnPosition = new Vector2D(x, z);
        if (!columns.containsKey(columnPosition)) {
            this.columns.put(columnPosition, new PlotDataColumn());
        }
        final PlotDataColumn column = columns.get(columnPosition);
        column.blocksData[y & 0xff] = blockData;
    }

    public BlockNBTData getBlockNBTData(int x, int y, int z) {
        final PlotDataColumn column = columns.get(new Vector2D(x, z));
        return column != null ? column.blocksData[y & 0xff] : null;
    }

    public Map<Vector2D, PlotData> splitIntoChunks() {
        final Map<Vector2D, PlotData> chunks = new HashMap<>();
        for (Vector2D columnPosition : columns.keySet()) {
            final int chunkX = (int) Math.floor(columnPosition.getX() / 16.),
                    chunkZ = (int) Math.floor(columnPosition.getZ() / 16.);
            final Vector2D chunkVector = new Vector2D(chunkX, chunkZ);
            if (!chunks.containsKey(chunkVector)) {
                chunks.put(chunkVector, new PlotData());
            }

            final PlotDataColumn column = columns.get(columnPosition);
            final Vector2D relColumnPosition = new Vector2D(
                    Math.floorMod(columnPosition.getX(), 16), Math.floorMod(columnPosition.getZ(), 16));

            final PlotData plotData = chunks.get(chunkVector);
            plotData.columns.put(relColumnPosition, column);
        }

        for (Map.Entry<Vector2D, PlotData> entry : chunks.entrySet()) {
            final Vector2D chunkPosition = entry.getKey();
            final PlotData plotData = entry.getValue();
            plotData.entities.value.addAll(this.entities.getEntitiesInChunk(chunkPosition.getX(), chunkPosition.getZ()));
            plotData.tileEntities.value.addAll(this.tileEntities.getEntitiesInChunk(chunkPosition.getX(), chunkPosition.getZ()));
        }
        return chunks;
    }

    public ListTag<CompoundTag> toSections() {
        final ListTag<CompoundTag> sectionTags = new ListTag<>("Sections");
        for (int yBase = 0; yBase < (256 / 16); yBase++) {
            boolean allAir = true;
            all:
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        final BlockNBTData blockData = getBlockNBTData(x, y + (yBase << 4), z);
                        if (blockData != null && blockData.getBlock() != 0) {
                            allAir = false;
                            break all;
                        }
                    }
                }
            }
            if (allAir) {
                continue;
            }

            final CompoundTag sectionTag = new CompoundTag();
            final byte[] blocks = new byte[16 * 16 * 16];
            final DataLayer dataValues = new DataLayer(blocks.length, 4);
            final DataLayer skyLight = new DataLayer(blocks.length, 4);
            final DataLayer blockLight = new DataLayer(blocks.length, 4);

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        final BlockNBTData blockData = getBlockNBTData(x, y + (yBase << 4), z);
                        if (blockData == null) {
                            continue;
                        }

                        blocks[(y << 8) | (z << 4) | x] = blockData.getBlock();
                        dataValues.set(x, y, z, blockData.getData());
                        skyLight.set(x, y, z, blockData.getSkyLight());
                        blockLight.set(x, y, z, blockData.getBlockLight());
                    }
                }
            }

            sectionTag.putByte("Y", (byte) (yBase % 0xff));
            sectionTag.putByteArray("Blocks", blocks);
            sectionTag.putByteArray("Data", dataValues.data);
            sectionTag.putByteArray("SkyLight", skyLight.data);
            sectionTag.putByteArray("BlockLight", blockLight.data);
            sectionTags.add(sectionTag);
        }
        return sectionTags;
    }

    public void merge(PlotData other, Vector2D shift) {
        for (Map.Entry<Vector2D, PlotDataColumn> entry : other.columns.entrySet()) {
            final Vector2D newPosition = new Vector2D(entry.getKey().getX() + shift.getX(),
                    entry.getKey().getZ() + shift.getZ());
            this.columns.put(newPosition, entry.getValue());
        }
        this.entities.getValue().addAll(other.entities.shiftPositions(shift));
        this.tileEntities.getValue().addAll(other.tileEntities.shiftPositions(shift));
    }

    public PlotData copy() {
        return new PlotData(
                new HashMap<>(columns), entities.copy(), tileEntities.copy()
        );
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    public PlotEntities getEntities() {
        return entities;
    }

    public PlotEntities getTileEntities() {
        return tileEntities;
    }

    public static class PlotDataColumn {
        private final BlockNBTData[] blocksData;

        public PlotDataColumn() {
            this.blocksData = new BlockNBTData[256];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PlotDataColumn column = (PlotDataColumn) o;
            return Arrays.equals(blocksData, column.blocksData);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(blocksData);
        }
    }

    @SuppressWarnings("unchecked")
    public static class PlotEntities {
        private final PlotEntitiesType type;
        private final List<CompoundTag> value;

        public PlotEntities(PlotEntitiesType type) {
            this.type = type;
            this.value = new ArrayList<>();
        }

        public PlotEntities(PlotEntitiesType type, List<CompoundTag> value) {
            this.type = type;
            this.value = value;
        }

        public List<CompoundTag> getEntitiesInChunk(int chunkX, int chunkZ) {
            final List<CompoundTag> entitiesInChunk = new ArrayList<>();
            for (CompoundTag entity : value) {
                final int entityChunkX, entityChunkZ;
                if (type == PlotEntitiesType.ENTITIES) {
                    final ListTag<DoubleTag> positions = (ListTag<DoubleTag>) entity.getList("Pos");
                    entityChunkX = (int) Math.floor(positions.get(0).data / 16.);
                    entityChunkZ = (int) Math.floor(positions.get(2).data / 16.);
                } else if (type == PlotEntitiesType.TILE_ENTITIES) {
                    entityChunkX = (int) Math.floor(entity.getInt("x") / 16.);
                    entityChunkZ = (int) Math.floor(entity.getInt("z") / 16.);
                } else {
                    throw new IllegalStateException("unreachable code");
                }

                if (entityChunkX == chunkX && entityChunkZ == chunkZ) {
                    entitiesInChunk.add(entity);
                }
            }
            return entitiesInChunk;
        }

        public List<CompoundTag> shiftPositions(Vector2D shift) {
            final List<CompoundTag> shiftedEntities = new ArrayList<>();
            for (CompoundTag entityOriginal : value) {
                final CompoundTag entity = (CompoundTag) entityOriginal.copy();
                if (type == PlotEntitiesType.ENTITIES) {
                    final ListTag<DoubleTag> positions = (ListTag<DoubleTag>) entity.getList("Pos");
                    positions.get(0).data += shift.getX();
                    positions.get(2).data += shift.getZ();
                } else if (type == PlotEntitiesType.TILE_ENTITIES) {
                    entity.putInt("x", entity.getInt("x") + shift.getX());
                    entity.putInt("z", entity.getInt("z") + shift.getX());
                }
                shiftedEntities.add(entity);
            }
            return shiftedEntities;
        }

        public List<CompoundTag> getValue() {
            return value;
        }

        public PlotEntities copy() {
            return new PlotEntities(type, new ArrayList<>(value));
        }
    }

    public enum PlotEntitiesType {
        ENTITIES,
        TILE_ENTITIES,
        ;
    }
}
