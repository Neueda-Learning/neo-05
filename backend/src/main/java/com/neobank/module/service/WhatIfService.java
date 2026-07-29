package com.neobank.module.service;

import com.neobank.module.dto.CreditPolicyRequest;
import com.neobank.module.dto.ProductTermDTO;
import com.neobank.module.dto.WhatIfChangeView;
import com.neobank.module.dto.WhatIfResponse;
import com.neobank.module.model.CreditRecord;
import com.neobank.module.repository.CreditRecordRepository;
import com.neobank.module.service.decision.CreditApplicationInput;
import com.neobank.module.service.decision.CreditConfiguration;
import com.neobank.module.service.decision.CreditDecisionEngine;
import com.neobank.module.service.decision.CreditDecisionResult;
import com.neobank.module.service.decision.CreditProduct;
import com.neobank.module.service.decision.ProductTerms;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC05: simulate draft policy against stored inputs without writes or callbacks. */
@Service
public class WhatIfService {

    private static final List<String> DECIDED_OUTCOMES = List.of(
            CreditRecord.STATUS_REJECTED,
            CreditRecord.STATUS_REFERRED,
            CreditRecord.STATUS_ACCEPTED
    );

    private final CreditRecordRepository creditRecords;
    private final CreditDecisionEngine engine;

    public WhatIfService(CreditRecordRepository creditRecords) {
        this.creditRecords = creditRecords;
        this.engine = new CreditDecisionEngine();
    }

    @Transactional(readOnly = true)
    public WhatIfResponse simulate(CreditPolicyRequest draft) {
        CreditConfiguration configuration = toConfiguration(draft);
        List<CreditRecord> rows = creditRecords
                .findByOutcomeInOrderBySubmittedAtDescApplicationIdDesc(DECIDED_OUTCOMES);

        int evaluated = 0;
        List<WhatIfChangeView> flips = new ArrayList<>();
        for (CreditRecord row : rows) {
            // Historical rows that never finished scoring (e.g. a module error left them
            // REFERRED with no product code) can't be replayed through the engine — skip
            // rather than let one bad row fail the whole simulation.
            if (row.getProductCode() == null || row.getProductCode().isBlank()) {
                continue;
            }
            evaluated++;
            CreditDecisionResult simulated = engine.evaluate(toInput(row), configuration);
            String from = baselineOutcome(row);
            String to = simulated.outcome().name();
            if (!from.equals(to)) {
                flips.add(new WhatIfChangeView(row.getApplicationId(), from, to));
            }
        }

        return new WhatIfResponse(evaluated, flips.size(), flips);
    }

    private CreditApplicationInput toInput(CreditRecord row) {
        return new CreditApplicationInput(
                normalizeProductCodeForEngine(row.getProductCode()),
                nz(row.getAnnualIncome()),
                nz(row.getMonthlyOutgoings()),
                0,
                positiveRequestedLimit(row.getRequestedLimit()));
    }

    // sampled referred cases should compare from machine outcome.
    private String baselineOutcome(CreditRecord row) {
        if (row.isSampled() && CreditRecord.STATUS_REFERRED.equals(row.getOutcome())) {
            return row.getMachineOutcome();
        }
        return row.getOutcome();
    }

    private CreditConfiguration toConfiguration(CreditPolicyRequest draft) {
        validateDraftProducts(draft.productTerms());
        Map<CreditProduct, ProductTerms> terms = draft.productTerms().stream()
                .collect(Collectors.toMap(
                        term -> CreditProduct.fromCode(
                                normalizeProductCodeForEngine(term.productCode())),
                        term -> new ProductTerms(
                                term.minIncome(),
                                term.maxLimit(),
                                term.apr())));
        return new CreditConfiguration(
                1,
                terms,
                draft.dtiLimit(),
                draft.roundingStep());
    }

    private void validateDraftProducts(List<ProductTermDTO> terms) {
        if (terms == null || terms.isEmpty()) {
            throw new IllegalArgumentException("product_terms required");
        }
        Set<CreditProduct> products = terms.stream()
                .map(term -> CreditProduct.fromCode(
                        normalizeProductCodeForEngine(term.productCode())))
                .collect(Collectors.toSet());
        if (products.size() != CreditProduct.values().length) {
            throw new IllegalArgumentException("all three products are required");
        }
    }

    // Mapping required by the module brief:
    // low_rate/standard -> premium, rewards -> platinum, student unchanged.
    private String normalizeProductCodeForEngine(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("productCode is required");
        }
        return switch (rawCode.trim().toUpperCase()) {
            case "CREDIT_CARD_STANDARD", "STANDARD", "CREDIT_CARD_LOW_RATE", "LOW_RATE", "PREMIUM"
                    -> "CREDIT_CARD_PREMIUM";
            case "CREDIT_CARD_REWARDS", "REWARDS", "PLATINUM"
                    -> "CREDIT_CARD_PLATINUM";
            case "CREDIT_CARD_STUDENT", "STUDENT"
                    -> "CREDIT_CARD_STUDENT";
            default -> rawCode;
        };
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private int positiveRequestedLimit(Integer value) {
        int requested = nz(value);
        return requested <= 0 ? 1 : requested;
    }
}
