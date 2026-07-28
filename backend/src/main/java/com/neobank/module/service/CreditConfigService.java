package com.neobank.module.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.CreditConfigHistoryItem;
import com.neobank.module.dto.CreditConfigResponse;
import com.neobank.module.dto.CreateConfigCommand;
import com.neobank.module.model.CreditConfig;
import com.neobank.module.repository.CreditConfigRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manages versioned credit configuration — insert-only, never updated.
 *
 * <p>The current config is always {@code MAX(version)}. Every new POST inserts a new row and
 * immediately becomes the current policy; the previous version is retained forever so decisions
 * made under it remain auditable with their exact numbers.</p>
 */
@Service
public class CreditConfigService {

    private static final Set<String> REQUIRED_PRODUCTS = Set.of("PREMIUM", "PLATINUM", "STUDENT");

    private final CreditConfigRepository repo;
    private final ObjectMapper json;

    public CreditConfigService(CreditConfigRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    /**
     * Validates the command, derives {@code version = MAX + 1}, and persists the new config row.
     *
     * @return the new version number
     */
    @Transactional
    public int createVersion(CreateConfigCommand cmd) {
        validateProducts(cmd);

        int nextVersion = repo.findMaxVersion().orElse(0) + 1;

        CreditConfig config = new CreditConfig(
                nextVersion,
                serializeProductTerms(cmd.productTerms()),
                cmd.dtiLimit(),
                BigDecimal.valueOf(cmd.roundingStep()),
                cmd.sampleEvery(),
                Instant.now()
        );
        repo.save(config);
        return nextVersion;
    }

    @Transactional(readOnly = true)
    public CreditConfigResponse current() {
        CreditConfig row = repo.findTopByOrderByVersionDesc()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no config found"));
        return CreditConfigResponse.of(row, parseProductTerms(row.getProductTerms()));
    }

    @Transactional(readOnly = true)
    public List<CreditConfigHistoryItem> history() {
        return repo.findAllByOrderByVersionDesc().stream()
                .map(c -> new CreditConfigHistoryItem(c.getVersion(), c.getEffectiveFrom()))
                .toList();
    }

    private void validateProducts(CreateConfigCommand cmd) {
        if (cmd.productTerms() == null) return;
        Set<String> missing = REQUIRED_PRODUCTS.stream()
                .filter(p -> !cmd.productTerms().containsKey(p))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "productTerms missing required products: " + missing);
        }
    }

    private String serializeProductTerms(Object terms) {
        try {
            return json.writeValueAsString(terms);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "productTerms could not be serialised: " + e.getOriginalMessage());
        }
    }

    private Map<String, CreditConfigResponse.ProductTerms> parseProductTerms(String rawJson) {
        try {
            return json.readValue(
                    rawJson,
                    new TypeReference<Map<String, CreditConfigResponse.ProductTerms>>() {}
            );
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "stored productTerms is invalid JSON");
        }
    }
}
