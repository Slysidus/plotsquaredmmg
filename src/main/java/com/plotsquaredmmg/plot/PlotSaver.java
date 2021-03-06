package com.plotsquaredmmg.plot;

import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.ListTag;
import com.mojang.nbt.NbtIo;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.storage.RegionFile;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class PlotSaver {
    private final Logger logger;
    private final File regionFolder;

    public PlotSaver(Logger logger, File regionFolder) {
        this.logger = logger;
        this.regionFolder = regionFolder;
    }

    public void save(PlotData plotData) throws IOException {
        final ListTag<CompoundTag> defaultEntities = new ListTag<>();
        final Long2ObjectMap<RegionFile> regionsCache = new Long2ObjectArrayMap<>();

        int chunkIndex = 0;
        final int totalChunks = plotData.getChunks().size();
        final int progressStep = Math.max(1, totalChunks / 50);
        for (long chunk : plotData) {
            final int chunkX = (int) (chunk >> 32), chunkZ = (int) chunk;

            final int regionX = (int) Math.floor(chunkX / 32.), regionZ = (int) Math.floor(chunkZ / 32.);
            final long region = (((long) regionX) << 32) | (regionZ & 0xffffffffL);

            RegionFile regionFile = regionsCache.get(region);
            if (regionFile == null) {
                final File file = new File(regionFolder, "r." + regionX + "." + regionZ + ".mca");
                regionFile = new RegionFile(file);
                regionsCache.put(region, regionFile);
            }

            final CompoundTag rootTag = new CompoundTag();
            final CompoundTag levelData = new CompoundTag();
            rootTag.put("Level", levelData);

            levelData.putByte("V", (byte) 1);
            levelData.putInt("xPos", chunkX);
            levelData.putInt("zPos", chunkZ);
            levelData.putLong("LastUpdated", 0);
            levelData.putLong("InhabitedTime", 0);
            levelData.putByte("TerrainPopulated", (byte) 1);

            levelData.putByte("LightCalculated", (byte) 1);
            final int[] heightMap = new int[256];
            levelData.putIntArray("HeightMap", heightMap);
            levelData.put("Sections", plotData.toSections(chunk));
            levelData.put("Entities", defaultEntities);
            levelData.put("TileEntities", defaultEntities);

            final DataOutputStream chunkOutputStream = regionFile.getChunkDataOutputStreamFromRawChunk(chunkX, chunkZ);
            NbtIo.write(rootTag, chunkOutputStream);
            chunkOutputStream.close();

            if (++chunkIndex % progressStep == 0 || chunkIndex == totalChunks) {
                logger.info(String.format("Saved chunk %s/%s [update every %s chunks]", chunkIndex, totalChunks, progressStep));
            }

            if (chunkIndex % 8192 == 0) {
                logger.info("(MEMORY SAVER) Clearing regions cache..");
                for (RegionFile r : regionsCache.values()) {
                    try {
                        r.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                regionsCache.clear();
            }
        }

        for (RegionFile region : regionsCache.values()) {
            try {
                region.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
