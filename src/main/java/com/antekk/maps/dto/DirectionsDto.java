package com.antekk.maps.dto;

import java.util.List;

public record DirectionsDto(
        String profile,
        PointDto start,
        PointDto end,
        List<RouteDto> routes
) {
    public record RouteDto(
            double distanceMeters,
            long durationMillis,
            double ascendMeters,
            double descendMeters,
            List<PointDto> points,
            List<InstructionDto> instructions
    ) {
    }

    public record InstructionDto(
            String text,
            String streetName,
            int sign,
            double distanceMeters,
            long durationMillis,
            PointDto point
    ) {
    }
}
