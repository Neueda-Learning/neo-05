package com.neobank.module.dto;

import java.util.List;
import java.util.Objects;

/** A bounded UC-01 result page and whether another match exists beyond that page. */
public record CaseSearchResponse(
        List<CaseSearchItem> cases,
        boolean more) {

    public CaseSearchResponse {
        cases = List.copyOf(Objects.requireNonNull(cases, "cases are required"));
    }
}
