package com.neobank.module.repository;

import com.neobank.module.model.CreditRecord;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditRecordRepository extends JpaRepository<CreditRecord, String> {

    int MAX_SEARCH_LIMIT = 10;

    List<CreditRecord> findAllByOrderBySubmittedAtDescApplicationIdDesc();

        List<CreditRecord> findByOutcomeInOrderBySubmittedAtDescApplicationIdDesc(
            List<String> outcomes);

    List<CreditRecord> findByApplicationIdContainingIgnoreCaseOrderBySubmittedAtDescApplicationIdDesc(
            String applicationId,
            Pageable pageable);

    List<CreditRecord> findByApplicationIdInOrderBySubmittedAtDescApplicationIdDesc(
            List<String> applicationIds,
            Pageable pageable);

    /**
     * Search locally by application id, reading one extra row so the service can derive
     * {@code more} without issuing a second count query.
     */
    default List<CreditRecord> searchByApplicationId(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        int boundedLimit = Math.min(limit, MAX_SEARCH_LIMIT);
        return findByApplicationIdContainingIgnoreCaseOrderBySubmittedAtDescApplicationIdDesc(
                query.trim(),
                PageRequest.of(0, boundedLimit + 1));
    }

    /** Read local cases for ids resolved by the orchestrator, including one overflow row. */
    default List<CreditRecord> searchByApplicationIds(List<String> applicationIds, int limit) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return List.of();
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        List<String> usableIds = applicationIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (usableIds.isEmpty()) {
            return List.of();
        }

        int boundedLimit = Math.min(limit, MAX_SEARCH_LIMIT);
        return findByApplicationIdInOrderBySubmittedAtDescApplicationIdDesc(
                usableIds,
                PageRequest.of(0, boundedLimit + 1));
    }
}
