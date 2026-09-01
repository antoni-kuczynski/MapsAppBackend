package com.antekk.maps.controller;

import com.antekk.maps.dto.DirectionsDto;
import com.antekk.maps.service.DirectionsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/directions")
public class DirectionsController {
    private final DirectionsService directionsService;

    public DirectionsController(DirectionsService directionsService) {
        this.directionsService = directionsService;
    }

    @GetMapping
    public DirectionsDto getDirections(
            @RequestParam double startLat,
            @RequestParam double startLng,
            @RequestParam double endLat,
            @RequestParam double endLng,
            @RequestParam(defaultValue = "car") String profile,
            @RequestParam(defaultValue = "false") boolean alternatives,
            @RequestParam(defaultValue = "pl") String lang
    ) {
        return directionsService.findRoutes(startLat, startLng, endLat, endLng,
                profile, alternatives, Locale.forLanguageTag(lang));
    }
}
