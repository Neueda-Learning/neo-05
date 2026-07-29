package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to make a decision on a REFERRED application.
 * Accepts ACCEPTED or REJECTED outcomes only.
 */
public record ReferredDecisionRequest(
        @NotBlank(message = "must not be blank") String decision,
        @NotBlank(message = "must not be blank") String reason,
        @NotBlank(message = "must not be blank") String operator) {
}
