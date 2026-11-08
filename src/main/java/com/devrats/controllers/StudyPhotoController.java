package com.devrats.controllers;

import com.devrats.dto.PageResponse;
import com.devrats.dto.StudyPhotoResponse;
import com.devrats.services.StudyPhotoService;
import com.devrats.services.study.StudyPhotoCommand;
import com.devrats.utils.AuthenticatedUserHelper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/challenges/{challengeId}/photos")
public class StudyPhotoController {

    private final StudyPhotoService photoService;

    public StudyPhotoController(StudyPhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudyPhotoResponse> publish(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID challengeId,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam(required = false) String title,
                                                      @RequestParam(required = false) String description,
                                                      @RequestParam(required = false) UUID githubRepositoryId,
                                                      @RequestParam(required = false) String commitUrl) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        StudyPhotoCommand command = new StudyPhotoCommand(file, title, description, githubRepositoryId, commitUrl);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(photoService.publish(challengeId, userId, command));
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudyPhotoResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID challengeId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        UUID actorId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(photoService.list(challengeId, actorId, userId, date, page, size));
    }

    @GetMapping("/{photoId}/url")
    public ResponseEntity<StudyPhotoResponse> refreshUrl(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID challengeId,
                                                         @PathVariable UUID photoId) {
        UUID actorId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(photoService.refreshUrl(challengeId, actorId, photoId));
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID challengeId,
                                       @PathVariable UUID photoId) {
        UUID actorId = AuthenticatedUserHelper.getUserId(jwt);
        photoService.delete(challengeId, actorId, photoId);
        return ResponseEntity.noContent().build();
    }
}
