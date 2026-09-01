package com.antekk.maps;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the endpoint through the real application context. The routing engine is mocked so the
 * test does not need an OpenStreetMap extract or an imported graph.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DirectionsApiIntegrationTest {
    private static final String QUERY = "/api/directions?startLat=52.20876379685948&startLng=21.010322570800785"
            + "&endLat=52.265846190602694&endLng=20.912647247314457";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GraphHopper graphHopper;

    @BeforeEach
    void setUp() {
        when(graphHopper.getTranslationMap()).thenReturn(new TranslationMap().doImport());
    }

    private void stubRoute(ResponsePath path) {
        GHResponse response = new GHResponse();
        response.add(path);
        when(graphHopper.route(any(GHRequest.class))).thenReturn(response);
    }

    private static ResponsePath path() {
        PointList points = new PointList();
        points.add(52.2081887, 21.0102968);
        points.add(52.265846190602694, 20.912647247314457);

        ResponsePath path = new ResponsePath().setDistance(11_723.0).setPoints(points);
        path.setTime(1_027_000L);
        path.setInstructions(new InstructionList(null));
        return path;
    }

    @Test
    void returnsRouteForValidRequest() throws Exception {
        stubRoute(path());

        mockMvc.perform(get(QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").value("car"))
                .andExpect(jsonPath("$.routes[0].distanceMeters").value(11_723.0))
                .andExpect(jsonPath("$.routes[0].points.length()").value(2));
    }

    @Test
    void returnsErrorEnvelopeWithIsoTimestampWhenPointIsOutsideTheImportedArea() throws Exception {
        when(graphHopper.route(any(GHRequest.class))).thenReturn(new GHResponse()
                .addError(new PointOutOfBoundsException("Point 0 is out of bounds: 40.7,-74.0", 0)));

        mockMvc.perform(get("/api/directions?startLat=40.7&startLng=-74.0&endLat=52.26&endLng=20.91"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT_OUT_OF_BOUNDS"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/directions"))
                .andExpect(jsonPath("$.details.point_index").value(0))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")));
    }

    @Test
    void returnsNotFoundWhenPointsAreNotConnected() throws Exception {
        when(graphHopper.route(any(GHRequest.class))).thenReturn(new GHResponse()
                .addError(new ConnectionNotFoundException("Connection between locations not found", Map.of())));

        mockMvc.perform(get(QUERY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_ROUTE_FOUND"));
    }

    @Test
    void rejectsCoordinatesOutsideTheValidRange() throws Exception {
        mockMvc.perform(get("/api/directions?startLat=120.0&startLng=21.0&endLat=52.26&endLng=20.91"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"))
                .andExpect(jsonPath("$.details.parameter").value("startLat"));
    }

    @Test
    void rejectsProfileThatIsNotConfigured() throws Exception {
        mockMvc.perform(get(QUERY + "&profile=helicopter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PROFILE"))
                .andExpect(jsonPath("$.details.requested").value("helicopter"));
    }

    @Test
    void rejectsRequestWithMissingCoordinate() throws Exception {
        mockMvc.perform(get("/api/directions?startLat=52.2&startLng=21.0&endLat=52.26"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.details.parameter").value("endLng"));
    }

    @Test
    void rejectsNonNumericCoordinate() throws Exception {
        mockMvc.perform(get("/api/directions?startLat=abc&startLng=21.0&endLat=52.26&endLng=20.91"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details.parameter").value("startLat"));
    }
}
