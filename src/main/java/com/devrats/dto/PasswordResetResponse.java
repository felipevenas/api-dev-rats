package com.devrats.dto;

public record PasswordResetResponse(String message, String resetToken) {

    public static PasswordResetResponse accepted(String resetToken) {
        return new PasswordResetResponse("Se houver uma conta com este e-mail, enviaremos um link para redefinir a senha.", resetToken);
    }
}
