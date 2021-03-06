package com.plotsquaredmg.plot;

import com.intellectualcrafters.plot.generator.SquarePlotWorld;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.ListTag;
import com.mojang.nbt.NbtIo;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.bukkit.World;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@SuppressWarnings("unchecked")
public class PlotReader {
    private final File worldFolder;

    public PlotReader(World world) {
        this.worldFolder = world.getWorldFolder();
    }

    public PlotData readRegion(Plot plot) throws IOException {
        final Location top = plot.getExtendedTopAbs();
        final Location bottom = plot.getExtendedBottomAbs();

        final SquarePlotWorld plotWorld = (SquarePlotWorld) plot.getArea();
        final int roadExtra = (int) Math.ceil(plotWorld.ROAD_WIDTH / 2.);

        final int topX = top.getX() + roadExtra, topZ = top.getZ() + roadExtra;
        final int bottomX = bottom.getX() - roadExtra, bottomZ = bottom.getZ() - roadExtra;
        return this.readRegion(topX, bottomX, topZ, bottomZ);
    }

    private PlotData readRegion(int topX, int bottomX, int topZ, int bottomZ) throws IOException {
        final Long2ObjectMap<RegionFile> regionsCache = new Long2ObjectArrayMap<>();
        final Long2ObjectMap<Byte2ObjectMap<int[]>> chunks = new Long2ObjectArrayMap<>();

        final int bottomChunkX = (int) Math.floor(bottomX / 16.), topChunkX = (int) Math.floor(topX / 16.);
        final int bottomChunkZ = (int) Math.floor(bottomZ / 16.), topChunkZ = (int) Math.floor(topZ / 16.);
        for (int chunkX = bottomChunkX; chunkX <= topChunkX; chunkX++) {
            for (int chunkZ = bottomChunkZ; chunkZ <= topChunkZ; chunkZ++) {
                final int regionX = (int) Math.floor(chunkX / 32.), regionZ = (int) Math.floor(chunkZ / 32.);
                final long region = (((long) regionX) << 32) | (regionZ & 0xffffffffL);

                RegionFile regionFile = regionsCache.get(region);
                if (regionFile == null) {
                    final File file = new File(worldFolder, "region/r." + regionX + "." + regionZ + ".mca");
                    regionFile = new RegionFile(file);
                    regionsCache.put(region, regionFile);
                }

                final int chunkXRel = Math.floorMod(chunkX, 32), chunkZRel = Math.floorMod(chunkZ, 32);
                if (!regionFile.hasChunk(chunkXRel, chunkZRel)) {
                    continue;
                }
                final DataInputStream regionChunkInputStream = regionFile.getChunkDataInputStream(chunkXRel, chunkZRel);
                final CompoundTag levelData = NbtIo.read(regionChunkInputStream).getCompound("Level");
                regionChunkInputStream.close();

                final ListTag<CompoundTag> sectionsTag = (ListTag<CompoundTag>) levelData.getList("Sections");
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        final int X = (chunkX << 4) + x, Z = (chunkZ << 4) + z;
                        if (!(X >= bottomX && X <= topX && Z >= bottomZ && Z <= topZ)) {
                            continue;
                        }

                        int[] column = new int[256];
                        for (int i = 0; i < (256 / 16); i++) {
                            if (i >= sectionsTag.size()) {
                                continue;
                            }

                            final CompoundTag sectionTag = sectionsTag.get(i);
                            final int yBase = sectionTag.getByte("Y");

                            final byte[] blocks = sectionTag.getByteArray("Blocks");
                            final DataLayer dataLayer = new DataLayer(sectionTag.getByteArray("Data"), 4);
                            final DataLayer blockLightLayer = new DataLayer(sectionTag.getByteArray("BlockLight"), 4);
                            final DataLayer skyLightLayer = new DataLayer(sectionTag.getByteArray("SkyLight"), 4);

                            for (int _y = 0; _y < 16; _y++) {
                                final int blockIndex = (_y << 8) | (z << 4) | x;
                                if (blockIndex >= blocks.length) {
                                    continue;
                                }

                                if (blocks[blockIndex] == 0) {
                                    continue;
                                }

                                final int blockData = (blocks[blockIndex] & 0xff)
                                        | (dataLayer.get(x, _y, z) & 0x0f) << 8
                                        | (blockLightLayer.get(x, _y, z) & 0x0f) << 12
                                        | (skyLightLayer.get(x, _y, z) & 0x0f) << 16;
                                column[_y + (yBase << 4)] = blockData;
                            }
                        }

                        int newSize = 256;
                        for (int i = 255; i >= 0; i--) {
                            if (column[i] == 0) {
                                newSize--;
                            } else {
                                break;
                            }
                        }

                        if (newSize < 256) {
                            column = Arrays.copyOfRange(column, 0, newSize);
                        }

                        final int newX = X - bottomX, newZ = Z - bottomZ;
                        final int newChunkX = (int) Math.floor(newX / 16.), newChunkZ = (int) Math.floor(newZ / 16.);
                        final long newChunk = (((long) newChunkX) << 32) | (newChunkZ & 0xffffffffL);
                        final int relX = Math.floorMod(newX, 16) & 0x0f, relZ = Math.floorMod(newZ, 16) & 0x0f;

                        Byte2ObjectMap<int[]> columnMap = chunks.get(newChunk);
                        if (columnMap == null) {
                            columnMap = new Byte2ObjectArrayMap<>();
                            chunks.put(newChunk, columnMap);
                        }
                        columnMap.put((byte) ((relX << 4) | relZ), column);
                    }
                }
            }
        }

        for (RegionFile region : regionsCache.values()) {
            try {
                region.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new PlotData(chunks);
    }
}
