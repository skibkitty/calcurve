package dev.skergan.calcurve.domain;

import java.util.List;

/** The back-calculated concentration of one unknown sample. */
public record Quantification(
        String sampleId,
        int replicates,
        double meanResponse,
        double concentration,
        double stdDev,
        double ci95,
        List<Flag> flags) {

    public Quantification {
        flags = List.copyOf(flags);
    }
}
