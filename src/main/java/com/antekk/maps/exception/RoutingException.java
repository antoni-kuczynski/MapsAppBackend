package com.antekk.maps.exception;

import java.util.Map;

public class RoutingException extends RuntimeException {
    private final ApiErrorCode code;
    private final Map<String, Object> details;

    public RoutingException(ApiErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public RoutingException(ApiErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ApiErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
