package dev.skergan.calcurve.api.dto;

import java.util.List;

/** The calibration fields shared by both request bodies. */
public interface StandardsInput {

    String analyte();

    String concentrationUnit();

    List<StandardDto> standards();

    Double minRSquared();

    default double[] concentrations() {
        return standards().stream().mapToDouble(StandardDto::concentration).toArray();
    }

    default double[] responses() {
        return standards().stream().mapToDouble(StandardDto::response).toArray();
    }
}
