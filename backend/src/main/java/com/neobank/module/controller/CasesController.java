package com.neobank.module.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.dto.CaseStatusUpdateRequest;
import com.neobank.module.dto.CaseView;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.dto.ReferredDecisionRequest;
import com.neobank.module.service.ApplicationService;

import jakarta.validation.Valid;

/**
 * UC 02 — Review Decision Workings · UC 03 — View Applicant · UC 04 — Manual Review Override.
 *
 * <p>GET endpoints are read-only. The numbers in the response were stored at /execute time; this controller
 * replays them, it never recalculates. Unknown id → 404 via
 * {@link GlobalExceptionHandler#handleNotFound}.</p>
 *
 * <p>PUT endpoint for manually accepting or rejecting referred decisions.</p>
 */
@RestController
@RequestMapping({"/cases", "/api/v1/cases"})
public class CasesController {

    private final ApplicationService applications;

    public CasesController(ApplicationService applications) {
        this.applications = applications;
    }

    /**
     * UC 02 — Get the stored decision and its workings.
     */
    @GetMapping("/{applicationId}")
    public ResponseEntity<CaseView> getCase(@PathVariable String applicationId) {
        return ResponseEntity.ok(applications.getCase(applicationId));
    }

    /**
     * UC 03 — Get the applicant details by proxying the orchestrator.
     * The applicant data is fetched live and never persisted in this module.
     */
    @GetMapping("/{applicationId}/applicant")
    public ResponseEntity<ApplicantViewDto> getApplicant(@PathVariable String applicationId) {
        return ResponseEntity.ok(applications.getApplicant(applicationId));
    }

    @PostMapping("/{applicationId}/override")
    public ResponseEntity<CaseView> overrideCase(@PathVariable String applicationId,
                                                 @Valid @RequestBody OverrideCaseRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(applications.overrideCase(applicationId, request));
    }

    /**
     * Decide on a REFERRED application — accept or reject it.
     * Only available for cases with REFERRED status.
     */
    @PostMapping("/{applicationId}/decide-referred")
    public ResponseEntity<CaseView> decideReferredCase(@PathVariable String applicationId,
                                                        @Valid @RequestBody ReferredDecisionRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(applications.decideReferredCase(applicationId, request));
    }
  
    /**
     * UC 04 — Manual review override: accept or decline a referred application.
     * Updates the local decision record and reports the outcome back to the orchestrator.
     */
    @PutMapping("/{applicationId}")
    public ResponseEntity<Void> updateCaseStatus(
            @PathVariable String applicationId,
            @RequestBody CaseStatusUpdateRequest request) {
        applications.updateCaseStatus(applicationId, request.status(), request.comment());
        return ResponseEntity.ok().build();
    }
}
