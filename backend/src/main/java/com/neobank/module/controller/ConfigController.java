package com.neobank.module.controller;

import com.neobank.module.dto.CreateConfigCommand;
import com.neobank.module.dto.CreditConfigHistoryItem;
import com.neobank.module.dto.CreditConfigResponse;
import com.neobank.module.service.CreditConfigService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Credit-policy configuration — lets a risk manager update product terms, DTI limit and
 * sampling rate without a deploy.
 *
 * <p>All writes are insert-only: every POST creates a new version row and immediately becomes the
 * current policy. Existing decisions keep their pinned version forever.</p>
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final CreditConfigService creditConfigService;

    public ConfigController(CreditConfigService creditConfigService) {
        this.creditConfigService = creditConfigService;
    }

    /**
     * Create a new credit-config version.
     *
     * <p>Returns {@code 201 Created} with {@code {"version": N}} where N is the new version
     * number. Validation errors return {@code 400} with field-level detail.</p>
     */
    @PostMapping
    public ResponseEntity<Map<String, Integer>> createVersion(
            @Valid @RequestBody CreateConfigCommand cmd) {
        int version = creditConfigService.createVersion(cmd);
        return ResponseEntity.status(201).body(Map.of("version", version));
    }

    @GetMapping("/current")
    public CreditConfigResponse current() {
        return creditConfigService.current();
    }

    @GetMapping("/history")
    public List<CreditConfigHistoryItem> history() {
        return creditConfigService.history();
    }
}
