package com.plotsquaredmg.world;

public class BlockNBTData {
    private final byte block;
    private final int data, skyLight, blockLight;

    public BlockNBTData(byte block, int data, int skyLight, int blockLight) {
        this.block = block;
        this.data = data;
        this.skyLight = skyLight;
        this.blockLight = blockLight;
    }

    public byte getBlock() {
        return block;
    }

    public int getData() {
        return data;
    }

    public int getSkyLight() {
        return skyLight;
    }

    public int getBlockLight() {
        return blockLight;
    }
}
