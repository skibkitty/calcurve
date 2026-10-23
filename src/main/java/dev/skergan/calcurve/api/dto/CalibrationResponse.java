package dev.skergan.calcurve.api.dto;

import dev.skergan.calcurve.domain.Calibration;
import dev.skergan.calcurve.domain.Flag;
import dev.skergan.calcurve.domain.LinearFit;
import java.util.Arrays;
import java.util.List;

public record CalibrationResponse(
        String analyte,
        int n,
        double slope,
        double slopeStdError,
        double intercept,
        double interceptStdError,
        double rSquared,
        double residualStdDev,
        double lod,
        double loq,
        String concentrationUnit,
        Range calibratedRange,
        List<Double> residuals,
        List<Flag> flags) {

    public record Range(double min, double max) {
    }

    public static CalibrationResponse from(String analyte, Calibration calibration) {
        LinearFit fit = calibration.fit();
        return new CalibrationResponse(
                analyte,
                fit.n(),
                fit.slope(),
                fit.slopeStdError(),
                fit.intercept(),
                fit.interceptStdError(),
                fit.rSquared(),
                fit.residualStdDev(),
                fit.lod(),
                fit.loq(),
                calibration.concentrationUnit(),
                new Range(fit.minX(), fit.maxX()),
                Arrays.stream(fit.residuals()).boxed().toList(),
                calibration.flags());
    }
}
