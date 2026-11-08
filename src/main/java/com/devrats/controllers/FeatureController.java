package com.devrats.controllers;

import com.devrats.config.AppProperties;
import com.devrats.dto.FeatureFlagsResponse;
import com.devrats.services.storage.StudyPhotoStorage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/features")
public class FeatureController {

    private final AppProperties properties;
    private final StudyPhotoStorage photoStorage;

    public FeatureController(AppProperties properties, StudyPhotoStorage photoStorage) {
        this.properties = properties;
        this.photoStorage = photoStorage;
    }

    @GetMapping
    public ResponseEntity<FeatureFlagsResponse> get() {
        boolean studyPhotos = properties.getStudyPhotos().isEnabled() && photoStorage.isAvailable();
        return ResponseEntity.ok(new FeatureFlagsResponse(studyPhotos));
    }
}
