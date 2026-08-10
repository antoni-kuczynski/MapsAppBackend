package com.antekk.maps.controller;

import com.antekk.maps.dto.MarkerDto;
import com.antekk.maps.service.MarkerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markers")
public class MarkerController {
    private final MarkerService markerService;

    public MarkerController(MarkerService markerService) {
        this.markerService = markerService;
    }

    @PostMapping
    public ResponseEntity<String> addMarker(@RequestBody MarkerDto marker) {
        markerService.create();
        return ResponseEntity.ok("Test ok");
    }


}
