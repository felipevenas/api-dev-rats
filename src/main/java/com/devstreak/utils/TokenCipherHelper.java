package com.devstreak.utils;

import com.devstreak.config.CryptoProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenCipherHelper {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final int keyId;
    private final SecureRandom random = new SecureRandom();

    public TokenCipherHelper(CryptoProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.getKey());
        if (keyBytes.length != 32) {
            throw new IllegalStateException("app.crypto.key deve ter 32 bytes em base64");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.keyId = properties.getKeyId();
    }

    public int getKeyId() {
        return keyId;
    }

    //---- Layout: 12-byte IV followed by ciphertext and the 16-byte GCM tag.
    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            //---- Prefix carries the key version so a rotation fails loudly instead of decrypting garbage.
            byte[] result = new byte[1 + iv.length + encrypted.length];
            result[0] = (byte) keyId;
            System.arraycopy(iv, 0, result, 1, iv.length);
            System.arraycopy(encrypted, 0, result, 1 + iv.length, encrypted.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar token", e);
        }
    }

    public String decrypt(byte[] value) {
        if (value == null || value.length <= 1 + IV_BYTES) {
            return null;
        }
        if ((value[0] & 0xFF) != keyId) {
            throw new IllegalStateException("Token cifrado com uma versão de chave diferente");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(value, 1, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(value, 1 + IV_BYTES, value.length - 1 - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decifrar token", e);
        }
    }
}
