package com.plotsquaredmmg;

import com.intellectualcrafters.plot.generator.HybridPlotManager;
import com.intellectualcrafters.plot.generator.HybridPlotWorld;
import com.intellectualcrafters.plot.generator.IndependentPlotGenerator;
import com.intellectualcrafters.plot.object.PlotArea;
import com.intellectualcrafters.plot.object.PlotId;
import com.intellectualcrafters.plot.object.PlotManager;
import com.intellectualcrafters.plot.object.PseudoRandom;
import com.intellectualcrafters.plot.util.block.ScopedLocalBlockQueue;

public class VoidGenerator extends IndependentPlotGenerator {
    private final String name;

    public VoidGenerator(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void generateChunk(ScopedLocalBlockQueue scopedLocalBlockQueue, PlotArea plotArea, PseudoRandom pseudoRandom) {
    }

    @Override
    public PlotArea getNewPlotArea(String world, String id, PlotId min, PlotId max) {
        return new HybridPlotWorld(world, id, this, min, max);
    }

    @Override
    public PlotManager getNewPlotManager() {
        return new HybridPlotManager();
    }

    @Override
    public void initialize(PlotArea plotArea) {
    }
}
