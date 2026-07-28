package com.neobank.module.service;

import com.neobank.module.model.CreditRecord;
import com.neobank.module.repository.CreditRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists the UC-00 handoff record in its own short transaction. */
@Service
public class CreditRecordAcceptanceService {

    private final CreditRecordRepository creditRecords;

    public CreditRecordAcceptanceService(CreditRecordRepository creditRecords) {
        this.creditRecords = creditRecords;
    }

    /**
     * Returns only after the insert has committed. {@code saveAndFlush} makes a duplicate-key
     * failure surface inside this transaction, so the caller can safely treat it as idempotent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertInProgress(String applicationId) {
        creditRecords.saveAndFlush(CreditRecord.inProgress(applicationId));
    }
}
