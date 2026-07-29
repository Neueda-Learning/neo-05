import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Field,
  Grid,
  Modal,
  PageHeader,
  Select,
  Spinner,
  TextInput,
  Textarea,
} from '../design-system';
import { statusTone } from '../status.js';
import { api } from '../api.js';

/**
 * UC 02 + UC 03 — Record detail: stored workings on the left, live applicant info on the right.
 * 
 * The left side shows the decision checks (representing the credit decision workings).
 * The right side shows applicant details fetched live from the orchestrator.
 */
export default function CaseDetailScreen({ caseId, onClose }) {
  const [caseData, setCaseData] = useState(null);
  const [applicant, setApplicant] = useState(null);
  const [caseError, setCaseError] = useState(null);
  const [applicantError, setApplicantError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);
  const [updateError, setUpdateError] = useState(null);
  const [overrideOpen, setOverrideOpen] = useState(false);
  const [overrideSubmitting, setOverrideSubmitting] = useState(false);
  const [overrideError, setOverrideError] = useState(null);
  const [overrideForm, setOverrideForm] = useState({
    newOutcome: 'REFERRED',
    reason: '',
    operator: '',
  });

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        // Load both case data and applicant data in parallel
        const [caseRes, applicantRes] = await Promise.allSettled([
          api.getCase(caseId),
          api.getApplicant(caseId),
        ]);

        if (caseRes.status === 'fulfilled') {
          setCaseData(caseRes.value);
          setCaseError(null);
        } else {
          setCaseError(caseRes.reason.message);
        }

        if (applicantRes.status === 'fulfilled') {
          setApplicant(applicantRes.value);
          setApplicantError(null);
        } else {
          setApplicantError(applicantRes.reason.message);
        }
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [caseId]);

  const handleStatusUpdate = async (status) => {
    setUpdating(true);
    setUpdateError(null);
    try {
      await api.updateCaseStatus(caseId, status);
      // Update local state to reflect the change
      setCaseData(prev => ({ ...prev, outcome: status }));
      // Close the detail screen after successful update
      onClose();
    } catch (error) {
      setUpdateError(error.message);
    } finally {
      setUpdating(false);
    }
  };

  const closeOverride = useCallback(() => {
    if (overrideSubmitting) return;
    setOverrideOpen(false);
  }, [overrideSubmitting]);

  if (loading) {
    return (
      <div style={{ padding: 'var(--ds-space-6)', textAlign: 'center' }}>
        <Spinner />
      </div>
    );
  }

  const workings = caseData?.workings;
  const decisionReason = workings?.decisionReason;
  const canOverrideRejectedCase = caseData?.outcome === 'REJECTED';

  // Split multi-reason string (reasons joined by '-', e.g. "CRE_APPROVED-CRE_LIMIT_CAPPED_TO_REQUEST")
  const reasonCodes = decisionReason ? decisionReason.split('-') : [];
  const hasReason = (code) => reasonCodes.includes(code);

  const KNOWN_REASONS = [
    'CRE_REJECTED_MIN_INCOME',
    'CRE_AFFORDABILITY_EXCEEDED',
    'CRE_APPROVED',
    'CRE_LIMIT_CAPPED_TO_REQUEST',
    'CRE_LIMIT_CAPPED_TO_BAND_MAX',
  ];
  const isUnknownReason = !decisionReason || !reasonCodes.some(r => KNOWN_REASONS.includes(r));

  // Determine step statuses based on decisionReason
  const isMinIncomeFailure = hasReason('CRE_REJECTED_MIN_INCOME');
  const isAffordabilityFailure = hasReason('CRE_AFFORDABILITY_EXCEEDED');
  const step1Status = isUnknownReason ? 'UNKNOWN' : (isMinIncomeFailure ? 'FAILED' : 'PASSED');
  const step1Tone = isUnknownReason ? null : (isMinIncomeFailure ? 'negative' : 'positive');

  const step2Status = isUnknownReason ? 'UNKNOWN' : (isMinIncomeFailure ? 'PENDING' : (isAffordabilityFailure ? 'REVIEW' : 'PASSED'));
  const step2Tone = isUnknownReason ? null : (isMinIncomeFailure ? null : (isAffordabilityFailure ? 'warning' : 'positive'));

  const step3Status = isUnknownReason ? 'UNKNOWN' : ((isMinIncomeFailure || isAffordabilityFailure) ? 'PENDING' : 'PASSED');
  const step3Tone = isUnknownReason ? null : ((isMinIncomeFailure || isAffordabilityFailure) ? null : 'positive');

  // Get reason text for display
  const getMinIncomeReason = () => {
    const minIncome = workings?.minIncome;
    const actualIncome = workings?.annualIncome;
    if (minIncome !== null && minIncome !== undefined && actualIncome !== undefined) {
      return `Expected minimum income £${minIncome.toLocaleString()}, real income £${actualIncome.toLocaleString()}`;
    }
    return `Expected minimum income not met`;
  };

  const getAffordabilityReason = () => {
    if (workings?.dti === null) {
      return `Zero income cannot be afforded`;
    }
    return `Expected DTI ${workings?.dtiLimit?.toFixed(2) ?? '—'}, but ${workings?.dti?.toFixed(2) ?? '—'}`;
  };

  const openOverride = () => {
    if (!canOverrideRejectedCase) return;
    setOverrideError(null);
    setOverrideForm({
      newOutcome: 'REFERRED',
      reason: '',
      operator: '',
    });
    setOverrideOpen(true);
  };

  const capToThreeWayMinimum = () => {
    if (!workings) return null;
    const { incomeBasisLimit, requestedLimit, productMaxLimit } = workings;
    if (
      incomeBasisLimit == null ||
      requestedLimit == null ||
      productMaxLimit == null
    ) {
      return null;
    }
    return Math.min(incomeBasisLimit, requestedLimit, productMaxLimit);
  };

  const submitOverride = async (event) => {
    event.preventDefault();
    const reason = overrideForm.reason.trim();
    const operator = overrideForm.operator.trim();

    if (!reason || !operator) {
      setOverrideError('Reason and operator are required.');
      return;
    }

    const payload = {
      newOutcome: overrideForm.newOutcome,
      reason,
      operator,
    };

    if (overrideForm.newOutcome === 'APPROVED') {
      // Backend requires grantedLimit for approved overrides.
      const fallbackLimit = workings?.grantedLimit ?? capToThreeWayMinimum();
      if (fallbackLimit == null) {
        setOverrideError('This case has no stored limit basis for APPROVED override.');
        return;
      }
      payload.grantedLimit = fallbackLimit;
    }

    setOverrideSubmitting(true);
    setOverrideError(null);
    try {
      const updated = await api.overrideCase(caseId, payload);
      setCaseData(updated);
      setOverrideOpen(false);
    } catch (error) {
      setOverrideError(error.message || 'Failed to submit override.');
    } finally {
      setOverrideSubmitting(false);
    }
  };

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--ds-space-4)' }}>
        <PageHeader
          title="Record detail"
          lede={`${caseData?.reference ?? '—'} · submitted · decided with config v${caseData?.creditConfigVersion ?? '—'}`}
        />
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>

      <Grid cols={2} gap={6}>
        {/* Left: Decision Checks */}
        <div>
          {/* Outcome badge */}
          <div style={{ marginBottom: 'var(--ds-space-4)' }}>
            <Badge tone={statusTone(caseData?.outcome)}>{caseData?.outcome}</Badge>
          </div>

          {/* Check / Step 1: Income Check */}
          <Card style={{ marginBottom: 'var(--ds-space-3)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--ds-space-2)' }}>
              <h3>Check / step 1</h3>
              <Badge tone={step1Tone}>{step1Status}</Badge>
            </div>
            <p style={{ color: 'var(--ds-text-secondary)', margin: 0 }}>
              minimum income eligibility
            </p>
            {isMinIncomeFailure && (
              <div style={{ marginTop: 'var(--ds-space-2)', fontSize: '0.875rem', color: 'var(--ds-text-negative, #B3403A)' }}>
                <p style={{ margin: '0.25rem 0' }}>{getMinIncomeReason()}</p>
              </div>
            )}
            {workings && (
              <div style={{ marginTop: 'var(--ds-space-2)', fontSize: '0.875rem' }}>
                <p style={{ margin: '0.25rem 0' }}>Annual Income: £{workings.annualIncome.toLocaleString()}</p>
                <p style={{ margin: '0.25rem 0' }}>Monthly Income: £{workings.monthlyIncome.toLocaleString()}</p>
              </div>
            )}
          </Card>

          {/* Check / Step 2: DTI Check */}
          <Card style={{ marginBottom: 'var(--ds-space-3)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--ds-space-2)' }}>
              <h3>Check / step 2</h3>
              <Badge tone={step2Tone}>{step2Status}</Badge>
            </div>
            <p style={{ color: 'var(--ds-text-secondary)', margin: 0 }}>
              affordability · debt-to-income ratio
            </p>
            {isAffordabilityFailure && (
              <div style={{ marginTop: 'var(--ds-space-2)', fontSize: '0.875rem', color: 'var(--ds-text-warning, #B7791F)' }}>
                <p style={{ margin: '0.25rem 0' }}>{getAffordabilityReason()}</p>
              </div>
            )}
            {workings && (
              <div style={{ marginTop: 'var(--ds-space-2)', fontSize: '0.875rem' }}>
                <p style={{ margin: '0.25rem 0' }}>
                  DTI: {workings.dti ? workings.dti.toFixed(2) : '—'} / {workings.dtiLimit.toFixed(2)}
                </p>
                <p style={{ margin: '0.25rem 0' }}>Monthly Outgoings: £{workings.monthlyOutgoings.toLocaleString()}</p>
              </div>
            )}
          </Card>

          {/* Check / Step 3: Credit Limit */}
          <Card>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--ds-space-2)' }}>
              <h3>Check / step 3</h3>
              <Badge tone={step3Tone}>{step3Status}</Badge>
            </div>
            <p style={{ color: 'var(--ds-text-secondary)', margin: 0 }}>
              credit limit calculation · income basis and rounding
            </p>
            {workings && (
              <div style={{ marginTop: 'var(--ds-space-2)', fontSize: '0.875rem' }}>
                <p style={{ margin: '0.25rem 0' }}>Requested: £{workings.requestedLimit.toLocaleString()}</p>
                <p style={{ margin: '0.25rem 0' }}>
                  Granted: {workings.grantedLimit ? '£' + workings.grantedLimit.toLocaleString() : '—'}
                </p>
                <p style={{ margin: '0.25rem 0' }}>APR: {workings.apr ? workings.apr.toFixed(1) + '%' : '—'}</p>
              </div>
            )}
          </Card>

          {canOverrideRejectedCase && (
            <div style={{ marginTop: 'var(--ds-space-3)' }}>
              <Button variant="primary" onClick={openOverride}>
                Act on this record...
              </Button>
            </div>
          )}

          {caseError && (
            <div style={{ marginTop: 'var(--ds-space-3)' }}>
              <Alert tone="negative" title="Could not load case detail">
                {caseError}
              </Alert>
            </div>
          )}
        </div>

        {/* Right: Applicant Sidebar */}
        <div>
          <Card style={{ backgroundColor: 'var(--ds-bg-secondary)', padding: 'var(--ds-space-4)' }}>
            <h3 style={{ marginTop: 0, marginBottom: 'var(--ds-space-3)' }}>
              Applicant — live from orchestrator
            </h3>

            {applicantError && (
              <Alert tone="negative" title="Could not load applicant">
                {applicantError}
              </Alert>
            )}

            {updateError && (
              <Alert tone="negative" title="Could not update status">
                {updateError}
              </Alert>
            )}

            {applicant && (
              <div style={{ fontSize: '0.875rem' }}>
                {applicant.partial && (
                  <div style={{ marginBottom: 'var(--ds-space-3)', fontSize: '0.75rem', color: 'var(--ds-text-secondary)' }}>
                    Orchestrator unavailable — showing stored data only
                  </div>
                )}
                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Full name</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>{applicant.fullName ?? '—'}</p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Date of birth</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>{applicant.dateOfBirth ?? '—'}</p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Employment status</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>{applicant.employmentStatus ?? '—'}</p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Annual income</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>
                    {applicant.finances?.annualIncome != null ? `£${applicant.finances.annualIncome.toLocaleString()}` : '—'}
                  </p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>
                    Monthly housing cost
                  </p>
                  <p style={{ margin: 0, fontWeight: 500 }}>
                    {applicant.finances?.monthlyHousingCost != null ? `£${applicant.finances.monthlyHousingCost.toLocaleString()}` : '—'}
                  </p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>
                    Existing credit commitments
                  </p>
                  <p style={{ margin: 0, fontWeight: 500 }}>
                    {applicant.finances?.existingCreditCommitments != null ? `£${applicant.finances.existingCreditCommitments.toLocaleString()}` : '—'}
                  </p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>
                    Requested credit limit
                  </p>
                  <p style={{ margin: 0, fontWeight: 500 }}>
                    {applicant.requestedCreditLimit != null ? `£${applicant.requestedCreditLimit.toLocaleString()}` : '—'}
                  </p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Channel</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>{applicant.channel ?? '—'}</p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Product code</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>{applicant.productCode ?? '—'}</p>
                </div>

                <div
                  style={{
                    marginTop: 'var(--ds-space-4)',
                    paddingTop: 'var(--ds-space-3)',
                    borderTop: '1px solid var(--ds-border)',
                  }}
                >
                  <div style={{ fontSize: '0.75rem', color: 'var(--ds-text-secondary)', marginBottom: 'var(--ds-space-3)' }}>
                    fetched on open — never stored
                  </div>

                  <div style={{ display: 'flex', gap: 'var(--ds-space-2)' }}>
                    <Button
                      variant="primary"
                      style={{ flex: 1 }}
                      onClick={() => handleStatusUpdate('ACCEPTED')}
                      disabled={updating}
                    >
                      {updating ? 'Updating…' : 'Accept'}
                    </Button>
                    <Button
                      variant="secondary"
                      style={{ flex: 1 }}
                      onClick={() => handleStatusUpdate('REJECTED')}
                      disabled={updating}
                    >
                      {updating ? 'Updating…' : 'Decline'}
                    </Button>
                  </div>
                </div>
              </div>
            )}
          </Card>
        </div>
      </Grid>

      <Modal
        open={overrideOpen}
        onClose={closeOverride}
        title="Act on this record"
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--ds-space-2)' }}>
            <Button variant="secondary" onClick={closeOverride} disabled={overrideSubmitting}>
              Cancel
            </Button>
            <Button
              variant="primary"
              type="submit"
              form="override-case-form"
              busy={overrideSubmitting}
              busyLabel="Submitting..."
            >
              Submit
            </Button>
          </div>
        }
      >
        <form id="override-case-form" onSubmit={submitOverride}>
          {overrideError && (
            <div style={{ marginBottom: 'var(--ds-space-3)' }}>
              <Alert tone="negative" title="Could not submit override">
                {overrideError}
              </Alert>
            </div>
          )}

          <Field label="New outcome" required>
            {({ id }) => (
              <Select
                id={id}
                value={overrideForm.newOutcome}
                onChange={(event) =>
                  setOverrideForm((prev) => ({ ...prev, newOutcome: event.target.value }))
                }
              >
                <option value="APPROVED">APPROVED</option>
                <option value="REFERRED">REFERRED</option>
              </Select>
            )}
          </Field>

          <Field
            label="Reason"
            required
            hint="Please provide a clear explanation for audit trail."
            style={{ marginTop: 'var(--ds-space-3)' }}
          >
            {({ id }) => (
              <Textarea
                id={id}
                rows={6}
                value={overrideForm.reason}
                onChange={(event) =>
                  setOverrideForm((prev) => ({ ...prev, reason: event.target.value }))
                }
                placeholder="Describe why the machine outcome is being overridden"
              />
            )}
          </Field>

          <Field label="Operator" required style={{ marginTop: 'var(--ds-space-3)' }}>
            {({ id }) => (
              <TextInput
                id={id}
                value={overrideForm.operator}
                onChange={(event) =>
                  setOverrideForm((prev) => ({ ...prev, operator: event.target.value }))
                }
                placeholder="e.g. b.dimovski"
              />
            )}
          </Field>
        </form>
      </Modal>
    </>
  );
}
