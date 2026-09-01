package com.antekk.maps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "graphhopper")
public record GraphHopperProperties(
        @DefaultValue("data/mazowieckie-latest.osm.pbf") String osmFile,
        @DefaultValue("data/graph-cache") String graphCacheLocation,
        @DefaultValue("car") List<String> profiles,
        @DefaultValue("3") int maxAlternativeRoutes
) {
}
