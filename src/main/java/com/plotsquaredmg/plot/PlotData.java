package com.plotsquaredmg.plot;

import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.ListTag;
import com.plotsquaredmg.util.Vector2D;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.world.level.chunk.DataLayer;
import org.apache.commons.lang.Validate;

import java.util.Map;

public class PlotData implements LongIterable {
    // 64{32:32} -> (8{4:4} -> 32{4:4:4:8}[])
    // chunk X:Z -> (block rel x:z -> skylight:light:data:block)
    private final Long2ObjectMap<Byte2ObjectMap<int[]>> chunks;
    private final Long2ObjectMap<Byte2ObjectMap<int[]>> references;

    public PlotData(Long2ObjectMap<Byte2ObjectMap<int[]>> chunks) {
        this.chunks = chunks;
        this.references = new Long2ObjectArrayMap<>();
    }

    public void newPlot(Vector2D shift) {
        final int shiftX = shift.getX();
        final int shiftZ = shift.getZ();

        for (Map.Entry<Long, Byte2ObjectMap<int[]>> chunkEntry : chunks.long2ObjectEntrySet()) {
            final long chunk = chunkEntry.getKey();
            final int chunkX = (int) (chunk >> 32), chunkZ = (int) chunk;

            for (Byte2ObjectMap.Entry<int[]> entry : chunkEntry.getValue().byte2ObjectEntrySet()) {
                final byte columnIndex = entry.getByteKey();
                final int x = ((chunkX << 4) + ((columnIndex >> 4) & 0x0f)) + shiftX;
                final int z = ((chunkZ << 4) + (columnIndex & 0x0f)) + shiftZ;

                final int newChunkX = (int) Math.floor(x / 16.), newChunkZ = (int) Math.floor(z / 16.);
                final long newChunk = (((long) newChunkX) << 32) | (newChunkZ & 0xffffffffL);
                final int relX = Math.floorMod(x, 16) & 0x0f, relZ = Math.floorMod(z, 16) & 0x0f;

                Byte2ObjectMap<int[]> refColumn = references.get(newChunk);
                if (refColumn == null) {
                    refColumn = new Byte2ObjectArrayMap<>();
                    references.put(newChunk, refColumn);
                }
                refColumn.put((byte) ((relX << 4) | relZ), entry.getValue());
            }
        }
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public LongSet getChunks() {
        final LongSet chunks = new LongArraySet(this.chunks.keySet());
        chunks.addAll(this.references.keySet());
        return chunks;
    }

    @Override
    public LongIterator iterator() {
        return getChunks().longIterator();
    }

    public ListTag<CompoundTag> toSections(long chunk) {
        final Byte2ObjectMap<int[]> chunkColumns = references.get(chunk);
        Validate.notNull(chunkColumns, "toSections must be called for an existing chunk");

        final ListTag<CompoundTag> sectionTags = new ListTag<>("Sections");
        for (int yBase = 0; yBase < (256 / 16); yBase++) {
            boolean allAir = true;
            all:
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        final byte columnIndex = (byte) ((x << 4) | z);
                        final int[] column = chunkColumns.get(columnIndex);
                        if (column != null && column.length > (y + (yBase << 4)) && ((byte) column[y + (yBase << 4)]) != 0) {
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
                for (int z = 0; z < 16; z++) {
                    final byte columnIndex = (byte) ((x << 4) | z);
                    final int[] column = chunkColumns.get(columnIndex);
                    if (column == null) {
                        continue;
                    }

                    for (int y = 0; y < 16; y++) {
                        if (column.length > (y + (yBase << 4))) {
                            final int blockData = column[y + (yBase << 4)];
                            if (((byte) blockData) == 0) {
                                continue;
                            }

                            blocks[(y << 8) | (z << 4) | x] = (byte) blockData;
                            dataValues.set(x, y, z, (blockData >> 8) & 0x0f);
                            blockLight.set(x, y, z, (blockData >> 12) & 0x0f);
                            skyLight.set(x, y, z, (blockData >> 16) & 0x0f);
                        }
                    }
                }
            }

            sectionTag.putByte("Y", (byte) yBase);
            sectionTag.putByteArray("Blocks", blocks);
            sectionTag.putByteArray("Data", dataValues.data);
            sectionTag.putByteArray("SkyLight", skyLight.data);
            sectionTag.putByteArray("BlockLight", blockLight.data);
            sectionTags.add(sectionTag);
        }
        return sectionTags;
    }
}
