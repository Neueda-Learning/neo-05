package com.neobank.module.dto;

/** One case whose outcome flips under the draft policy. */
public record WhatIfChangeView(
        String applicationId,
        String from,
        String to) {
}
