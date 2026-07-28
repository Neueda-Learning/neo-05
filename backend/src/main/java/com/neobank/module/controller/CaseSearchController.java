package com.neobank.module.controller;

import com.neobank.module.dto.CaseSearchResponse;
import com.neobank.module.service.CaseSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-01 read-only search surface for the Credit Board. */
@RestController
@RequestMapping({"/cases", "/api/v1/cases"})
public class CaseSearchController {

    private final CaseSearchService caseSearch;

    public CaseSearchController(CaseSearchService caseSearch) {
        this.caseSearch = caseSearch;
    }

    @GetMapping
    public ResponseEntity<CaseSearchResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(caseSearch.search(q, limit));
    }
}
