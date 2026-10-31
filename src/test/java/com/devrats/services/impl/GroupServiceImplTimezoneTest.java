package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.CreateChallengeRequest;
import com.devrats.dto.CreateGroupRequest;
import com.devrats.dto.GroupResponse;
import com.devrats.entities.GroupEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.VerificationMode;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepository;
import com.devrats.repositories.GroupMemberRepository;
import com.devrats.repositories.GroupRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuditService;
import com.devrats.services.ChallengeService;
import com.devrats.services.GroupAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupServiceImplTimezoneTest {

    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final GroupMemberRepository memberRepository = mock(GroupMemberRepository.class);
    private final ChallengeRepository challengeRepository = mock(ChallengeRepository.class);
    private final ChallengeParticipantRepository participantRepository = mock(ChallengeParticipantRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChallengeService challengeService = mock(ChallengeService.class);
    private final GroupAccessService accessService = mock(GroupAccessService.class);
    private final AuditService auditService = mock(AuditService.class);

    private GroupServiceImpl service;
    private UUID creatorId;

    @BeforeEach
    void setUp() {
        service = new GroupServiceImpl(groupRepository, memberRepository, challengeRepository, participantRepository,
                userRepository, challengeService, accessService, auditService, new AppProperties(),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        creatorId = UUID.randomUUID();
        UserEntity creator = new UserEntity();
        creator.setId(creatorId);
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(groupRepository.save(any(GroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCreateGroupWithBrasilia_whenTimezoneIsMissing() {
        GroupResponse response = service.createGroup(creatorId, request(null));

        assertThat(response.timezone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void shouldCreateGroupWithBrasilia_whenTimezoneIsBlank() {
        GroupResponse response = service.createGroup(creatorId, request("  "));

        assertThat(response.timezone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void shouldCreateGroupWithInformedTimezone_whenValid() {
        GroupResponse response = service.createGroup(creatorId, request("Europe/Lisbon"));

        assertThat(response.timezone()).isEqualTo("Europe/Lisbon");
    }

    @Test
    void shouldRejectInvalidTimezoneOnCreateGroup() {
        assertThatThrownBy(() -> service.createGroup(creatorId, request("Mars/Olympus")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Fuso horário inválido");
    }

    private CreateGroupRequest request(String timezone) {
        return new CreateGroupRequest("Grupo de teste", null, timezone, 10,
                new CreateChallengeRequest(VerificationMode.OWN_REPOSITORIES, 5,
                        Instant.parse("2026-01-02T00:00:00Z"), null, null, List.of()));
    }
}
