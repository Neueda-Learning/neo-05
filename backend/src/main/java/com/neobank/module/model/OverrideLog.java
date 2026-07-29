package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "override_log")
public class OverrideLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Column(name = "old_outcome", nullable = false, length = 32)
    private String oldOutcome;

    @Column(name = "new_outcome", nullable = false, length = 32)
    private String newOutcome;

    @Column(name = "granted_limit")
    private Integer grantedLimit;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "operator", nullable = false, length = 255)
    private String operator;

    @Column(name = "overridden_at", nullable = false)
    private Instant overriddenAt;

    protected OverrideLog() {
        // JPA
    }

    public static OverrideLog of(String applicationId,
                                 String oldOutcome,
                                 String newOutcome,
                                 Integer grantedLimit,
                                 String reason,
                                 String operator) {
        OverrideLog row = new OverrideLog();
        row.applicationId = applicationId;
        row.oldOutcome = oldOutcome;
        row.newOutcome = newOutcome;
        row.grantedLimit = grantedLimit;
        row.reason = reason;
        row.operator = operator;
        return row;
    }

    @PrePersist
    void onCreate() {
        if (overriddenAt == null) {
            overriddenAt = Instant.now();
        }
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getOldOutcome() {
        return oldOutcome;
    }

    public String getNewOutcome() {
        return newOutcome;
    }

    public Integer getGrantedLimit() {
        return grantedLimit;
    }

    public String getReason() {
        return reason;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getOverriddenAt() {
        return overriddenAt;
    }
}