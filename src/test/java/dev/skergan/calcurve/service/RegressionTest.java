package dev.skergan.calcurve.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.skergan.calcurve.domain.LinearFit;
import org.junit.jupiter.api.Test;

class RegressionTest {

    /** Section 5 fluorescein standards. Reference values from NumPy/SciPy. */
    static final double[] X = {0, 2, 4, 6, 8, 10, 12};
    static final double[] Y = {2.1, 5.0, 9.0, 12.6, 17.3, 21.0, 24.7};

    static final double TOL = 1e-4;

    @Test
    void fitsReferenceDataset() {
        LinearFit fit = Regression.fit(X, Y);

        assertThat(fit.n()).isEqualTo(7);
        assertThat(fit.slope()).isCloseTo(1.930357, within(TOL));
        assertThat(fit.intercept()).isCloseTo(1.517857, within(TOL));
        assertThat(fit.rSquared()).isCloseTo(0.997760, within(TOL));
        assertThat(fit.residualStdDev()).isCloseTo(0.432848, within(TOL));
        assertThat(fit.slopeStdError()).isCloseTo(0.040900, within(TOL));
        assertThat(fit.interceptStdError()).isCloseTo(0.294936, within(TOL));
        assertThat(fit.minX()).isEqualTo(0);
        assertThat(fit.maxX()).isEqualTo(12);
    }

    @Test
    void computesDetectionAndQuantitationLimits() {
        LinearFit fit = Regression.fit(X, Y);

        assertThat(fit.lod()).isCloseTo(0.739965, within(0.739965 * 1e-5));
        assertThat(fit.loq()).isCloseTo(2.242319, within(2.242319 * 1e-5));
    }

    @Test
    void reportsResiduals() {
        double[] expected = {0.582143, -0.378571, -0.239286, -0.500000, 0.339286, 0.178571, 0.0178571};

        double[] residuals = Regression.fit(X, Y).residuals();

        for (int i = 0; i < expected.length; i++) {
            assertThat(residuals[i]).as("residual %d", i).isCloseTo(expected[i], within(TOL));
        }
    }

    @Test
    void perfectLineHasUnitRSquaredAndZeroResidualSd() {
        double[] x = {0, 1, 2, 3, 4};
        double[] y = {1, 3, 5, 7, 9};

        LinearFit fit = Regression.fit(x, y);

        assertThat(fit.slope()).isCloseTo(2.0, within(1e-12));
        assertThat(fit.intercept()).isCloseTo(1.0, within(1e-12));
        assertThat(fit.rSquared()).isCloseTo(1.0, within(1e-12));
        assertThat(fit.residualStdDev()).isCloseTo(0.0, within(1e-12));
    }

    @Test
    void identicalConcentrationsAreDegenerate() {
        double[] x = {5, 5, 5};
        double[] y = {1, 2, 3};

        assertThatThrownBy(() -> Regression.fit(x, y)).isInstanceOf(DegenerateStandardsException.class);
    }

    @Test
    void tCriticalMatchesTable() {
        assertThat(Regression.tCritical95(5)).isCloseTo(2.570582, within(1e-6));
    }
}
