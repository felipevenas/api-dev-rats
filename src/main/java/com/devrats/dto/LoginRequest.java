package com.devrats.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Credenciais de login.
 *
 * <p>{@code identifier} aceita e-mail ou nome de usuário e é o formato recomendado.
 * {@code email} permanece apenas para compatibilidade com clientes antigos.</p>
 */
public record LoginRequest(
        @Deprecated(since = "login por identificador")
        @Email(message = "E-mail inválido")
        @Size(max = 320, message = "E-mail inválido")
        String email,
        @Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "Identificador inválido")
        @Size(max = 320, message = "Identificador inválido")
        String identifier,
        @NotBlank(message = "Senha é obrigatória")
        @Size(max = 128, message = "Senha inválida")
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
