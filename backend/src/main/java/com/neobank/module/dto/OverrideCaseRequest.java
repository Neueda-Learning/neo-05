package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;

public record OverrideCaseRequest(
        @NotBlank(message = "must not be blank") String newOutcome,
        Integer grantedLimit,
        @NotBlank(message = "must not be blank") String reason,
        @NotBlank(message = "must not be blank") String operator) {
}