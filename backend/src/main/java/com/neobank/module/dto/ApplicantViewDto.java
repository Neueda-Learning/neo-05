package com.neobank.module.dto;

import com.neobank.module.integrations.orchestrator.Application;

/**
 * The read model for GET /cases/{applicationId}/applicant — applicant details fetched live
 * from the orchestrator. This is a live proxy, never persisted.
 */
public record ApplicantViewDto(
        String fullName,
        String dateOfBirth,
        String employmentStatus,
        FinancesView finances,
        int requestedCreditLimit) {

    public record FinancesView(
            int annualIncome,
            int monthlyHousingCost,
            int existingCreditCommitments) {}

    public static ApplicantViewDto of(Application application) {
        int annualIncome = 0;
        int monthlyHousingCost = 0;
        int existingCreditCommitments = 0;
        int requestedCreditLimit = 0;
        String employmentStatus = "";

        if (application.finances() != null) {
            annualIncome = application.finances().annualIncome() != null
                    ? application.finances().annualIncome()
                    : 0;
            monthlyHousingCost = application.finances().monthlyHousingCost() != null
                    ? application.finances().monthlyHousingCost()
                    : 0;
            existingCreditCommitments = application.finances().existingCreditCommitments() != null
                    ? application.finances().existingCreditCommitments()
                    : 0;
        }

        if (application.product() != null && application.product().requestedCreditLimit() != null) {
            requestedCreditLimit = application.product().requestedCreditLimit();
        }

        if (application.employment() != null && application.employment().status() != null) {
            employmentStatus = application.employment().status();
        }

        return new ApplicantViewDto(
                application.applicant().fullName(),
                application.applicant().dateOfBirth(),
                employmentStatus,
                new FinancesView(annualIncome, monthlyHousingCost, existingCreditCommitments),
                requestedCreditLimit);
    }
}
