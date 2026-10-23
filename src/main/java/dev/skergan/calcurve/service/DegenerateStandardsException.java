package dev.skergan.calcurve.service;

/** Thrown when all standards share one concentration (Sxx = 0), so no slope can be fitted. */
public class DegenerateStandardsException extends RuntimeException {

    public DegenerateStandardsException() {
        super("All standards have the same concentration; a calibration line cannot be fitted");
    }
}
