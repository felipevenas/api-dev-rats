package com.devrats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Envio de solicitação de amizade.
 *
 * <p>Aceita maiúsculas na entrada e normaliza para minúsculas no serviço; o
 * formato é o mesmo do cadastro para evitar divergência entre busca e envio.</p>
 */
public record SendFriendRequestRequest(
        @NotBlank
        @Size(min = 2, max = 30)
        @Pattern(regexp = "^[a-zA-Z0-9_]{2,30}$", message = "use 2-30 caracteres: letras, números ou _")
        String username
) {
}
