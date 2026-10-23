package dev.skergan.calcurve.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import dev.skergan.calcurve.domain.Calibration;
import dev.skergan.calcurve.domain.Flag;
import dev.skergan.calcurve.domain.QcFlag;
import dev.skergan.calcurve.domain.Quantification;
import dev.skergan.calcurve.domain.Sample;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalibrationServiceTest {

    static final double[] X = RegressionTest.X;
    static final double[] Y = RegressionTest.Y;
    static final double TOL = 1e-4;

    final CalibrationService service = new CalibrationService();

    @Test
    void referenceDatasetPassesQc() {
        Calibration calibration = service.calibrate(X, Y, "pg/mL", null);

        assertThat(calibration.flags()).isEmpty();
    }

    @Test
    void quantifiesSection5Unknowns() {
        Calibration calibration = service.calibrate(X, Y, "pg/mL", null);

        List<Quantification> results = service.quantify(calibration, List.of(
                new Sample("S-01", List.of(2.9)),
                new Sample("S-02", List.of(13.5)),
                new Sample("S-03", List.of(23.0)),
                new Sample("S-04", List.of(13.5, 13.3, 13.7))));

        assertQuantification(results.get(0), "S-01", 1, 0.716004, 0.264570, 0.680098);
        assertQuantification(results.get(1), "S-02", 1, 6.20722, 0.239754, 0.616308);
        assertQuantification(results.get(2), "S-03", 1, 11.1286, 0.263193, 0.676560);
        assertQuantification(results.get(3), "S-04", 3, 6.20722, 0.154797, 0.397918);

        assertThat(results.get(0).flags()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(QcFlag.BELOW_LOD);
            assertThat(f.severity()).isEqualTo(QcFlag.Severity.WARNING);
            assertThat(f.message()).isEqualTo("0.716 pg/mL is below the LOD of 0.740 pg/mL");
        });
        assertThat(results.subList(1, 4)).allSatisfy(q -> assertThat(q.flags()).isEmpty());
    }

    @Test
    void ciHalfWidthUsesStudentT() {
        Calibration calibration = service.calibrate(X, Y, null, null);
        Quantification q = service.quantify(calibration, List.of(new Sample("S", List.of(13.5)))).getFirst();

        assertThat(q.ci95() / q.stdDev()).isCloseTo(2.570582, within(1e-6));
    }

    @Test
    void responseBetweenLodAndLoqIsBelowLoq() {
        Calibration calibration = service.calibrate(X, Y, "pg/mL", null);
        // x0 = (5.0 - 1.517857) / 1.930357 ≈ 1.80, between LOD 0.74 and LOQ 2.24
        Quantification q = service.quantify(calibration, List.of(new Sample("S", List.of(5.0)))).getFirst();

        assertThat(codes(q.flags())).containsExactly(QcFlag.BELOW_LOQ);
    }

    @Test
    void fourStandardsRaiseFewStandards() {
        Calibration calibration = service.calibrate(
                new double[] {0, 2, 4, 6}, new double[] {2.1, 5.0, 9.0, 12.6}, "pg/mL", null);

        assertThat(codes(calibration.flags())).contains(QcFlag.FEW_STANDARDS);
    }

    @Test
    void lowLinearityUsesOverridableThreshold() {
        assertThat(codes(service.calibrate(X, Y, null, 0.999).flags())).containsExactly(QcFlag.LOW_LINEARITY);
        assertThat(service.calibrate(X, Y, null, null).flags()).isEmpty();
    }

    @Test
    void flagsSingleResidualOutlier() {
        double[] x = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18};
        double[] y = {1.6, 5.16, 9.37, 12.98, 19.94, 20.92, 24.51, 28.57, 32.3, 36.34};

        Calibration calibration = service.calibrate(x, y, "pg/mL", null);

        List<Flag> outliers = calibration.flags().stream()
                .filter(f -> f.code() == QcFlag.RESIDUAL_OUTLIER)
                .toList();
        assertThat(outliers).singleElement().satisfies(f -> {
            assertThat(f.message()).startsWith("Standard 4 (8 pg/mL)");
            assertThat(f.message()).contains("2.66 × s_y/x");
        });
    }

    @Test
    void responseAboveHighestStandardIsExtrapolated() {
        Calibration calibration = service.calibrate(X, Y, "pg/mL", null);

        Quantification q = service.quantify(calibration, List.of(new Sample("HIGH", List.of(30.0)))).getFirst();

        assertThat(q.concentration()).isGreaterThan(12);
        assertThat(codes(q.flags())).containsExactly(QcFlag.EXTRAPOLATED);
    }

    @Test
    void negativeSlopeIsAnError() {
        QcException e = catchThrowableOfType(() -> service.calibrate(
                new double[] {0, 2, 4, 6, 8}, new double[] {10, 8.1, 6.0, 3.9, 2.0}, null, null), QcException.class);

        assertThat(e.primary().code()).isEqualTo(QcFlag.NON_POSITIVE_SLOPE);
        assertThat(e.primary().severity()).isEqualTo(QcFlag.Severity.ERROR);
    }

    @Test
    void twoStandardsAreTooFew() {
        QcException e = catchThrowableOfType(
                () -> service.calibrate(new double[] {0, 2}, new double[] {1, 5}, null, null), QcException.class);

        assertThat(codes(e.flags())).containsExactly(QcFlag.TOO_FEW_STANDARDS);
    }

    @Test
    void identicalConcentrationsAreDegenerate() {
        QcException e = catchThrowableOfType(
                () -> service.calibrate(new double[] {3, 3, 3}, new double[] {1, 2, 3}, null, null), QcException.class);

        assertThat(codes(e.flags())).containsExactly(QcFlag.DEGENERATE_STANDARDS);
    }

    private static void assertQuantification(Quantification q, String sampleId, int replicates,
                                             double concentration, double stdDev, double ci95) {
        assertThat(q.sampleId()).isEqualTo(sampleId);
        assertThat(q.replicates()).isEqualTo(replicates);
        assertThat(q.concentration()).as("%s concentration", sampleId).isCloseTo(concentration, within(TOL));
        assertThat(q.stdDev()).as("%s stdDev", sampleId).isCloseTo(stdDev, within(TOL));
        assertThat(q.ci95()).as("%s ci95", sampleId).isCloseTo(ci95, within(TOL));
    }

    private static List<QcFlag> codes(List<Flag> flags) {
        return flags.stream().map(Flag::code).toList();
    }
}
