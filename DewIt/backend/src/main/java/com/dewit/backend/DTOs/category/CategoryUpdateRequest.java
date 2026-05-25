package com.dewit.backend.DTOs.category;

import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @Size(max = 100) String name
) {
}
