package com.plotsquaredmg;

import com.plotsquaredmg.commands.MakeMegaSubCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlotSquaredMG extends JavaPlugin {
    @Override
    public void onEnable() {
        new MakeMegaSubCommand(this);
    }

    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        final VoidGenerator voidGenerator = new VoidGenerator(getName());
        return (ChunkGenerator) voidGenerator.specify(worldName);
    }
}
