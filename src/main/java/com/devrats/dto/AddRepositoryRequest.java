package com.devrats.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddRepositoryRequest(@NotNull UUID githubRepositoryId) {
}
