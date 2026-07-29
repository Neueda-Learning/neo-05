package com.neobank.module.controller;

import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.dto.CaseView;
import com.neobank.module.dto.OverrideCaseRequest;
import jakarta.validation.Valid;
import com.neobank.module.service.ApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC 02 — Review Decision Workings · UC 03 — View Applicant.
 *
 * <p>Read-only. The numbers in the response were stored at /execute time; this controller
 * replays them, it never recalculates. Unknown id → 404 via
 * {@link GlobalExceptionHandler#handleNotFound}.</p>
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
}
