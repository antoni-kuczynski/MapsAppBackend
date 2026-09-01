package com.antekk.maps.exception;

import com.antekk.maps.dto.ApiErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RoutingException.class)
    public ResponseEntity<ApiErrorDto> handleRoutingException(RoutingException exception, HttpServletRequest request) {
        return toResponse(ApiErrorDto.of(exception.code(), exception.getMessage(), path(request), exception.details()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorDto> handleMissingParameter(MissingServletRequestParameterException exception,
                                                              HttpServletRequest request) {
        return toResponse(ApiErrorDto.of(
                ApiErrorCode.MISSING_PARAMETER,
                "Missing required request parameter '" + exception.getParameterName() + "'",
                path(request),
                Map.of("parameter", exception.getParameterName())
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorDto> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                          HttpServletRequest request) {
        String expectedType = exception.getRequiredType() == null ? "unknown" : exception.getRequiredType().getSimpleName();
        return toResponse(ApiErrorDto.of(
                ApiErrorCode.INVALID_PARAMETER,
                "Parameter '" + exception.getName() + "' has an invalid value: '" + exception.getValue() + "'",
                path(request),
                Map.of("parameter", exception.getName(), "expectedType", expectedType)
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        // Spring MVC failures such as an unsupported HTTP method already carry a meaningful status.
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            return ResponseEntity.status(status).body(new ApiErrorDto(
                    Instant.now(),
                    status.value(),
                    resolved == null ? "" : resolved.getReasonPhrase(),
                    ApiErrorCode.REQUEST_REJECTED.name(),
                    exception.getMessage(),
                    path(request),
                    Map.of()
            ));
        }
        log.error("Unhandled exception while processing {}", path(request), exception);
        return toResponse(ApiErrorDto.of(
                ApiErrorCode.INTERNAL_ERROR,
                "Unexpected server error",
                path(request)
        ));
    }

    private static ResponseEntity<ApiErrorDto> toResponse(ApiErrorDto error) {
        return ResponseEntity.status(error.status()).body(error);
    }

    private static String path(HttpServletRequest request) {
        return request == null ? null : request.getRequestURI();
    }
}
