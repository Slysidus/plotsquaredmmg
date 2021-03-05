package com.plotsquaredmg.commands;

import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.NbtIo;
import com.plotsquaredmg.PlotSquaredMG;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class DumpChunkCommand implements CommandExecutor {
    private final PlotSquaredMG plugin;

    public DumpChunkCommand(PlotSquaredMG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final Player player = (Player) sender;
        if (!player.isOp()) {
            return false;
        }

        final int chunkX = (int) Math.floor(player.getLocation().getBlockX() / 16.),
                chunkZ = (int) Math.floor(player.getLocation().getBlockZ() / 16.);
        final int regionX = (int) Math.floor(chunkX / 32.),
                regionZ = (int) Math.floor(chunkZ / 32.);

        player.getWorld().save();
        final File file = new File(player.getWorld().getWorldFolder(), "region/r." + regionX + "." + regionZ + ".mca");
        try {
            final RegionFile regionFile = new RegionFile(file);
            final DataInputStream regionChunkInputStream = regionFile.getChunkDataInputStreamFromRawChunk(chunkX, chunkZ);
            final CompoundTag chunkData = NbtIo.read(regionChunkInputStream);
            regionChunkInputStream.close();

            final File dumpFile = new File(plugin.getDataFolder(), "chunk-dump-" + chunkX + "-" + chunkZ + ".nbt");
            if (dumpFile.exists()) {
                dumpFile.delete();
            }

            if (!dumpFile.getParentFile().exists()) {
                dumpFile.getParentFile().mkdirs();
            }
            dumpFile.createNewFile();
            try (PrintStream printStream = new PrintStream(dumpFile)) {
                chunkData.print(printStream);
                printStream.flush();
            }
            player.sendMessage(ChatColor.GREEN + "Chunk (x: " + chunkX + ", z:" + chunkZ + ") has been dumped to '" + dumpFile.getCanonicalPath() + "'");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
}
