package org.zeroxamr.sculptor.terrain;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.zeroxamr.sculptor.engine.SplineEngine;
import org.zeroxamr.sculptor.session.SculptSession;

import java.util.ArrayList;
import java.util.List;

public class TerrainCarver {

    private final Plugin plugin;
    private static final int STEPS_3D        = 1000;
    private static final int BLOCKS_PER_TICK = 10;

    public TerrainCarver(Plugin plugin) { this.plugin = plugin; }

    // ── Entry point ───────────────────────────────────────────────────────

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
        List<int[]> curveBlocks = getCurveBlocks(curve);

        session.pushUndo(snapshotFromBlocks(world, curveBlocks, session.getShape()));

        if (session.isAnimationEnabled()) {
            carveAnimated(world, curveBlocks, session.getShape());
        } else {
            carveInstant(world, curveBlocks, session.getShape());
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

    // ── Gap filling via Bresenham 3D ──────────────────────────────────────

    private List<int[]> getCurveBlocks(List<double[]> curve) {
        List<int[]> blocks = new ArrayList<>();
        for (int i = 0; i < curve.size() - 1; i++) {
            int x0 = (int) Math.round(curve.get(i)[0]);
            int y0 = (int) Math.round(curve.get(i)[1]);
            int z0 = (int) Math.round(curve.get(i)[2]);
            int x1 = (int) Math.round(curve.get(i + 1)[0]);
            int y1 = (int) Math.round(curve.get(i + 1)[1]);
            int z1 = (int) Math.round(curve.get(i + 1)[2]);
            blocks.addAll(bresenham3D(x0, y0, z0, x1, y1, z1));
        }
        return blocks;
    }

    private List<int[]> bresenham3D(int x0, int y0, int z0,
                                    int x1, int y1, int z1) {
        List<int[]> points = new ArrayList<>();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int dominant = Math.max(dx, Math.max(dy, dz));
        if (dominant == 0) {
            points.add(new int[]{x0, y0, z0});
            return points;
        }
        int ex = dominant / 2;
        int ey = dominant / 2;
        int ez = dominant / 2;
        int x = x0, y = y0, z = z0;
        for (int i = 0; i <= dominant; i++) {
            points.add(new int[]{x, y, z});
            ex -= dx; ey -= dy; ez -= dz;
            if (ex < 0) { x += sx; ex += dominant; }
            if (ey < 0) { y += sy; ey += dominant; }
            if (ez < 0) { z += sz; ez += dominant; }
        }
        return points;
    }

    // ── Snapshot ──────────────────────────────────────────────────────────

    private List<int[]> snapshotFromBlocks(World world, List<int[]> curveBlocks,
                                           SplineEngine.Shape shape) {
        List<int[]> snap = new ArrayList<>();
        for (int[] pt : curveBlocks) {
            collectBlocks(world, pt[0], pt[1], pt[2], shape, snap);
        }
        return snap;
    }

    private void collectBlocks(World world, int cx, int cy, int cz,
                               SplineEngine.Shape shape, List<int[]> snap) {
        switch (shape) {
            case RIDGE -> {
                for (int d = -20; d <= TerrainLayer.DEPTH; d++) {
                    int y = cy - d;
                    if (y < TerrainLayer.BEDROCK_Y) break;
                    snap.add(new int[]{cx, y, cz,
                            world.getBlockAt(cx, y, cz).getType().ordinal()});
                }
            }
            case SURFACE -> {
                snap.add(new int[]{cx, cy, cz,
                        world.getBlockAt(cx, cy, cz).getType().ordinal()});
                snap.add(new int[]{cx, cy + 1, cz,
                        world.getBlockAt(cx, cy + 1, cz).getType().ordinal()});
            }
            case TUBE -> {
                int r = TerrainLayer.TUBE_RADIUS;
                for (int dx = -r; dx <= r; dx++)
                    for (int dy = -r; dy <= r; dy++)
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx*dx + dy*dy + dz*dz <= r*r) {
                                int bx = cx+dx, by = cy+dy, bz = cz+dz;
                                if (by < TerrainLayer.BEDROCK_Y) continue;
                                snap.add(new int[]{bx, by, bz,
                                        world.getBlockAt(bx, by, bz).getType().ordinal()});
                            }
                        }
            }
        }
    }

    // ── Instant carve ─────────────────────────────────────────────────────

    private void carveInstant(World world, List<int[]> curveBlocks,
                              SplineEngine.Shape shape) {
        for (int[] pt : curveBlocks) {
            placeShape(world, pt[0], pt[1], pt[2], shape);
        }
    }

    // ── Animated carve ────────────────────────────────────────────────────

    private void carveAnimated(World world, List<int[]> curveBlocks,
                               SplineEngine.Shape shape) {
        new BukkitRunnable() {
            int index = 0;
            @Override public void run() {
                int processed = 0;
                while (index < curveBlocks.size() && processed < BLOCKS_PER_TICK) {
                    int[] pt = curveBlocks.get(index);
                    placeShape(world, pt[0], pt[1], pt[2], shape);
                    index++;
                    processed++;
                }
                if (index >= curveBlocks.size()) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Shape dispatch ────────────────────────────────────────────────────

    private void placeShape(World world, int cx, int cy, int cz,
                            SplineEngine.Shape shape) {
        switch (shape) {
            case RIDGE   -> placeRidge(world, cx, cy, cz);
            case TUBE    -> placeTube(world, cx, cy, cz);
            case SURFACE -> placeSurface(world, cx, cy, cz);
        }
    }

    private void placeRidge(World world, int cx, int cy, int cz) {
        for (int y = cy + 1; y <= cy + 20; y++)
            world.getBlockAt(cx, y, cz).setType(Material.AIR);
        for (int d = 0; d < TerrainLayer.DEPTH; d++) {
            int y = cy - d;
            if (y <= TerrainLayer.BEDROCK_Y) break;
            world.getBlockAt(cx, y, cz).setType(TerrainLayer.getMaterial(d));
        }
    }

    private void placeSurface(World world, int cx, int cy, int cz) {
        world.getBlockAt(cx, cy + 1, cz).setType(Material.AIR);
        world.getBlockAt(cx, cy, cz).setType(Material.GRASS_BLOCK);
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