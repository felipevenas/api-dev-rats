package com.devrats.controllers;

import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.RateLimitException;
import com.devrats.security.TraceIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException exception) {
        return build(exception.getErrorCode().getStatus(), exception.getErrorCode().name(),
                exception.getErrorCode().getTitle(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getTitle(), "Um ou mais campos são inválidos");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException exception) {
        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getTitle(), exception.getMessage());
        problem.setProperty("errors", exception.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadRequest(Exception exception) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getTitle(), "Requisição inválida");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException exception) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.name(),
                ErrorCode.RESOURCE_NOT_FOUND.getTitle(), "Recurso não encontrado");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.VALIDATION_ERROR.name(),
                "Método não permitido", "Método HTTP não suportado para este recurso");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.name(),
                ErrorCode.FORBIDDEN.getTitle(), "Você não tem permissão para este recurso");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException exception) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.name(),
                ErrorCode.UNAUTHORIZED.getTitle(), "Não autenticado");
    }

    @ExceptionHandler(RateLimitException.class)
    public ProblemDetail handleRateLimit(RateLimitException exception) {
        ProblemDetail problem = build(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED.name(),
                ErrorCode.RATE_LIMITED.getTitle(), "Tente novamente em alguns instantes");
        problem.setProperty("retryAfterSeconds", exception.getRetryAfter().toSeconds());
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadSize(MaxUploadSizeExceededException exception) {
        return build(ErrorCode.PAYLOAD_TOO_LARGE.getStatus(), ErrorCode.PAYLOAD_TOO_LARGE.name(),
                ErrorCode.PAYLOAD_TOO_LARGE.getTitle(), "Arquivo enviado excede o limite");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException exception) {
        log.warn("Violação de integridade: {}", exception.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT.name(),
                ErrorCode.DATA_CONFLICT.getTitle(), "A operação conflita com o estado atual do recurso");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Erro não tratado", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
                ErrorCode.INTERNAL_ERROR.getTitle(), "Erro interno inesperado");
    }

    private Map<String, String> toFieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", error.getDefaultMessage() == null
                ? "Valor inválido" : error.getDefaultMessage());
    }

    private ProblemDetail build(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.devrats.app/problems/" + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
