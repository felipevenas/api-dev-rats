package com.devstreak.dto;

public record PasswordResetResponse(String message, String resetToken) {

    public static PasswordResetResponse accepted(String resetToken) {
        return new PasswordResetResponse("Se o e-mail existir, enviaremos as instruções de recuperação.", resetToken);
    }
}
