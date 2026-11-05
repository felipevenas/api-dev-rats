package com.devrats.services.chat;

import com.devrats.config.AppProperties;
import com.devrats.enums.AttachmentKind;
import com.devrats.enums.ChatMessageType;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatAttachmentPolicyTest {

    private final ChatAttachmentPolicy policy = new ChatAttachmentPolicy(new AppProperties());

    @Test
    void shouldAcceptPdfDocument() {
        MockMultipartFile file = new MockMultipartFile("file", "contrato.pdf",
                "application/pdf", "%PDF-1.7\nconteudo".getBytes());

        ChatAttachmentPolicy.ValidatedAttachment validated =
                policy.validate(ChatMessageType.FILE, file, null);

        assertThat(validated.kind()).isEqualTo(AttachmentKind.FILE);
        assertThat(validated.contentType()).isEqualTo("application/pdf");
        assertThat(validated.originalName()).isEqualTo("contrato.pdf");
    }

    @Test
    void shouldAcceptOoxmlDocumentWithContentTypesManifest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "documento.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipWith("[Content_Types].xml"));

        ChatAttachmentPolicy.ValidatedAttachment validated =
                policy.validate(ChatMessageType.FILE, file, null);

        assertThat(validated.kind()).isEqualTo(AttachmentKind.FILE);
        assertThat(validated.contentType()).contains("wordprocessingml");
    }

    @Test
    void shouldRejectRenamedZipWithoutOoxmlManifest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "documento.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                zipWith("payload.txt"));

        assertThatThrownBy(() -> policy.validate(ChatMessageType.FILE, file, null))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ATTACHMENT_INVALID_FORMAT);
    }

    @Test
    void shouldRejectExecutableExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "script.exe",
                "application/octet-stream", "MZ".getBytes());

        assertThatThrownBy(() -> policy.validate(ChatMessageType.FILE, file, null))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ATTACHMENT_INVALID_FORMAT);
    }

    @Test
    void shouldAcceptWebmVoiceWithMagicBytes() {
        byte[] content = {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "voz.webm",
                "audio/webm", content);

        ChatAttachmentPolicy.ValidatedAttachment validated =
                policy.validate(ChatMessageType.VOICE, file, 12);

        assertThat(validated.kind()).isEqualTo(AttachmentKind.VOICE);
        assertThat(validated.contentType()).isEqualTo("audio/webm");
        assertThat(validated.durationSeconds()).isEqualTo(12);
    }

    @Test
    void shouldRejectVoiceAboveDurationLimit() {
        byte[] content = {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3};
        MockMultipartFile file = new MockMultipartFile("file", "voz.webm",
                "audio/webm", content);

        assertThatThrownBy(() -> policy.validate(ChatMessageType.VOICE, file, 301))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    private byte[] zipWith(String entryName) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    @Test
    void shouldRejectTextTypeOnAttachmentEndpoint() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt",
                "text/plain", "oi".getBytes());

        assertThatThrownBy(() -> policy.validate(ChatMessageType.TEXT, file, null))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
