package dev.skergan.calcurve;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "CalCurve API",
        version = "v1",
        description = "Fits analytical calibration curves (OLS), reports LOD/LOQ and QC flags, "
                + "and quantifies unknown samples with 95% confidence intervals."))
public class CalCurveApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalCurveApplication.class, args);
    }
}
