package org.zeroxamr.sculptor.terrain;

import org.bukkit.Material;

public class TerrainLayer {

    public static Material getMaterial(int depth) {
        if (depth == 0) return Material.GRASS_BLOCK;
        if (depth <= 3)  return Material.DIRT;
        return Material.STONE;
    }

    public static final int BEDROCK_Y = -64;
    public static final int DEPTH     = 10;
    public static final int TUBE_RADIUS = 2;
}