package com.antekk.maps.exception;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    /** Coordinate outside the valid latitude/longitude range, or not a finite number. */
    INVALID_COORDINATES(HttpStatus.BAD_REQUEST),
    /** Requested routing profile is not configured on the server. */
    UNKNOWN_PROFILE(HttpStatus.BAD_REQUEST),
    /** Point lies outside the area covered by the imported OpenStreetMap extract. */
    POINT_OUT_OF_BOUNDS(HttpStatus.BAD_REQUEST),
    /** Point is inside the covered area but has no routable road nearby. */
    POINT_NOT_FOUND(HttpStatus.BAD_REQUEST),
    /** Both points are routable but no connection between them exists. */
    NO_ROUTE_FOUND(HttpStatus.NOT_FOUND),
    /** Routing engine rejected the request for a reason without a more specific code. */
    ROUTING_FAILED(HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    /** Request rejected by the web layer, e.g. unsupported HTTP method or unknown path. */
    REQUEST_REJECTED(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
