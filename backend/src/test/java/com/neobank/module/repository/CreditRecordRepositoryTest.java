package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neobank.module.model.CreditRecord;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CreditRecordRepositoryTest {

    @Autowired
    private CreditRecordRepository creditRecords;

    @Test
    void applicationIdSearchIsCaseInsensitiveAndReadsLimitPlusOne() {
        List<CreditRecord> records = IntStream.range(0, 12)
                .mapToObj(index -> CreditRecord.inProgress("UC01-MARIA-%02d".formatted(index)))
                .toList();
        creditRecords.saveAllAndFlush(records);
        creditRecords.saveAndFlush(CreditRecord.inProgress("UC01-UNRELATED"));

        List<CreditRecord> matches = creditRecords.searchByApplicationId("mArIa", 10);

        assertThat(matches).hasSize(11);
        assertThat(matches).extracting(CreditRecord::getApplicationId)
                .allMatch(applicationId -> applicationId.contains("MARIA"));
        assertThat(matches.getFirst().getApplicationId()).isEqualTo("UC01-MARIA-11");
    }

    @Test
    void searchNeverReadsMoreThanTheMaximumPlusOne() {
        List<CreditRecord> records = IntStream.range(0, 15)
                .mapToObj(index -> CreditRecord.inProgress("UC01-CAPPED-%02d".formatted(index)))
                .toList();
        creditRecords.saveAllAndFlush(records);

        assertThat(creditRecords.searchByApplicationId("CAPPED", 100)).hasSize(11);
    }

    @Test
    void resolvedApplicationIdsOnlyReturnMatchingLocalCasesWithOverflowRow() {
        List<CreditRecord> records = IntStream.range(0, 12)
                .mapToObj(index -> CreditRecord.inProgress("UC01-NAME-%02d".formatted(index)))
                .toList();
        creditRecords.saveAllAndFlush(records);
        creditRecords.saveAndFlush(CreditRecord.inProgress("UC01-NOT-RESOLVED"));

        List<String> resolvedIds = records.stream()
                .map(CreditRecord::getApplicationId)
                .toList();
        List<CreditRecord> matches = creditRecords.searchByApplicationIds(resolvedIds, 10);

        assertThat(matches).hasSize(11);
        assertThat(matches).extracting(CreditRecord::getApplicationId)
                .doesNotContain("UC01-NOT-RESOLVED");
    }

    @Test
    void blankSearchIsEmptyAndLimitMustBePositive() {
        assertThat(creditRecords.searchByApplicationId("  ", 10)).isEmpty();
        assertThat(creditRecords.searchByApplicationIds(List.of(), 10)).isEmpty();
        assertThatThrownBy(() -> creditRecords.searchByApplicationId("APP", 0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }
}
