package com.plotsquaredmg.world;

import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.ListTag;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.DataLayer;
import org.apache.commons.lang.Validate;

public class PlotData implements Cloneable {
    private final Long2ObjectMap<Int2IntMap> chunks;

    public PlotData() {
        this.chunks = new Long2ObjectArrayMap<>();
    }

    public PlotData(Long2ObjectMap<Int2IntMap> chunks) {
        this.chunks = chunks;
    }

//    public void merge(PlotData other, Vector2D shift) {
//        for (Map.Entry<Vector2D, PlotDataColumn> entry : other.columns.entrySet()) {
//            final Vector2D newPosition = new Vector2D(entry.getKey().getX() + shift.getX(),
//                    entry.getKey().getZ() + shift.getZ());
//            this.columns.put(newPosition, entry.getValue());
//        }
//        this.entities.getValue().addAll(other.entities.shiftPositions(shift));
//        this.tileEntities.getValue().addAll(other.tileEntities.shiftPositions(shift));
//    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public Long2ObjectMap<Int2IntMap> getChunks() {
        return chunks;
    }

    public ListTag<CompoundTag> toSections(long chunk) {
        final Int2IntMap chunkBlocks = chunks.get(chunk);
        Validate.notNull(chunkBlocks, "toSections must be called for an existing chunk");

        final ListTag<CompoundTag> sectionTags = new ListTag<>("Sections");
        for (int yBase = 0; yBase < (256 / 16); yBase++) {
            boolean allAir = true;
            all:
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        final int block = (y + (yBase << 4) << 8) | (z << 4) | x;
                        final byte blockData = (byte) chunkBlocks.get(block);
                        if (blockData != 0) {
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
                        final int block = (y + (yBase << 4) << 8) | (z << 4) | x;
                        final int blockData = chunkBlocks.get(block);
                        if (blockData == 0) {
                            continue;
                        }

                        blocks[(y << 8) | (z << 4) | x] = (byte) blockData;
                        dataValues.set(x, y, z, (blockData >> 8) & 0x0f);
                        skyLight.set(x, y, z, (blockData >> 12) & 0x0f);
                        blockLight.set(x, y, z, (blockData >> 16) & 0x0f);
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

    //    public PlotData copy() {
//        return new PlotData(isChunk, chunks.clone());
//    }
}
