package dev.skergan.calcurve.domain;

import java.util.List;

/** An unknown sample: one or more replicate instrument responses. */
public record Sample(String sampleId, List<Double> responses) {

    public Sample {
        responses = List.copyOf(responses);
    }
}
