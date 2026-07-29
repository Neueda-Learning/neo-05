package com.neobank.module.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** UC05 request payload for what-if simulation. */
public record WhatIfRequest(
        @JsonProperty("draft")
        @NotNull(message = "draft required")
        @Valid
        CreditPolicyRequest draft) {
}
