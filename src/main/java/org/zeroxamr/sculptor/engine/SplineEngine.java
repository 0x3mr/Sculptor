package org.zeroxamr.sculptor.engine;

import java.util.List;

public class SplineEngine {

    public enum Interpolation { LINEAR, SPLINE }
    public enum Axis { TWO_D, THREE_D }
    public enum Shape { RIDGE, TUBE, SURFACE }

    /**
     * Evaluate the curve at evenly-spaced parameter steps.
     * Returns a list of {x, y, z} world positions along the curve.
     *
     * @param points raw control points {x, y, z}
     * @param interp LINEAR or SPLINE
     * @param axis   TWO_D (fixed Z) or THREE_D (parametric)
     * @param steps  how many points to sample along the curve
     */
    public static List<double[]> evaluate(
            List<int[]> points,
            Interpolation interp,
            Axis axis,
            int steps) {

        if (points.size() < 2) return List.of();

        if (axis == Axis.TWO_D) {
            return evaluate2D(points, interp, steps);
        } else {
            return evaluate3D(points, interp, steps);
        }
    }

    // ── 2D: interpolate Y as function of X, fixed Z ───────────────────────

    private static List<double[]> evaluate2D(
            List<int[]> points, Interpolation interp, int steps) {

        // Sort by X
        List<int[]> sorted = points.stream()
                .sorted((a, b) -> Integer.compare(a[0], b[0]))
                .toList();

        double fixedZ = sorted.get(0)[2];
        double[] xs = sorted.stream().mapToDouble(p -> p[0]).toArray();
        double[] ys = sorted.stream().mapToDouble(p -> p[1]).toArray();

        double[] coeffs = interp == Interpolation.SPLINE
                ? computeSplineM(xs, ys) : null;

        int x0 = sorted.get(0)[0];
        int xN = sorted.get(sorted.size() - 1)[0];

        List<double[]> result = new java.util.ArrayList<>();
        for (int x = x0; x <= xN; x++) {
            double y = interp == Interpolation.SPLINE
                    ? evalSpline(xs, ys, coeffs, x)
                    : evalLinear(xs, ys, x);
            result.add(new double[]{x, y, fixedZ});
        }
        return result;
    }

    // ── 3D: parametric spline through X(t), Y(t), Z(t) ───────────────────

    private static List<double[]> evaluate3D(
            List<int[]> points, Interpolation interp, int steps) {

        int n = points.size();
        double[] t = new double[n];
        t[0] = 0;
        for (int i = 1; i < n; i++) {
            double dx = points.get(i)[0] - points.get(i-1)[0];
            double dy = points.get(i)[1] - points.get(i-1)[1];
            double dz = points.get(i)[2] - points.get(i-1)[2];
            t[i] = t[i-1] + Math.sqrt(dx*dx + dy*dy + dz*dz); // chord length param
        }

        double[] xs = points.stream().mapToDouble(p -> p[0]).toArray();
        double[] ys = points.stream().mapToDouble(p -> p[1]).toArray();
        double[] zs = points.stream().mapToDouble(p -> p[2]).toArray();

        double[] Mx = interp == Interpolation.SPLINE ? computeSplineM(t, xs) : null;
        double[] My = interp == Interpolation.SPLINE ? computeSplineM(t, ys) : null;
        double[] Mz = interp == Interpolation.SPLINE ? computeSplineM(t, zs) : null;

        List<double[]> result = new java.util.ArrayList<>();
        double tMax = t[n-1];
        for (int i = 0; i <= steps; i++) {
            double ti = tMax * i / steps;
            double x = interp == Interpolation.SPLINE
                    ? evalSpline(t, xs, Mx, ti) : evalLinear(t, xs, ti);
            double y = interp == Interpolation.SPLINE
                    ? evalSpline(t, ys, My, ti) : evalLinear(t, ys, ti);
            double z = interp == Interpolation.SPLINE
                    ? evalSpline(t, zs, Mz, ti) : evalLinear(t, zs, ti);
            result.add(new double[]{x, y, z});
        }
        return result;
    }

    // ── Thomas algorithm — returns second derivatives M ───────────────────

    private static double[] computeSplineM(double[] x, double[] y) {
        int n = x.length - 1;
        if (n < 2) return new double[x.length];

        double[] h = new double[n];
        for (int i = 0; i < n; i++) h[i] = x[i+1] - x[i];

        double[] alpha = new double[n-1];
        for (int i = 1; i < n; i++) {
            alpha[i-1] = 3.0 * ((y[i+1]-y[i])/h[i] - (y[i]-y[i-1])/h[i-1]);
        }

        double[] l  = new double[n-1];
        double[] mu = new double[n-1];
        double[] z  = new double[n-1];

        l[0]  = 2.0 * (h[0] + h[1]);
        mu[0] = (n > 2) ? h[1] / l[0] : 0;
        z[0]  = alpha[0] / l[0];

        for (int i = 1; i < n-1; i++) {
            l[i]  = 2.0*(h[i]+h[i+1]) - h[i]*mu[i-1];
            mu[i] = (i < n-2) ? h[i+1]/l[i] : 0;
            z[i]  = (alpha[i] - h[i]*z[i-1]) / l[i];
        }

        double[] M = new double[n+1]; // natural BC: M[0]=M[n]=0
        for (int j = n-2; j >= 0; j--) {
            M[j+1] = z[j] - mu[j] * M[j+2];
        }
        return M;
    }

    // ── Evaluate spline at parameter value xi ─────────────────────────────

    private static double evalSpline(double[] x, double[] y, double[] M, double xi) {
        int n = x.length - 1;
        int seg = n - 1;
        for (int i = 0; i < n-1; i++) {
            if (xi <= x[i+1]) { seg = i; break; }
        }
        double h  = x[seg+1] - x[seg];
        double dx = xi - x[seg];
        return y[seg]
                + ((y[seg+1]-y[seg])/h - h*(2*M[seg]+M[seg+1])/6.0) * dx
                + (M[seg]/2.0) * dx*dx
                + ((M[seg+1]-M[seg])/(6.0*h)) * dx*dx*dx;
    }

    // ── Evaluate linear interpolation at parameter value xi ───────────────

    private static double evalLinear(double[] x, double[] y, double xi) {
        int n = x.length - 1;
        int seg = n - 1;
        for (int i = 0; i < n-1; i++) {
            if (xi <= x[i+1]) { seg = i; break; }
        }
        double t = (xi - x[seg]) / (x[seg+1] - x[seg]);
        return y[seg] + t * (y[seg+1] - y[seg]);
    }
}