package com.antekk.maps;

import com.graphhopper.GraphHopper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MapsBackendApplicationTests {

    /**
     * Replaces the real routing engine so the context can start without an imported
     * OpenStreetMap graph.
     */
    @MockitoBean
    private GraphHopper graphHopper;

    @Test
    void contextLoads() {
    }

}
