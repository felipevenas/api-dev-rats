package com.devrats.dto;

import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @Size(min = 2, max = 120) String name,
        @Size(max = 500) String description
) {
}
