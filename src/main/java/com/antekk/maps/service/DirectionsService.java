package com.antekk.maps.service;

import com.antekk.maps.config.GraphHopperProperties;
import com.antekk.maps.dto.DirectionsDto;
import com.antekk.maps.dto.PointDto;
import com.antekk.maps.exception.ApiErrorCode;
import com.antekk.maps.exception.RoutingException;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.Translation;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.GHException;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DirectionsService {
    private final GraphHopper graphHopper;
    private final List<String> availableProfiles;
    private final int maxAlternativeRoutes;

    public DirectionsService(GraphHopper graphHopper, GraphHopperProperties properties) {
        this.graphHopper = graphHopper;
        this.availableProfiles = properties.profiles();
        this.maxAlternativeRoutes = properties.maxAlternativeRoutes();
    }

    public DirectionsDto findRoutes(double startLat, double startLng, double endLat, double endLng,
                                    String profile, boolean alternatives, Locale locale) {
        validateCoordinate(startLat, startLng, "start");
        validateCoordinate(endLat, endLng, "end");
        if (!availableProfiles.contains(profile)) {
            throw new RoutingException(
                    ApiErrorCode.UNKNOWN_PROFILE,
                    "Unknown profile '" + profile + "'",
                    Map.of("requested", profile, "available", availableProfiles)
            );
        }

        GHRequest request = new GHRequest(startLat, startLng, endLat, endLng)
                .setProfile(profile)
                .setLocale(locale);
        if (alternatives) {
            request.setAlgorithm(Parameters.Algorithms.ALT_ROUTE);
            request.getHints().putObject(Parameters.Algorithms.AltRoute.MAX_PATHS, maxAlternativeRoutes);
        }

        GHResponse response = graphHopper.route(request);
        if (response.hasErrors()) {
            throw toRoutingException(response.getErrors().getFirst());
        }

        Translation translation = graphHopper.getTranslationMap().getWithFallBack(locale);
        List<DirectionsDto.RouteDto> routes = response.getAll().stream()
                .map(path -> toRoute(path, translation))
                .toList();
        return new DirectionsDto(profile, new PointDto(startLat, startLng), new PointDto(endLat, endLng), routes);
    }

    private static DirectionsDto.RouteDto toRoute(ResponsePath path, Translation translation) {
        return new DirectionsDto.RouteDto(
                path.getDistance(),
                path.getTime(),
                path.getAscend(),
                path.getDescend(),
                toPoints(path.getPoints()),
                path.getInstructions().stream().map(instruction -> toInstruction(instruction, translation)).toList()
        );
    }

    private static DirectionsDto.InstructionDto toInstruction(Instruction instruction, Translation translation) {
        PointList points = instruction.getPoints();
        PointDto point = points.isEmpty() ? null : new PointDto(points.getLat(0), points.getLon(0));
        return new DirectionsDto.InstructionDto(
                instruction.getTurnDescription(translation),
                instruction.getName(),
                instruction.getSign(),
                instruction.getDistance(),
                instruction.getTime(),
                point
        );
    }

    private static List<PointDto> toPoints(PointList pointList) {
        List<PointDto> points = new ArrayList<>(pointList.size());
        for (int i = 0; i < pointList.size(); i++) {
            points.add(new PointDto(pointList.getLat(i), pointList.getLon(i)));
        }
        return points;
    }

    /**
     * Translates a GraphHopper failure into an API error code
     */
    private static RoutingException toRoutingException(Throwable error) {
        ApiErrorCode code = switch (error) {
            case PointOutOfBoundsException ignored -> ApiErrorCode.POINT_OUT_OF_BOUNDS;
            case PointNotFoundException ignored -> ApiErrorCode.POINT_NOT_FOUND;
            case ConnectionNotFoundException ignored -> ApiErrorCode.NO_ROUTE_FOUND;
            default -> ApiErrorCode.ROUTING_FAILED;
        };
        Map<String, Object> details = error instanceof GHException ghException && ghException.getDetails() != null
                ? Map.copyOf(ghException.getDetails())
                : Map.of();
        return new RoutingException(code, error.getMessage(), details);
    }

    private static void validateCoordinate(double lat, double lng, String name) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new RoutingException(
                    ApiErrorCode.INVALID_COORDINATES,
                    name + " latitude must be a number between -90 and 90, was " + lat,
                    Map.of("parameter", name + "Lat", "value", String.valueOf(lat))
            );
        }
        if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new RoutingException(
                    ApiErrorCode.INVALID_COORDINATES,
                    name + " longitude must be a number between -180 and 180, was " + lng,
                    Map.of("parameter", name + "Lng", "value", String.valueOf(lng))
            );
        }
    }
}
