package com.devrats.utils;

import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatImageProcessorTest {

    @Test
    void shouldReencodeAndKeepProportion() throws Exception {
        BufferedImage image = new BufferedImage(4000, 2000, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.GREEN);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);

        ChatImageProcessor.ProcessedImage processed = ChatImageProcessor.process(output.toByteArray(), 8_388_608);

        assertThat(processed.width()).isEqualTo(2048);
        assertThat(processed.height()).isEqualTo(1024);
        assertThat(processed.bytes()[0] & 0xFF).isEqualTo(0xFF);
        assertThat(processed.bytes()[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void shouldRejectNonImagePayload() {
        assertThatThrownBy(() -> ChatImageProcessor.process("not-an-image".getBytes(), 1024))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ATTACHMENT_INVALID_FORMAT);
    }

    @Test
    void shouldRejectPayloadAboveLimit() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);

        assertThatThrownBy(() -> ChatImageProcessor.process(output.toByteArray(), 10))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ATTACHMENT_TOO_LARGE);
    }
}
