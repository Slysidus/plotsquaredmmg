package com.plotsquaredmmg.commands;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.commands.CommandCategory;
import com.intellectualcrafters.plot.commands.RequiredType;
import com.intellectualcrafters.plot.commands.SubCommand;
import com.intellectualcrafters.plot.generator.SquarePlotWorld;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.object.SetupObject;
import com.intellectualcrafters.plot.util.MainUtil;
import com.intellectualcrafters.plot.util.SetupUtils;
import com.plotsquared.general.commands.CommandDeclaration;
import com.plotsquaredmmg.PlotSquaredMMG;
import com.plotsquaredmmg.plot.PlotReader;
import com.plotsquaredmmg.plot.PlotSaver;
import com.plotsquaredmmg.plot.PlotData;
import com.plotsquaredmmg.util.SnailGrid;
import com.plotsquaredmmg.util.Vector2D;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

@CommandDeclaration(command = "mmg", permission = "plots.mmg", category = CommandCategory.ADMINISTRATION, requiredType = RequiredType.PLAYER, description = "Make mega world", usage = "/plot mmg <world>")
public class MakeMegaSubCommand extends SubCommand implements Listener {
    private final PlotSquaredMMG plugin;
    private volatile boolean inProgress;

    public MakeMegaSubCommand(PlotSquaredMMG plugin) {
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
            logger.info("Note: no progress is sent for this step, but as long as no error is thrown, it's doing its job.");
            PlotData plotDataLoad = null;
            try {
                logger.info("Reading data from the plot..");
                plotDataLoad = new PlotReader(originWorld).readRegion(plot);
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

            plotData.newPlot(new Vector2D(0, 0));
            final SnailGrid snailGrid = new SnailGrid(gridIncrement, 0, 0);
            final int generateProgressStep = Math.max(1, plotsToGenerate / 5);
            for (int generated = 1; generated <= plotsToGenerate; generated++) {
                plotData.newPlot(snailGrid.next());
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
                        logger.info("Saving MCAs files..");
                        new PlotSaver(logger, regionFolder).save(plotData);
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
                        setupObject.setupGenerator = plugin.getName();
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
                        final String time = String.format("%s hours %s minutes and %s seconds", durationSeconds / 3600, durationSeconds / 60, durationSeconds % 60);
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
}
