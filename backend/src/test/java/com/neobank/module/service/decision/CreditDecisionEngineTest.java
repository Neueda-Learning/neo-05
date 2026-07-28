package com.neobank.module.service.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neobank.module.model.Decision;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CreditDecisionEngineTest {

    private final CreditDecisionEngine engine = new CreditDecisionEngine();

    @Test
    void configurationSelectionUsesTheHighestVersion() {
        CreditConfiguration version1 = configuration(1);
        CreditConfiguration version7 = configuration(7);
        CreditConfiguration version3 = configuration(3);

        assertThat(CreditConfigurationSelector.latest(List.of(version1, version7, version3)))
                .isSameAs(version7);
    }

    @Test
    void incomeBelowThePremiumMinimumIsRejectedBeforeAffordabilityIsCalculated() {
        CreditDecisionResult result = evaluate(
                "CREDIT_CARD_PREMIUM", 11_999, 2_000, 500, 3_000);

        assertThat(result.outcome()).isEqualTo(Decision.REJECTED);
        assertThat(result.reason()).isEqualTo(DecisionReason.CRE_INCOME_BELOW_MINIMUM);
        assertThat(result.monthlyIncome()).isNull();
        assertThat(result.monthlyOutgoings()).isNull();
        assertThat(result.dti()).isNull();
        assertThat(result.calculatedLimit()).isNull();
        assertThat(result.grantedLimit()).isNull();
    }

    @Test
    void incomeExactlyAtTheMinimumPassesTheIncomeRule() {
        CreditDecisionResult result = evaluate(
                "PREMIUM", 12_000, 300, 100, 2_000);

        assertThat(result.outcome()).isEqualTo(Decision.ACCEPTED);
        assertThat(result.monthlyIncome()).isEqualTo(1_000);
        assertThat(result.dti()).isEqualByComparingTo("0.40");
    }

    @Test
    void platinumUsesItsOwnTermsAndFloorsTheIncomeBasedLimit() {
        CreditDecisionResult result = evaluate(
                "CREDIT_CARD_PLATINUM", 34_000, 1_000, 180, 3_000);

        assertThat(result.product()).isEqualTo(CreditProduct.PLATINUM);
        assertThat(result.outcome()).isEqualTo(Decision.ACCEPTED);
        assertThat(result.reason()).isEqualTo(DecisionReason.CRE_APPROVED);
        assertThat(result.monthlyIncome()).isEqualTo(2_833);
        assertThat(result.monthlyOutgoings()).isEqualTo(1_180);
        assertThat(result.dti()).isEqualByComparingTo("0.42");
        assertThat(result.calculatedLimit()).isEqualTo(2_800);
        assertThat(result.grantedLimit()).isEqualTo(2_800);
        assertThat(result.apr()).isEqualByComparingTo("24.9");
        assertThat(result.capReason()).isNull();
    }

    @Test
    void dtiAboveTheLimitIsReferredButKeepsTheMachineLimit() {
        CreditDecisionResult result = evaluate(
                "PLATINUM", 48_000, 2_000, 320, 5_000);

        assertThat(result.outcome()).isEqualTo(Decision.REFERRED);
        assertThat(result.reason()).isEqualTo(DecisionReason.CRE_AFFORDABILITY_EXCEEDED);
        assertThat(result.dti()).isEqualByComparingTo("0.58");
        assertThat(result.calculatedLimit()).isEqualTo(4_000);
        assertThat(result.grantedLimit()).isNull();
    }

    @Test
    void dtiExactlyAtTheLimitPasses() {
        CreditDecisionResult result = evaluate(
                "PREMIUM", 12_000, 300, 150, 2_000);

        assertThat(result.dti()).isEqualByComparingTo("0.45");
        assertThat(result.outcome()).isEqualTo(Decision.ACCEPTED);
    }

    @Test
    void dtiThatRoundsDownToTheLimitPasses() {
        CreditDecisionResult result = evaluate(
                "PREMIUM", 12_000, 300, 154, 2_000);

        // 454 / 1000 = 0.454, and policy compares the two-decimal value 0.45.
        assertThat(result.dti()).isEqualByComparingTo("0.45");
        assertThat(result.outcome()).isEqualTo(Decision.ACCEPTED);
    }

    @Test
    void dtiThatRoundsAboveTheLimitIsReferred() {
        CreditDecisionResult result = evaluate(
                "PREMIUM", 12_000, 300, 155, 2_000);

        // 455 / 1000 = 0.455, rounded HALF_UP to 0.46.
        assertThat(result.dti()).isEqualByComparingTo("0.46");
        assertThat(result.outcome()).isEqualTo(Decision.REFERRED);
        assertThat(result.reason()).isEqualTo(DecisionReason.CRE_AFFORDABILITY_EXCEEDED);
    }

    @Test
    void zeroIncomeStudentIsReferredWithoutDividingByZero() {
        CreditDecisionResult result = evaluate(
                "CREDIT_CARD_STUDENT", 0, 0, 0, 1_000);

        assertThat(result.product()).isEqualTo(CreditProduct.STUDENT);
        assertThat(result.outcome()).isEqualTo(Decision.REFERRED);
        assertThat(result.reason()).isEqualTo(DecisionReason.CRE_AFFORDABILITY_EXCEEDED);
        assertThat(result.dti()).isNull();
        assertThat(result.calculatedLimit()).isZero();
        assertThat(result.grantedLimit()).isNull();
    }

    @Test
    void requestedAmountCanCapAnAcceptedLimit() {
        CreditDecisionResult result = evaluate(
                "PREMIUM", 60_000, 1_000, 0, 3_000);

        assertThat(result.outcome()).isEqualTo(Decision.ACCEPTED);
        assertThat(result.grantedLimit()).isEqualTo(3_000);
        assertThat(result.capReason()).isEqualTo(LimitCapReason.TO_REQUEST);
        assertThat(result.reason()).isEqualTo(DecisionReason.CRE_LIMIT_CAPPED_TO_REQUEST);
    }

    @Test
    void productMaximumCanCapAnAcceptedLimit() {
        CreditDecisionResult result = evaluate(
                "PLATINUM", 150_000, 1_000, 0, 15_000);

        assertThat(result.outcome()).isEqualTo(Decision.ACCEPTED);
        assertThat(result.grantedLimit()).isEqualTo(10_000);
        assertThat(result.capReason()).isEqualTo(LimitCapReason.TO_BAND_MAX);
        assertThat(result.reason()).isEqualTo(DecisionReason.CRE_LIMIT_CAPPED_TO_BAND_MAX);
    }

    @Test
    void studentUsesItsOwnMaximumAndApr() {
        CreditDecisionResult result = evaluate(
                "STUDENT", 18_000, 400, 100, 1_500);

        assertThat(result.outcome()).isEqualTo(Decision.ACCEPTED);
        assertThat(result.grantedLimit()).isEqualTo(1_000);
        assertThat(result.apr()).isEqualByComparingTo("34.9");
        assertThat(result.reason()).isEqualTo(DecisionReason.CRE_LIMIT_CAPPED_TO_BAND_MAX);
    }

    @Test
    void unsupportedProductsAreNotSilentlyMappedToAnotherCard() {
        assertThatThrownBy(() -> evaluate(
                "CREDIT_CARD_REWARDS", 34_000, 1_000, 180, 3_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported credit product");
    }

    @Test
    void latestConfigurationRequiresAtLeastOneVersion() {
        assertThatThrownBy(() -> CreditConfigurationSelector.latest(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one credit configuration");
    }

    @Test
    void configurationRequiresTermsForAllThreeProducts() {
        assertThatThrownBy(() -> new CreditConfiguration(
                1,
                Map.of(
                        CreditProduct.PREMIUM,
                        new ProductTerms(12_000, 5_000, new BigDecimal("29.9")),
                        CreditProduct.PLATINUM,
                        new ProductTerms(20_000, 10_000, new BigDecimal("24.9"))),
                new BigDecimal("0.45"),
                100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing terms for STUDENT");
    }

    private CreditDecisionResult evaluate(String productCode, int annualIncome,
                                          int housingCost, int commitments,
                                          int requestedLimit) {
        CreditApplicationInput input = new CreditApplicationInput(
                productCode, annualIncome, housingCost, commitments, requestedLimit);
        return engine.evaluate(input, configuration(2));
    }

    private static CreditConfiguration configuration(int version) {
        return new CreditConfiguration(
                version,
                Map.of(
                        CreditProduct.PREMIUM,
                        new ProductTerms(12_000, 5_000, new BigDecimal("29.9")),
                        CreditProduct.PLATINUM,
                        new ProductTerms(20_000, 10_000, new BigDecimal("24.9")),
                        CreditProduct.STUDENT,
                        new ProductTerms(0, 1_000, new BigDecimal("34.9"))),
                new BigDecimal("0.45"),
                100);
    }
}
