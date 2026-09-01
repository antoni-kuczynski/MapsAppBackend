package com.antekk.maps.exception;

/**
 * Thrown during startup when the routing engine cannot be initialised, e.g. the
 * OpenStreetMap extract is missing or the configured profiles are not supported.
 */
public class GraphHopperConfigurationException extends IllegalStateException {
    public GraphHopperConfigurationException(String message) {
        super(message);
    }
}
