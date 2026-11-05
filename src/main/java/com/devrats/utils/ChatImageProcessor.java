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
import java.awt.geom.AffineTransform;
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
        BufferedImage image = applyOrientation(readImage(source), readOrientation(source));
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
                BufferedImage image;
                try {
                    image = reader.read(0);
                } catch (RuntimeException exception) {
                    throw new DomainException(ErrorCode.ATTACHMENT_INVALID_FORMAT, "Arquivo de imagem inválido");
                }
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

    /**
     * Lê a tag EXIF Orientation (0x0112) do APP1 do JPEG. O ImageIO do JDK não
     * aplica a orientação automaticamente; sem isso, fotos de celular podem
     * aparecer giradas depois de removermos os metadados.
     */
    static int readOrientation(byte[] source) {
        if (source.length < 4 || (source[0] & 0xFF) != 0xFF || (source[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int position = 2;
        while (position + 4 <= source.length) {
            if ((source[position] & 0xFF) != 0xFF) {
                position++;
                continue;
            }
            int marker = source[position + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0xD9) {
                position += 2;
                continue;
            }
            if (marker == 0xDA) {
                return 1;
            }
            int length = ((source[position + 2] & 0xFF) << 8) | (source[position + 3] & 0xFF);
            if (length < 2 || position + 2 + length > source.length) {
                return 1;
            }
            if (marker == 0xE1 && length >= 8 && matchesAscii(source, position + 4, "Exif\0\0")) {
                int orientation = parseTiffOrientation(source, position + 10, position + 2 + length);
                return orientation >= 1 && orientation <= 8 ? orientation : 1;
            }
            position += 2 + length;
        }
        return 1;
    }

    private static int parseTiffOrientation(byte[] data, int tiffStart, int segmentEnd) {
        if (tiffStart + 8 > segmentEnd) {
            return 1;
        }
        boolean littleEndian;
        if (data[tiffStart] == 'I' && data[tiffStart + 1] == 'I') {
            littleEndian = true;
        } else if (data[tiffStart] == 'M' && data[tiffStart + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        if (readU16(data, tiffStart + 2, littleEndian) != 42) {
            return 1;
        }
        long ifdOffset = readU32(data, tiffStart + 4, littleEndian);
        long ifd = tiffStart + ifdOffset;
        if (ifd < tiffStart || ifd + 2 > segmentEnd) {
            return 1;
        }
        int entries = readU16(data, (int) ifd, littleEndian);
        int entryStart = (int) ifd + 2;
        for (int index = 0; index < entries; index++) {
            int entry = entryStart + index * 12;
            if (entry + 12 > segmentEnd) {
                return 1;
            }
            int tag = readU16(data, entry, littleEndian);
            int type = readU16(data, entry + 2, littleEndian);
            long count = readU32(data, entry + 4, littleEndian);
            if (tag == 0x0112 && type == 3 && count == 1) {
                long valueOffset = readU32(data, entry + 8, littleEndian);
                int valueAddress = count * 2 <= 4 ? entry + 8 : (int) (tiffStart + valueOffset);
                if (valueAddress + 2 > segmentEnd) {
                    return 1;
                }
                return readU16(data, valueAddress, littleEndian);
            }
        }
        return 1;
    }

    private static int readU16(byte[] data, int offset, boolean littleEndian) {
        int first = data[offset] & 0xFF;
        int second = data[offset + 1] & 0xFF;
        return littleEndian ? (second << 8) | first : (first << 8) | second;
    }

    private static long readU32(byte[] data, int offset, boolean littleEndian) {
        long first = data[offset] & 0xFFL;
        long second = data[offset + 1] & 0xFFL;
        long third = data[offset + 2] & 0xFFL;
        long fourth = data[offset + 3] & 0xFFL;
        return littleEndian
                ? (fourth << 24) | (third << 16) | (second << 8) | first
                : (first << 24) | (second << 16) | (third << 8) | fourth;
    }

    private static boolean matchesAscii(byte[] data, int offset, String value) {
        if (offset + value.length() > data.length) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (data[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        boolean swap = orientation >= 5;
        int targetWidth = swap ? sourceHeight : sourceWidth;
        int targetHeight = swap ? sourceWidth : sourceHeight;
        BufferedImage oriented = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = oriented.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(image, affineFor(orientation, sourceWidth, sourceHeight), null);
        } finally {
            graphics.dispose();
        }
        return oriented;
    }

    private static AffineTransform affineFor(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
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
