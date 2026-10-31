package com.devrats.utils;

import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarImageProcessorTest {

    private static final long MAX_BYTES = 2_097_152;

    @Test
    void shouldReturn512Jpeg_whenImageIsValidPng() throws IOException {
        byte[] source = imageBytes(800, 600, "png");

        byte[] result = AvatarImageProcessor.process(source, MAX_BYTES);

        assertThat(result[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result[1] & 0xFF).isEqualTo(0xD8);
        BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(normalized.getWidth()).isEqualTo(AvatarImageProcessor.TARGET_SIZE);
        assertThat(normalized.getHeight()).isEqualTo(AvatarImageProcessor.TARGET_SIZE);
    }

    @Test
    void shouldReturnJpeg_whenImageIsValidJpeg() throws IOException {
        byte[] result = AvatarImageProcessor.process(imageBytes(300, 300, "jpg"), MAX_BYTES);

        assertThat(result[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void shouldReject_whenContentIsNotAnImage() {
        byte[] source = "isto-nao-e-uma-imagem".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> AvatarImageProcessor.process(source, MAX_BYTES))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AVATAR_INVALID_FORMAT);
    }

    @Test
    void shouldReject_whenDimensionsAreTooSmall() throws IOException {
        byte[] source = imageBytes(32, 32, "png");

        assertThatThrownBy(() -> AvatarImageProcessor.process(source, MAX_BYTES))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AVATAR_INVALID_DIMENSIONS);
    }

    @Test
    void shouldReject_whenDimensionsAreTooLarge() throws IOException {
        byte[] source = imageBytes(2049, 100, "png");

        assertThatThrownBy(() -> AvatarImageProcessor.process(source, MAX_BYTES))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AVATAR_INVALID_DIMENSIONS);
    }

    @Test
    void shouldReject_whenPayloadExceedsLimit() {
        byte[] source = new byte[1025];
        source[0] = (byte) 0xFF;
        source[1] = (byte) 0xD8;
        source[2] = (byte) 0xFF;

        assertThatThrownBy(() -> AvatarImageProcessor.process(source, 1024))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AVATAR_TOO_LARGE);
    }

    private byte[] imageBytes(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height,
                "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.ORANGE);
        graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
