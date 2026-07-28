package com.neobank.module.repository;

import com.neobank.module.model.CreditRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditRecordRepository extends JpaRepository<CreditRecord, String> {

    List<CreditRecord> findAllByOrderBySubmittedAtDescApplicationIdDesc();
}