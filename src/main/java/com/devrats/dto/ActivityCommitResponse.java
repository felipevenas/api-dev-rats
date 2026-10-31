package com.devrats.dto;

import java.time.Instant;

/**
 * Commit que validou um dia do desafio.
 *
 * <p>Deliberadamente expõe apenas metadados de identidade e localização do commit:
 * mensagem e diff nunca saem da API (política de privacidade do projeto).</p>
 */
public record ActivityCommitResponse(
        String sha,
        String repositoryFullName,
        String commitUrl,
        Instant committedAt,
        String authorLogin
) {
}
