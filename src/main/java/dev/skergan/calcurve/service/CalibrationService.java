package dev.skergan.calcurve.service;

import dev.skergan.calcurve.domain.Calibration;
import dev.skergan.calcurve.domain.Flag;
import dev.skergan.calcurve.domain.LinearFit;
import dev.skergan.calcurve.domain.QcFlag;
import dev.skergan.calcurve.domain.Quantification;
import dev.skergan.calcurve.domain.Sample;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Fits calibrations, applies the QC rules, and quantifies unknowns. Stateless. */
@Service
public class CalibrationService {

    /**
     * Default minimum R² for an acceptable linear calibration. 0.995 is a common acceptance
     * criterion in analytical method validation; callers can override it per request.
     */
    public static final double DEFAULT_MIN_R_SQUARED = 0.995;

    static final int MIN_STANDARDS = 3;
    static final int RECOMMENDED_STANDARDS = 5;
    /** A standard whose |residual| exceeds this many s_y/x is flagged as an outlier. */
    static final double OUTLIER_SIGMAS = 2.0;

    /**
     * Fits the standards and applies the calibration-level QC rules.
     *
     * @param minRSquared linearity threshold, or {@code null} for {@link #DEFAULT_MIN_R_SQUARED}
     * @throws QcException if any ERROR-severity rule fails
     */
    public Calibration calibrate(double[] concentrations, double[] responses,
                                 String concentrationUnit, Double minRSquared) {
        int n = concentrations.length;
        if (n < MIN_STANDARDS) {
            throw new QcException(List.of(QcFlag.TOO_FEW_STANDARDS.withMessage(
                    "At least %d standards are required, got %d".formatted(MIN_STANDARDS, n))));
        }

        LinearFit fit;
        try {
            fit = Regression.fit(concentrations, responses);
        } catch (DegenerateStandardsException e) {
            throw new QcException(List.of(QcFlag.DEGENERATE_STANDARDS.withMessage(e.getMessage())));
        }

        double threshold = minRSquared != null ? minRSquared : DEFAULT_MIN_R_SQUARED;
        List<Flag> flags = new ArrayList<>();

        if (fit.slope() <= 0) {
            flags.add(QcFlag.NON_POSITIVE_SLOPE.withMessage(
                    "Slope is %s; response must increase with concentration".formatted(fmt(fit.slope()))));
        }
        if (n < RECOMMENDED_STANDARDS) {
            flags.add(QcFlag.FEW_STANDARDS.withMessage(
                    "Only %d standards; at least %d are recommended".formatted(n, RECOMMENDED_STANDARDS)));
        }
        if (fit.rSquared() < threshold) {
            flags.add(QcFlag.LOW_LINEARITY.withMessage(
                    "R² of %s is below the minimum of %s".formatted(fmt(fit.rSquared(), 4), threshold)));
        }
        double[] residuals = fit.residuals();
        double outlierLimit = OUTLIER_SIGMAS * fit.residualStdDev();
        // On an exact line the residuals are pure rounding noise; comparing them to s_y/x is meaningless.
        boolean exactFit = fit.rSquared() > 1 - 1e-12;
        for (int i = 0; i < n && !exactFit; i++) {
            if (Math.abs(residuals[i]) > outlierLimit) {
                flags.add(QcFlag.RESIDUAL_OUTLIER.withMessage(
                        "Standard %d (%s) has a residual of %s, %s × s_y/x (limit %s)".formatted(
                                i, withUnit(concentrations[i], concentrationUnit), fmt(residuals[i]),
                                fmt(Math.abs(residuals[i]) / fit.residualStdDev()), fmt(OUTLIER_SIGMAS))));
            }
        }

        if (flags.stream().anyMatch(Flag::isError)) {
            // Errors first, so the primary problem is flags[0]; keep warnings for context.
            flags.sort(Comparator.comparing(f -> !f.isError()));
            throw new QcException(flags);
        }
        return new Calibration(fit, concentrationUnit, flags);
    }

    /** Back-calculates each sample's concentration with its 95% CI and per-sample QC flags. */
    public List<Quantification> quantify(Calibration calibration, List<Sample> samples) {
        LinearFit fit = calibration.fit();
        String unit = calibration.concentrationUnit();
        List<Quantification> results = new ArrayList<>(samples.size());

        for (Sample sample : samples) {
            int m = sample.responses().size();
            double meanResponse = sample.responses().stream().mapToDouble(Double::doubleValue).average()
                    .orElseThrow(() -> new IllegalArgumentException("Sample " + sample.sampleId() + " has no responses"));
            Regression.InversePrediction p = Regression.inversePredict(fit, meanResponse, m);
            double x0 = p.concentration();

            List<Flag> flags = new ArrayList<>();
            if (x0 < fit.lod()) {
                flags.add(QcFlag.BELOW_LOD.withMessage("%s is below the LOD of %s"
                        .formatted(withUnit(x0, unit), withUnit(fit.lod(), unit))));
            } else if (x0 < fit.loq()) {
                flags.add(QcFlag.BELOW_LOQ.withMessage("%s is below the LOQ of %s"
                        .formatted(withUnit(x0, unit), withUnit(fit.loq(), unit))));
            }
            if (x0 < fit.minX() || x0 > fit.maxX()) {
                flags.add(QcFlag.EXTRAPOLATED.withMessage("%s is outside the calibrated range of %s–%s"
                        .formatted(withUnit(x0, unit), fmt(fit.minX()), withUnit(fit.maxX(), unit))));
            }

            results.add(new Quantification(sample.sampleId(), m, meanResponse, x0, p.stdDev(), p.ci95(), flags));
        }
        return results;
    }

    private static String withUnit(double value, String unit) {
        return unit == null || unit.isBlank() ? fmt(value) : fmt(value) + " " + unit;
    }

    /** Three significant figures, plain notation, for human-readable messages. */
    private static String fmt(double value) {
        return fmt(value, 3);
    }

    private static String fmt(double value, int significantFigures) {
        return new BigDecimal(value).round(new MathContext(significantFigures)).toPlainString();
    }
}
