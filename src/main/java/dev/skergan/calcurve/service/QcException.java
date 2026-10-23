package dev.skergan.calcurve.service;

import dev.skergan.calcurve.domain.Flag;
import java.util.List;

/** Thrown when a calibration fails a QC rule of ERROR severity. The first flag is the primary error. */
public class QcException extends RuntimeException {

    private final transient List<Flag> flags;

    public QcException(List<Flag> flags) {
        super(flags.getFirst().message());
        this.flags = List.copyOf(flags);
    }

    public List<Flag> flags() {
        return flags;
    }

    public Flag primary() {
        return flags.getFirst();
    }
}
