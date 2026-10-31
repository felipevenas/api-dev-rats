package com.devrats.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciais de login.
 *
 * <p>{@code identifier} aceita e-mail ou nome de usuário e é o formato recomendado.
 * {@code email} permanece apenas para compatibilidade com clientes antigos.</p>
 */
public record LoginRequest(
        @Deprecated(since = "login por identificador")
        @Size(max = 320, message = "E-mail inválido")
        String email,
        @Size(max = 320, message = "Identificador inválido")
        String identifier,
        @NotBlank(message = "Senha é obrigatória")
        String password
) {

    @AssertTrue(message = "Informe o e-mail ou o nome de usuário")
    public boolean isIdentifierPresent() {
        return hasText(identifier) || hasText(email);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
