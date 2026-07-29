package com.neobank.module.service;

import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.dto.CaseView;
import com.neobank.module.dto.DemoShowcaseView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CreditConfig;
import com.neobank.module.model.CreditRecord;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.CreditConfigRepository;
import com.neobank.module.repository.CreditRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>Your module's work happens here. This is the class you came here to write.</h2>
 *
 * <p>An ordinary service class, like the ones you wrote in Week 2 — the difference is only that the
 * layers around it are already built. The controller has answered {@code 202} and let the
 * orchestrator go; {@code integrations.orchestrator} handles both ends of the wire; the repository
 * handles storage. None of that changes when your logic changes.</p>
 *
 * <p><b>Right now it does the three smallest things that prove the contract works:</b> it prints a
 * line, it writes a row, and it reports {@code ACCEPTED}. All three are placeholders. Replacing
 * them <em>one at a time</em>, keeping the journey green after each, is the way to spend the first
 * hour — the most common way to lose a hackathon day is writing all the logic before running any
 * of it.</p>
 *
 * <h3>What to replace, in order</h3>
 *
 * <ol>
 *   <li><b>The log line</b> → whatever your module actually needs to say.</li>
 *   <li><b>The row</b> → your own table. {@link DemoShowcase} explains how; the short version is a
 *       new Liquibase change set and a new entity, not extra columns on {@code demo_showcase}.</li>
 *   <li><b>The always-{@code ACCEPTED} status</b> → your rules. Read what you need off
 *       {@code request.application()} — it is fully typed, so your IDE will show you the fields —
 *       and return {@code ACCEPTED}, {@code REJECTED} or {@code REFERRED} with a reason a bank
 *       employee could read to a customer. Keep the rules in a method of their own, and test them
 *       without Spring: a rule is a function from an application to an outcome.</li>
 * </ol>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final CreditRecordAcceptanceService acceptance;
    private final CreditRecordRepository creditRecords;
    private final CreditConfigRepository creditConfigs;
    private final OrchestratorClient orchestrator;
    private final ObjectMapper objectMapper;

    /**
     * {@code applicationTaskExecutor} is the thread pool Spring Boot configures for you. Tune it in
     * {@code application.yml} under {@code spring.task.execution.*} — pool size matters once your
     * logic calls a slow mock, because that is what limits how many applications you can handle at
     * once.
     */
    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              CreditRecordAcceptanceService acceptance,
                              CreditRecordRepository creditRecords,
                              CreditConfigRepository creditConfigs,
                              OrchestratorClient orchestrator) {
        this.executor = executor;
        this.acceptance = acceptance;
        this.creditRecords = creditRecords;
        this.creditConfigs = creditConfigs;
        this.orchestrator = orchestrator;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Hand the work to the pool and return immediately.
     *
     * <p>The controller calls this and then writes the {@code 202}. <b>Nothing here may block:</b>
     * the orchestrator is holding a connection open, and a module that does its work on the request
     * thread turns a fast journey into a slow one.</p>
     */
    public void processApplicationAsync(ApplicationRequest request) {
        String applicationId = request.applicationId();
        CreditRecord existing = creditRecords.findById(applicationId).orElse(null);

        if (existing == null) {
            boolean inserted = tryInsertInProgress(applicationId);
            if (inserted) {
                // insertInProgress returns only after its independent short transaction commits.
                executor.execute(() -> processApplication(request));
                return;
            }

            existing = creditRecords.findById(applicationId).orElse(null);
        }

        if (existing != null && existing.hasFinalOutcome()) {
            executor.execute(() -> replayStoredOutcome(applicationId));
        }
    }

    /**
     * Do the work: say something, store something, report something.
     *
     * <p>Package-private on purpose — the outside world goes through
     * {@link #processApplicationAsync}, and a unit test can call this directly on the test thread,
     * which is what makes it testable without a thread pool.</p>
     *
     * <p><b>Deliberately not {@code @Transactional}.</b> The repository's own save is transactional;
     * wrapping the whole method would put the HTTP call inside that transaction, so a slow or
     * unreachable orchestrator could roll back a row this module had already committed. Store
     * first, report second, and let the two fail independently. When you add several writes that
     * must land together, put {@code @Transactional} on a method that does only the writes.</p>
     */
    void processApplication(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            log.info("Received application {}", request.summary());

            CreditRecord row = creditRecords.findById(applicationId).orElse(null);
            if (row == null || !row.isInProgress()) {
                return;
            }
                log.info("HELLO WORLD  - application {} is being processed by the module", applicationId);
                CreditConfig activeConfig = creditConfigs
                    .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(Instant.now())
                    .orElseThrow(() -> new IllegalStateException("No effective credit config found"));
                ProductTerms terms = resolveTerms(activeConfig, request.application());
                
                ScoringInput input = scoringInput(request.application(), terms);
                BigDecimal dti = input.monthlyIncome() <= 0
                    ? null
                    : BigDecimal.valueOf(input.monthlyOutgoings())
                        .divide(BigDecimal.valueOf(input.monthlyIncome()), 2, RoundingMode.HALF_UP);
                dti = dti == null ? null : dti.min(BigDecimal.valueOf(99.99));
                row.applyScoring(
                    activeConfig.getVersion(),
                    activeConfig.getConfigId(),
                    terms.productCode(),
                    input.annualIncome(),
                    input.monthlyIncome(),
                    input.monthlyOutgoings(),
                    dti,
                    input.incomeBasisLimit(),
                    input.requestedLimit(),
                    terms.maxLimit(),
                    null,
                    terms.apr(),
                    null);

                if (terms.minIncome() > input.annualIncome()) {
                row.markFinal(CreditRecord.STATUS_REJECTED, "CRE_REJECTED_MIN_INCOME");
                creditRecords.save(row);
                orchestrator.applicationStatusUpdate(applicationId, Decision.REJECTED, "CRE_REJECTED_MIN_INCOME");
                return;
                }

                if (dti == null || dti.compareTo(activeConfig.getDtiLimit()) > 0) {
                row.markFinal(CreditRecord.STATUS_REFERRED, "CRE_AFFORDABILITY_EXCEEDED");
                creditRecords.save(row);
                orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                        "CRE_AFFORDABILITY_EXCEEDED");
                return;
                }

                int rawLimit = Math.min(input.incomeBasisLimit(), Math.min(terms.maxLimit(), input.requestedLimit()));
                int roundingStep = activeConfig.getRoundingStep().intValue();
                int grantedLimit = roundingStep <= 0 ? rawLimit : (rawLimit / roundingStep) * roundingStep;
                String capReason = capReason(rawLimit, input.incomeBasisLimit(), input.requestedLimit(), terms.maxLimit());

                row.applyScoring(
                    activeConfig.getVersion(),
                    activeConfig.getConfigId(),
                    terms.productCode(),
                    input.annualIncome(),
                    input.monthlyIncome(),
                    input.monthlyOutgoings(),
                    dti,
                    input.incomeBasisLimit(),
                    input.requestedLimit(),
                    terms.maxLimit(),
                    grantedLimit,
                    terms.apr(),
                    capReason);
                row.markFinal(CreditRecord.STATUS_ACCEPTED, capReason);
            creditRecords.save(row);
                orchestrator.applicationStatusUpdate(applicationId, Decision.ACCEPTED, capReason);
        } catch (RuntimeException e) {
            // A module that throws never reports, and the orchestrator then waits out its 30s
            // timeout and ends the journey FAILED with nothing to explain it. So: refer it to a
            // human and say why. Keep this guard when you replace the body above.
            log.error("processApplication failed for {} — referring", applicationId, e);
            creditRecords.findById(applicationId).ifPresent(row -> {
                row.markFinal(CreditRecord.STATUS_REFERRED, "module error: " + e);
                creditRecords.save(row);
            });
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    private ProductTerms resolveTerms(CreditConfig config, Application application) {
        String normalizedCode = normalizeProductCode(application);
        Map<String, ProductTermsRaw> termsByCode;
        try {
            termsByCode = objectMapper.readValue(config.getProductTerms(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse credit_config.product_terms", e);
        }

        ProductTermsRaw raw = termsByCode.get(normalizedCode);
        if (raw == null) {
            raw = termsByCode.get(legacyAlias(normalizedCode));
        }
        if (raw == null) {
            throw new IllegalStateException("Unsupported product code in config: " + normalizedCode);
        }
        return new ProductTerms(normalizedCode, raw.minIncome(), raw.maxLimit(), raw.apr());
    }

    private ScoringInput scoringInput(Application application, ProductTerms terms) {
        int annualIncome = 0;
        int monthlyOutgoings = 0;
        int requestedLimit = 0;

        if (application != null && application.finances() != null) {
            annualIncome = nz(application.finances().annualIncome());
            monthlyOutgoings = nz(application.finances().monthlyHousingCost())
                    + nz(application.finances().existingCreditCommitments());
        }
        if (application != null && application.product() != null) {
            requestedLimit = nz(application.product().requestedCreditLimit());
        }

        int monthlyIncome = annualIncome / 12;
        int incomeBasisLimit = monthlyIncome;
        return new ScoringInput(annualIncome, monthlyIncome, monthlyOutgoings, incomeBasisLimit, requestedLimit);
    }

    private String normalizeProductCode(Application application) {
        String code = application == null || application.product() == null ? null : application.product().productCode();
        if (code == null) {
            return "STANDARD";
        }
        return switch (code) {
            case "CREDIT_CARD_STANDARD" -> "STANDARD";
            case "CREDIT_CARD_REWARDS" -> "REWARDS";
            case "CREDIT_CARD_STUDENT" -> "STUDENT";
            case "CREDIT_CARD_PREMIUM" -> "PREMIUM";
            case "CREDIT_CARD_PLATINUM" -> "PLATINUM";
            default -> code;
        };
    }

    private String legacyAlias(String normalizedCode) {
        return switch (normalizedCode) {
            case "STANDARD" -> "PREMIUM";
            case "REWARDS" -> "PLATINUM";
            default -> normalizedCode;
        };
    }

    private String capReason(int rawLimit, int incomeBasisLimit, int requestedLimit, int productMaxLimit) {
        if (rawLimit == incomeBasisLimit) {
            return "CRE_APPROVED";
        }
        if (rawLimit == requestedLimit) {
            return "CRE_LIMIT_CAPPED_TO_REQUEST";
        }
        return "CRE_LIMIT_CAPPED_TO_BAND_MAX";
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private record ProductTerms(String productCode, int minIncome, int maxLimit, BigDecimal apr) {
    }

    private record ProductTermsRaw(Integer minIncome, Integer maxLimit, BigDecimal apr) {
    }

    private record ScoringInput(int annualIncome,
                                int monthlyIncome,
                                int monthlyOutgoings,
                                int incomeBasisLimit,
                                int requestedLimit) {
    }

    private boolean tryInsertInProgress(String applicationId) {
        try {
            acceptance.insertInProgress(applicationId);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private void replayStoredOutcome(String applicationId) {
        creditRecords.findById(applicationId)
                .filter(CreditRecord::hasFinalOutcome)
                .ifPresent(row -> orchestrator.applicationStatusUpdate(
                        applicationId,
                        asDecision(row.getOutcome()),
                        row.getDecisionReason() == null ? "replayed stored outcome" : row.getDecisionReason()));
    }

    private Decision asDecision(String outcome) {
        return switch (outcome) {
            case CreditRecord.STATUS_ACCEPTED -> Decision.ACCEPTED;
            case CreditRecord.STATUS_REJECTED -> Decision.REJECTED;
            case CreditRecord.STATUS_REFERRED -> Decision.REFERRED;
            default -> throw new IllegalArgumentException("Unsupported outcome: " + outcome);
        };
    }

    /** Everything this module has answered, newest first — what its own UI reads. */
    @Transactional(readOnly = true)
    public List<DemoShowcaseView> findAll() {
        return creditRecords.findAllByOrderBySubmittedAtDescApplicationIdDesc().stream()
                .map(DemoShowcaseView::of)
                .toList();
    }

    /**
     * Read a stored decision and its workings — UC 02.
     * The dtiLimit comes from the pinned config version, not the record itself.
     *
     * @throws NoSuchElementException if no case exists for the given applicationId
     */
    @Transactional(readOnly = true)
    public CaseView getCase(String applicationId) {
        CreditRecord row = creditRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("case not found: " + applicationId));
        CreditConfig config = resolveConfigForCase(row);
        return CaseView.of(row, config.getDtiLimit());
    }

        private CreditConfig resolveConfigForCase(CreditRecord row) {
        Long configId = row.getCreditConfigId();
        if (configId != null) {
            return creditConfigs.findById(configId)
                .orElseThrow(() -> new IllegalStateException(
                    "config id " + configId + " not found"));
        }

        return creditConfigs.findFirstByVersionOrderByEffectiveFromDescConfigIdDesc(
                row.getCreditConfigVersion())
            .orElseThrow(() -> new IllegalStateException(
                "config version " + row.getCreditConfigVersion() + " not found"));
        }

    /**
     * Fetch and return applicant details from the orchestrator — UC 03.
     * This is a live proxy call, never persisted. The applicant data is always fetched fresh.
     *
     * @throws Exception if the orchestrator is unreachable or returns an error
     */
    public ApplicantViewDto getApplicant(String applicationId) {
        Application application = orchestrator.fetchApplication(applicationId);
        return ApplicantViewDto.of(application);
    }
}
