package dev.skergan.calcurve.domain;

import java.util.List;

/** A fitted calibration line together with the (non-fatal) QC flags it raised. */
public record Calibration(LinearFit fit, String concentrationUnit, List<Flag> flags) {

    public Calibration {
        flags = List.copyOf(flags);
    }
}
