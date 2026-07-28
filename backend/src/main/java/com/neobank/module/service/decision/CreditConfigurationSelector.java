package com.neobank.module.service.decision;

import java.util.Collection;
import java.util.Comparator;

/** Selects the current configuration using the agreed {@code MAX(version)} rule. */
public final class CreditConfigurationSelector {

    private CreditConfigurationSelector() {
    }

    public static CreditConfiguration latest(Collection<CreditConfiguration> configurations) {
        if (configurations == null || configurations.isEmpty()) {
            throw new IllegalArgumentException("At least one credit configuration is required");
        }
        return configurations.stream()
                .max(Comparator.comparingInt(CreditConfiguration::version))
                .orElseThrow();
    }
}
