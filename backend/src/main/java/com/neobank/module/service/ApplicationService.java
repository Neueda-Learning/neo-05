package com.neobank.module.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.dto.CaseView;
import com.neobank.module.dto.DemoShowcaseView;
import com.neobank.module.dto.OverrideCaseRequest;
import com.neobank.module.dto.ReferredDecisionRequest;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CreditConfig;
import com.neobank.module.model.CreditRecord;
import com.neobank.module.model.Decision;
import com.neobank.module.model.OverrideLog;
import com.neobank.module.repository.CreditConfigRepository;
import com.neobank.module.repository.CreditRecordRepository;
import com.neobank.module.repository.OverrideLogRepository;

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
    private final OverrideLogRepository overrideLogs;
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
                              OverrideLogRepository overrideLogs,
                              OrchestratorClient orchestrator) {
        this.executor = executor;
        this.acceptance = acceptance;
        this.creditRecords = creditRecords;
        this.creditConfigs = creditConfigs;
        this.overrideLogs = overrideLogs;
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
                CreditConfig activeConfig = selectActiveConfig(request.application());
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
                row.recordMachineDecision(CreditRecord.STATUS_REJECTED, "CRE_REJECTED_MIN_INCOME");
                creditRecords.save(row);
                orchestrator.applicationStatusUpdate(applicationId, Decision.REJECTED, "CRE_REJECTED_MIN_INCOME");
                return;
                }

                if (dti == null || dti.compareTo(activeConfig.getDtiLimit()) > 0) {
                row.recordMachineDecision(CreditRecord.STATUS_REFERRED, "CRE_AFFORDABILITY_EXCEEDED");
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
                row.recordMachineDecision(CreditRecord.STATUS_ACCEPTED, capReason);
            creditRecords.save(row);
                orchestrator.applicationStatusUpdate(applicationId, Decision.ACCEPTED, capReason);
        } catch (RuntimeException e) {
            // A module that throws never reports, and the orchestrator then waits out its 30s
            // timeout and ends the journey FAILED with nothing to explain it. So: refer it to a
            // human and say why. Keep this guard when you replace the body above.
            log.error("processApplication failed for {} — referring", applicationId, e);
            creditRecords.findById(applicationId).ifPresent(row -> {
                row.recordMachineDecision(CreditRecord.STATUS_REFERRED, "module error: " + e);
                creditRecords.save(row);
            });
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    private ProductTerms resolveTerms(CreditConfig config, Application application) {
        return resolveTerms(config, normalizeProductCode(application));
    }

    private ProductTerms resolveTerms(CreditConfig config, String normalizedCode) {
        String catalogueCode = catalogueProductCode(normalizedCode);

        ProductTerms fromColumns = termsFromProductRows(config, catalogueCode, normalizedCode);
        if (fromColumns != null) {
            return fromColumns;
        }

        String rawTerms = config.getProductTerms();
        if (rawTerms == null || rawTerms.isBlank()) {
            throw new IllegalStateException("credit_config.product_terms is required");
        }
        String trimmedTerms = rawTerms.trim();
        if (!trimmedTerms.startsWith("{")
                && !trimmedTerms.startsWith("[")
                && !trimmedTerms.startsWith("\"")) {
            return namedProfileTerms(trimmedTerms, catalogueCode, normalizedCode);
        }

        try {
            JsonNode root = objectMapper.readTree(rawTerms);
            if (root.isObject()) {
                Map<String, ProductTermsRaw> termsByCode = objectMapper.convertValue(
                        root, new TypeReference<>() { });
                ProductTermsRaw terms = findObjectTerms(termsByCode, normalizedCode, catalogueCode);
                if (terms != null) {
                    return toProductTerms(normalizedCode, terms);
                }
            } else if (root.isArray()) {
                List<ProductTermsListRaw> terms = objectMapper.convertValue(
                        root, new TypeReference<>() { });
                ProductTermsListRaw match = terms.stream()
                        .filter(term -> catalogueCode.equals(catalogueProductCode(term.productCode())))
                        .findFirst()
                        .orElse(null);
                if (match != null) {
                    return new ProductTerms(normalizedCode, match.minIncome(), match.maxLimit(), match.apr());
                }
            } else if (root.isTextual()) {
                return namedProfileTerms(root.asText(), catalogueCode, normalizedCode);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse credit_config.product_terms", e);
        }

        // New policy rows persist the profile as plain text rather than JSON text.
        return namedProfileTerms(rawTerms, catalogueCode, normalizedCode);
    }

    private CreditConfig selectActiveConfig(Application application) {
        String profile = policyProfile(application);
        return creditConfigs.findFirstByProductTermsOrderByVersionDescConfigIdDesc(profile)
                .orElseGet(() -> creditConfigs
                        .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(Instant.now())
                        .orElseThrow(() -> new IllegalStateException("No effective credit config found")));
    }

    private ProductTerms termsFromProductRows(CreditConfig config,
                                               String catalogueCode,
                                               String normalizedCode) {
        List<CreditConfig> rows = creditConfigs
                .findAllByVersionAndProductTermsOrderByConfigIdDesc(
                        config.getVersion(), config.getProductTerms());
        if (rows == null || rows.isEmpty()) {
            rows = List.of(config);
        }

        return rows.stream()
                .filter(row -> catalogueCode.equals(catalogueProductCode(row.getProductCode())))
                .filter(row -> row.getMinIncome() != null
                        && row.getMaxLimit() != null
                        && row.getApr() != null)
                .findFirst()
                .map(row -> new ProductTerms(
                        normalizedCode,
                        row.getMinIncome(),
                        row.getMaxLimit(),
                        BigDecimal.valueOf(row.getApr())))
                .orElse(null);
    }

    private ProductTermsRaw findObjectTerms(Map<String, ProductTermsRaw> termsByCode,
                                            String normalizedCode,
                                            String catalogueCode) {
        ProductTermsRaw direct = termsByCode.get(normalizedCode);
        if (direct == null) {
            direct = termsByCode.get(legacyAlias(normalizedCode));
        }
        if (direct != null) {
            return direct;
        }
        return termsByCode.entrySet().stream()
                .filter(entry -> catalogueCode.equals(catalogueProductCode(entry.getKey())))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private ProductTerms toProductTerms(String normalizedCode, ProductTermsRaw raw) {
        return new ProductTerms(normalizedCode, raw.minIncome(), raw.maxLimit(), raw.apr());
    }

    private ProductTerms namedProfileTerms(String profile,
                                           String catalogueCode,
                                           String normalizedCode) {
        String normalizedProfile = profile == null ? "" : profile.trim().toUpperCase();
        if ("PLATIUM".equals(normalizedProfile)) {
            normalizedProfile = "PLATINUM";
        }

        ProductTermsRaw raw = switch (normalizedProfile) {
            case "PLATINUM" -> switch (catalogueCode) {
                case "CREDIT_CARD_REWARDS" -> new ProductTermsRaw(24000, 8000, new BigDecimal("14.9"));
                case "CREDIT_CARD_LOW_RATE" -> new ProductTermsRaw(18000, 5000, new BigDecimal("12.9"));
                case "CREDIT_CARD_STUDENT" -> new ProductTermsRaw(12000, 1500, new BigDecimal("9.9"));
                default -> null;
            };
            case "PREMIUM" -> switch (catalogueCode) {
                case "CREDIT_CARD_REWARDS" -> new ProductTermsRaw(26000, 8500, new BigDecimal("15.2"));
                case "CREDIT_CARD_LOW_RATE" -> new ProductTermsRaw(20000, 5500, new BigDecimal("13.4"));
                case "CREDIT_CARD_STUDENT" -> new ProductTermsRaw(12000, 1800, new BigDecimal("10.2"));
                default -> null;
            };
            case "STUDENT" -> switch (catalogueCode) {
                case "CREDIT_CARD_REWARDS" -> new ProductTermsRaw(20000, 4500, new BigDecimal("16.9"));
                case "CREDIT_CARD_LOW_RATE" -> new ProductTermsRaw(15000, 2500, new BigDecimal("14.9"));
                case "CREDIT_CARD_STUDENT" -> new ProductTermsRaw(10000, 1200, new BigDecimal("9.9"));
                default -> null;
            };
            default -> null;
        };

        if (raw == null) {
            throw new IllegalStateException(
                    "Unsupported product/profile configuration: " + catalogueCode + "/" + profile);
        }
        return toProductTerms(normalizedCode, raw);
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
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("productCode is required");
        }
        return switch (code) {
            case "CREDIT_CARD_STANDARD", "CREDIT_CARD_LOW_RATE" -> "STANDARD";
            case "CREDIT_CARD_REWARDS" -> "REWARDS";
            case "CREDIT_CARD_STUDENT" -> "STUDENT";
            case "CREDIT_CARD_PREMIUM" -> "PREMIUM";
            case "CREDIT_CARD_PLATINUM" -> "PLATINUM";
            default -> code;
        };
    }

    private String policyProfile(Application application) {
        return switch (catalogueProductCode(normalizeProductCode(application))) {
            case "CREDIT_CARD_REWARDS" -> "PLATINUM";
            case "CREDIT_CARD_LOW_RATE" -> "PREMIUM";
            case "CREDIT_CARD_STUDENT" -> "STUDENT";
            default -> throw new IllegalArgumentException("Unsupported productCode");
        };
    }

    private String catalogueProductCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return switch (code.trim().toUpperCase()) {
            case "REWARDS", "PLATINUM", "CREDIT_CARD_REWARDS", "CREDIT_CARD_PLATINUM" ->
                    "CREDIT_CARD_REWARDS";
            case "STANDARD", "PREMIUM", "CREDIT_CARD_STANDARD", "CREDIT_CARD_LOW_RATE",
                    "CREDIT_CARD_PREMIUM" -> "CREDIT_CARD_LOW_RATE";
            case "STUDENT", "CREDIT_CARD_STUDENT" -> "CREDIT_CARD_STUDENT";
            default -> code.trim().toUpperCase();
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

    private record ProductTermsListRaw(String productCode,
                                       Integer minIncome,
                                       Integer maxLimit,
                                       BigDecimal apr) {
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
                        replayReason(row)));
    }

    private String replayReason(CreditRecord row) {
        if (row.getDecisionReason() != null) {
            return row.getDecisionReason();
        }
        if (row.getMachineDecisionReason() != null) {
            return row.getMachineDecisionReason();
        }
        return "replayed stored outcome";
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
        return buildCaseView(row);
        }

        @Transactional
        public CaseView overrideCase(String applicationId, OverrideCaseRequest request) {
        CreditRecord row = creditRecords.findById(applicationId)
            .orElseThrow(() -> new NoSuchElementException("case not found: " + applicationId));

        ManualOverrideCommand command = parseOverride(request);
        validateOverride(row, command);
        Integer grantedLimitToApply = acceptedGrantedLimit(command.internalOutcome(), command.grantedLimit());

        if (isDuplicateOverride(applicationId, row, command)) {
            return buildCaseView(row);
        }

        String oldOutcome = row.getOutcome();
        row.applyManualOverride(
            command.internalOutcome(),
            grantedLimitToApply,
            command.reason(),
            command.operator());
        creditRecords.save(row);

        overrideLogs.save(OverrideLog.of(
            applicationId,
            oldOutcome,
            command.internalOutcome(),
            grantedLimitToApply,
            command.reason(),
            command.operator()));

        orchestrator.applicationStatusUpdate(
            applicationId,
            asDecision(command.internalOutcome()),
            callbackComment(row, command));

        return buildCaseView(row);
        }

        @Transactional
        public CaseView decideReferredCase(String applicationId, ReferredDecisionRequest request) {
            CreditRecord row = creditRecords.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("case not found: " + applicationId));

            String normalizedDecision = request.decision() == null ? "" : request.decision().trim().toUpperCase();
            String internalOutcome = switch (normalizedDecision) {
                case "ACCEPTED" -> CreditRecord.STATUS_ACCEPTED;
                case "REJECTED" -> CreditRecord.STATUS_REJECTED;
                default -> throw new IllegalArgumentException(
                        "decision must be one of ACCEPTED or REJECTED");
            };
            validateReferredDecision(row, internalOutcome, request);
            Integer grantedLimitToApply = acceptedGrantedLimit(internalOutcome, request.grantedLimit());

            String oldOutcome = row.getOutcome();
            row.applyManualOverride(
                internalOutcome,
                grantedLimitToApply,
                request.reason(),
                request.operator());
            creditRecords.save(row);

            overrideLogs.save(OverrideLog.of(
                applicationId,
                oldOutcome,
                internalOutcome,
                grantedLimitToApply,
                request.reason(),
                request.operator()));

            orchestrator.applicationStatusUpdate(
                applicationId,
                asDecision(internalOutcome),
                request.reason());

            return buildCaseView(row);
        }

        private CaseView buildCaseView(CreditRecord row) {
            CreditConfig config = resolveConfigForCase(row);
            BigDecimal dtiLimit = config != null && config.getDtiLimit() != null
                    ? config.getDtiLimit()
                    : BigDecimal.ZERO;

        Integer minIncome = null;
            if (config != null && row.getProductCode() != null) {
            try {
                minIncome = resolveTerms(config, row.getProductCode()).minIncome();
            } catch (Exception e) {
                log.warn("Failed to extract minIncome for productCode {}", row.getProductCode(), e);
            }
        }

        List<CaseView.OverrideView> overrides = overrideLogs
                .findByApplicationIdOrderByOverriddenAtDescIdDesc(row.getApplicationId())
                .stream()
                .map(CaseView.OverrideView::of)
                .toList();
        return CaseView.of(row, dtiLimit, minIncome, overrides);
    }

    private CreditConfig resolveConfigForCase(CreditRecord row) {
        Long configId = row.getCreditConfigId();
        if (configId != null) {
            CreditConfig byId = creditConfigs.findById(configId).orElse(null);
            if (byId == null) {
            log.warn("config id {} not found for case {}; returning case with fallback config values",
                configId, row.getApplicationId());
            }
            return byId;
        }

        Integer configVersion = row.getCreditConfigVersion();
        if (configVersion == null) {
            log.warn("credit_config version missing for case {}; returning case with fallback config values",
                row.getApplicationId());
            return null;
        }

        CreditConfig byVersion = creditConfigs
            .findFirstByVersionOrderByEffectiveFromDescConfigIdDesc(configVersion)
            .orElse(null);
        if (byVersion == null) {
            log.warn("config version {} not found for case {}; returning case with fallback config values",
                configVersion, row.getApplicationId());
        }
        return byVersion;
    }

    /**
     * Fetch and return applicant details from the orchestrator — UC 03.
     * This is a live proxy call, never persisted. The applicant data is always fetched fresh.
     *
     * <p>When the orchestrator returns 404 (application not found in the remote store), a partial
     * fallback is built from the locally stored {@link CreditRecord}: only {@code productCode} is
     * populated; all other fields are null so the UI renders "—". The response is still HTTP 200
     * so the case detail page can show whatever it has rather than failing entirely.</p>
     */
    public ApplicantViewDto getApplicant(String applicationId) {
        try {
            Application application = orchestrator.fetchApplication(applicationId);
            return ApplicantViewDto.of(application);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Orchestrator returned 404 for applicant {}; returning partial from DB", applicationId);
            return creditRecords.findById(applicationId)
                    .map(ApplicantViewDto::fromRecord)
                    .orElseThrow(() -> new NoSuchElementException("case not found: " + applicationId));
        }
    }

    private ManualOverrideCommand parseOverride(OverrideCaseRequest request) {
        String normalizedOutcome = request.newOutcome() == null ? "" : request.newOutcome().trim().toUpperCase();
        String internalOutcome = switch (normalizedOutcome) {
            case "APPROVED" -> CreditRecord.STATUS_ACCEPTED;
            case "REFERRED" -> CreditRecord.STATUS_REFERRED;
            default -> throw new IllegalArgumentException(
                    "newOutcome must be one of APPROVED or REFERRED");
        };
        String trimmedReason = request.reason().trim();
        String trimmedOperator = request.operator().trim();
        Integer grantedLimit = acceptedGrantedLimit(internalOutcome, request.grantedLimit());
        return new ManualOverrideCommand(normalizedOutcome, internalOutcome, grantedLimit,
                trimmedReason, trimmedOperator);
    }

    private void validateOverride(CreditRecord row, ManualOverrideCommand command) {
        if (!CreditRecord.STATUS_REJECTED.equals(row.getOutcome())) {
            throw new UnprocessableCaseOverrideException(
                    "only REJECTED cases can be overridden");
        }

        if (CreditRecord.STATUS_ACCEPTED.equals(command.internalOutcome())) {
            validateGrantedLimitAgainstProductMax(row, command.grantedLimit());
        }
    }

    private void validateReferredDecision(CreditRecord row,
                                          String internalOutcome,
                                          ReferredDecisionRequest request) {
        if (!CreditRecord.STATUS_REFERRED.equals(row.getOutcome())) {
            throw new UnprocessableCaseOverrideException(
                    "only REFERRED cases can be decided here; case status is " + row.getOutcome());
        }
        if (CreditRecord.STATUS_ACCEPTED.equals(internalOutcome)) {
            validateGrantedLimitAgainstProductMax(row, request.grantedLimit());
        }
    }

    private Integer acceptedGrantedLimit(String internalOutcome, Integer grantedLimit) {
        return CreditRecord.STATUS_ACCEPTED.equals(internalOutcome) ? grantedLimit : null;
    }

    private void validateGrantedLimitAgainstProductMax(CreditRecord row, Integer grantedLimit) {
        if (grantedLimit == null) {
            throw new UnprocessableCaseOverrideException("grantedLimit is required");
        }
        if (grantedLimit <= 0) {
            throw new UnprocessableCaseOverrideException("grantedLimit must be positive");
        }

        Integer productMaxLimit = row.getProductMaxLimit();
        if (productMaxLimit == null || productMaxLimit <= 0) {
            throw new UnprocessableCaseOverrideException(
                    "productMaxLimit is missing; cannot validate grantedLimit");
        }

        if (grantedLimit >= productMaxLimit) {
            throw new UnprocessableCaseOverrideException(
                    "grantedLimit must be less than stored productMaxLimit of " + productMaxLimit);
        }
    }

    private boolean isDuplicateOverride(String applicationId,
                                        CreditRecord row,
                                        ManualOverrideCommand command) {
        boolean recordAlreadyMatches = row.getOutcome().equals(command.internalOutcome())
            && Objects.equals(row.getDecisionReason(), command.reason())
            && Objects.equals(row.getDecidedBy(), command.operator())
            && Objects.equals(
                    row.getGrantedLimit(),
                    acceptedGrantedLimit(command.internalOutcome(), command.grantedLimit()));
        if (!recordAlreadyMatches) {
            return false;
        }

        return overrideLogs.findFirstByApplicationIdOrderByOverriddenAtDescIdDesc(applicationId)
            .map(latest -> latest.getNewOutcome().equals(command.internalOutcome())
                && Objects.equals(latest.getGrantedLimit(), command.grantedLimit())
                && latest.getReason().equals(command.reason())
                && latest.getOperator().equals(command.operator()))
            .orElse(true);
    }

    private String callbackComment(CreditRecord row, ManualOverrideCommand command) {
        return switch (command.externalOutcome()) {
            case "APPROVED" -> "local-manual CRE_MANUAL_APPROVED limit="
                    + command.grantedLimit() + " apr=" + row.getApr() + " reason=" + command.reason();
            default -> "local-manual CRE_MANUAL_REFERRED reason=" + command.reason();
        };
    }

    private record ManualOverrideCommand(String externalOutcome,
                                         String internalOutcome,
                                         Integer grantedLimit,
                                         String reason,
                                         String operator) {
    }
    /**
     * Update the decision status after manual review (UC 04).
     * Called when a user accepts or declines a referred application in the UI.
     * Updates the local record and reports the decision back to the orchestrator.
     *
     * @param applicationId the case id
     * @param status        {@code ACCEPTED} or {@code REJECTED}
     * @param comment       reason for the decision
     */
    @Transactional
    public void updateCaseStatus(String applicationId, String status, String comment) {
        CreditRecord record = creditRecords.findById(applicationId)
            .orElseThrow(() -> new NoSuchElementException("Application " + applicationId + " not found"));

        try {
            Decision decision = Decision.valueOf(status);
            record.applyManualOverride(status, comment != null ? comment : "");
            creditRecords.save(record);
            log.info("Updated {} to {} (manual override)", applicationId, decision);

            // Report the decision back to the orchestrator
            orchestrator.applicationStatusUpdate(applicationId, decision, comment != null ? comment : "");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status, e);
        }
    }
}
