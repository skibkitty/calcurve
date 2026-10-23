package dev.skergan.calcurve.domain;

import java.util.Arrays;

/**
 * Immutable result of an ordinary least-squares fit {@code y = intercept + slope * x}.
 * Also carries the summary sums needed to invert the line for unknowns.
 */
public record LinearFit(
        int n,
        double slope,
        double intercept,
        double slopeStdError,
        double interceptStdError,
        double rSquared,
        double residualStdDev,
        double lod,
        double loq,
        double xMean,
        double yMean,
        double sxx,
        double minX,
        double maxX,
        double[] residuals) {

    public LinearFit {
        residuals = residuals.clone();
    }

    @Override
    public double[] residuals() {
        return residuals.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LinearFit other
                && n == other.n
                && Double.compare(slope, other.slope) == 0
                && Double.compare(intercept, other.intercept) == 0
                && Double.compare(slopeStdError, other.slopeStdError) == 0
                && Double.compare(interceptStdError, other.interceptStdError) == 0
                && Double.compare(rSquared, other.rSquared) == 0
                && Double.compare(residualStdDev, other.residualStdDev) == 0
                && Double.compare(lod, other.lod) == 0
                && Double.compare(loq, other.loq) == 0
                && Double.compare(xMean, other.xMean) == 0
                && Double.compare(yMean, other.yMean) == 0
                && Double.compare(sxx, other.sxx) == 0
                && Double.compare(minX, other.minX) == 0
                && Double.compare(maxX, other.maxX) == 0
                && Arrays.equals(residuals, other.residuals);
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(slope) + Arrays.hashCode(residuals);
    }

    @Override
    public String toString() {
        return "LinearFit[n=%d, slope=%s, intercept=%s, rSquared=%s, residualStdDev=%s]"
                .formatted(n, slope, intercept, rSquared, residualStdDev);
    }
}
