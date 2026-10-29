package com.devstreak.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class SignatureHelper {

    private static final String ALGORITHM = "HmacSHA256";

    private SignatureHelper() {
    }

    //---- GitHub sends "sha256=<hex>"; compare decoded bytes in constant time.
    public static boolean isValidSha256(byte[] payload, String secret, String header) {
        if (payload == null || secret == null || secret.isBlank() || header == null || !header.startsWith("sha256=")) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().parseHex(header.substring("sha256=".length()));
            byte[] actual = hmacSha256(payload, secret);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static byte[] hmacSha256(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular HMAC", e);
        }
    }
}
