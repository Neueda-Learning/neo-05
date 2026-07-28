package com.neobank.module.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neobank.module.model.CreditConfig;

public interface CreditConfigRepository extends JpaRepository<CreditConfig, Integer> {

    /**
     * Get the current (highest version) credit policy configuration.
     * current = MAX(version).
     */
    Optional<CreditConfig> findTopByOrderByVersionDesc();
}
