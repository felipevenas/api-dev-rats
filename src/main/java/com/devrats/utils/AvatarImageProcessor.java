package com.devrats.utils;

import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Normaliza a foto de perfil enviada pelo usuário.
 *
 * <p>Valida o formato pelos magic bytes (o Content-Type do cliente não é confiável),
 * aplica center-crop quadrado, redimensiona para 512x512 e reencoda em JPEG,
 * descartando EXIF e demais metadados da imagem original.</p>
 */
public final class AvatarImageProcessor {

    public static final int TARGET_SIZE = 512;
    public static final int MIN_DIMENSION = 64;
    public static final int MAX_DIMENSION = 2048;

    private static final float JPEG_QUALITY = 0.85f;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private AvatarImageProcessor() {
    }

    public static byte[] process(byte[] source, long maxBytes) {
        validatePayload(source, maxBytes);
        BufferedImage image = readImage(source);
        validateDimensions(image.getWidth(), image.getHeight());
        BufferedImage normalized = cropAndResize(image);
        return encodeJpeg(normalized);
    }

    private static void validatePayload(byte[] source, long maxBytes) {
        if (source == null || source.length == 0) {
            throw new DomainException(ErrorCode.AVATAR_INVALID_FORMAT, "Arquivo de imagem vazio");
        }
        if (source.length > maxBytes) {
            throw new DomainException(ErrorCode.AVATAR_TOO_LARGE,
                    "A imagem deve ter no máximo " + (maxBytes / 1024 / 1024) + " MB");
        }
        if (!isJpeg(source) && !isPng(source)) {
            throw new DomainException(ErrorCode.AVATAR_INVALID_FORMAT,
                    "Envie uma imagem JPEG ou PNG");
        }
    }

    private static BufferedImage readImage(byte[] source) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw new DomainException(ErrorCode.AVATAR_INVALID_FORMAT, "Arquivo de imagem inválido");
            }
            return image;
        } catch (IOException exception) {
            throw new DomainException(ErrorCode.AVATAR_INVALID_FORMAT, "Arquivo de imagem inválido");
        }
    }

    private static void validateDimensions(int width, int height) {
        if (width < MIN_DIMENSION || height < MIN_DIMENSION
                || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new DomainException(ErrorCode.AVATAR_INVALID_DIMENSIONS,
                    "A imagem deve ter entre " + MIN_DIMENSION + " e " + MAX_DIMENSION + " pixels por lado");
        }
    }

    private static BufferedImage cropAndResize(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        int x = (image.getWidth() - side) / 2;
        int y = (image.getHeight() - side) / 2;
        BufferedImage cropped = image.getSubimage(x, y, side, side);

        BufferedImage resized = new BufferedImage(TARGET_SIZE, TARGET_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            //---- PNG com transparência vira JPEG: compor sobre branco evita bordas escuras.
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, TARGET_SIZE, TARGET_SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(cropped, 0, 0, TARGET_SIZE, TARGET_SIZE, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Codificador JPEG indisponível");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(image, null, null), params);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Falha ao processar a imagem");
        } finally {
            writer.dispose();
        }
    }

    private static boolean isJpeg(byte[] source) {
        return source.length >= 3
                && (source[0] & 0xFF) == 0xFF
                && (source[1] & 0xFF) == 0xD8
                && (source[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] source) {
        if (source.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (source[index] != PNG_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }
}
