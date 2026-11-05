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
 * Cliente S3 do chat. Usa um bucket privado separado do bucket público de avatares.
 *
 * <p>Sem bucket/credenciais o bean continua criado, mas {@link #isAvailable()}
 * retorna {@code false}; uploads respondem 503 e o chat de texto segue funcionando.</p>
 */
@Component
public class S3ChatAttachmentStorage implements ChatAttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ChatAttachmentStorage.class);

    private final AppProperties.Storage base;
    private final AppProperties.ChatStorage chat;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3ChatAttachmentStorage(AppProperties properties) {
        this.base = properties.getStorage();
        this.chat = properties.getChat().getStorage();
        boolean configured = chat.isConfigured() && base.hasCredentials();
        this.client = configured ? createClient(base) : null;
        this.presigner = configured ? createPresigner(base) : null;
        if (!configured) {
            log.warn("Storage de anexos do chat desabilitado: APP_CHAT_STORAGE_BUCKET/"
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
                            .bucket(chat.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .cacheControl("private, max-age=3600")
                            .build(),
                    RequestBody.fromBytes(content));
            log.info("Anexo de chat enviado: key={}, bytes={}", key, content.length);
        } catch (SdkException exception) {
            log.error("Falha ao enviar anexo de chat: key={}", key, exception);
            throw new DomainException(ErrorCode.CHAT_STORAGE_UNAVAILABLE,
                    "Não foi possível armazenar o arquivo");
        }
    }

    @Override
    public String presignGet(String key, Duration ttl, String contentDisposition, String contentType) {
        requireAvailable();
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(chat.getBucket())
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
            log.error("Falha ao assinar URL de anexo: key={}", key, exception);
            throw new DomainException(ErrorCode.CHAT_STORAGE_UNAVAILABLE,
                    "Não foi possível gerar o link do arquivo");
        }
    }

    @Override
    public void delete(String key) {
        if (client == null || key == null || key.isBlank()) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(chat.getBucket())
                    .key(key)
                    .build());
            log.info("Anexo de chat removido: key={}", key);
        } catch (SdkException exception) {
            //---- Best-effort: objeto órfão não pode impedir a exclusão da mensagem.
            log.warn("Falha ao remover anexo de chat: key={}", key, exception);
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
            throw new DomainException(ErrorCode.CHAT_STORAGE_UNAVAILABLE,
                    "Armazenamento de anexos não configurado");
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
