package dev.skergan.calcurve.api.dto;

import dev.skergan.calcurve.domain.Sample;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "An unknown sample with one or more replicate responses")
public record UnknownDto(
        @Schema(example = "S-01") @NotBlank @Size(max = 64) String sampleId,
        @Schema(example = "[13.5, 13.3, 13.7]") @NotEmpty List<@NotNull @Finite Double> responses) {

    public Sample toSample() {
        return new Sample(sampleId, responses);
    }
}
