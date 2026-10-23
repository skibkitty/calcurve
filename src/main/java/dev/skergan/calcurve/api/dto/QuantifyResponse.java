package dev.skergan.calcurve.api.dto;

import dev.skergan.calcurve.domain.Flag;
import dev.skergan.calcurve.domain.Quantification;
import java.util.List;

public record QuantifyResponse(CalibrationResponse calibration, List<Result> results) {

    public record Result(
            String sampleId,
            int replicates,
            double meanResponse,
            double concentration,
            double stdDev,
            double ci95,
            List<Flag> flags) {

        public static Result from(Quantification q) {
            return new Result(q.sampleId(), q.replicates(), q.meanResponse(), q.concentration(),
                    q.stdDev(), q.ci95(), q.flags());
        }
    }
}
