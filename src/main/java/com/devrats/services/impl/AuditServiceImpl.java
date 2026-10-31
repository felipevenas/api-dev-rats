package com.devrats.services.impl;

import com.devrats.entities.AuditLogEntity;
import com.devrats.enums.AuditAction;
import com.devrats.repositories.AuditLogRepository;
import com.devrats.services.AuditService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(UUID actorUserId, String entityType, UUID entityId, AuditAction action, Map<String, Object> metadata) {
        AuditLogEntity log = new AuditLogEntity();
        log.setActorUserId(actorUserId);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setMetadata(metadata == null || metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata));
        auditLogRepository.save(log);
    }
}
