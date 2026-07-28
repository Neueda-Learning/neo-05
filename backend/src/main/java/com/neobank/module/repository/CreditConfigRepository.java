package com.neobank.module.repository;

import com.neobank.module.model.CreditConfig;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditConfigRepository extends JpaRepository<CreditConfig, Integer> {

    Optional<CreditConfig> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(
            LocalDateTime now);
}
