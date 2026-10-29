package com.devstreak.exceptions;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dados inválidos"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Não autenticado"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Acesso negado"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Usuário não encontrado"),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "Grupo não encontrado"),
    GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Participante não encontrado"),
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "Convite não encontrado"),
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Desafio não encontrado"),
    REPOSITORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Repositório não encontrado"),
    RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "Resultado não encontrado"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Conflito de dados"),
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "E-mail já cadastrado"),
    USERNAME_ALREADY_USED(HttpStatus.CONFLICT, "Nome de usuário já cadastrado"),
    GITHUB_ALREADY_CONNECTED(HttpStatus.CONFLICT, "Conta do GitHub já conectada"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "Usuário já é membro do grupo"),
    GROUP_FULL(HttpStatus.CONFLICT, "Grupo lotado"),
    INVITE_EXPIRED(HttpStatus.CONFLICT, "Convite expirado"),
    INVITE_REVOKED(HttpStatus.CONFLICT, "Convite revogado"),
    INVITE_EXHAUSTED(HttpStatus.CONFLICT, "Convite esgotado"),
    ADMISSION_CLOSED(HttpStatus.CONFLICT, "Período de admissão encerrado"),
    CHALLENGE_ALREADY_OPEN(HttpStatus.CONFLICT, "Já existe um desafio aberto neste grupo"),
    INVALID_CHALLENGE_STATE(HttpStatus.CONFLICT, "Estado do desafio não permite esta operação"),
    REPOSITORY_LIMIT_REACHED(HttpStatus.CONFLICT, "Limite de repositórios atingido"),
    REPOSITORY_ALREADY_LINKED(HttpStatus.CONFLICT, "Repositório já vinculado ao desafio"),
    REPOSITORIES_LOCKED(HttpStatus.CONFLICT, "Repositórios não podem ser alterados após o início"),
    GITHUB_NOT_CONNECTED(HttpStatus.PRECONDITION_FAILED, "Conta do GitHub não conectada"),
    GITHUB_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "GitHub indisponível"),
    GITHUB_INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "Assinatura do webhook inválida"),
    GITHUB_INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, "Payload do webhook inválido"),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Payload muito grande"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Muitas requisições"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
