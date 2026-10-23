package dev.skergan.calcurve.api;

import dev.skergan.calcurve.api.dto.CalibrationRequest;
import dev.skergan.calcurve.api.dto.CalibrationResponse;
import dev.skergan.calcurve.api.dto.QuantifyRequest;
import dev.skergan.calcurve.api.dto.QuantifyResponse;
import dev.skergan.calcurve.api.dto.StandardsInput;
import dev.skergan.calcurve.api.dto.UnknownDto;
import dev.skergan.calcurve.domain.Calibration;
import dev.skergan.calcurve.service.CalibrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calibrations")
@Tag(name = "Calibrations")
@ApiResponse(responseCode = "400", description = "Malformed JSON or failed validation",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "422", description = "The standards fail a QC rule of ERROR severity",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class CalibrationController {

    private final CalibrationService service;

    public CalibrationController(CalibrationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Fit a calibration curve",
            description = "Ordinary least-squares fit with slope/intercept uncertainty, R², LOD, LOQ, residuals and QC flags.")
    @ApiResponse(responseCode = "200", description = "Fitted calibration")
    public CalibrationResponse calibrate(@Valid @RequestBody CalibrationRequest request) {
        return CalibrationResponse.from(request.analyte(), fit(request));
    }

    @PostMapping("/quantify")
    @Operation(summary = "Quantify unknown samples",
            description = "Fits the standards, then converts each unknown's mean response into a concentration with a 95% CI.")
    @ApiResponse(responseCode = "200", description = "Calibration and per-sample results")
    public QuantifyResponse quantify(@Valid @RequestBody QuantifyRequest request) {
        Calibration calibration = fit(request);
        var results = service.quantify(calibration, request.unknowns().stream().map(UnknownDto::toSample).toList())
                .stream()
                .map(QuantifyResponse.Result::from)
                .toList();
        return new QuantifyResponse(CalibrationResponse.from(request.analyte(), calibration), results);
    }

    private Calibration fit(StandardsInput input) {
        return service.calibrate(input.concentrations(), input.responses(),
                input.concentrationUnit(), input.minRSquared());
    }
}
