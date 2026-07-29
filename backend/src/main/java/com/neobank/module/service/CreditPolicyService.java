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
import java.util.stream.Collectors;
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
            "REWARDS", "CREDIT_CARD_REWARDS",
            "STANDARD", "CREDIT_CARD_LOW_RATE",
            "STUDENT", "CREDIT_CARD_STUDENT"
    );

    private static final List<ProductTermDTO> REWARDS_PROFILE_TERMS = List.of(
            new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 8000, new BigDecimal("14.9")),
            new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 5000, new BigDecimal("12.9")),
            new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("9.9"))
    );

    private static final List<ProductTermDTO> STANDARD_PROFILE_TERMS = List.of(
            new ProductTermDTO("CREDIT_CARD_REWARDS", 26000, 8500, new BigDecimal("15.2")),
            new ProductTermDTO("CREDIT_CARD_LOW_RATE", 20000, 5500, new BigDecimal("13.4")),
            new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1800, new BigDecimal("10.2"))
    );

        private static final List<ProductTermDTO> STUDENT_PROFILE_TERMS = List.of(
            new ProductTermDTO("CREDIT_CARD_REWARDS", 20000, 4500, new BigDecimal("16.9")),
            new ProductTermDTO("CREDIT_CARD_LOW_RATE", 15000, 2500, new BigDecimal("14.9")),
            new ProductTermDTO("CREDIT_CARD_STUDENT", 10000, 1200, new BigDecimal("9.9"))
        );

            private static final List<String> POLICY_STREAMS = List.of("PLATINUM", "PREMIUM", "STUDENT");

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
        CreditConfig current = policies.findFirstByOrderByVersionDescConfigIdDesc()
                .or(() -> policies.findTopByOrderByVersionDesc())
                .orElseThrow(() -> new NoSuchElementException("no credit policy version exists"));
        List<CreditConfig> rows = policies.findAllByVersionOrderByConfigIdDesc(current.getVersion());
        if (rows.isEmpty()) {
            rows = List.of(current);
        }
        return toView(current, rows);
    }

    /**
     * Get one specific policy version.
     */
    @Transactional(readOnly = true)
    public CreditPolicyView getPolicyVersion(int version) {
        List<CreditConfig> rows = policies.findAllByVersionOrderByConfigIdDesc(version);
        if (rows.isEmpty()) {
            throw new NoSuchElementException("credit policy version not found: " + version);
        }
        return toView(rows.getFirst(), rows);
    }

    /**
     * Get one specific policy stream version.
     */
    @Transactional(readOnly = true)
    public CreditPolicyView getPolicyVersion(int version, String policyCode) {
        String stream = normalizePolicyProfile(policyCode);
        if (!stream.isBlank()) {
            List<CreditConfig> rows = policies.findAllByVersionAndProductTermsOrderByConfigIdDesc(version, stream);
            if (!rows.isEmpty()) {
                return toView(rows.getFirst(), rows);
            }
            throw new NoSuchElementException(
                    "credit policy version not found for " + stream + ": " + version);
        }
        return getPolicyVersion(version);
    }

    /**
     * Get all policy versions, newest first.
     */
    @Transactional(readOnly = true)
    public List<CreditPolicyView> listPolicies() {
        List<CreditPolicyView> views = new ArrayList<>();
        for (String stream : POLICY_STREAMS) {
            CreditConfig latest = policies.findFirstByProductTermsOrderByVersionDescConfigIdDesc(stream)
                    .orElse(null);
            if (latest == null) {
                continue;
            }

            List<CreditConfig> streamRows = policies.findAllByVersionAndProductTermsOrderByConfigIdDesc(
                    latest.getVersion(),
                    stream);
            if (streamRows.isEmpty()) {
                streamRows = List.of(latest);
            }
            views.add(toView(streamRows.getFirst(), streamRows));
        }

        if (!views.isEmpty()) {
            return views;
        }

        for (Integer version : policies.findDistinctVersionsDesc()) {
            List<CreditConfig> versionRows = policies.findAllByVersionOrderByConfigIdDesc(version);
            if (versionRows.isEmpty()) {
                continue;
            }
            views.add(toView(versionRows.getFirst(), versionRows));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public List<CreditPolicyView> listPolicies(String policyCode) {
        String stream = normalizePolicyProfile(policyCode);
        if (stream.isBlank()) {
            return listPolicies();
        }

        List<CreditPolicyView> views = new ArrayList<>();
        Map<Integer, List<CreditConfig>> rowsByVersion = new LinkedHashMap<>();
        for (CreditConfig row : policies.findAllByProductTermsOrderByVersionDescConfigIdDesc(stream)) {
            rowsByVersion.computeIfAbsent(row.getVersion(), ignored -> new ArrayList<>()).add(row);
        }

        for (List<CreditConfig> versionRows : rowsByVersion.values()) {
            if (versionRows.isEmpty()) {
                continue;
            }
            views.add(toView(versionRows.getFirst(), versionRows));
        }
        return views;
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

        String serializedTerms = serializeProductTerms(request.productTerms());
        String persistedPolicyCode = normalizePolicyProfile(request.policyCode());
        if (persistedPolicyCode.isBlank()) {
            throw new IllegalArgumentException("policy_code required");
        }

        int nextVersion = policies.findFirstByProductTermsOrderByVersionDescConfigIdDesc(persistedPolicyCode)
            .map(existing -> existing.getVersion() + 1)
            .orElse(1);

        String productTermsForStorage = persistedPolicyCode.isBlank() ? serializedTerms : persistedPolicyCode;

        List<CreditConfig> rows = request.productTerms().stream()
            .map(term -> CreditConfig.of(
                nextVersion,
            productTermsForStorage,
                request.dtiLimit(),
                BigDecimal.valueOf(request.roundingStep()),
                request.sampleEvery(),
                null // will be set by @PrePersist
            ).withProductTermColumns(
                term.productCode(),
                term.minIncome(),
                term.maxLimit(),
                term.apr()))
            .toList();

        List<CreditConfig> savedRows = policies.saveAll(rows);
        CreditConfig latest = savedRows.stream()
            .max(java.util.Comparator.comparing(CreditConfig::getConfigId))
            .orElseThrow();
        return toView(latest, savedRows);
    }

    /**
     * Validate the policy request against all constraints.
     */
    private void validate(CreditPolicyRequest request) {
        Map<String, Long> countsByProduct = request.productTerms().stream()
                .collect(Collectors.groupingBy(
                        term -> normalizeProductCode(term.productCode()),
                        LinkedHashMap::new,
                        Collectors.counting()));

        Set<String> providedProducts = countsByProduct.keySet();

        Set<String> missing = new java.util.HashSet<>(CATALOGUE_PRODUCTS);
        missing.removeAll(providedProducts);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("missing products: " + missing);
        }

        Set<String> unknown = new java.util.HashSet<>(providedProducts);
        unknown.removeAll(CATALOGUE_PRODUCTS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown products in request: " + unknown);
        }

        List<String> duplicates = countsByProduct.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate products in request: " + duplicates);
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

    private CreditPolicyView toView(CreditConfig config, List<CreditConfig> rowsForVersion) {
        List<ProductTermDTO> terms = parseProductTermsFromRows(rowsForVersion);
        if (terms.isEmpty()) {
            terms = parseProductTerms(config.getProductTerms());
        }
        return CreditPolicyView.of(config, terms);
    }

    private List<ProductTermDTO> parseProductTermsFromRows(List<CreditConfig> rowsForVersion) {
        if (rowsForVersion == null || rowsForVersion.isEmpty()) {
            return List.of();
        }

        Map<String, ProductTermDTO> byCode = new LinkedHashMap<>();
        for (CreditConfig row : rowsForVersion) {
            String code = normalizeProductCode(row.getProductCode());
            if (code == null || code.isBlank()) {
                continue;
            }
            if (row.getMinIncome() == null || row.getMaxLimit() == null || row.getApr() == null) {
                continue;
            }

            byCode.put(code, new ProductTermDTO(
                    code,
                    row.getMinIncome(),
                    row.getMaxLimit(),
                    BigDecimal.valueOf(row.getApr())
            ));
        }

        if (byCode.isEmpty()) {
            return List.of();
        }

        List<ProductTermDTO> ordered = new ArrayList<>();
        for (String code : CATALOGUE_PRODUCT_ORDER) {
            ProductTermDTO term = byCode.get(code);
            if (term != null) {
                ordered.add(term);
            }
        }

        for (Map.Entry<String, ProductTermDTO> entry : byCode.entrySet()) {
            if (!CATALOGUE_PRODUCTS.contains(entry.getKey())) {
                ordered.add(entry.getValue());
            }
        }
        return ordered;
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
            case "REWARDS", "PLATINUM", "CREDIT_CARD_REWARDS" -> REWARDS_PROFILE_TERMS;
            case "STANDARD", "PREMIUM", "CREDIT_CARD_STANDARD", "CREDIT_CARD_LOW_RATE" -> STANDARD_PROFILE_TERMS;
            case "STUDENT", "CREDIT_CARD_STUDENT" -> STUDENT_PROFILE_TERMS;
            default -> STANDARD_PROFILE_TERMS;
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
        // Accept new canonical names and normalise to the stored legacy values for DB lookup.
        if ("REWARDS".equals(value)) {
            return "PLATINUM";
        }
        if ("STANDARD".equals(value)) {
            return "PREMIUM";
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
