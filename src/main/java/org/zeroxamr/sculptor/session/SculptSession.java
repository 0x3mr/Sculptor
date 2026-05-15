package org.zeroxamr.sculptor.session;

import org.zeroxamr.sculptor.engine.SplineEngine;
import java.util.*;

public class SculptSession {

    private final List<int[]> controlPoints = new ArrayList<>();
    private SplineEngine.Interpolation interpolation = SplineEngine.Interpolation.LINEAR;
    private SplineEngine.Axis axis = SplineEngine.Axis.TWO_D;
    private SplineEngine.Shape shape = SplineEngine.Shape.RIDGE;
    private boolean animationEnabled = true;
    private boolean carved = false;

    // ── Undo stack — each entry is a snapshot of blocks before a carve ────
    private final Deque<List<int[]>> undoStack = new ArrayDeque<>();
    // Each int[] = {x, y, z, materialOrdinal}

    // ── Control points ────────────────────────────────────────────────────

    public void addPoint(int x, int y, int z) {
        controlPoints.removeIf(p -> p[0] == x && p[2] == z);
        controlPoints.add(new int[]{x, y, z});
        carved = false;
    }

    public boolean removePoint(int x, int z) {
        boolean removed = controlPoints.removeIf(p -> p[0] == x && p[2] == z);
        if (removed) carved = false;
        return removed;
    }

    public void clear() {
        controlPoints.clear();
        carved = false;
    }

    public List<int[]> getControlPoints() { return controlPoints; }
    public boolean hasPoints() { return controlPoints.size() >= 2; }
    public boolean isCarved() { return carved; }
    public void setCarved(boolean v) { carved = v; }

    // ── Undo ──────────────────────────────────────────────────────────────

    public void pushUndo(List<int[]> snapshot) { undoStack.push(snapshot); }
    public boolean canUndo() { return !undoStack.isEmpty(); }
    public List<int[]> popUndo() { return undoStack.pop(); }

    // ── Style cycling ─────────────────────────────────────────────────────

    public void cycleStyle() {
        if (interpolation == SplineEngine.Interpolation.LINEAR
                && axis == SplineEngine.Axis.TWO_D) {
            interpolation = SplineEngine.Interpolation.SPLINE;
        } else if (interpolation == SplineEngine.Interpolation.SPLINE
                && axis == SplineEngine.Axis.TWO_D) {
            interpolation = SplineEngine.Interpolation.LINEAR;
            axis = SplineEngine.Axis.THREE_D;
        } else if (interpolation == SplineEngine.Interpolation.LINEAR
                && axis == SplineEngine.Axis.THREE_D) {
            interpolation = SplineEngine.Interpolation.SPLINE;
        } else {
            interpolation = SplineEngine.Interpolation.LINEAR;
            axis = SplineEngine.Axis.TWO_D;
        }
        carved = false;
    }

    public void cycleShape() {
        shape = switch (shape) {
            case RIDGE   -> SplineEngine.Shape.TUBE;
            case TUBE    -> SplineEngine.Shape.SURFACE;
            case SURFACE -> SplineEngine.Shape.RIDGE;
        };
        carved = false;
    }

    public String getStyleName() {
        return interpolation.name().toLowerCase() + "-" + axis.name().toLowerCase().replace("_d", "d");
    }

    public String getShapeName() { return shape.name().toLowerCase(); }

    // ── Getters ───────────────────────────────────────────────────────────

    public SplineEngine.Interpolation getInterpolation() { return interpolation; }
    public SplineEngine.Axis getAxis() { return axis; }
    public SplineEngine.Shape getShape() { return shape; }
    public boolean isAnimationEnabled() { return animationEnabled; }
    public void setAnimation(boolean v) { animationEnabled = v; }
}