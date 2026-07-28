package com.neobank.module.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neobank.module.model.CreditRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseSearchDtoTest {

    @Test
    void mapsOnlyLocallyStoredCaseFields() {
        CaseSearchItem item = CaseSearchItem.of(CreditRecord.inProgress("APP-1234"));

        assertThat(item.applicationId()).isEqualTo("APP-1234");
        assertThat(item.outcome()).isEqualTo("in-progress");
        assertThat(item.grantedLimit()).isNull();
        assertThat(item.sampled()).isFalse();
    }

    @Test
    void responseDefensivelyCopiesItsBoundedResults() {
        List<CaseSearchItem> source = new ArrayList<>();
        source.add(CaseSearchItem.of(CreditRecord.inProgress("APP-1")));

        CaseSearchResponse response = new CaseSearchResponse(source, true);
        source.clear();

        assertThat(response.cases()).extracting(CaseSearchItem::applicationId)
                .containsExactly("APP-1");
        assertThat(response.more()).isTrue();
        assertThatThrownBy(() -> response.cases().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
