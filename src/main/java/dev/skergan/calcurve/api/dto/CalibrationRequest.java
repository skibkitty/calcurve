package dev.skergan.calcurve.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CalibrationRequest(
        @Schema(example = "Fluorescein") String analyte,
        @Schema(example = "pg/mL") String concentrationUnit,
        @Schema(example = "fluorescence") String responseUnit,
        @NotNull @Size(min = 1) List<@NotNull @Valid StandardDto> standards,
        @Schema(description = "Minimum acceptable R² (default 0.995)", example = "0.995")
        @DecimalMin("0.0") @DecimalMax("1.0") Double minRSquared) implements StandardsInput {
}
