package com.devstreak.services;

import com.devstreak.dto.ActivityCalendarResponse;
import com.devstreak.dto.ChallengeProgressResponse;
import com.devstreak.dto.ChallengeResultResponse;
import com.devstreak.dto.DashboardResponse;
import com.devstreak.dto.FeedItemResponse;

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
