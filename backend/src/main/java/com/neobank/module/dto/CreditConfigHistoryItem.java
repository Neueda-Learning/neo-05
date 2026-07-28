package com.neobank.module.dto;

import java.time.Instant;

public record CreditConfigHistoryItem(int version, Instant effectiveFrom) {}
