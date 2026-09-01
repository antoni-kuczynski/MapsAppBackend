package com.antekk.maps.config;

import com.antekk.maps.exception.GraphHopperConfigurationException;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(GraphHopperProperties.class)
public class GraphHopperConfig {

    /**
     * Encoded values required by the built-in custom models of each supported profile.
     * They must exist in the graph before the custom model can reference them.
     */
    private static final Map<String, List<String>> PROFILE_ENCODED_VALUES = Map.of(
            "car", List.of("car_access", "car_average_speed", "road_access", "max_speed", "ferry_speed"),
            "bike", List.of("bike_access", "bike_priority", "bike_average_speed", "bike_network",
                    "bike_road_access", "mtb_rating", "hike_rating", "ferry_speed"),
            "foot", List.of("foot_access", "foot_priority", "foot_average_speed", "foot_road_access",
                    "hike_rating", "mtb_rating", "ferry_speed")
    );

    /** Needed by the turn instruction generator regardless of the profile. */
    private static final List<String> INSTRUCTION_ENCODED_VALUES =
            List.of("roundabout", "road_class", "road_environment", "max_speed");

    @Bean(destroyMethod = "close")
    public GraphHopper graphHopper(GraphHopperProperties properties) {
        List<String> profileNames = properties.profiles();
        if (profileNames == null || profileNames.isEmpty()) {
            throw new GraphHopperConfigurationException("graphhopper.profiles must contain at least one profile, supported: "
                    + PROFILE_ENCODED_VALUES.keySet());
        }
        profileNames.stream()
                .filter(name -> !PROFILE_ENCODED_VALUES.containsKey(name))
                .findFirst()
                .ifPresent(name -> {
                    throw new GraphHopperConfigurationException("Unsupported graphhopper.profiles entry: '" + name
                            + "', supported: " + PROFILE_ENCODED_VALUES.keySet());
                });

        Path graphCache = Path.of(properties.graphCacheLocation());
        Path osmFile = Path.of(properties.osmFile());
        if (!Files.isDirectory(graphCache) && !(Files.isRegularFile(osmFile) && Files.isReadable(osmFile))) {
            throw new GraphHopperConfigurationException("OpenStreetMap file not found: " + osmFile.toAbsolutePath()
                    + ". Download an extract (e.g. https://download.geofabrik.de/europe/poland/mazowieckie-latest.osm.pbf)"
                    + " or point graphhopper.osm-file at an existing .osm.pbf file.");
        }

        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(osmFile.toString());
        hopper.setGraphHopperLocation(graphCache.toString());
        hopper.setEncodedValuesString(encodedValuesFor(profileNames));
        hopper.setProfiles(profileNames.stream()
                .map(name -> new Profile(name).setCustomModel(GHUtility.loadCustomModelFromJar(name + ".json")))
                .toList());
        hopper.getCHPreparationHandler().setCHProfiles(profileNames.stream().map(CHProfile::new).toList());
        hopper.importOrLoad();
        return hopper;
    }

    static String encodedValuesFor(List<String> profileNames) {
        Set<String> encodedValues = new LinkedHashSet<>(INSTRUCTION_ENCODED_VALUES);
        profileNames.forEach(name -> encodedValues.addAll(PROFILE_ENCODED_VALUES.get(name)));
        return String.join(",", encodedValues);
    }
}
