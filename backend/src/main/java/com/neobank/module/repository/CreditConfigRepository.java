package com.neobank.module.repository;

import com.neobank.module.model.CreditConfig;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditConfigRepository extends JpaRepository<CreditConfig, Long> {

    /**
     * Get the current (highest version) credit policy configuration.
     * current = MAX(version).
     */
    Optional<CreditConfig> findTopByOrderByVersionDesc();

    Optional<CreditConfig> findFirstByVersionOrderByEffectiveFromDescConfigIdDesc(Integer version);

    Optional<CreditConfig> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(
            Instant now);
}
