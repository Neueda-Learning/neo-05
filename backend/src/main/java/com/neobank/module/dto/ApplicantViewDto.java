package com.neobank.module.dto;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.CreditRecord;

/**
 * The read model for GET /cases/{applicationId}/applicant — applicant details fetched live
 * from the orchestrator. This is a live proxy, never persisted.
 *
 * <p>When the orchestrator returns 404, a partial fallback is built from the stored
 * {@link CreditRecord}: only {@code fullName} (null) and {@code productCode} are meaningful;
 * all other fields are null / zero so the UI can display "—".</p>
 */
public record ApplicantViewDto(
        String fullName,
        String dateOfBirth,
        String employmentStatus,
        FinancesView finances,
        Integer requestedCreditLimit,
        String channel,
        String productCode,
        boolean partial) {

    public record FinancesView(
            Integer annualIncome,
            Integer monthlyHousingCost,
            Integer existingCreditCommitments) {}

    public static ApplicantViewDto of(Application application) {
        Integer annualIncome = null;
        Integer monthlyHousingCost = null;
        Integer existingCreditCommitments = null;
        Integer requestedCreditLimit = null;
        String employmentStatus = null;
        String productCode = null;

        if (application.finances() != null) {
            annualIncome = application.finances().annualIncome();
            monthlyHousingCost = application.finances().monthlyHousingCost();
            existingCreditCommitments = application.finances().existingCreditCommitments();
        }

        if (application.product() != null) {
            requestedCreditLimit = application.product().requestedCreditLimit();
            productCode = application.product().productCode();
        }

        if (application.employment() != null && application.employment().status() != null) {
            employmentStatus = application.employment().status();
        }

        return new ApplicantViewDto(
                application.applicant() != null ? application.applicant().fullName() : null,
                application.applicant() != null ? application.applicant().dateOfBirth() : null,
                employmentStatus,
                new FinancesView(annualIncome, monthlyHousingCost, existingCreditCommitments),
                requestedCreditLimit,
                application.channel(),
                productCode,
                false);
    }

    /** Fallback built from the stored credit record when the orchestrator returns 404. */
    public static ApplicantViewDto fromRecord(CreditRecord record) {
        return new ApplicantViewDto(
                null,
                null,
                null,
                new FinancesView(null, null, null),
                null,
                null,
                record.getProductCode(),
                true);
    }
}
