package com.devstreak.services;

import com.devstreak.enums.AuditAction;

import java.util.Map;
import java.util.UUID;

public interface AuditService {

    void record(UUID actorUserId, String entityType, UUID entityId, AuditAction action, Map<String, Object> metadata);
}
