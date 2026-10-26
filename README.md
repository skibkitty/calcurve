# CalCurve

CalCurve is a Java 21 / Spring Boot 3 REST API that fits analytical calibration curves and converts unknown instrument readings into concentrations. Post your standards (known concentration → response) and get back the least-squares line with slope and intercept uncertainties, R², residual standard deviation, LOD/LOQ, residuals and automated QC flags. Post unknown samples alongside the standards and each comes back as a concentration with its standard deviation, a 95% confidence interval (Student's t), and flags for below-LOD/LOQ or extrapolated results.

[![CI](https://github.com/skibkitty/calcurve/actions/workflows/ci.yml/badge.svg)](https://github.com/skibkitty/calcurve/actions/workflows/ci.yml)

## Running it

Requires JDK 21+.

```bash
mvn spring-boot:run        # or ./mvnw spring-boot:run if Maven isn't installed
```

The API listens on port 8080; set `SERVER_PORT` to use another (e.g. `SERVER_PORT=8095 mvn spring-boot:run`). Interactive docs are at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`.

With Docker:

```bash
docker build -t calcurve .
docker run --rm -p 8080:8080 calcurve
```

Run the tests with `mvn verify`.

## API

Base path `/api/v1`. All bodies are JSON. Numbers in responses are rounded to 6 significant figures; all math is done in full double precision.

### `POST /api/v1/calibrations` — fit a calibration curve

```bash
curl -s http://localhost:8080/api/v1/calibrations \
  -H 'Content-Type: application/json' \
  -d '{
    "analyte": "Fluorescein",
    "concentrationUnit": "pg/mL",
    "responseUnit": "fluorescence",
    "standards": [
      {"concentration": 0,  "response": 2.1},
      {"concentration": 2,  "response": 5.0},
      {"concentration": 4,  "response": 9.0},
      {"concentration": 6,  "response": 12.6},
      {"concentration": 8,  "response": 17.3},
      {"concentration": 10, "response": 21.0},
      {"concentration": 12, "response": 24.7}
    ],
    "minRSquared": 0.995
  }'
```

```json
{
  "analyte": "Fluorescein",
  "n": 7,
  "slope": 1.93036, "slopeStdError": 0.0409003,
  "intercept": 1.51786, "interceptStdError": 0.294936,
  "rSquared": 0.99776,
  "residualStdDev": 0.432848,
  "lod": 0.739965, "loq": 2.24232,
  "concentrationUnit": "pg/mL",
  "calibratedRange": {"min": 0, "max": 12},
  "residuals": [0.582143, -0.378571, -0.239286, -0.5, 0.339286, 0.178571, 0.0178571],
  "flags": []
}
```

### `POST /api/v1/calibrations/quantify` — quantify unknowns

```bash
curl -s http://localhost:8080/api/v1/calibrations/quantify \
  -H 'Content-Type: application/json' \
  -d '{
    "analyte": "Fluorescein",
    "concentrationUnit": "pg/mL",
    "standards": [
      {"concentration": 0,  "response": 2.1},
      {"concentration": 2,  "response": 5.0},
      {"concentration": 4,  "response": 9.0},
      {"concentration": 6,  "response": 12.6},
      {"concentration": 8,  "response": 17.3},
      {"concentration": 10, "response": 21.0},
      {"concentration": 12, "response": 24.7}
    ],
    "unknowns": [
      {"sampleId": "S-01", "responses": [2.9]},
      {"sampleId": "S-02", "responses": [13.5]},
      {"sampleId": "S-03", "responses": [23.0]},
      {"sampleId": "S-04", "responses": [13.5, 13.3, 13.7]}
    ]
  }'
```

The response holds the full `calibration` object shown above, plus:

```json
"results": [
  {"sampleId": "S-01", "replicates": 1, "meanResponse": 2.9,  "concentration": 0.716004, "stdDev": 0.26457,  "ci95": 0.680098,
   "flags": [{"code": "BELOW_LOD", "severity": "WARNING", "message": "0.716 pg/mL is below the LOD of 0.740 pg/mL"}]},
  {"sampleId": "S-02", "replicates": 1, "meanResponse": 13.5, "concentration": 6.20722,  "stdDev": 0.239754, "ci95": 0.616308, "flags": []},
  {"sampleId": "S-03", "replicates": 1, "meanResponse": 23,   "concentration": 11.1286,  "stdDev": 0.263193, "ci95": 0.67656,  "flags": []},
  {"sampleId": "S-04", "replicates": 3, "meanResponse": 13.5, "concentration": 6.20722,  "stdDev": 0.154797, "ci95": 0.397918, "flags": []}
]
```

## How it's validated

The statistics are checked in JUnit 5 tests against independently computed reference values for a 7-point fluorescein dataset (the one used in the examples above): slope, intercept, their standard errors, R², s_y/x, LOD/LOQ, residuals, back-calculated concentrations and 95% CI half-widths, plus the Student's t critical value against tabulated values. The reference values were generated with NumPy/SciPy, not by the code under test. Spring Boot MockMvc tests drive the HTTP layer end to end: the example requests above, 6-significant-figure rounding, and the 400/422 problem responses. All 26 tests run on every push via GitHub Actions (`mvn verify`).

## Error handling

All errors are [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) `application/problem+json` bodies.

| Status | When | Extra property |
| --- | --- | --- |
| 400 | Malformed JSON, or a field fails validation (`standards` missing or empty, negative or non-finite concentration, blank or >64-char `sampleId`, empty `responses`) | `errors`: `[{field, message}]` |
| 422 | A QC rule of ERROR severity fails, or all concentrations are identical | `flags`; `type` is `https://calcurve.dev/problems/<code>`, e.g. `.../too-few-standards` |

```bash
curl -s http://localhost:8080/api/v1/calibrations \
  -H 'Content-Type: application/json' \
  -d '{"analyte": "Fluorescein",
       "standards": [{"concentration": 0, "response": 2.1},
                     {"concentration": 2, "response": 5.0}]}'
```

```json
{
  "type": "https://calcurve.dev/problems/too-few-standards",
  "title": "Calibration failed QC",
  "status": 422,
  "detail": "At least 3 standards are required, got 2",
  "instance": "/api/v1/calibrations",
  "flags": [{"code": "TOO_FEW_STANDARDS", "severity": "ERROR", "message": "At least 3 standards are required, got 2"}]
}
```

## Math

With n standards (xᵢ, yᵢ), x̄ and ȳ their means, Sxx = Σ(xᵢ − x̄)², Sxy = Σ(xᵢ − x̄)(yᵢ − ȳ):

| Quantity | Formula |
| --- | --- |
| Slope b | Sxy / Sxx |
| Intercept a | ȳ − b·x̄ |
| Residual eᵢ | yᵢ − (a + b·xᵢ) |
| Residual SD s_y/x | √( Σeᵢ² / (n − 2) ) |
| SE of slope s_b | s_y/x / √Sxx |
| SE of intercept s_a | s_y/x · √( Σxᵢ² / (n·Sxx) ) |
| R² | 1 − Σeᵢ² / Σ(yᵢ − ȳ)² |
| LOD | 3.3 · s_y/x / b |
| LOQ | 10 · s_y/x / b |

Quantifying an unknown from m replicate responses with mean y₀:

- x₀ = (y₀ − a) / b
- s_x0 = (s_y/x / b) · √( 1/m + 1/n + (y₀ − ȳ)² / (b² · Sxx) )
- 95% CI half-width = t(0.975, n − 2) · s_x0

## QC rules

| Code | Severity | Condition |
| --- | --- | --- |
| `TOO_FEW_STANDARDS` | ERROR (HTTP 422) | n < 3 |
| `DEGENERATE_STANDARDS` | ERROR (HTTP 422) | All concentrations identical (Sxx = 0) |
| `FEW_STANDARDS` | WARNING | 3 ≤ n < 5 |
| `LOW_LINEARITY` | WARNING | R² < 0.995 (override per request with `minRSquared`) |
| `RESIDUAL_OUTLIER` | WARNING | \|eᵢ\| > 2·s_y/x; the message names the standard's index and concentration |
| `NON_POSITIVE_SLOPE` | ERROR (HTTP 422) | b ≤ 0 |
| `BELOW_LOQ` | WARNING (per unknown) | x₀ < LOQ |
| `BELOW_LOD` | WARNING (per unknown) | x₀ < LOD; replaces `BELOW_LOQ` |
| `EXTRAPOLATED` | WARNING (per unknown) | x₀ < min(xᵢ) or x₀ > max(xᵢ) |

With 7 or fewer standards a single outlier cannot exceed 2·s_y/x, because the outlier inflates s_y/x itself; the rule becomes useful from about 8 standards up.

## Design decisions

- **Stateless by design.** No database and no session: every request carries its standards and gets a complete answer. The service scales horizontally and there is no stored data to secure or migrate.
- **Pure-math core, separate from Spring.** `Regression` is a plain final class with static methods and no framework imports, so the statistics are unit-tested directly against reference values. `CalibrationService` layers the QC rules on top, and the controller only maps DTOs.
- **Precision at the edge.** All arithmetic uses `double`; rounding to 6 significant figures happens only in a Jackson serializer, so intermediate results never lose precision.
- **ProblemDetail errors.** Validation failures (400) and QC failures (422) use RFC 7807 bodies with a stable `type` URI per error code, so clients can branch on the code rather than parse messages.
- **LOD/LOQ convention.** LOD = 3.3σ/slope and LOQ = 10σ/slope (ICH Q2), with σ taken as the residual standard deviation of the calibration, s_y/x.
