package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_config")
public class CreditConfig {

    @Id
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "product_terms", nullable = false, length = 255)
    private String productTerms;

    @Column(name = "dti_limit", nullable = false, precision = 5, scale = 2)
    private BigDecimal dtiLimit;

    @Column(name = "rounding_step", nullable = false, precision = 10, scale = 2)
    private BigDecimal roundingStep;

    @Column(name = "sample_every", nullable = false)
    private Integer sampleEvery;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    protected CreditConfig() {
        // JPA
    }

    public static CreditConfig of(Integer version,
                                  String productTerms,
                                  BigDecimal dtiLimit,
                                  BigDecimal roundingStep,
                                  Integer sampleEvery,
                                  LocalDateTime effectiveFrom) {
        CreditConfig config = new CreditConfig();
        config.version = version;
        config.productTerms = productTerms;
        config.dtiLimit = dtiLimit;
        config.roundingStep = roundingStep;
        config.sampleEvery = sampleEvery;
        config.effectiveFrom = effectiveFrom;
        return config;
    }

    public Integer getVersion() {
        return version;
    }

    public String getProductTerms() {
        return productTerms;
    }

    public BigDecimal getDtiLimit() {
        return dtiLimit;
    }

    public BigDecimal getRoundingStep() {
        return roundingStep;
    }

    public Integer getSampleEvery() {
        return sampleEvery;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }
}
