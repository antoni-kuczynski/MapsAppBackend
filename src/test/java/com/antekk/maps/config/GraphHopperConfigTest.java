package com.antekk.maps.config;

import com.antekk.maps.exception.GraphHopperConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphHopperConfigTest {
    private final GraphHopperConfig config = new GraphHopperConfig();

    @TempDir
    Path tempDir;

    private GraphHopperProperties properties(String osmFile, String graphCache, List<String> profiles) {
        return new GraphHopperProperties(osmFile, graphCache, profiles, 3);
    }

    @Test
    void failsWithActionableMessageWhenOsmFileAndGraphCacheAreMissing() {
        GraphHopperProperties properties = properties(
                tempDir.resolve("no-such-file.osm.pbf").toString(),
                tempDir.resolve("no-such-cache").toString(),
                List.of("car")
        );

        assertThatThrownBy(() -> config.graphHopper(properties))
                .isInstanceOf(GraphHopperConfigurationException.class)
                .hasMessageContaining("no-such-file.osm.pbf")
                .hasMessageContaining("download.geofabrik.de");
    }

    @Test
    void failsWhenOsmFileIsADirectoryInsteadOfAFile() throws IOException {
        Path notAFile = Files.createDirectory(tempDir.resolve("osm-dir"));
        GraphHopperProperties properties = properties(
                notAFile.toString(),
                tempDir.resolve("no-such-cache").toString(),
                List.of("car")
        );

        assertThatThrownBy(() -> config.graphHopper(properties))
                .isInstanceOf(GraphHopperConfigurationException.class)
                .hasMessageContaining("OpenStreetMap file not found");
    }

    @Test
    void failsWhenProfileIsNotSupported() {
        GraphHopperProperties properties = properties("any.osm.pbf", "any-cache", List.of("car", "helicopter"));

        assertThatThrownBy(() -> config.graphHopper(properties))
                .isInstanceOf(GraphHopperConfigurationException.class)
                .hasMessageContaining("helicopter")
                .hasMessageContaining("supported");
    }

    @Test
    void failsWhenNoProfileIsConfigured() {
        GraphHopperProperties properties = properties("any.osm.pbf", "any-cache", List.of());

        assertThatThrownBy(() -> config.graphHopper(properties))
                .isInstanceOf(GraphHopperConfigurationException.class)
                .hasMessageContaining("at least one profile");
    }

    /** The profile check must run before the file check, otherwise the message would be misleading. */
    @Test
    void reportsUnsupportedProfileEvenWhenOsmFileIsAlsoMissing() {
        GraphHopperProperties properties = properties(
                tempDir.resolve("no-such-file.osm.pbf").toString(),
                tempDir.resolve("no-such-cache").toString(),
                List.of("helicopter")
        );

        assertThatThrownBy(() -> config.graphHopper(properties))
                .isInstanceOf(GraphHopperConfigurationException.class)
                .hasMessageContaining("helicopter");
    }

    @Test
    void encodedValuesContainInstructionAndProfileSpecificValuesWithoutDuplicates() {
        String encodedValues = GraphHopperConfig.encodedValuesFor(List.of("car", "foot"));
        List<String> values = List.of(encodedValues.split(","));

        assertThat(values)
                .contains("roundabout", "road_class", "road_environment", "max_speed")
                .contains("car_access", "car_average_speed")
                .contains("foot_access", "foot_priority")
                .doesNotHaveDuplicates();
    }

    @Test
    void encodedValuesOnlyContainValuesOfConfiguredProfiles() {
        String encodedValues = GraphHopperConfig.encodedValuesFor(List.of("car"));

        assertThat(encodedValues).doesNotContain("foot_access", "bike_access");
    }
}
