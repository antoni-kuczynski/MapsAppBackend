package com.antekk.maps.controller;

import com.antekk.maps.dto.DirectionsDto;
import com.antekk.maps.dto.PointDto;
import com.antekk.maps.exception.ApiErrorCode;
import com.antekk.maps.exception.ApiExceptionHandler;
import com.antekk.maps.exception.RoutingException;
import com.antekk.maps.service.DirectionsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DirectionsControllerTest {
    private static final String URL = "/api/directions";
    private static final String QUERY = "?startLat=52.20876379685948&startLng=21.010322570800785"
            + "&endLat=52.265846190602694&endLng=20.912647247314457";

    private final DirectionsService directionsService = mock(DirectionsService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DirectionsController(directionsService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private void stubSuccess() {
        DirectionsDto response = new DirectionsDto(
                "car",
                new PointDto(52.20876379685948, 21.010322570800785),
                new PointDto(52.265846190602694, 20.912647247314457),
                List.of(new DirectionsDto.RouteDto(
                        11_723.0,
                        1_027_000L,
                        0.0,
                        0.0,
                        List.of(new PointDto(52.2081887, 21.0102968), new PointDto(52.2079179, 21.0104243)),
                        List.of(new DirectionsDto.InstructionDto(
                                "skręć w prawo na Rakowiecka", "Rakowiecka", 2, 150.0, 30_000L,
                                new PointDto(52.2081887, 21.0102968)))
                ))
        );
        when(directionsService.findRoutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyString(), anyBoolean(), any(Locale.class))).thenReturn(response);
    }

    private void stubFailure(RoutingException exception) {
        when(directionsService.findRoutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyString(), anyBoolean(), any(Locale.class))).thenThrow(exception);
    }

    // --- successful response ---

    @Test
    void returnsRoutesAsJson() throws Exception {
        stubSuccess();

        mockMvc.perform(get(URL + QUERY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.profile").value("car"))
                .andExpect(jsonPath("$.start.lat").value(52.20876379685948))
                .andExpect(jsonPath("$.start.lng").value(21.010322570800785))
                .andExpect(jsonPath("$.end.lat").value(52.265846190602694))
                .andExpect(jsonPath("$.routes.length()").value(1))
                .andExpect(jsonPath("$.routes[0].distanceMeters").value(11_723.0))
                .andExpect(jsonPath("$.routes[0].durationMillis").value(1_027_000L))
                .andExpect(jsonPath("$.routes[0].points.length()").value(2))
                .andExpect(jsonPath("$.routes[0].points[0].lat").value(52.2081887))
                .andExpect(jsonPath("$.routes[0].instructions[0].text").value("skręć w prawo na Rakowiecka"))
                .andExpect(jsonPath("$.routes[0].instructions[0].streetName").value("Rakowiecka"))
                .andExpect(jsonPath("$.routes[0].instructions[0].sign").value(2));
    }

    @Test
    void appliesDefaultProfileAlternativesAndLanguage() throws Exception {
        stubSuccess();

        mockMvc.perform(get(URL + QUERY)).andExpect(status().isOk());

        verify(directionsService).findRoutes(
                eq(52.20876379685948), eq(21.010322570800785),
                eq(52.265846190602694), eq(20.912647247314457),
                eq("car"), eq(false), eq(Locale.forLanguageTag("pl")));
    }

    @Test
    void forwardsExplicitProfileAlternativesAndLanguage() throws Exception {
        stubSuccess();

        mockMvc.perform(get(URL + QUERY + "&profile=foot&alternatives=true&lang=en"))
                .andExpect(status().isOk());

        ArgumentCaptor<Locale> locale = ArgumentCaptor.forClass(Locale.class);
        verify(directionsService).findRoutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq("foot"), eq(true), locale.capture());
        assertThat(locale.getValue().getLanguage()).isEqualTo("en");
    }

    // --- error envelope ---

    @Test
    void returnsErrorEnvelopeWithCodeMessageAndDetails() throws Exception {
        stubFailure(new RoutingException(
                ApiErrorCode.POINT_OUT_OF_BOUNDS,
                "Point 0 is out of bounds: 40.7,-74.0",
                Map.of("index", 0)));

        mockMvc.perform(get(URL + QUERY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("POINT_OUT_OF_BOUNDS"))
                .andExpect(jsonPath("$.message").value("Point 0 is out of bounds: 40.7,-74.0"))
                .andExpect(jsonPath("$.path").value(URL))
                .andExpect(jsonPath("$.details.index").value(0))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void usesNotFoundStatusWhenNoRouteExists() throws Exception {
        stubFailure(new RoutingException(ApiErrorCode.NO_ROUTE_FOUND, "Connection between locations not found"));

        mockMvc.perform(get(URL + QUERY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NO_ROUTE_FOUND"));
    }

    @Test
    void omitsDetailsFieldWhenThereAreNoDetails() throws Exception {
        stubFailure(new RoutingException(ApiErrorCode.ROUTING_FAILED, "Routing failed"));

        mockMvc.perform(get(URL + QUERY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void reportsInvalidCoordinatesFromService() throws Exception {
        stubFailure(new RoutingException(
                ApiErrorCode.INVALID_COORDINATES,
                "start latitude must be a number between -90 and 90, was 120.0",
                Map.of("parameter", "startLat", "value", "120.0")));

        mockMvc.perform(get(URL + "?startLat=120.0&startLng=21.0&endLat=52.2&endLng=20.9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"))
                .andExpect(jsonPath("$.details.parameter").value("startLat"));
    }

    // --- request validation by the web layer ---

    @Test
    void returnsMissingParameterErrorWhenCoordinateIsAbsent() throws Exception {
        mockMvc.perform(get(URL + "?startLat=52.2&startLng=21.0&endLat=52.26"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Missing required request parameter 'endLng'"))
                .andExpect(jsonPath("$.details.parameter").value("endLng"))
                .andExpect(jsonPath("$.path").value(URL));

        verifyNoInteractions(directionsService);
    }

    @Test
    void returnsInvalidParameterErrorWhenCoordinateIsNotANumber() throws Exception {
        mockMvc.perform(get(URL + "?startLat=abc&startLng=21.0&endLat=52.26&endLng=20.91"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value(containsString("startLat")))
                .andExpect(jsonPath("$.details.parameter").value("startLat"))
                .andExpect(jsonPath("$.details.expectedType").exists());

        verifyNoInteractions(directionsService);
    }

    @Test
    void returnsInvalidParameterErrorWhenCoordinateIsEmpty() throws Exception {
        mockMvc.perform(get(URL + "?startLat=&startLng=21.0&endLat=52.26&endLng=20.91"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());

        verifyNoInteractions(directionsService);
    }

    @Test
    void rejectsUnsupportedHttpMethodWithoutFallingBackToInternalError() throws Exception {
        mockMvc.perform(post(URL + QUERY))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));

        verifyNoInteractions(directionsService);
    }

    // --- unexpected failures ---

    @Test
    void hidesInternalDetailsWhenServiceThrowsUnexpectedException() throws Exception {
        when(directionsService.findRoutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyString(), anyBoolean(), any(Locale.class)))
                .thenThrow(new IllegalStateException("graph cache corrupted at /var/data/secret"));

        mockMvc.perform(get(URL + QUERY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(jsonPath("$.message").value(not(containsString("secret"))));
    }
}
