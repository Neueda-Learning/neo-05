package com.neobank.module.controller;

import com.neobank.module.dto.CaseSearchResponse;
import com.neobank.module.dto.ApplicantView;
import com.neobank.module.service.ApplicantService;
import com.neobank.module.service.CaseSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-01 read-only search surface for the Credit Board. */
@RestController
@RequestMapping({"/cases", "/api/v1/cases"})
public class CaseSearchController {

    private final CaseSearchService caseSearch;
    private final ApplicantService applicants;

    public CaseSearchController(CaseSearchService caseSearch, ApplicantService applicants) {
        this.caseSearch = caseSearch;
        this.applicants = applicants;
    }

    @GetMapping
    public ResponseEntity<CaseSearchResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(caseSearch.search(q, limit));
    }

    @GetMapping("/{applicationId}/applicant")
    public ResponseEntity<ApplicantView> applicant(@PathVariable String applicationId) {
        return ResponseEntity.ok(applicants.getApplicant(applicationId));
    }
}
