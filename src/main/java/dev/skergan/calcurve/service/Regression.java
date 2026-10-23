package dev.skergan.calcurve.service;

import dev.skergan.calcurve.domain.LinearFit;
import org.apache.commons.math3.distribution.TDistribution;

/**
 * Pure calibration math: ordinary least squares and inverse prediction.
 * No Spring dependencies, so it can be unit-tested directly.
 */
public final class Regression {

    /** ICH Q2 convention: LOD = 3.3 σ / slope. */
    static final double LOD_FACTOR = 3.3;
    /** ICH Q2 convention: LOQ = 10 σ / slope. */
    static final double LOQ_FACTOR = 10.0;

    private Regression() {
    }

    /** An unknown's back-calculated concentration with its standard deviation and 95% CI half-width. */
    public record InversePrediction(double concentration, double stdDev, double ci95) {
    }

    /**
     * Fits {@code y = a + b·x} by ordinary least squares.
     *
     * @throws IllegalArgumentException if fewer than 3 points are given (n − 2 degrees of freedom are needed)
     * @throws DegenerateStandardsException if every x is identical
     */
    public static LinearFit fit(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("x and y must have the same length");
        }
        int n = x.length;
        if (n < 3) {
            throw new IllegalArgumentException("At least 3 points are required, got " + n);
        }

        double xMean = mean(x);
        double yMean = mean(y);
        double sxx = 0;
        double sxy = 0;
        double syy = 0;
        double sumX2 = 0;
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - xMean;
            double dy = y[i] - yMean;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
            sumX2 += x[i] * x[i];
            minX = Math.min(minX, x[i]);
            maxX = Math.max(maxX, x[i]);
        }
        if (sxx == 0) {
            throw new DegenerateStandardsException();
        }

        double slope = sxy / sxx;
        double intercept = yMean - slope * xMean;

        double[] residuals = new double[n];
        double sse = 0;
        for (int i = 0; i < n; i++) {
            residuals[i] = y[i] - (intercept + slope * x[i]);
            sse += residuals[i] * residuals[i];
        }

        double residualStdDev = Math.sqrt(sse / (n - 2));
        double slopeStdError = residualStdDev / Math.sqrt(sxx);
        double interceptStdError = residualStdDev * Math.sqrt(sumX2 / (n * sxx));
        // A flat response (syy = 0) explains nothing; report R² = 0 instead of NaN.
        double rSquared = syy == 0 ? 0 : 1 - sse / syy;
        double lod = LOD_FACTOR * residualStdDev / slope;
        double loq = LOQ_FACTOR * residualStdDev / slope;

        return new LinearFit(n, slope, intercept, slopeStdError, interceptStdError, rSquared,
                residualStdDev, lod, loq, xMean, yMean, sxx, minX, maxX, residuals);
    }

    /**
     * Back-calculates x₀ from the mean of {@code replicates} responses, with its standard deviation
     * and the 95% confidence interval half-width (Student's t, n − 2 degrees of freedom).
     */
    public static InversePrediction inversePredict(LinearFit fit, double meanResponse, int replicates) {
        if (replicates < 1) {
            throw new IllegalArgumentException("At least one replicate is required");
        }
        double b = fit.slope();
        double x0 = (meanResponse - fit.intercept()) / b;
        double dy = meanResponse - fit.yMean();
        double sx0 = (fit.residualStdDev() / b)
                * Math.sqrt(1.0 / replicates + 1.0 / fit.n() + (dy * dy) / (b * b * fit.sxx()));
        double ci95 = tCritical95(fit.n() - 2) * sx0;
        return new InversePrediction(x0, sx0, ci95);
    }

    /** Two-sided 95% critical value of Student's t, i.e. t(0.975, df). */
    public static double tCritical95(int degreesOfFreedom) {
        return new TDistribution(degreesOfFreedom).inverseCumulativeProbability(0.975);
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }
}
