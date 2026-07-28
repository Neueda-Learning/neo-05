package com.neobank.module.controller;

import com.neobank.module.dto.CaseView;
import com.neobank.module.service.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC 02 — Review Decision Workings.
 *
 * <p>Read-only. The numbers in the response were stored at /execute time; this controller
 * replays them, it never recalculates. Unknown id → 404 via
 * {@link GlobalExceptionHandler#handleNotFound}.</p>
 */
@RestController
@RequestMapping("/cases")
public class CasesController {

    private final ApplicationService applications;

    public CasesController(ApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<CaseView> getCase(@PathVariable String applicationId) {
        return ResponseEntity.ok(applications.getCase(applicationId));
    }
}
