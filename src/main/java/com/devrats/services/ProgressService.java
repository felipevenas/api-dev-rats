package com.devrats.services;

import com.devrats.dto.ActivityCalendarResponse;
import com.devrats.dto.ChallengeProgressResponse;
import com.devrats.dto.ChallengeResultResponse;
import com.devrats.dto.DashboardResponse;
import com.devrats.dto.FeedItemResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProgressService {

    List<ChallengeProgressResponse> listProgress(UUID challengeId, UUID userId);

    List<ChallengeProgressResponse> listLeaderboard(UUID challengeId, UUID userId);

    ActivityCalendarResponse getActivity(UUID challengeId, UUID userId, UUID targetUserId, LocalDate from, LocalDate to);

    ChallengeResultResponse getResult(UUID challengeId, UUID userId);

    DashboardResponse getDashboard(UUID userId);

    List<FeedItemResponse> getFeed(UUID challengeId, UUID userId, int limit);

    void recalculateParticipant(UUID participantId);
}
