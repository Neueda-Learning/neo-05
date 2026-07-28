package com.neobank.module.service;

import com.neobank.module.dto.CreditPolicyRequest;
import com.neobank.module.dto.CreditPolicyView;
import com.neobank.module.dto.ProductTermDTO;
import com.neobank.module.model.CreditConfig;
import com.neobank.module.repository.CreditConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages credit policy versions. A version is the WHOLE config: all three products' terms
 * plus dtiLimit, roundingStep, sampleEvery.
 * current = MAX(version).
 *
 * Validation rules:
 * - all three catalogue products present
 * - minIncome ≥ 0, maxLimit > 0, apr > 0 with one decimal
 * - 0 < dtiLimit < 1
 * - sampleEvery ≥ 1
 */
@Service
public class CreditPolicyService {

    public static final Set<String> CATALOGUE_PRODUCTS = Set.of(
            "CREDIT_CARD_REWARDS",
            "CREDIT_CARD_LOW_RATE",
            "CREDIT_CARD_STUDENT"
    );

    private final CreditConfigRepository policies;
    private final ObjectMapper mapper;

    public CreditPolicyService(CreditConfigRepository policies, ObjectMapper mapper) {
        this.policies = policies;
        this.mapper = mapper;
    }

    /**
     * Get the current policy version (highest version number).
     * Includes parsed product terms.
     */
    @Transactional(readOnly = true)
    public CreditPolicyView getCurrentPolicy() {
        CreditConfig current = policies.findTopByOrderByVersionDesc()
                .orElseThrow(() -> new NoSuchElementException("no credit policy version exists"));
        List<ProductTermDTO> terms = parseProductTerms(current.getProductTerms());
        return CreditPolicyView.of(current, terms);
    }

    /**
     * Create a new policy version (increments version number).
     * Validates all constraints before persisting.
     *
     * @param request the new policy configuration
     * @return the created policy view
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public CreditPolicyView createVersion(CreditPolicyRequest request) {
        validate(request);

        int nextVersion = policies.findTopByOrderByVersionDesc()
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);

        String serializedTerms = serializeProductTerms(request.productTerms());

        CreditConfig version = new CreditConfig(
                nextVersion,
                serializedTerms,
                request.dtiLimit(),
                BigDecimal.valueOf(request.roundingStep()),
                request.sampleEvery(),
                null // will be set by @PrePersist
        );

        CreditConfig saved = policies.save(version);
        return CreditPolicyView.of(saved, request.productTerms());
    }

    /**
     * Validate the policy request against all constraints.
     */
    private void validate(CreditPolicyRequest request) {
        // Validate all three catalogue products present
        Set<String> providedProducts = request.productTerms().stream()
                .map(ProductTermDTO::productCode)
                .collect(java.util.stream.Collectors.toSet());

        Set<String> missing = new java.util.HashSet<>(CATALOGUE_PRODUCTS);
        missing.removeAll(providedProducts);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("missing products: " + missing);
        }

        if (providedProducts.size() > CATALOGUE_PRODUCTS.size()) {
            throw new IllegalArgumentException("unknown products in request");
        }

        // Individual validations (also enforced by @Valid on DTOs)
        for (ProductTermDTO term : request.productTerms()) {
            if (term.minIncome() < 0) {
                throw new IllegalArgumentException(
                        term.productCode() + ": minIncome must be >= 0");
            }
            if (term.maxLimit() <= 0) {
                throw new IllegalArgumentException(
                        term.productCode() + ": maxLimit must be > 0");
            }
            if (term.apr().signum() <= 0) {
                throw new IllegalArgumentException(
                        term.productCode() + ": apr must be > 0");
            }
            if (term.apr().scale() != 1) {
                throw new IllegalArgumentException(
                        term.productCode() + ": apr must have exactly one decimal place");
            }
        }

        // dtiLimit already validated by @DecimalMin/@DecimalMax
        // sampleEvery already validated by @Min
    }

    /**
     * Serialize product terms list to a JSON string for storage.
     */
    private String serializeProductTerms(List<ProductTermDTO> terms) {
        try {
            return mapper.writeValueAsString(terms);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize product terms", e);
        }
    }

    /**
     * Parse product terms from stored JSON string.
     */
    private List<ProductTermDTO> parseProductTerms(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<ProductTermDTO>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse product terms", e);
        }
    }
}
