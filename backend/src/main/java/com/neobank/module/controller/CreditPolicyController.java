package com.neobank.module.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.neobank.module.dto.CreditPolicyRequest;
import com.neobank.module.dto.CreditPolicyView;
import com.neobank.module.service.CreditPolicyService;

import jakarta.validation.Valid;

/**
 * UC06: Risk manager changes credit policy without deploy.
 * - GET /api/v1/credit-policy — fetch current policy for editor prefill (from UC05 simulator)
 * - POST /api/v1/credit-policy — create new version with validation
 */
@RestController
@RequestMapping("/api/v1/credit-policy")
public class CreditPolicyController {

    private final CreditPolicyService policies;

    public CreditPolicyController(CreditPolicyService policies) {
        this.policies = policies;
    }

    /**
     * Get the current credit policy version, prefilled for editing.
     * Invoked by PolicyEditorScreen when opened from UC05 simulator.
     */
    @GetMapping
    public ResponseEntity<CreditPolicyView> getCurrentPolicy() {
        CreditPolicyView current = policies.getCurrentPolicy();
        return ResponseEntity.ok(current);
    }

    /**
     * List all policy versions, newest first.
     */
    @GetMapping("/versions")
    public ResponseEntity<List<CreditPolicyView>> listPolicies(
            @RequestParam(name = "policyCode", required = false) String policyCode) {
        List<CreditPolicyView> versions = policies.listPolicies(policyCode);
        return ResponseEntity.ok(versions);
    }

    /**
     * Get one specific policy version.
     */
    @GetMapping("/{version}")
    public ResponseEntity<CreditPolicyView> getPolicyVersion(
            @PathVariable int version,
            @RequestParam(name = "policyCode", required = false) String policyCode) {
        CreditPolicyView view = policies.getPolicyVersion(version, policyCode);
        return ResponseEntity.ok(view);
    }

    /**
     * Create a new policy version. Validates all constraints.
     * Increments version number automatically (current = MAX(version)).
     */
    @PostMapping
    public ResponseEntity<CreditPolicyView> createVersion(@Valid @RequestBody CreditPolicyRequest request) {
        CreditPolicyView created = policies.createVersion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
