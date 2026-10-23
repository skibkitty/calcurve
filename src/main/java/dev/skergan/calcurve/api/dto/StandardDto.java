package dev.skergan.calcurve.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "A calibration standard: known concentration and its measured instrument response")
public record StandardDto(
        @Schema(example = "4") @NotNull @PositiveOrZero @Finite Double concentration,
        @Schema(example = "9.0") @NotNull @Finite Double response) {
}
