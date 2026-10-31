package com.devrats.services.storage;

import com.devrats.config.AppProperties;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.time.Duration;

/**
 * Cliente S3 síncrono compartilhado pela aplicação.
 *
 * <p>O bean é sempre criado. Quando bucket/credenciais não estão configurados o client
 * permanece nulo e o upload responde 503, sem derrubar o startup. Endpoints alternativos
 * (R2/MinIO) usam path-style, exigido por esses provedores.</p>
 */
@Component
public class S3AvatarStorage implements AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(S3AvatarStorage.class);

    private final AppProperties.Storage properties;
    private final S3Client client;

    public S3AvatarStorage(AppProperties appProperties) {
        this.properties = appProperties.getStorage();
        this.client = createClient(this.properties);
        if (client == null) {
            log.warn("Storage de avatares desabilitado: APP_STORAGE_BUCKET/APP_STORAGE_ACCESS_KEY/"
                    + "APP_STORAGE_SECRET_KEY não configurados");
        }
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public void upload(String key, byte[] content, String contentType) {
        if (client == null) {
            throw new DomainException(ErrorCode.AVATAR_STORAGE_UNAVAILABLE,
                    "Armazenamento de imagens não configurado");
        }
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
            log.info("Avatar enviado para o storage: key={}, bytes={}", key, content.length);
        } catch (SdkException exception) {
            log.error("Falha ao enviar avatar para o storage: key={}", key, exception);
            throw new DomainException(ErrorCode.AVATAR_STORAGE_UNAVAILABLE,
                    "Não foi possível armazenar a imagem");
        }
    }

    @Override
    public void delete(String key) {
        if (client == null || key == null || key.isBlank()) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            log.info("Avatar removido do storage: key={}", key);
        } catch (SdkException exception) {
            //---- Best-effort: um objeto órfão não pode impedir a troca ou a remoção do avatar.
            log.warn("Falha ao remover avatar do storage: key={}", key, exception);
        }
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    private static S3Client createClient(AppProperties.Storage storage) {
        if (!storage.isConfigured()) {
            return null;
        }
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey())))
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (storage.getEndpoint() != null && !storage.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.getEndpoint()));
            //---- R2 e MinIO não resolvem bucket por subdomínio; path-style é o modo suportado.
            builder.forcePathStyle(true);
        }
        return builder.build();
    }
}
