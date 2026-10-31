package com.devrats.services;

import com.devrats.dto.ActivityCalendarResponse;
import com.devrats.dto.ActivityCommitResponse;
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

    /**
     * Lista os commits elegíveis que validaram um dia do desafio para um participante.
     *
     * @param actorId      usuário autenticado (precisa ser membro do grupo do desafio)
     * @param targetUserId participante consultado; {@code null} usa o próprio ator
     * @param date         dia do desafio no fuso do desafio
     * @param limit        máximo de commits retornados (teto de 50)
     */
    List<ActivityCommitResponse> listActivityCommits(UUID challengeId, UUID actorId, UUID targetUserId,
                                                     LocalDate date, int limit);

    ChallengeResultResponse getResult(UUID challengeId, UUID userId);

    DashboardResponse getDashboard(UUID userId);

    List<FeedItemResponse> getFeed(UUID challengeId, UUID userId, int limit);

    void recalculateParticipant(UUID participantId);
}
