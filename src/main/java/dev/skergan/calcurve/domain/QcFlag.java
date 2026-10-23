package dev.skergan.calcurve.domain;

/** Quality-control rules applied to a calibration and to each quantified unknown. */
public enum QcFlag {

    TOO_FEW_STANDARDS(Severity.ERROR),
    DEGENERATE_STANDARDS(Severity.ERROR),
    NON_POSITIVE_SLOPE(Severity.ERROR),
    FEW_STANDARDS(Severity.WARNING),
    LOW_LINEARITY(Severity.WARNING),
    RESIDUAL_OUTLIER(Severity.WARNING),
    BELOW_LOQ(Severity.WARNING),
    BELOW_LOD(Severity.WARNING),
    EXTRAPOLATED(Severity.WARNING);

    public enum Severity { INFO, WARNING, ERROR }

    private final Severity severity;

    QcFlag(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }

    /** Kebab-case slug used in RFC 7807 problem type URIs, e.g. {@code too-few-standards}. */
    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }

    public Flag withMessage(String message) {
        return new Flag(this, severity, message);
    }
}
