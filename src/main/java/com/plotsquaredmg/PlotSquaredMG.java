package com.plotsquaredmg;

import com.plotsquaredmg.commands.MakeMegaSubCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlotSquaredMG extends JavaPlugin {
    @Override
    public void onEnable() {
        new MakeMegaSubCommand(this);
    }
}
