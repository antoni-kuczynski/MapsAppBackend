package com.antekk.maps.dto;

import com.antekk.maps.exception.ApiErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorDto(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, Object> details
) {
    public static ApiErrorDto of(ApiErrorCode code, String message, String path, Map<String, Object> details) {
        return new ApiErrorDto(
                Instant.now(),
                code.status().value(),
                code.status().getReasonPhrase(),
                code.name(),
                message,
                path,
                details == null ? Map.of() : details
        );
    }

    public static ApiErrorDto of(ApiErrorCode code, String message, String path) {
        return of(code, message, path, Map.of());
    }
}
