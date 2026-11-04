package com.devrats.enums;

import com.devrats.exceptions.ValidationException;

import java.util.Locale;

/**
 * Direção de uma solicitação em relação a quem consulta.
 */
public enum FriendRequestDirection {
    RECEIVED,
    SENT;

    /**
     * Converte o parâmetro de query (case-insensitive) no enum.
     *
     * @throws ValidationException quando o valor não é {@code received} nem {@code sent}
     */
    public static FriendRequestDirection from(String value) {
        if (value == null || value.isBlank()) {
            return RECEIVED;
        }
        for (FriendRequestDirection direction : values()) {
            if (direction.name().equals(value.trim().toUpperCase(Locale.ROOT))) {
                return direction;
            }
        }
        throw new ValidationException("direction deve ser received ou sent");
    }
}
