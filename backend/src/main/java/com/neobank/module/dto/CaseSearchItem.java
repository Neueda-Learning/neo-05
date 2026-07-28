package com.neobank.module.dto;

import com.neobank.module.model.CreditRecord;
import java.time.Instant;
import java.util.Objects;

/** One local credit case returned by the UC-01 search endpoint. */
public record CaseSearchItem(
        String applicationId,
        Instant submittedAt,
        String outcome,
        Integer grantedLimit,
        boolean sampled) {

    public CaseSearchItem {
        Objects.requireNonNull(applicationId, "applicationId is required");
        Objects.requireNonNull(outcome, "outcome is required");
    }

    /** Map only locally stored decision data; applicant details are deliberately excluded. */
    public static CaseSearchItem of(CreditRecord record) {
        Objects.requireNonNull(record, "record is required");
        return new CaseSearchItem(
                record.getApplicationId(),
                record.getSubmittedAt(),
                record.apiStatus(),
                record.getGrantedLimit(),
                record.isSampled());
    }
}
