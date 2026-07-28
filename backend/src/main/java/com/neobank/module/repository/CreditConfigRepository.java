package com.neobank.module.repository;

import com.neobank.module.model.CreditConfig;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreditConfigRepository extends JpaRepository<CreditConfig, Integer> {

    /** The highest version currently in the table — used to derive the next version number. */
    @Query("SELECT MAX(c.version) FROM CreditConfig c")
    Optional<Integer> findMaxVersion();

    Optional<CreditConfig> findTopByOrderByVersionDesc();

    Optional<CreditConfig> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(
            Instant effectiveFrom);

    default Optional<CreditConfig> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(
            LocalDateTime effectiveFrom) {
        if (effectiveFrom == null) {
            return Optional.empty();
        }
        return findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(
                effectiveFrom.toInstant(ZoneOffset.UTC));
    }

    java.util.List<CreditConfig> findAllByOrderByVersionDesc();
}
