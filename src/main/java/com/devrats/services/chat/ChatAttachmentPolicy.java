package com.devrats.services.chat;

import com.devrats.config.AppProperties;
import com.devrats.enums.AttachmentKind;
import com.devrats.enums.ChatMessageType;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.utils.ChatImageProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Valida e normaliza anexos antes de qualquer escrita no S3.
 *
 * <p>O Content-Type do cliente não é confiável: a decisão usa extensão + magic
 * bytes. Formatos executáveis, SVG, HTML e compactados ficam fora da allowlist.</p>
 */
@Component
public class ChatAttachmentPolicy {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> VOICE_EXTENSIONS = Set.of("webm", "ogg", "mp4", "m4a", "mp3");
    private static final Set<String> FILE_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "pptx", "txt", "csv");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBM_SIGNATURE = {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};

    private final AppProperties.Chat properties;

    public ChatAttachmentPolicy(AppProperties appProperties) {
        this.properties = appProperties.getChat();
    }

    public ValidatedAttachment validate(ChatMessageType type, MultipartFile file, Integer durationSeconds) {
        if (type == ChatMessageType.TEXT) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Use o envio de texto para mensagens TEXT");
        }
        if (file == null || file.isEmpty()) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Envie um arquivo");
        }
        byte[] content = readBytes(file);
        String originalName = sanitizeName(file.getOriginalFilename());
        String extension = extensionOf(originalName);

        return switch (type) {
            case IMAGE -> validateImage(content, originalName, extension);
            case VOICE -> validateVoice(content, originalName, extension, durationSeconds);
            case FILE -> validateFile(content, originalName, extension);
            case TEXT -> throw new DomainException(ErrorCode.VALIDATION_ERROR, "Tipo de mensagem inválido");
        };
    }

    private ValidatedAttachment validateImage(byte[] content, String originalName, String extension) {
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Envie uma imagem JPEG ou PNG");
        }
        ChatImageProcessor.ProcessedImage processed =
                ChatImageProcessor.process(content, properties.getMaxImageBytes());
        return new ValidatedAttachment(AttachmentKind.IMAGE, processed.bytes(), "image/jpeg", "jpg",
                replaceExtension(originalName, "jpg"), processed.bytes().length,
                processed.width(), processed.height(), null);
    }

    private ValidatedAttachment validateVoice(byte[] content, String originalName, String extension,
                                               Integer durationSeconds) {
        if (!VOICE_EXTENSIONS.contains(extension)) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT,
                    "Envie um áudio WebM, OGG, MP4, M4A ou MP3");
        }
        if (content.length > properties.getMaxVoiceBytes()) {
            throw new DomainException(ErrorCode.ATTACHMENT_TOO_LARGE,
                    "O áudio deve ter no máximo " + (properties.getMaxVoiceBytes() / 1024 / 1024) + " MB");
        }
        if (!isValidAudio(content, extension)) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Arquivo de áudio inválido");
        }
        Integer duration = durationSeconds;
        if (duration != null && (duration <= 0 || duration > properties.getMaxVoiceSeconds())) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "A mensagem de voz deve ter no máximo " + properties.getMaxVoiceSeconds() + " segundos");
        }
        return new ValidatedAttachment(AttachmentKind.VOICE, content, audioContentType(extension), extension,
                originalName, content.length, null, null, duration);
    }

    private ValidatedAttachment validateFile(byte[] content, String originalName, String extension) {
        if (!FILE_EXTENSIONS.contains(extension)) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT,
                    "Envie PDF, DOCX, XLSX, PPTX, TXT ou CSV");
        }
        if (content.length > properties.getMaxFileBytes()) {
            throw new DomainException(ErrorCode.ATTACHMENT_TOO_LARGE,
                    "O documento deve ter no máximo " + (properties.getMaxFileBytes() / 1024 / 1024) + " MB");
        }
        if (!isValidFile(content, extension)) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Arquivo inválido");
        }
        return new ValidatedAttachment(AttachmentKind.FILE, content, fileContentType(extension), extension,
                originalName, content.length, null, null, null);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Não foi possível ler o arquivo");
        }
    }

    private boolean isValidAudio(byte[] content, String extension) {
        return switch (extension) {
            case "webm" -> startsWith(content, WEBM_SIGNATURE);
            case "ogg" -> startsWith(content, new byte[]{'O', 'g', 'g', 'S'});
            case "mp4", "m4a" -> content.length > 12 && matchesAscii(content, 4, "ftyp");
            case "mp3" -> startsWith(content, new byte[]{'I', 'D', '3'})
                    || (content.length >= 2 && (content[0] & 0xFF) == 0xFF
                    && ((content[1] & 0xFF) == 0xFB || (content[1] & 0xFF) == 0xF3
                    || (content[1] & 0xFF) == 0xF2 || (content[1] & 0xFF) == 0xF4));
            default -> false;
        };
    }

    private boolean isValidFile(byte[] content, String extension) {
        return switch (extension) {
            case "pdf" -> startsWith(content, PDF_SIGNATURE);
            case "docx", "xlsx", "pptx" -> startsWith(content, new byte[]{'P', 'K', 0x03, 0x04});
            case "txt", "csv" -> isPlainText(content);
            default -> false;
        };
    }

    private boolean isPlainText(byte[] content) {
        int limit = Math.min(content.length, 8192);
        for (int index = 0; index < limit; index++) {
            if (content[index] == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAscii(byte[] content, int offset, String value) {
        if (content.length < offset + value.length()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (content[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private String sanitizeName(String name) {
        String value = name == null ? "arquivo" : name;
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isBlank()) {
            value = "arquivo";
        }
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String replaceExtension(String name, String extension) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + "." + extension;
    }

    private String audioContentType(String extension) {
        return switch (extension) {
            case "webm" -> "audio/webm";
            case "ogg" -> "audio/ogg";
            case "mp4", "m4a" -> "audio/mp4";
            case "mp3" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
    }

    private String fileContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    public record ValidatedAttachment(
            AttachmentKind kind,
            byte[] content,
            String contentType,
            String extension,
            String originalName,
            long sizeBytes,
            Integer width,
            Integer height,
            Integer durationSeconds
    ) {
    }
}
