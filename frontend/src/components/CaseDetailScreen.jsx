import React, { useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Grid, PageHeader, Spinner } from '../design-system';
import { statusTone, time } from '../status.js';
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

  if (loading) {
    return (
      <div style={{ padding: 'var(--ds-space-6)', textAlign: 'center' }}>
        <Spinner />
      </div>
    );
  }

  const workings = caseData?.workings;
  const dtiStatus = !workings?.dti
    ? null
    : workings.dti > workings.dtiLimit
      ? 'REFERRED'
      : 'PASSED';

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
              {workings && workings.annualIncome > 0 ? (
                <Badge tone="positive">PASSED</Badge>
              ) : (
                <Badge tone="negative">REVIEW</Badge>
              )}
            </div>
            <p style={{ color: 'var(--ds-text-secondary)', margin: 0 }}>
              minimum income eligibility
            </p>
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
              <Badge tone={dtiStatus === 'REFERRED' ? 'warning' : 'positive'}>
                {dtiStatus || 'PENDING'}
              </Badge>
            </div>
            <p style={{ color: 'var(--ds-text-secondary)', margin: 0 }}>
              affordability · debt-to-income ratio
            </p>
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
              <Badge tone="positive">PASSED</Badge>
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
                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Full name</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>{applicant.fullName}</p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Date of birth</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>{applicant.dateOfBirth}</p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Employment status</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>{applicant.employmentStatus}</p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>Annual income</p>
                  <p style={{ margin: 0, fontWeight: 500 }}>
                    £{applicant.finances.annualIncome.toLocaleString()}
                  </p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>
                    Monthly housing cost
                  </p>
                  <p style={{ margin: 0, fontWeight: 500 }}>
                    £{applicant.finances.monthlyHousingCost.toLocaleString()}
                  </p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>
                    Existing credit commitments
                  </p>
                  <p style={{ margin: 0, fontWeight: 500 }}>
                    £{applicant.finances.existingCreditCommitments.toLocaleString()}
                  </p>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-3)' }}>
                  <p style={{ color: 'var(--ds-text-secondary)', margin: '0 0 0.25rem 0' }}>
                    Requested credit limit
                  </p>
                  <p style={{ margin: 0, fontWeight: 500 }}>
                    £{applicant.requestedCreditLimit.toLocaleString()}
                  </p>
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
    </>
  );
}
