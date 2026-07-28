package com.neobank.module.service;

/** Signals that live applicant data could not be fetched from the owning orchestrator. */
public class ApplicantUnavailableException extends RuntimeException {

    public ApplicantUnavailableException(String applicationId, Throwable cause) {
        super("applicant data is temporarily unavailable for " + applicationId, cause);
    }
}
