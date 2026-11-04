package com.devrats.services;

import com.devrats.enums.PresenceStatus;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Presença calculada a partir de heartbeats. O instante cru nunca é exposto.
 */
public interface PresenceService {

    void heartbeat(UUID userId);

    PresenceStatus statusOf(UUID userId);

    /**
     * @return um status para cada id informado; ids sem heartbeat ficam {@code OFFLINE}
     */
    Map<UUID, PresenceStatus> statusesOf(Collection<UUID> userIds);
}
