package dev.skergan.calcurve.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** One raised QC flag, serialized as {@code {code, severity, message}}. */
public record Flag(QcFlag code, QcFlag.Severity severity, String message) {

    @JsonIgnore
    public boolean isError() {
        return severity == QcFlag.Severity.ERROR;
    }
}
