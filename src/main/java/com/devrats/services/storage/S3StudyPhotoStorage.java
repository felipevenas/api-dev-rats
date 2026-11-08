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
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * Cliente S3 das fotos de estudo. Usa um bucket privado separado do bucket
 * público de avatares e do bucket de anexos do chat.
 *
 * <p>Sem bucket/credenciais o bean continua criado, mas {@link #isAvailable()}
 * retorna {@code false}; o modo foto fica indisponível e os uploads respondem 503.</p>
 */
@Component
public class S3StudyPhotoStorage implements StudyPhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(S3StudyPhotoStorage.class);

    private final AppProperties.Storage base;
    private final AppProperties.PhotoStorage photos;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StudyPhotoStorage(AppProperties properties) {
        this.base = properties.getStorage();
        this.photos = properties.getStudyPhotos().getStorage();
        boolean configured = photos.isConfigured() && base.hasCredentials();
        this.client = configured ? createClient(base) : null;
        this.presigner = configured ? createPresigner(base) : null;
        if (!configured) {
            log.warn("Storage de fotos de estudo desabilitado: APP_STUDY_PHOTOS_STORAGE_BUCKET/"
                    + "APP_STORAGE_ACCESS_KEY/APP_STORAGE_SECRET_KEY não configurados");
        }
    }

    @Override
    public boolean isAvailable() {
        return client != null && presigner != null;
    }

    @Override
    public void upload(String key, byte[] content, String contentType) {
        requireAvailable();
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(photos.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .cacheControl("private, max-age=3600")
                            .build(),
                    RequestBody.fromBytes(content));
            log.info("Foto de estudo enviada: key={}, bytes={}", key, content.length);
        } catch (SdkException exception) {
            log.error("Falha ao enviar foto de estudo: key={}", key, exception);
            throw new DomainException(ErrorCode.PHOTO_STORAGE_UNAVAILABLE,
                    "Não foi possível armazenar a foto");
        }
    }

    @Override
    public String presignGet(String key, Duration ttl, String contentDisposition, String contentType) {
        requireAvailable();
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(photos.getBucket())
                    .key(key)
                    .responseContentDisposition(contentDisposition)
                    .responseContentType(contentType)
                    .build();
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(ttl)
                            .getObjectRequest(request)
                            .build())
                    .url()
                    .toString();
        } catch (RuntimeException exception) {
            log.error("Falha ao assinar URL de foto de estudo: key={}", key, exception);
            throw new DomainException(ErrorCode.PHOTO_STORAGE_UNAVAILABLE,
                    "Não foi possível gerar o link da foto");
        }
    }

    @Override
    public void delete(String key) {
        if (client == null || key == null || key.isBlank()) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(photos.getBucket())
                    .key(key)
                    .build());
            log.info("Foto de estudo removida: key={}", key);
        } catch (SdkException exception) {
            //---- Best-effort: objeto órfão não pode impedir a exclusão do registro.
            log.warn("Falha ao remover foto de estudo: key={}", key, exception);
        }
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
        if (presigner != null) {
            presigner.close();
        }
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new DomainException(ErrorCode.PHOTO_STORAGE_UNAVAILABLE,
                    "Armazenamento de fotos não configurado");
        }
    }

    private static S3Client createClient(AppProperties.Storage storage) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(credentials(storage))
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30)))
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        applyEndpoint(builder, storage);
        return builder.build();
    }

    private static S3Presigner createPresigner(AppProperties.Storage storage) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(credentials(storage));
        if (storage.getEndpoint() != null && !storage.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.getEndpoint()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build());
        }
        return builder.build();
    }

    private static void applyEndpoint(S3ClientBuilder builder, AppProperties.Storage storage) {
        if (storage.getEndpoint() != null && !storage.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.getEndpoint()));
            //---- R2 e MinIO não resolvem bucket por subdomínio; path-style é o modo suportado.
            builder.forcePathStyle(true);
        }
    }

    private static StaticCredentialsProvider credentials(AppProperties.Storage storage) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey()));
    }
}
