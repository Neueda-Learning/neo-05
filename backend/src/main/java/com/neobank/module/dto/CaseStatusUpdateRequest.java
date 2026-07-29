package com.neobank.module.dto;

/**
 * Request to update a case status after manual review.
 * Used when a referred decision needs to be accepted or rejected.
 *
 * @param status  {@code ACCEPTED} · {@code REJECTED} — the manual override
 * @param comment reason for the decision, to be reported back to the orchestrator
 */
public record CaseStatusUpdateRequest(
        String status,
        String comment) {
}
