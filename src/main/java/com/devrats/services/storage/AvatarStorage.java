package com.devrats.services.storage;

/**
 * Armazenamento de imagens de avatar em object storage S3 compatível.
 *
 * <p>Implementações devem falhar com {@code AVATAR_STORAGE_UNAVAILABLE} no upload quando
 * a configuração estiver ausente, e tratar a remoção como operação best-effort.</p>
 */
public interface AvatarStorage {

    boolean isAvailable();

    void upload(String key, byte[] content, String contentType);

    void delete(String key);
}
