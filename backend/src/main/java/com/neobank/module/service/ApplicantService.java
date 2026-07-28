package com.neobank.module.service;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import org.springframework.stereotype.Service;

/** Reads applicant data live from its owner and performs no local persistence. */
@Service
public class ApplicantService {

    private final OrchestratorClient orchestrator;

    public ApplicantService(OrchestratorClient orchestrator) {
        this.orchestrator = orchestrator;
    }

    public ApplicantView getApplicant(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("applicationId is required");
        }

        try {
            Application application = orchestrator.fetchApplication(applicationId);
            if (application == null) {
                throw new IllegalStateException("orchestrator returned an empty application");
            }
            return ApplicantView.of(application);
        } catch (Exception exception) {
            throw new ApplicantUnavailableException(applicationId, exception);
        }
    }
}
