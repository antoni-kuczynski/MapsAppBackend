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
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectionsServiceTest {
    private static final double START_LAT = 52.20876379685948;
    private static final double START_LNG = 21.010322570800785;
    private static final double END_LAT = 52.265846190602694;
    private static final double END_LNG = 20.912647247314457;

    private final GraphHopper graphHopper = mock(GraphHopper.class);
    private DirectionsService service;

    @BeforeEach
    void setUp() {
        TranslationMap translationMap = new TranslationMap().doImport();
        when(graphHopper.getTranslationMap()).thenReturn(translationMap);
        service = new DirectionsService(graphHopper, properties(List.of("car", "foot")));
    }

    private static GraphHopperProperties properties(List<String> profiles) {
        return new GraphHopperProperties("any.osm.pbf", "any-cache", profiles, 3);
    }

    private DirectionsDto route() {
        return service.findRoutes(START_LAT, START_LNG, END_LAT, END_LNG, "car", false, Locale.ENGLISH);
    }

    private static ResponsePath samplePath() {
        PointList points = new PointList();
        points.add(START_LAT, START_LNG);
        points.add(52.25, 20.95);
        points.add(END_LAT, END_LNG);

        PointList instructionPoints = new PointList();
        instructionPoints.add(START_LAT, START_LNG);
        InstructionList instructions = new InstructionList(null);
        instructions.add(new Instruction(Instruction.TURN_RIGHT, "Rakowiecka", instructionPoints)
                .setDistance(150.0)
                .setTime(30_000L));

        ResponsePath path = new ResponsePath()
                .setDistance(11_723.0)
                .setAscend(12.5)
                .setDescend(7.5)
                .setPoints(points);
        path.setTime(1_027_000L);
        path.setInstructions(instructions);
        return path;
    }

    private void stubRouteResponse(ResponsePath... paths) {
        GHResponse response = new GHResponse();
        for (ResponsePath path : paths) {
            response.add(path);
        }
        when(graphHopper.route(any(GHRequest.class))).thenReturn(response);
    }

    private void stubRouteError(Throwable error) {
        when(graphHopper.route(any(GHRequest.class))).thenReturn(new GHResponse().addError(error));
    }

    // --- successful routing ---

    @Test
    void mapsGraphHopperPathToResponseDto() {
        stubRouteResponse(samplePath());

        DirectionsDto result = route();

        assertThat(result.profile()).isEqualTo("car");
        assertThat(result.start()).isEqualTo(new PointDto(START_LAT, START_LNG));
        assertThat(result.end()).isEqualTo(new PointDto(END_LAT, END_LNG));
        assertThat(result.routes()).hasSize(1);

        DirectionsDto.RouteDto firstRoute = result.routes().getFirst();
        assertThat(firstRoute.distanceMeters()).isEqualTo(11_723.0);
        assertThat(firstRoute.durationMillis()).isEqualTo(1_027_000L);
        assertThat(firstRoute.ascendMeters()).isEqualTo(12.5);
        assertThat(firstRoute.descendMeters()).isEqualTo(7.5);
        assertThat(firstRoute.points()).containsExactly(
                new PointDto(START_LAT, START_LNG),
                new PointDto(52.25, 20.95),
                new PointDto(END_LAT, END_LNG)
        );
    }

    @Test
    void mapsInstructionsIncludingTurnDescriptionAndLocation() {
        stubRouteResponse(samplePath());

        DirectionsDto.InstructionDto instruction = route().routes().getFirst().instructions().getFirst();

        assertThat(instruction.text()).isEqualTo("turn right onto Rakowiecka");
        assertThat(instruction.streetName()).isEqualTo("Rakowiecka");
        assertThat(instruction.sign()).isEqualTo(Instruction.TURN_RIGHT);
        assertThat(instruction.distanceMeters()).isEqualTo(150.0);
        assertThat(instruction.durationMillis()).isEqualTo(30_000L);
        assertThat(instruction.point()).isEqualTo(new PointDto(START_LAT, START_LNG));
    }

    @Test
    void returnsEmptyRouteListWhenEngineFoundNoPaths() {
        stubRouteResponse();

        assertThat(route().routes()).isEmpty();
    }

    @Test
    void returnsAllAlternativePathsWhenAlternativesRequested() {
        stubRouteResponse(samplePath(), samplePath(), samplePath());

        DirectionsDto result = service.findRoutes(START_LAT, START_LNG, END_LAT, END_LNG, "car", true, Locale.ENGLISH);

        assertThat(result.routes()).hasSize(3);
    }

    // --- request building ---

    @Test
    void buildsRequestWithoutAlternativeAlgorithmByDefault() {
        stubRouteResponse(samplePath());

        route();

        GHRequest request = captureRequest();
        assertThat(request.getProfile()).isEqualTo("car");
        assertThat(request.getAlgorithm()).isNotEqualTo(Parameters.Algorithms.ALT_ROUTE);
        assertThat(request.getPoints()).hasSize(2);
        assertThat(request.getPoints().getFirst().getLat()).isEqualTo(START_LAT);
        assertThat(request.getPoints().getFirst().getLon()).isEqualTo(START_LNG);
        assertThat(request.getPoints().getLast().getLat()).isEqualTo(END_LAT);
        assertThat(request.getPoints().getLast().getLon()).isEqualTo(END_LNG);
    }

    @Test
    void buildsRequestWithAlternativeAlgorithmAndConfiguredPathLimit() {
        service = new DirectionsService(graphHopper, new GraphHopperProperties("any.osm.pbf", "cache", List.of("car"), 5));
        stubRouteResponse(samplePath());

        service.findRoutes(START_LAT, START_LNG, END_LAT, END_LNG, "car", true, Locale.ENGLISH);

        GHRequest request = captureRequest();
        assertThat(request.getAlgorithm()).isEqualTo(Parameters.Algorithms.ALT_ROUTE);
        assertThat(request.getHints().getInt(Parameters.Algorithms.AltRoute.MAX_PATHS, 0)).isEqualTo(5);
    }

    @Test
    void passesRequestedLocaleToEngine() {
        stubRouteResponse(samplePath());

        service.findRoutes(START_LAT, START_LNG, END_LAT, END_LNG, "car", false, Locale.of("pl"));

        assertThat(captureRequest().getLocale().getLanguage()).isEqualTo("pl");
    }

    private GHRequest captureRequest() {
        ArgumentCaptor<GHRequest> captor = ArgumentCaptor.forClass(GHRequest.class);
        verify(graphHopper).route(captor.capture());
        return captor.getValue();
    }

    // --- invalid coordinates ---

    @ParameterizedTest(name = "lat={0}, lng={1} -> invalid {2}")
    @CsvSource({
            "90.1, 21.0, startLat",
            "-90.1, 21.0, startLat",
            "52.2, 180.1, startLng",
            "52.2, -180.1, startLng",
            "NaN, 21.0, startLat",
            "52.2, NaN, startLng",
            "Infinity, 21.0, startLat",
            "-Infinity, 21.0, startLat"
    })
    void rejectsInvalidStartCoordinates(double lat, double lng, String expectedParameter) {
        assertThatThrownBy(() -> service.findRoutes(lat, lng, END_LAT, END_LNG, "car", false, Locale.ENGLISH))
                .asInstanceOf(type(RoutingException.class))
                .satisfies(exception -> {
                    assertThat(exception.code()).isEqualTo(ApiErrorCode.INVALID_COORDINATES);
                    assertThat(exception.details()).containsEntry("parameter", expectedParameter);
                });
    }

    @ParameterizedTest(name = "lat={0}, lng={1} -> invalid {2}")
    @CsvSource({
            "91.0, 21.0, endLat",
            "52.2, 181.0, endLng",
            "NaN, 21.0, endLat"
    })
    void rejectsInvalidEndCoordinates(double lat, double lng, String expectedParameter) {
        assertThatThrownBy(() -> service.findRoutes(START_LAT, START_LNG, lat, lng, "car", false, Locale.ENGLISH))
                .asInstanceOf(type(RoutingException.class))
                .satisfies(exception -> {
                    assertThat(exception.code()).isEqualTo(ApiErrorCode.INVALID_COORDINATES);
                    assertThat(exception.details()).containsEntry("parameter", expectedParameter);
                });
    }

    @Test
    void acceptsCoordinatesExactlyOnTheAllowedBoundary() {
        stubRouteResponse(samplePath());

        assertThat(service.findRoutes(90.0, 180.0, -90.0, -180.0, "car", false, Locale.ENGLISH)).isNotNull();
    }

    @Test
    void doesNotCallEngineWhenCoordinatesAreInvalid() {
        assertThatThrownBy(() -> service.findRoutes(120.0, 21.0, END_LAT, END_LNG, "car", false, Locale.ENGLISH))
                .isInstanceOf(RoutingException.class);

        verify(graphHopper, never()).route(any(GHRequest.class));
    }

    // --- profiles ---

    @Test
    void rejectsUnknownProfileWithListOfAvailableProfiles() {
        assertThatThrownBy(() -> service.findRoutes(START_LAT, START_LNG, END_LAT, END_LNG, "bike", false, Locale.ENGLISH))
                .asInstanceOf(type(RoutingException.class))
                .satisfies(exception -> {
                    assertThat(exception.code()).isEqualTo(ApiErrorCode.UNKNOWN_PROFILE);
                    assertThat(exception.getMessage()).contains("bike");
                    assertThat(exception.details())
                            .containsEntry("requested", "bike")
                            .containsEntry("available", List.of("car", "foot"));
                });

        verify(graphHopper, never()).route(any(GHRequest.class));
    }

    @Test
    void acceptsEveryConfiguredProfile() {
        stubRouteResponse(samplePath());

        DirectionsDto result = service.findRoutes(START_LAT, START_LNG, END_LAT, END_LNG, "foot", false, Locale.ENGLISH);

        assertThat(result.profile()).isEqualTo("foot");
    }

    // --- engine errors ---

    @Test
    void mapsPointOutOfBoundsToDedicatedErrorCode() {
        stubRouteError(new PointOutOfBoundsException(
                "Point 0 is out of bounds: 40.7,-74.0, the bounds are: 19.19,23.16,50.96,53.51", 0));

        assertThatThrownBy(this::route)
                .asInstanceOf(type(RoutingException.class))
                .satisfies(exception -> {
                    assertThat(exception.code()).isEqualTo(ApiErrorCode.POINT_OUT_OF_BOUNDS);
                    assertThat(exception.code().status().value()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("out of bounds");
                    assertThat(exception.details()).containsEntry(PointNotFoundException.INDEX_KEY, 0);
                });
    }

    @Test
    void mapsPointNotFoundToDedicatedErrorCode() {
        stubRouteError(new PointNotFoundException("Cannot find point 1: 52.0,21.0", 1));

        assertThatThrownBy(this::route)
                .asInstanceOf(type(RoutingException.class))
                .satisfies(exception -> {
                    assertThat(exception.code()).isEqualTo(ApiErrorCode.POINT_NOT_FOUND);
                    assertThat(exception.details()).containsEntry(PointNotFoundException.INDEX_KEY, 1);
                });
    }

    @Test
    void mapsMissingConnectionToNotFoundErrorCode() {
        stubRouteError(new ConnectionNotFoundException("Connection between locations not found", Map.of("from", 0)));

        assertThatThrownBy(this::route)
                .asInstanceOf(type(RoutingException.class))
                .satisfies(exception -> {
                    assertThat(exception.code()).isEqualTo(ApiErrorCode.NO_ROUTE_FOUND);
                    assertThat(exception.code().status().value()).isEqualTo(404);
                    assertThat(exception.details()).containsEntry("from", 0);
                });
    }

    @Test
    void mapsUnrecognisedEngineErrorToGenericRoutingFailure() {
        stubRouteError(new IllegalArgumentException("Profile 'car' not found"));

        assertThatThrownBy(this::route)
                .asInstanceOf(type(RoutingException.class))
                .satisfies(exception -> {
                    assertThat(exception.code()).isEqualTo(ApiErrorCode.ROUTING_FAILED);
                    assertThat(exception.getMessage()).isEqualTo("Profile 'car' not found");
                    assertThat(exception.details()).isEmpty();
                });
    }

    @Test
    void reportsFirstErrorWhenEngineReturnsSeveral() {
        GHResponse response = new GHResponse();
        response.addError(new PointOutOfBoundsException("first", 0));
        response.addError(new IllegalArgumentException("second"));
        when(graphHopper.route(any(GHRequest.class))).thenReturn(response);

        assertThatThrownBy(this::route)
                .asInstanceOf(type(RoutingException.class))
                .satisfies(exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.POINT_OUT_OF_BOUNDS));
    }
}
