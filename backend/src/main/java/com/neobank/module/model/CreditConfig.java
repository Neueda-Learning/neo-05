package com.neobank.module.model;


import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;

/**
 * Credit policy configuration versioned by integer, with product terms as a single field.
 * current = MAX(version). A new version is the WHOLE config: all three products' terms
 * plus dtiLimit, roundingStep, sampleEvery.
 */

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


    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;


    protected CreditConfig() {
        // JPA
    }
  
     private CreditConfig() {
    }

    public static CreditConfig of(Integer version,
                                  String productTerms,
                                  BigDecimal dtiLimit,
                                  BigDecimal roundingStep,
                                  Integer sampleEvery,
                                  Instant effectiveFrom) {
        CreditConfig config = new CreditConfig();
        config.version = version;
        config.productTerms = productTerms;
        config.dtiLimit = dtiLimit;
        config.roundingStep = roundingStep;
        config.sampleEvery = sampleEvery;
        config.effectiveFrom = effectiveFrom;
        return config;
    }

    @PrePersist
    void onCreate() {
        if (effectiveFrom == null) {
            effectiveFrom = Instant.now();
        }
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

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }
}
