package dev.skergan.calcurve.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import dev.skergan.calcurve.service.QcException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps failures to RFC 7807 {@link ProblemDetail} responses. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String PROBLEM_BASE = "https://calcurve.dev/problems/";

    record FieldError(String field, String message) {
    }

    @ExceptionHandler(QcException.class)
    ProblemDetail handleQc(QcException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.primary().message());
        problem.setType(URI.create(PROBLEM_BASE + e.primary().code().slug()));
        problem.setTitle("Calibration failed QC");
        problem.setProperty("flags", e.flags());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String detail = errors.stream()
                .map(err -> err.field() + " " + err.message())
                .collect(Collectors.joining("; "));
        return badRequest("validation-error", "Validation failed", detail, errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        String field = "body";
        String message = "Request body is missing or is not valid JSON";
        if (ex.getCause() instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            field = jsonPath(jme.getPath());
            message = "has an invalid value";
        }
        return badRequest("malformed-request", "Malformed request", message,
                List.of(new FieldError(field, message)));
    }

    /** Renders a Jackson path in the same style as bean validation, e.g. {@code standards[2].concentration}. */
    private static String jsonPath(List<JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : path) {
            if (ref.getFieldName() != null) {
                sb.append(sb.isEmpty() ? "" : ".").append(ref.getFieldName());
            } else {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private static ResponseEntity<Object> badRequest(String slug, String title, String detail, List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create(PROBLEM_BASE + slug));
        problem.setTitle(title);
        problem.setProperties(Map.of("errors", errors));
        return ResponseEntity.badRequest().body(problem);
    }
}
