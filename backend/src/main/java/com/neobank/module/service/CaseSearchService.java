package com.neobank.module.service;

import com.neobank.module.dto.CaseSearchItem;
import com.neobank.module.dto.CaseSearchResponse;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CreditRecord;
import com.neobank.module.repository.CreditRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/** UC-01 case search without storing or querying applicant data locally. */
@Service
public class CaseSearchService {

    private static final Logger log = LoggerFactory.getLogger(CaseSearchService.class);

    private final CreditRecordRepository creditRecords;
    private final OrchestratorClient orchestrator;

    public CaseSearchService(CreditRecordRepository creditRecords,
                             OrchestratorClient orchestrator) {
        this.creditRecords = creditRecords;
        this.orchestrator = orchestrator;
    }

    @Transactional(readOnly = true)
    public CaseSearchResponse search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return new CaseSearchResponse(List.of(), false);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        int boundedLimit = Math.min(limit, CreditRecordRepository.MAX_SEARCH_LIMIT);
        String normalizedQuery = query.strip();
        List<CreditRecord> matches = looksLikeApplicationId(normalizedQuery)
                ? creditRecords.searchByApplicationId(normalizedQuery, boundedLimit)
                : searchByApplicantName(normalizedQuery, boundedLimit);

        boolean more = matches.size() > boundedLimit;
        List<CaseSearchItem> cases = matches.stream()
                .limit(boundedLimit)
                .map(CaseSearchItem::of)
                .toList();
        return new CaseSearchResponse(cases, more);
    }

    private List<CreditRecord> searchByApplicantName(String applicantName, int limit) {
        try {
            List<String> applicationIds = orchestrator.resolveApplicationIdsByName(applicantName);
            if (applicationIds == null || applicationIds.isEmpty()) {
                return List.of();
            }
            return creditRecords.searchByApplicationIds(applicationIds, limit);
        } catch (RuntimeException exception) {
            log.warn("Applicant-name search could not reach the orchestrator: {}",
                    exception.toString());
            return List.of();
        }
    }

    private boolean looksLikeApplicationId(String query) {
        String upper = query.toUpperCase(Locale.ROOT);
        return upper.startsWith("APP-") || upper.startsWith("SIM-");
    }
}
