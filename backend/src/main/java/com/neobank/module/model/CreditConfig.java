package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One versioned row of credit policy — insert-only, never updated or deleted.
 *
 * <p>The current version is always {@code MAX(version)}. Every {@link
 * com.neobank.module.model.CreditRecord} decision pins the version that was current when it ran,
 * so last month's numbers are always recoverable.</p>
 */
@Entity
@Table(name = "credit_config")
public class CreditConfig {

    @Id
    private Integer version;

    /** JSON-serialised map of product code → {minIncome, maxLimit, apr}. */
    @Column(name = "product_terms", nullable = false, length = 255)
    private String productTerms;

    @Column(name = "dti_limit", nullable = false, precision = 5, scale = 2)
    private BigDecimal dtiLimit;

    @Column(name = "rounding_step", nullable = false, precision = 10, scale = 2)
    private BigDecimal roundingStep;

    @Column(name = "sample_every", nullable = false)
    private int sampleEvery;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    protected CreditConfig() {}

    public CreditConfig(Integer version, String productTerms, BigDecimal dtiLimit,
                        BigDecimal roundingStep, int sampleEvery, Instant effectiveFrom) {
        this.version = version;
        this.productTerms = productTerms;
        this.dtiLimit = dtiLimit;
        this.roundingStep = roundingStep;
        this.sampleEvery = sampleEvery;
        this.effectiveFrom = effectiveFrom;
    }

    public Integer getVersion() { return version; }
    public String getProductTerms() { return productTerms; }
    public BigDecimal getDtiLimit() { return dtiLimit; }
    public BigDecimal getRoundingStep() { return roundingStep; }
    public int getSampleEvery() { return sampleEvery; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
}
