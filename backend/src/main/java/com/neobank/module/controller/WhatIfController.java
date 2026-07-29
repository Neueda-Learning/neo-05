package com.neobank.module.controller;

import com.neobank.module.dto.WhatIfRequest;
import com.neobank.module.dto.WhatIfResponse;
import com.neobank.module.service.WhatIfService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC05 read-only what-if simulation endpoint. */
@RestController
@RequestMapping("/api/v1/what-if")
public class WhatIfController {

    private final WhatIfService whatIfService;

    public WhatIfController(WhatIfService whatIfService) {
        this.whatIfService = whatIfService;
    }

    @PostMapping
    public ResponseEntity<WhatIfResponse> simulate(@Valid @RequestBody WhatIfRequest request) {
        return ResponseEntity.ok(whatIfService.simulate(request.draft()));
    }
}
