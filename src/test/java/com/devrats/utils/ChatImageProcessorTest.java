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
    void shouldApplyExifOrientationBeforeReencoding() throws Exception {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        byte[] withExif = withOrientation(output.toByteArray(), 6);

        ChatImageProcessor.ProcessedImage processed = ChatImageProcessor.process(withExif, 8_388_608);

        assertThat(ChatImageProcessor.readOrientation(withExif)).isEqualTo(6);
        assertThat(processed.width()).isEqualTo(100);
        assertThat(processed.height()).isEqualTo(200);
    }

    @Test
    void shouldRejectNonImagePayload() {
        assertThatThrownBy(() -> ChatImageProcessor.process("not-an-image".getBytes(), 1024))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ATTACHMENT_INVALID_FORMAT);
    }

    private byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] exif = {
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0, 42, 0, 0, 0, 8,
                0, 1,
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
                0, 0, 0, 0
        };
        int length = exif.length + 2;
        byte[] app1 = new byte[4 + exif.length];
        app1[0] = (byte) 0xFF;
        app1[1] = (byte) 0xE1;
        app1[2] = (byte) (length >> 8);
        app1[3] = (byte) length;
        System.arraycopy(exif, 0, app1, 4, exif.length);

        byte[] result = new byte[jpeg.length + app1.length];
        result[0] = jpeg[0];
        result[1] = jpeg[1];
        System.arraycopy(app1, 0, result, 2, app1.length);
        System.arraycopy(jpeg, 2, result, 2 + app1.length, jpeg.length - 2);
        return result;
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
