package com.devrats.services;

import com.devrats.dto.PageResponse;
import com.devrats.dto.StudyPhotoResponse;
import com.devrats.dto.UserDataExportResponse;
import com.devrats.services.study.StudyPhotoCommand;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StudyPhotoService {

    StudyPhotoResponse publish(UUID challengeId, UUID userId, StudyPhotoCommand command);

    PageResponse<StudyPhotoResponse> list(UUID challengeId, UUID actorId, UUID targetUserId, LocalDate date,
                                          int page, int size);

    StudyPhotoResponse refreshUrl(UUID challengeId, UUID actorId, UUID photoId);

    void delete(UUID challengeId, UUID actorId, UUID photoId);

    List<UserDataExportResponse.StudyPhotoExport> exportForUser(UUID userId);

    void deleteAllForUser(UUID userId);
}
