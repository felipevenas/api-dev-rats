package com.devrats.utils;

import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
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
 * Normaliza imagens enviadas no chat.
 *
 * <p>Valida o formato por magic bytes, limita dimensões, preserva a proporção,
 * reencoda em JPEG e remove EXIF/metadados. Diferente do avatar, não faz crop
 * quadrado: a foto do chat mantém o enquadramento original.</p>
 */
public final class ChatImageProcessor {

    public static final int MAX_OUTPUT_DIMENSION = 2048;
    public static final int MAX_SOURCE_DIMENSION = 4096;

    private static final float JPEG_QUALITY = 0.85f;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ChatImageProcessor() {
    }

    public static ProcessedImage process(byte[] source, long maxBytes) {
        validatePayload(source, maxBytes);
        BufferedImage image = readImage(source);
        BufferedImage normalized = scale(image);
        return new ProcessedImage(encodeJpeg(normalized), normalized.getWidth(), normalized.getHeight());
    }

    public record ProcessedImage(byte[] bytes, int width, int height) {
    }

    private static void validatePayload(byte[] source, long maxBytes) {
        if (source == null || source.length == 0) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Arquivo de imagem vazio");
        }
        if (source.length > maxBytes) {
            throw new DomainException(ErrorCode.ATTACHMENT_TOO_LARGE,
                    "A imagem deve ter no máximo " + (maxBytes / 1024 / 1024) + " MB");
        }
        if (!isJpeg(source) && !isPng(source)) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT,
                    "Envie uma imagem JPEG ou PNG");
        }
    }

    private static BufferedImage readImage(byte[] source) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Arquivo de imagem inválido");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Arquivo de imagem inválido");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION) {
                    throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT,
                            "Dimensões de imagem inválidas");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Arquivo de imagem inválido");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Arquivo de imagem inválido");
        }
    }

    private static BufferedImage scale(BufferedImage image) {
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        double ratio = Math.min(1.0, (double) MAX_OUTPUT_DIMENSION / Math.max(sourceWidth, sourceHeight));
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * ratio));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * ratio));

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            //---- PNG com transparência vira JPEG: compor sobre branco evita bordas escuras.
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
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
