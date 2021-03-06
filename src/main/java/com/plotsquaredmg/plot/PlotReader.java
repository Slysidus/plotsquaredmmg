package com.plotsquaredmg.plot;

import com.intellectualcrafters.plot.generator.SquarePlotWorld;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.ListTag;
import com.mojang.nbt.NbtIo;
import com.plotsquaredmg.world.PlotData;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.bukkit.World;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

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
        final Long2ObjectMap<Int2IntMap> chunks = new Long2ObjectArrayMap<>();

        long nanos = System.nanoTime();
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

                // todo : offset
                final long chunk = (((long) chunkX) << 32) | (chunkZ & 0xffffffffL);
                final Int2IntMap chunkData = new Int2IntArrayMap();

                final ListTag<CompoundTag> sectionsTag = (ListTag<CompoundTag>) levelData.getList("Sections");
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int i = 0; i < (256 / 16); i++) {
                            if (i >= sectionsTag.size()) {
                                continue;
                            }

                            final CompoundTag sectionTag = sectionsTag.get(i);
                            final int yBase = sectionTag.getByte("Y");

                            final byte[] blocks = sectionTag.getByteArray("Blocks");
                            final DataLayer dataLayer = new DataLayer(sectionTag.getByteArray("Data"), 4);
                            final DataLayer skyLightLayer = new DataLayer(sectionTag.getByteArray("SkyLight"), 4);
                            final DataLayer blockLightLayer = new DataLayer(sectionTag.getByteArray("BlockLight"), 4);

                            for (int _y = 0; _y < 16; _y++) {
                                final int blockIndex = (_y << 8) | (z << 4) | x;
                                if (blockIndex >= blocks.length) {
                                    continue;
                                }

                                final int y = _y + (yBase << 4);
                                final int block = (y << 8) | (z << 4) | x;
                                final int blockData = (blocks[blockIndex] & 0xff)
                                        | (dataLayer.get(x, _y, z) & 0x0f) << 8
                                        | (blockLightLayer.get(x, _y, z) & 0x0f) << 12
                                        | (skyLightLayer.get(x, _y, z) & 0x0f) << 16;
                                chunkData.put(block, blockData);
                            }
                        }
                    }
                }

                chunks.put(chunk, chunkData);
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
