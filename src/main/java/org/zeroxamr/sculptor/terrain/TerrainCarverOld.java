package org.zeroxamr.sculptor.terrain;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.zeroxamr.sculptor.engine.SplineEngine;
import org.zeroxamr.sculptor.session.SculptSession;

import java.util.ArrayList;
import java.util.List;

public class TerrainCarverOld {

    private final Plugin plugin;
    private static final int STEPS_3D = 300;

    public TerrainCarverOld(Plugin plugin) { this.plugin = plugin; }

    public void carve(Player player, SculptSession session) {
        List<double[]> curve = SplineEngine.evaluate(
                session.getControlPoints(),
                session.getInterpolation(),
                session.getAxis(),
                STEPS_3D
        );

        if (curve.isEmpty()) {
            player.sendMessage("Could not compute curve. Check control points.");
            return;
        }

        World world = player.getWorld();

        // Snapshot blocks before touching them
        List<int[]> snapshot = snapshot(world, curve, session.getShape());
        session.pushUndo(snapshot);

        if (session.isAnimationEnabled()) {
            carveAnimated(world, curve, session.getShape());
        } else {
            carveInstant(world, curve, session.getShape());
        }

        session.setCarved(true);
    }

    // ── Undo ──────────────────────────────────────────────────────────────

    public void applyUndo(World world, List<int[]> snapshot) {
        for (int[] entry : snapshot) {
            world.getBlockAt(entry[0], entry[1], entry[2])
                    .setType(Material.values()[entry[3]]);
        }
    }

    // ── Snapshot ──────────────────────────────────────────────────────────

    private List<int[]> snapshot(World world, List<double[]> curve, SplineEngine.Shape shape) {
        List<int[]> snap = new ArrayList<>();
        for (double[] pt : curve) {
            int cx = (int) Math.round(pt[0]);
            int cy = (int) Math.round(pt[1]);
            int cz = (int) Math.round(pt[2]);
            collectBlocks(world, cx, cy, cz, shape, snap);
        }
        return snap;
    }

    private void collectBlocks(World world, int cx, int cy, int cz,
                               SplineEngine.Shape shape, List<int[]> snap) {
        switch (shape) {
            case RIDGE, SURFACE -> {
                int depth = shape == SplineEngine.Shape.SURFACE ? 1 : TerrainLayer.DEPTH;
                for (int d = -20; d <= depth; d++) {
                    int y = cy - d;
                    if (y < TerrainLayer.BEDROCK_Y) break;
                    Block b = world.getBlockAt(cx, y, cz);
                    snap.add(new int[]{cx, y, cz, b.getType().ordinal()});
                }
            }
            case TUBE -> {
                int r = TerrainLayer.TUBE_RADIUS;
                for (int dx = -r; dx <= r; dx++)
                    for (int dy = -r; dy <= r; dy++)
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx*dx + dy*dy + dz*dz <= r*r) {
                                int bx = cx+dx, by = cy+dy, bz = cz+dz;
                                Block b = world.getBlockAt(bx, by, bz);
                                snap.add(new int[]{bx, by, bz, b.getType().ordinal()});
                            }
                        }
            }
        }
    }

    // ── Instant ───────────────────────────────────────────────────────────

    private void carveInstant(World world, List<double[]> curve, SplineEngine.Shape shape) {
        for (double[] pt : curve) {
            int cx = (int) Math.round(pt[0]);
            int cy = (int) Math.round(pt[1]);
            int cz = (int) Math.round(pt[2]);
            placeShape(world, cx, cy, cz, shape);
        }
    }

    // ── Animated ──────────────────────────────────────────────────────────

    private void carveAnimated(World world, List<double[]> curve, SplineEngine.Shape shape) {
        final int BLOCKS_PER_TICK = 10; // tune this up or down
        new BukkitRunnable() {
            int index = 0;
            @Override public void run() {
                int processed = 0;
                while (index < curve.size() && processed < BLOCKS_PER_TICK) {
                    double[] pt = curve.get(index);
                    placeShape(world,
                            (int) Math.round(pt[0]),
                            (int) Math.round(pt[1]),
                            (int) Math.round(pt[2]),
                            shape);
                    index++;
                    processed++;
                }
                if (index >= curve.size()) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Shape dispatch ────────────────────────────────────────────────────

    private void placeShape(World world, int cx, int cy, int cz, SplineEngine.Shape shape) {
        switch (shape) {
            case RIDGE   -> placeRidge(world, cx, cy, cz);
            case TUBE    -> placeTube(world, cx, cy, cz);
            case SURFACE -> placeSurface(world, cx, cy, cz);
        }
    }

    private void placeRidge(World world, int cx, int cy, int cz) {
        // Clear above
        for (int y = cy + 1; y <= cy + 20; y++)
            world.getBlockAt(cx, y, cz).setType(Material.AIR);
        // Place layered column downward
        for (int d = 0; d < TerrainLayer.DEPTH; d++) {
            int y = cy - d;
            if (y <= TerrainLayer.BEDROCK_Y) break;
            world.getBlockAt(cx, y, cz).setType(TerrainLayer.getMaterial(d));
        }
    }

    private void placeSurface(World world, int cx, int cy, int cz) {
        world.getBlockAt(cx, cy, cz).setType(Material.GRASS_BLOCK);
        world.getBlockAt(cx, cy + 1, cz).setType(Material.AIR);
    }

    private void placeTube(World world, int cx, int cy, int cz) {
        int r = TerrainLayer.TUBE_RADIUS;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx*dx + dy*dy + dz*dz <= r*r) {
                        int y = cy + dy;
                        if (y <= TerrainLayer.BEDROCK_Y) continue;
                        world.getBlockAt(cx+dx, y, cz+dz)
                                .setType(TerrainLayer.getMaterial(Math.abs(dy)));
                    }
                }
    }
}