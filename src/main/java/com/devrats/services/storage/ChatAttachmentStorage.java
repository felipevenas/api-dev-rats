package com.devrats.services.storage;

import java.time.Duration;

/**
 * Armazenamento privado de anexos de chat.
 *
 * <p>A object key nunca é exposta ao cliente. O download é feito por URL
 * assinada de curta duração; a API não faz proxy dos bytes.</p>
 */
public interface ChatAttachmentStorage {

    boolean isAvailable();

    void upload(String key, byte[] content, String contentType);

    String presignGet(String key, Duration ttl, String contentDisposition, String contentType);

    void delete(String key);
}
