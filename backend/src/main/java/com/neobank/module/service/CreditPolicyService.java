package com.neobank.module.service;

import com.neobank.module.dto.CreditPolicyRequest;
import com.neobank.module.dto.CreditPolicyView;
import com.neobank.module.dto.ProductTermDTO;
import com.neobank.module.model.CreditConfig;
import com.neobank.module.repository.CreditConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        private static final List<String> CATALOGUE_PRODUCT_ORDER = List.of(
            "CREDIT_CARD_REWARDS",
            "CREDIT_CARD_LOW_RATE",
            "CREDIT_CARD_STUDENT"
        );

        private static final Map<String, String> LEGACY_PRODUCT_CODE_MAP = Map.of(
            "PLATINUM", "CREDIT_CARD_REWARDS",
            "PREMIUM", "CREDIT_CARD_LOW_RATE",
            "STUDENT", "CREDIT_CARD_STUDENT"
        );

            private static final List<ProductTermDTO> PLATINUM_PROFILE_TERMS = List.of(
                new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 8000, new BigDecimal("14.9")),
                new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 5000, new BigDecimal("12.9")),
                new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("9.9"))
            );

            private static final List<ProductTermDTO> PREMIUM_PROFILE_TERMS = List.of(
                new ProductTermDTO("CREDIT_CARD_REWARDS", 26000, 8500, new BigDecimal("15.2")),
                new ProductTermDTO("CREDIT_CARD_LOW_RATE", 20000, 5500, new BigDecimal("13.4")),
                new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1800, new BigDecimal("10.2"))
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
        return toView(current);
    }

    /**
     * Get one specific policy version.
     */
    @Transactional(readOnly = true)
    public CreditPolicyView getPolicyVersion(int version) {
        CreditConfig config = policies.findById(version)
                .orElseThrow(() -> new NoSuchElementException("credit policy version not found: " + version));
        return toView(config);
    }

    /**
     * Get all policy versions, newest first.
     */
    @Transactional(readOnly = true)
    public List<CreditPolicyView> listPolicies() {
        return policies.findAllByOrderByVersionDesc().stream()
                .map(this::toView)
                .toList();
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
        } catch (JsonProcessingException e) {
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
        } catch (IOException ignored) {
            return parseLegacyProductTerms(json);
        }
    }

    private CreditPolicyView toView(CreditConfig config) {
        List<ProductTermDTO> terms = parseProductTerms(config.getProductTerms());
        return CreditPolicyView.of(config, terms);
    }

    /**
     * Supports legacy seeded rows where product_terms is an object keyed by product family.
     */
    private List<ProductTermDTO> parseLegacyProductTerms(String json) {
        try {
            Map<String, LegacyProductTerm> legacy = mapper.readValue(
                    json,
                    new TypeReference<LinkedHashMap<String, LegacyProductTerm>>() {
                    }
            );

            Map<String, ProductTermDTO> normalized = new HashMap<>();
            for (Map.Entry<String, LegacyProductTerm> entry : legacy.entrySet()) {
                String normalizedCode = normalizeProductCode(entry.getKey());
                LegacyProductTerm value = entry.getValue();

                if (value == null) {
                    throw new IllegalArgumentException("missing terms for product: " + entry.getKey());
                }

                normalized.put(normalizedCode, new ProductTermDTO(
                        normalizedCode,
                        value.minIncome(),
                        value.maxLimit(),
                        value.apr()
                ));
            }

            List<ProductTermDTO> ordered = new ArrayList<>();
            for (String code : CATALOGUE_PRODUCT_ORDER) {
                if (normalized.containsKey(code)) {
                    ordered.add(normalized.get(code));
                }
            }

            for (Map.Entry<String, ProductTermDTO> entry : normalized.entrySet()) {
                if (!CATALOGUE_PRODUCTS.contains(entry.getKey())) {
                    ordered.add(entry.getValue());
                }
            }

            return ordered;
        } catch (IOException ignored) {
            return parseNamedPolicyProfile(json);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to parse product terms", e);
        }
    }

    private List<ProductTermDTO> parseNamedPolicyProfile(String rawProductTerms) {
        String normalized = normalizePolicyProfile(rawProductTerms);

        return switch (normalized) {
            case "PLATINUM" -> PLATINUM_PROFILE_TERMS;
            case "PREMIUM" -> PREMIUM_PROFILE_TERMS;
            default -> throw new RuntimeException("Failed to parse product terms");
        };
    }

    private String normalizePolicyProfile(String rawProductTerms) {
        if (rawProductTerms == null) {
            return "";
        }

        String value = rawProductTerms.trim().toUpperCase();
        if ("PLATIUM".equals(value)) {
            return "PLATINUM";
        }
        return value;
    }

    private String normalizeProductCode(String code) {
        if (code == null) {
            return null;
        }
        return LEGACY_PRODUCT_CODE_MAP.getOrDefault(code, code);
    }

    private record LegacyProductTerm(Integer minIncome, Integer maxLimit, BigDecimal apr) {
    }
}
