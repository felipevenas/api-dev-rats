package com.devrats.utils;

import com.devrats.exceptions.ValidationException;

public final class PasswordPolicyHelper {

    private PasswordPolicyHelper() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < 10) {
            throw new ValidationException("A senha deve ter pelo menos 10 caracteres");
        }
        if (password.length() > 128) {
            throw new ValidationException("A senha deve ter no máximo 128 caracteres");
        }
        if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")
                || !password.matches(".*\\d.*") || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new ValidationException("A senha deve conter maiúscula, minúscula, número e caractere especial");
        }
    }
}
