package com.neobank.module.repository;

import com.neobank.module.model.CreditConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreditConfigRepository extends JpaRepository<CreditConfig, Long> {

    /**
     * Get the current (highest version) credit policy configuration.
     * current = MAX(version).
     */
    Optional<CreditConfig> findTopByOrderByVersionDesc();

    /**
     * Get the current row by version then latest config id.
     */
    Optional<CreditConfig> findFirstByOrderByVersionDescConfigIdDesc();

    /**
     * Get all policy versions, newest first.
     */
    java.util.List<CreditConfig> findAllByOrderByVersionDesc();

    /**
     * Get all config rows ordered by version (newest first) and within version by latest row first.
     */
    java.util.List<CreditConfig> findAllByOrderByVersionDescConfigIdDesc();

    /**
     * Get all rows for one version, latest row first.
     */
    java.util.List<CreditConfig> findAllByVersionOrderByConfigIdDesc(Integer version);

        /**
         * Get all rows for one policy version (policy stream + version), latest row first.
         */
        java.util.List<CreditConfig> findAllByVersionAndProductTermsOrderByConfigIdDesc(
            Integer version,
            String productTerms);

        java.util.List<CreditConfig> findAllByProductTermsOrderByVersionDescConfigIdDesc(String productTerms);

        /**
         * Get latest version row for one policy stream.
         */
        Optional<CreditConfig> findFirstByProductTermsOrderByVersionDescConfigIdDesc(String productTerms);

    @Query("select distinct c.version from CreditConfig c order by c.version desc")
    List<Integer> findDistinctVersionsDesc();

    Optional<CreditConfig> findFirstByVersionOrderByEffectiveFromDescConfigIdDesc(Integer version);

    Optional<CreditConfig> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(
            Instant now);
}
