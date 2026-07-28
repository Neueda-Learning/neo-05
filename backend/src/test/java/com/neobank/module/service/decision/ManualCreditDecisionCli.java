package com.neobank.module.service.decision;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Small command-line harness for manually exercising the pure decision engine.
 *
 * <p>This lives in test sources deliberately: it is a developer aid, not a production endpoint.
 */
public final class ManualCreditDecisionCli {

    private ManualCreditDecisionCli() {
    }

    public static void main(String[] args) {
        if (args.length != 5) {
            printUsage();
            System.exit(2);
        }

        try {
            CreditApplicationInput input = new CreditApplicationInput(
                    args[0],
                    parseInteger(args[1], "annualIncome"),
                    parseInteger(args[2], "monthlyHousingCost"),
                    parseInteger(args[3], "existingCreditCommitments"),
                    parseInteger(args[4], "requestedCreditLimit"));

            CreditConfiguration selected = CreditConfigurationSelector.latest(
                    java.util.List.of(manualConfiguration()));
            CreditDecisionResult result = new CreditDecisionEngine().evaluate(input, selected);
            printResult(result);
        } catch (IllegalArgumentException exception) {
            System.err.println("Invalid input: " + exception.getMessage());
            printUsage();
            System.exit(2);
        }
    }

    private static int parseInteger(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a whole number: " + value,
                    exception);
        }
    }

    private static CreditConfiguration manualConfiguration() {
        return new CreditConfiguration(
                1,
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

    private static void printResult(CreditDecisionResult result) {
        System.out.println("=== Credit decision ===");
        System.out.println("configurationVersion : " + result.configurationVersion());
        System.out.println("product              : " + result.product());
        System.out.println("outcome              : " + result.outcome());
        System.out.println("reason               : " + result.reason());
        System.out.println("annualIncome         : " + money(result.annualIncome()));
        System.out.println("monthlyIncome        : " + money(result.monthlyIncome()));
        System.out.println("monthlyOutgoings     : " + money(result.monthlyOutgoings()));
        System.out.println("dti                  : " + value(result.dti()));
        System.out.println("dtiLimit             : 0.45");
        System.out.println("incomeBasisLimit     : " + money(result.incomeBasisLimit()));
        System.out.println("productMaximumLimit  : " + money(result.productMaximumLimit()));
        System.out.println("requestedLimit       : " + money(result.requestedLimit()));
        System.out.println("calculatedLimit      : " + money(result.calculatedLimit()));
        System.out.println("grantedLimit         : " + money(result.grantedLimit()));
        System.out.println("apr                  : " + result.apr() + "%");
        System.out.println("capReason            : " + value(result.capReason()));
    }

    private static String money(Integer value) {
        return value == null ? "-" : "GBP " + value;
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  ManualCreditDecisionCli <product> <annualIncome>"
                + " <monthlyHousingCost> <existingCreditCommitments> <requestedLimit>");
        System.err.println("Products:");
        System.err.println("  PREMIUM | PLATINUM | STUDENT");
        System.err.println("Example:");
        System.err.println("  ManualCreditDecisionCli PLATINUM 34000 1000 180 3000");
    }
}
