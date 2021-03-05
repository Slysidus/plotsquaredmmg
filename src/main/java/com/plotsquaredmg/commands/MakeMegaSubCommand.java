package com.plotsquaredmg.commands;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.commands.CommandCategory;
import com.intellectualcrafters.plot.commands.RequiredType;
import com.intellectualcrafters.plot.commands.SubCommand;
import com.intellectualcrafters.plot.generator.SquarePlotWorld;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.object.SetupObject;
import com.intellectualcrafters.plot.util.MainUtil;
import com.intellectualcrafters.plot.util.SetupUtils;
import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.DoubleTag;
import com.mojang.nbt.ListTag;
import com.mojang.nbt.NbtIo;
import com.plotsquared.bukkit.generator.BukkitPlotGenerator;
import com.plotsquared.general.commands.CommandDeclaration;
import com.plotsquaredmg.PlotSquaredMG;
import com.plotsquaredmg.util.SnailGrid;
import com.plotsquaredmg.util.Vector2D;
import com.plotsquaredmg.world.BlockNBTData;
import com.plotsquaredmg.world.PlotData;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@SuppressWarnings("unchecked")
@CommandDeclaration(command = "mmg", permission = "plots.mmg", category = CommandCategory.ADMINISTRATION, requiredType = RequiredType.PLAYER, description = "Make mega world", usage = "/plot mmg <world>")
public class MakeMegaSubCommand extends SubCommand implements Listener {
    private final PlotSquaredMG plugin;
    private volatile boolean inProgress;

    public MakeMegaSubCommand(PlotSquaredMG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final PlotPlayer plotPlayer, final String[] args) {
        final Logger logger = plugin.getLogger();
        final long startedAt = System.currentTimeMillis();

        if (args.length != 2) {
            MainUtil.sendMessage(plotPlayer, "&cUsage: /plot mmg <target_world_name> <plot_count>. Note: plot borders and roads are copied too.");
            return false;
        }
        final String targetWorldName = args[0];
        final int plotsToGenerate;
        try {
            plotsToGenerate = Integer.parseInt(args[1]);
        } catch (NumberFormatException ignored) {
            MainUtil.sendMessage(plotPlayer, "&cThe second argument must be a number!");
            return false;
        }

        final Plot plot = plotPlayer.getLocation().getPlot();
        if (plot == null || !(plot.getArea() instanceof SquarePlotWorld)) {
            MainUtil.sendMessage(plotPlayer, "&cYou need to be in a plot to make a mega world out of it.");
            return false;
        }
        if (plot.isMerged()) {
            MainUtil.sendMessage(plotPlayer, "&cMerged plots cannot be used as a mega world base.");
        }

        if (inProgress) {
            MainUtil.sendMessage(plotPlayer, "&cA generation operation is already is progress, please wait for it to finish.");
            return false;
        }
        synchronized (this) {
            this.inProgress = true;
        }

        MainUtil.sendMessage(plotPlayer, String.format(
                "&aProcess started (world: '%s', plots: %s). Please check the console for progress.", args[0], plotsToGenerate));

        plugin.getLogger().info("Preparing the world..");
        final World originWorld = plugin.getServer().getWorld(plot.getArea().worldname);
        final boolean origAutoSave = originWorld.isAutoSave();
        if (originWorld.isAutoSave()) {
            originWorld.setAutoSave(false);
        }
        originWorld.save();

        logger.info("Operation moving to async thread.");
        final SquarePlotWorld plotWorld = (SquarePlotWorld) plot.getArea();

        logger.info("Data from the plot has been read successfully!");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            logger.info("[STEP 1] Reading data from the plot..");
            PlotData plotDataLoad = null;
            try {
                plotDataLoad = readPlotData(plot, originWorld);
                if (plotDataLoad == null || plotDataLoad.isEmpty()) {
                    plotDataLoad = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (origAutoSave) {
                        originWorld.setAutoSave(true);
                    }
                });

                if (plotDataLoad == null) {
                    if (plotPlayer.isOnline()) {
                        MainUtil.sendMessage(plotPlayer, "Unable to read data from the plot! Check console for details.");
                    }

                    synchronized (this) {
                        this.inProgress = false;
                    }
                    return;
                }
            }

            final PlotData plotData = plotDataLoad;
            logger.info("[STEP 2] Generating repeated plots..");

            final int roadExtraFloor = (int) Math.floor(plotWorld.ROAD_WIDTH / 2.);
            final int gridIncrement = roadExtraFloor * 2 + (plotWorld.ROAD_WIDTH % 2 == 1 ? 1 : 0) + plotWorld.PLOT_WIDTH;

            final PlotData modelData = plotData.copy();
            final SnailGrid snailGrid = new SnailGrid(gridIncrement, 0, 0);
            final int generateProgressStep = plotsToGenerate / 5;
            for (int generated = 1; generated <= plotsToGenerate; generated++) {
                plotData.merge(modelData, snailGrid.next());
                if (generated % generateProgressStep == 0 || generated == plotsToGenerate) {
                    logger.info(String.format("Generated plot %s/%s [update every %s plots]", generated, plotsToGenerate, generateProgressStep));
                }
            }

            logger.info("[STEP 3] Saving generated world..");
            logger.info("Preparing bukkit world.. (moving sync)");
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                final File worldFolder = new File(plugin.getServer().getWorldContainer(), targetWorldName);
                World world = plugin.getServer().getWorld(targetWorldName);
                if (world != null) {
                    for (Player player : world.getPlayers()) {
                        player.kickPlayer(ChatColor.RED + "The world you were in has been deleted.");
                    }
                    plugin.getServer().unloadWorld(world, true);
                }
                worldFolder.renameTo(new File(plugin.getServer().getWorldContainer(), targetWorldName + "-" + System.currentTimeMillis()));

                world = new WorldCreator(targetWorldName)
                        .environment(World.Environment.NORMAL)
                        .type(WorldType.FLAT)
                        .createWorld();
                plugin.getServer().unloadWorld(world, true);

                logger.info("Saving the world.. (moving async)");
                final File regionFolder = new File(world.getWorldFolder(), "region");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (regionFolder.exists() && !regionFolder.isDirectory()) {
                        regionFolder.delete();
                    }
                    regionFolder.mkdirs();
                    final File[] mcaFiles = regionFolder.listFiles(File::isFile);
                    if (mcaFiles != null) {
                        Arrays.stream(mcaFiles).forEach(File::delete);
                    }

                    try {
                        this.saveWorld(plotData, regionFolder);
                        logger.info("World MCAs have been saved!");
                    } catch (IOException e) {
                        e.printStackTrace();
                        if (plotPlayer.isOnline()) {
                            MainUtil.sendMessage(plotPlayer, "Unable to write some of the data! Created world is most likely corrupted! Check console for details.");
                        }

                        synchronized (this) {
                            this.inProgress = false;
                        }
                        return;
                    }

                    logger.info("[STEP 4] Registering world to PlotSquared.. (moving sync)");

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        SetupObject setupObject = new SetupObject();
                        setupObject.world = targetWorldName;
                        setupObject.setupGenerator = PS.imp().getPluginName();
                        setupObject.plotManager = PS.imp().getPluginName();
                        SetupUtils.manager.setupWorld(setupObject);

                        final File plotSquaredFolder = plugin.getServer().getPluginManager()
                                .getPlugin("PlotSquared")
                                .getDataFolder();
                        final File worldsFile = new File(plotSquaredFolder, "config/worlds.yml");
                        final YamlConfiguration worldConfiguration = YamlConfiguration.loadConfiguration(worldsFile);

                        final ConfigurationSection worldSection = worldConfiguration.getConfigurationSection("worlds." + targetWorldName);
                        final ConfigurationSection worldModel = worldConfiguration.getConfigurationSection("worlds." + originWorld.getName());
                        for (String key : worldModel.getKeys(false)) {
                            worldSection.set(key, worldModel.get(key));
                        }
                        try {
                            worldConfiguration.save(worldsFile);

                            final YamlConfiguration removeRefs = YamlConfiguration.loadConfiguration(worldsFile);
                            removeRefs.save(worldsFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                            if (plotPlayer.isOnline()) {
                                MainUtil.sendMessage(plotPlayer, "&cUnable to add world to PlotSquared!");
                            }

                            synchronized (this) {
                                this.inProgress = false;
                            }
                            return;
                        }
                        PS.get().setupConfigs();
                        plugin.getServer().dispatchCommand(Bukkit.getConsoleSender(), "plot reload");

                        logger.info("World has been saved to PlotSquared, use '/plot area tp " + targetWorldName + "'");
                        final long durationSeconds = (System.currentTimeMillis() - startedAt) / 1000;
                        final String time = String.format("%s minutes and %s seconds", durationSeconds / 60, durationSeconds % 60);
                        logger.info("FINISHED - Mega world generated in " + time + ".");
                        if (plotPlayer.isOnline()) {
                            MainUtil.sendMessage(plotPlayer, "&aMega world generation finished in " + time + "!");
                            MainUtil.sendMessage(plotPlayer, "&7Tip: Use '/plot area tp " + targetWorldName + "'.");
                        }

                        synchronized (this) {
                            this.inProgress = false;
                        }
                    });
                });
            });
        });
        return true;
    }

    private void saveWorld(PlotData plotData, File regionFolder) throws IOException {
        final Logger logger = plugin.getLogger();

        logger.info("Splitting world into chunks..");
        final Map<Vector2D, PlotData> chunks = plotData.splitIntoChunks();
        final Map<Vector2D, RegionFile> regions = new HashMap<>();

//        logger.info("Generating void around zone..");
//        final int voidShift = 8;
//
//        Vector2D top = Collections.max(chunks.keySet());
//        Vector2D bottom = Collections.min(chunks.keySet());
//        top = new Vector2D(top.getX() + voidShift, top.getZ() + voidShift);
//        bottom = new Vector2D(bottom.getX() - voidShift, bottom.getZ() - voidShift);

//        for (int chunkX = bottom.getX(); chunkX <= top.getX(); chunkX++) {
//            for (int chunkZ = bottom.getZ(); chunkZ <= top.getZ(); chunkZ++) {
//                final Vector2D chunkPosition = new Vector2D(chunkX, chunkZ);
//                if (!chunks.containsKey(chunkPosition)) {
//                    chunks.put(chunkPosition, new PlotData());
//                }
//            }
//        }

        logger.info("Saving MCA files..");
        int chunkIndex = 0;
        final int totalChunks = chunks.size();
        final int progressStep = totalChunks / 20;
        for (Map.Entry<Vector2D, PlotData> entry : chunks.entrySet()) {
            final Vector2D chunkPosition = entry.getKey();
            final PlotData chunkData = entry.getValue();

            final int regionX = (int) Math.floor(chunkPosition.getX() / 32.), regionZ = (int) Math.floor(chunkPosition.getZ() / 32.);
            final Vector2D regionVector = new Vector2D(regionX, regionZ);
            if (!regions.containsKey(regionVector)) {
                final File file = new File(regionFolder, "r." + regionX + "." + regionZ + ".mca");
                regions.put(regionVector, new RegionFile(file));
            }
            final RegionFile regionFile = regions.get(regionVector);

            final CompoundTag rootTag = new CompoundTag();
            final CompoundTag levelData = new CompoundTag();
            rootTag.put("Level", levelData);

            levelData.putByte("V", (byte) 1);
            levelData.putInt("xPos", chunkPosition.getX());
            levelData.putInt("zPos", chunkPosition.getZ());
            levelData.putLong("LastUpdated", 0);
            levelData.putLong("InhabitedTime", 0);
            levelData.putByte("TerrainPopulated", (byte) 1);

            levelData.putByte("LightCalculated", (byte) 1);
            final int[] heightMap = new int[256];
            levelData.putIntArray("HeightMap", heightMap);
            levelData.put("Sections", chunkData.toSections());

            final ListTag<CompoundTag> entities = new ListTag<>();
            for (CompoundTag entity : chunkData.getEntities().getValue()) {
                entities.add(entity);
            }
            levelData.put("Entities", entities);

            final ListTag<CompoundTag> tileEntities = new ListTag<>();
            for (CompoundTag tileEntity : chunkData.getTileEntities().getValue()) {
                tileEntities.add(tileEntity);
            }
            levelData.put("TileEntities", tileEntities);

            final DataOutputStream chunkOutputStream = regionFile.getChunkDataOutputStreamFromRawChunk(
                    chunkPosition.getX(), chunkPosition.getZ());
            NbtIo.write(rootTag, chunkOutputStream);
            chunkOutputStream.close();

            if (++chunkIndex % progressStep == 0 || chunkIndex == totalChunks) {
                logger.info(String.format("Saved chunk %s/%s [update every %s chunks]", chunkIndex, totalChunks, progressStep));
            }
        }
        regions.values().forEach(RegionFile::closeAndHandle);

        synchronized (this) {
            this.inProgress = false;
        }
        logger.info("Finished!");
    }

    private PlotData readPlotData(Plot plot, World world) throws IOException {
        final Location top = plot.getExtendedTopAbs();
        final Location bottom = plot.getExtendedBottomAbs();

        final SquarePlotWorld plotWorld = (SquarePlotWorld) plot.getArea();
        final int roadExtra = (int) Math.ceil(plotWorld.ROAD_WIDTH / 2.);

        final int topX = top.getX() + roadExtra, topZ = top.getZ() + roadExtra;
        final int bottomX = bottom.getX() - roadExtra, bottomZ = bottom.getZ() - roadExtra;

        final Map<Vector2D, RegionFile> regions = new HashMap<>();
        final Map<Vector2D, CompoundTag> chunks = new HashMap<>();

        plugin.getLogger().info("Reading data from the plot..");
        final Map<Vector, BlockNBTData> blocksData = new HashMap<>();

        final Vector offset = new Vector(-bottomX, 0, -bottomZ);
        for (int X = bottomX; X <= topX; X++) {
            for (int Z = bottomZ; Z <= topZ; Z++) {
                final int chunkX = (int) Math.floor(X / 16.), chunkZ = (int) Math.floor(Z / 16.);
                final Vector2D chunkVector = new Vector2D(chunkX, chunkZ);
                if (!chunks.containsKey(chunkVector)) {
                    final int regionX = (int) Math.floor(chunkX / 32.), regionZ = (int) Math.floor(chunkZ / 32.);
                    final Vector2D regionVector = new Vector2D(regionX, regionZ);
                    if (!regions.containsKey(regionVector)) {
                        final File file = new File(world.getWorldFolder(), "region/r." + regionX + "." + regionZ + ".mca");
                        regions.put(regionVector, new RegionFile(file));
                    }
                    final RegionFile regionFile = regions.get(regionVector);

                    final int chunkXRel = Math.floorMod(chunkX, 32), chunkZRel = Math.floorMod(chunkZ, 32);
                    if (!regionFile.hasChunk(chunkXRel, chunkZRel)) {
                        continue;
                    }
                    final DataInputStream regionChunkInputStream = regionFile.getChunkDataInputStream(chunkXRel, chunkZRel);
                    final CompoundTag chunkData = NbtIo.read(regionChunkInputStream);
                    regionChunkInputStream.close();
                    chunks.put(chunkVector, chunkData);
                }

                final CompoundTag levelData = chunks.get(chunkVector).getCompound("Level");
                final ListTag<CompoundTag> sectionsTag = (ListTag<CompoundTag>) levelData.getList("Sections");

                for (int i = 0; i < (256 / 16); i++) {
                    if (i >= sectionsTag.size()) {
                        continue;
                    }

                    final CompoundTag sectionTag = sectionsTag.get(i);
                    final int yBase = sectionTag.getByte("Y");

                    final int x = Math.floorMod(X, 16), z = Math.floorMod(Z, 16);
                    final byte[] blocks = sectionTag.getByteArray("Blocks");
                    final DataLayer dataLayer = new DataLayer(sectionTag.getByteArray("Data"), 4);
                    final DataLayer skyLightLayer = new DataLayer(sectionTag.getByteArray("SkyLight"), 4);
                    final DataLayer blockLightLayer = new DataLayer(sectionTag.getByteArray("BlockLight"), 4);

                    for (int y = 0; y < 16; y++) {
                        final int blockIndex = (y << 8) | (z << 4) | x;
                        if (blockIndex >= blocks.length) {
                            continue;
                        }

                        final int Y = y + (yBase << 4);
                        final Vector blockPosition = new Vector(X, Y, Z).add(offset);
                        final BlockNBTData blockData = new BlockNBTData(blocks[(y << 8) | (z << 4) | x],
                                dataLayer.get(x, y, z), skyLightLayer.get(x, y, z), blockLightLayer.get(x, y, z));
                        blocksData.put(blockPosition, blockData);
                    }
                }
            }
        }
        regions.values().forEach(RegionFile::closeAndHandle);

        final PlotData plotData = new PlotData();
        blocksData.forEach((position, data) -> plotData.setBlockNBTData(position.getBlockX(), position.getBlockY(), position.getBlockZ(), data));
        chunks.forEach((chunkPosition, rootTag) -> {
            final CompoundTag levelData = rootTag.getCompound("Level");

            final ListTag<CompoundTag> entitiesTag = (ListTag<CompoundTag>) levelData.getList("Entities");
            for (CompoundTag entityTag : entitiesTag.getValue()) {
                final ListTag<DoubleTag> positions = (ListTag<DoubleTag>) entityTag.getList("Pos");
                positions.get(0).data += offset.getBlockX();
                positions.get(2).data += offset.getBlockZ();
                plotData.getEntities().getValue().add(entityTag);
            }

            final ListTag<CompoundTag> tileEntitiesTag = (ListTag<CompoundTag>) levelData.getList("TileEntities");
            for (CompoundTag tileEntityTag : tileEntitiesTag.getValue()) {
                tileEntityTag.putInt("x", tileEntityTag.getInt("x") + offset.getBlockX());
                tileEntityTag.putInt("z", tileEntityTag.getInt("z") + offset.getBlockZ());
                plotData.getTileEntities().getValue().add(tileEntityTag);
            }
        });
        return plotData;
    }
}
