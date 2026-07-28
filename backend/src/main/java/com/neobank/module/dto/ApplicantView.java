package com.neobank.module.dto;

import com.neobank.module.integrations.orchestrator.Application;

import java.util.Objects;

/** Live, non-persisted subset of the orchestrator-owned application used by the case sidebar. */
public record ApplicantView(
        Applicant applicant,
        Employment employment,
        Finances finances,
        Product product) {

    public static ApplicantView of(Application application) {
        Objects.requireNonNull(application, "application is required");

        Application.Applicant sourceApplicant = application.applicant();
        Application.Employment sourceEmployment = application.employment();
        Application.Finances sourceFinances = application.finances();
        Application.Product sourceProduct = application.product();

        return new ApplicantView(
                sourceApplicant == null ? null : new Applicant(
                        sourceApplicant.fullName(), sourceApplicant.dateOfBirth()),
                sourceEmployment == null ? null : new Employment(sourceEmployment.status()),
                sourceFinances == null ? null : new Finances(
                        sourceFinances.annualIncome(),
                        sourceFinances.monthlyHousingCost(),
                        sourceFinances.existingCreditCommitments()),
                sourceProduct == null ? null : new Product(sourceProduct.requestedCreditLimit()));
    }

    public record Applicant(String fullName, String dateOfBirth) { }

    public record Employment(String status) { }

    public record Finances(
            Integer annualIncome,
            Integer monthlyHousingCost,
            Integer existingCreditCommitments) { }

    public record Product(Integer requestedCreditLimit) { }
}
