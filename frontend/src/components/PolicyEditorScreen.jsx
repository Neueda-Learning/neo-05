import { useEffect, useRef, useState } from 'react';
import { Button, EmptyState } from '../design-system';
import { fetchApi } from '../api.js';
import '../styles.css';

const POLICY_TO_PRODUCT_CODE = {
  PLATINUM: 'CREDIT_CARD_REWARDS',
  PREMIUM: 'CREDIT_CARD_LOW_RATE',
  STUDENT: 'CREDIT_CARD_STUDENT',
};

// What-if's draft uses CREDIT_CARD_STANDARD/REWARDS/STUDENT; this editor's own
// product_terms use CREDIT_CARD_LOW_RATE for the same "premium" tier — map on the way in.
function mapDraftProductCode(rawCode) {
  const code = String(rawCode || '').toUpperCase();
  if (code.includes('STANDARD') || code.includes('PREMIUM') || code.includes('LOW_RATE')) {
    return 'CREDIT_CARD_LOW_RATE';
  }
  if (code.includes('REWARDS') || code.includes('PLATINUM')) {
    return 'CREDIT_CARD_REWARDS';
  }
  if (code.includes('STUDENT')) {
    return 'CREDIT_CARD_STUDENT';
  }
  return rawCode;
}

export default function PolicyEditorScreen({ selectedVersion, selectedPolicyCode, initialDraft, onBackToList }) {
  const [isDraftFromVersion, setIsDraftFromVersion] = useState(false);
  const isReadOnly = Number.isInteger(selectedVersion) && !isDraftFromVersion;
  const [rememberedPolicyCode, setRememberedPolicyCode] = useState(selectedPolicyCode || null);
  const [latestVersion, setLatestVersion] = useState(null);
  const [historyRows, setHistoryRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const appliedDraftRef = useRef(null);

  const [policy, setPolicy] = useState({
    version: null,
    dti_limit: 0.45,
    rounding_step: 100,
    sample_every: 10,
    product_terms: [
      { productCode: 'CREDIT_CARD_REWARDS', minIncome: 24000, maxLimit: 5000, apr: 18.9 },
      { productCode: 'CREDIT_CARD_LOW_RATE', minIncome: 18000, maxLimit: 3000, apr: 12.9 },
      { productCode: 'CREDIT_CARD_STUDENT', minIncome: 12000, maxLimit: 1500, apr: 22.9 },
    ],
  });

  useEffect(() => {
    const loadPolicy = async () => {
      try {
        setLoading(true);
        setError(null);
        const endpoint = selectedVersion
          ? `/api/v1/credit-policy/${selectedVersion}${selectedPolicyCode ? `?policyCode=${encodeURIComponent(selectedPolicyCode)}` : ''}`
          : '/api/v1/credit-policy';
        const response = await fetchApi('GET', endpoint);

        setPolicy(response);

        const currentPolicyCode = selectedPolicyCode || response?.policy_name || null;
        const versions = currentPolicyCode
          ? await fetchApi('GET', `/api/v1/credit-policy/versions?policyCode=${encodeURIComponent(currentPolicyCode)}`)
          : await fetchApi('GET', '/api/v1/credit-policy/versions');

        setLatestVersion(Number(versions?.[0]?.version || response?.version || 0) || null);
        setHistoryRows(
          Array.isArray(versions)
            ? versions.filter((row) => Number(row.version) !== Number(response?.version))
            : []
        );

        if (selectedPolicyCode) {
          setRememberedPolicyCode(selectedPolicyCode);
        } else if (response?.policy_name && String(response.policy_name).trim()) {
          setRememberedPolicyCode(String(response.policy_name).trim());
        } else {
          setRememberedPolicyCode(null);
        }
      } catch (err) {
        setError(err.message || 'Failed to load policy');
        setHistoryRows([]);
      } finally {
        setLoading(false);
      }
    };

    loadPolicy();
  }, [selectedVersion, selectedPolicyCode]);

  useEffect(() => {
    setIsDraftFromVersion(false);
    setSuccess(null);
    setError(null);
    setRememberedPolicyCode(selectedPolicyCode || null);
  }, [selectedVersion, selectedPolicyCode]);

  // Carries over the What-if Simulator's draft (dti_limit/rounding_step/sample_every/product_terms)
  // once the baseline policy has finished loading, so "Apply Draft" doesn't land on an empty form.
  // Guarded by object identity so it applies exactly once per draft, and never re-applies over
  // the operator's own edits.
  useEffect(() => {
    if (loading || !initialDraft || appliedDraftRef.current === initialDraft) {
      return;
    }
    appliedDraftRef.current = initialDraft;

    const mappedTerms = Array.isArray(initialDraft.product_terms)
      ? initialDraft.product_terms.map((term) => ({
          productCode: mapDraftProductCode(term.productCode),
          minIncome: term.minIncome,
          maxLimit: term.maxLimit,
          apr: term.apr,
        }))
      : [];

    setPolicy((prev) => ({
      ...prev,
      dti_limit: Number(initialDraft.dti_limit),
      rounding_step: Number(initialDraft.rounding_step),
      sample_every: Number(initialDraft.sample_every),
      product_terms: mappedTerms.length === 3 ? mappedTerms : prev.product_terms,
    }));
    setIsDraftFromVersion(true);
    setSuccess(null);
    setError(null);
  }, [loading, initialDraft]);

  const updatePolicyField = (field, value) => {
    setPolicy((prev) => ({
      ...prev,
      [field]: field === 'dti_limit' ? parseFloat(value) : parseInt(value, 10),
    }));
  };

  const updateProductTerm = (productCode, field, value) => {
    setPolicy((prev) => ({
      ...prev,
      product_terms: prev.product_terms.map((term) =>
        term.productCode === productCode
          ? {
              ...term,
              [field]: field === 'apr' ? parseFloat(value) : parseInt(value, 10),
            }
          : term
      ),
    }));
  };

  const selectedProductCode = POLICY_TO_PRODUCT_CODE[String(rememberedPolicyCode || '').toUpperCase()] || null;
  const visibleProductTerms = selectedProductCode
    ? policy.product_terms.filter((term) => term.productCode === selectedProductCode)
    : policy.product_terms;
  const primaryTerm = visibleProductTerms[0] || null;

  const validate = () => {
    const errors = [];

    if (policy.dti_limit <= 0 || policy.dti_limit >= 1) {
      errors.push('DTI Limit must be between 0 and 1 (exclusive)');
    }

    if (policy.sample_every < 1) {
      errors.push('Sample Every must be >= 1');
    }

    const productCodes = new Set(policy.product_terms.map((term) => term.productCode));
    const expected = new Set(['CREDIT_CARD_REWARDS', 'CREDIT_CARD_LOW_RATE', 'CREDIT_CARD_STUDENT']);
    if (productCodes.size !== expected.size) {
      errors.push('All three credit card products must be present');
    }

    policy.product_terms.forEach((term, index) => {
      if (term.minIncome < 0) {
        errors.push(`Product ${index + 1}: minIncome must be >= 0`);
      }
      if (term.maxLimit <= 0) {
        errors.push(`Product ${index + 1}: maxLimit must be > 0`);
      }
      if (term.apr <= 0) {
        errors.push(`Product ${index + 1}: apr must be > 0`);
      }
      if (!Number.isFinite(term.apr) || term.apr.toString().split('.')[1]?.length !== 1) {
        errors.push(`Product ${index + 1}: apr must have exactly one decimal place`);
      }
    });

    return errors;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (isReadOnly) {
      return;
    }

    setError(null);
    setSuccess(null);

    const validationErrors = validate();
    if (validationErrors.length > 0) {
      setError(validationErrors.join('\n'));
      return;
    }

    try {
      setSubmitting(true);
      const request = {
        dti_limit: policy.dti_limit,
        rounding_step: policy.rounding_step,
        sample_every: policy.sample_every,
        product_terms: policy.product_terms,
        policy_code: rememberedPolicyCode,
      };

      const response = await fetchApi('POST', '/api/v1/credit-policy', request);
      setSuccess(`Policy version ${response.version} created successfully!`);
      setPolicy(response);
      if (onBackToList) {
        onBackToList({
          notice: `Policy version ${response.version} created successfully.`,
        });
      }
    } catch (err) {
      setError(err.message || 'Failed to create new policy version');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div className="editor-container"><p>Loading policy...</p></div>;
  }

  const getHistoryTerm = (row) => {
    const terms = Array.isArray(row?.product_terms) ? row.product_terms : [];
    if (selectedProductCode) {
      return terms.find((term) => term?.productCode === selectedProductCode) || null;
    }
    return terms[0] || null;
  };

  const formatDateTime = (value) => {
    if (!value) {
      return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString();
  };

  return (
    <div className="editor-container">
      <h1>Credit Policy Editor</h1>
      <p className="subtitle">UC06: Risk Manager — Manage credit policy without deploy</p>

      {isReadOnly && (
        <div className="success-box">
          <strong>Read-only details:</strong> viewing saved policy version v{policy.version}.
          {latestVersion != null && <> Saving for this policy stream creates v{latestVersion + 1}.</>}
        </div>
      )}

      {isDraftFromVersion && (
        <div className="success-box">
          <strong>Draft mode:</strong> editing values from v{policy.version}.{' '}
          {latestVersion != null
            ? `Saving will create policy version v${latestVersion + 1}.`
            : 'Saving will create the next version in this policy stream.'}
        </div>
      )}

      {error && (
        <div className="error-box">
          <strong>Error:</strong>
          <pre>{error}</pre>
        </div>
      )}

      {success && (
        <div className="success-box">
          <strong>Success:</strong> {success}
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div
          className="policy-header-actions"
          style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}
        >
          {onBackToList && (
            <Button variant="secondary" size="sm" onClick={onBackToList}>
              Back to policy list
            </Button>
          )}
          {selectedVersion != null && (
            <Button
              variant="secondary"
              size="sm"
              type="button"
              onClick={() => {
                setIsDraftFromVersion((current) => !current);
                setSuccess(null);
                setError(null);
              }}
            >
              {isDraftFromVersion ? 'Cancel' : 'New Version'}
            </Button>
          )}
          {!isReadOnly && (
            <div style={{ marginLeft: 'auto' }}>
              <Button
                variant="secondary"
                size="sm"
                type="submit"
                busy={submitting}
                busyLabel="Creating version..."
              >
                Create New Policy Version
              </Button>
            </div>
          )}
        </div>

        {selectedProductCode && (
          <p className="subtitle" style={{ marginBottom: '0.75rem' }}>
            Showing {selectedProductCode} for this policy stream. Other product terms are preserved unchanged.
          </p>
        )}

        <div style={{ marginBottom: '1rem' }}>
          <table className="ds-table">
            <tbody>
                <tr>
                  <td>Policy Configuration</td>
                  <td>v{policy.version}</td>
                  <td>Policy Code</td>
                  <td>{rememberedPolicyCode || '-'}</td>
                </tr>
                <tr>
                  <td>DTI Limit <span className="required">*</span> <span className="help-text">(0 &lt; value &lt; 1)</span></td>
                  <td>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      max="1"
                      value={policy.dti_limit}
                      onChange={(e) => updatePolicyField('dti_limit', e.target.value)}
                      disabled={isReadOnly}
                      required
                    />
                  </td>
                  <td>Min Income <span className="required">*</span> <span className="help-text">(≥ 0)</span></td>
                  <td>
                    {primaryTerm ? (
                      <input
                        type="number"
                        min="0"
                        value={primaryTerm.minIncome}
                        onChange={(e) => updateProductTerm(primaryTerm.productCode, 'minIncome', e.target.value)}
                        disabled={isReadOnly}
                        required
                      />
                    ) : '-'}
                  </td>
                </tr>
                <tr>
                  <td>Sample Every <span className="required">*</span> <span className="help-text">(≥ 1)</span></td>
                  <td>
                    <input
                      type="number"
                      min="1"
                      value={policy.sample_every}
                      onChange={(e) => updatePolicyField('sample_every', e.target.value)}
                      disabled={isReadOnly}
                      required
                    />
                  </td>
                  <td>Max Limit <span className="required">*</span> <span className="help-text">(&gt; 0)</span></td>
                  <td>
                    {primaryTerm ? (
                      <input
                        type="number"
                        min="1"
                        value={primaryTerm.maxLimit}
                        onChange={(e) => updateProductTerm(primaryTerm.productCode, 'maxLimit', e.target.value)}
                        disabled={isReadOnly}
                        required
                      />
                    ) : '-'}
                  </td>
                </tr>
                <tr>
                  <td>Rounding Step <span className="required">*</span></td>
                  <td>
                    <input
                      type="number"
                      min="1"
                      value={policy.rounding_step}
                      onChange={(e) => updatePolicyField('rounding_step', e.target.value)}
                      disabled={isReadOnly}
                      required
                    />
                  </td>
                  <td>APR % <span className="required">*</span> <span className="help-text">(one decimal)</span></td>
                  <td>
                    {primaryTerm ? (
                      <input
                        type="number"
                        step="0.1"
                        min="0.1"
                        value={primaryTerm.apr}
                        onChange={(e) => updateProductTerm(primaryTerm.productCode, 'apr', e.target.value)}
                        disabled={isReadOnly}
                        required
                      />
                    ) : '-'}
                  </td>
                </tr>
                {!selectedProductCode && visibleProductTerms.slice(1).flatMap((term) => ([
                  <tr key={`${term.productCode}-row1`}>
                    <td>{term.productCode} Min Income <span className="required">*</span> <span className="help-text">(≥ 0)</span></td>
                    <td>
                      <input
                        type="number"
                        min="0"
                        value={term.minIncome}
                        onChange={(e) => updateProductTerm(term.productCode, 'minIncome', e.target.value)}
                        disabled={isReadOnly}
                        required
                      />
                    </td>
                    <td>{term.productCode} Max Limit <span className="required">*</span> <span className="help-text">(&gt; 0)</span></td>
                    <td>
                      <input
                        type="number"
                        min="1"
                        value={term.maxLimit}
                        onChange={(e) => updateProductTerm(term.productCode, 'maxLimit', e.target.value)}
                        disabled={isReadOnly}
                        required
                      />
                    </td>
                  </tr>,
                  <tr key={`${term.productCode}-row2`}>
                    <td>{term.productCode} APR % <span className="required">*</span> <span className="help-text">(one decimal)</span></td>
                    <td>
                      <input
                        type="number"
                        step="0.1"
                        min="0.1"
                        value={term.apr}
                        onChange={(e) => updateProductTerm(term.productCode, 'apr', e.target.value)}
                        disabled={isReadOnly}
                        required
                      />
                    </td>
                    <td></td>
                    <td></td>
                  </tr>
                ]))}
            </tbody>
          </table>
        </div>

      </form>

      <h2>History</h2>
      <div style={{ maxHeight: '18rem', overflowY: 'auto' }}>
        {historyRows.length === 0 ? (
          <EmptyState title="No previous versions">
            This policy does not have older saved versions yet.
          </EmptyState>
        ) : (
          <table className="ds-table">
            <thead>
              <tr>
                <th>Version</th>
                <th>DTI Limit</th>
                <th>Min Income</th>
                <th>Sample Every</th>
                <th>Max Limit</th>
                <th>Rounding Step</th>
                <th>APR %</th>
                <th>Effective Time</th>
              </tr>
            </thead>
            <tbody>
              {historyRows.map((row) => {
                const term = getHistoryTerm(row);
                return (
                  <tr key={`${row.policy_name || rememberedPolicyCode || 'policy'}-${row.version}`}>
                    <td>{row.version ?? '-'}</td>
                    <td>{row.dti_limit ?? '-'}</td>
                    <td>{term?.minIncome ?? '-'}</td>
                    <td>{row.sample_every ?? '-'}</td>
                    <td>{term?.maxLimit ?? '-'}</td>
                    <td>{row.rounding_step ?? '-'}</td>
                    <td>{term?.apr ?? '-'}</td>
                    <td>{formatDateTime(row.effective_from)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
      <p className="ds-table__footnote" style={{ marginTop: '0.5rem' }}>
        {historyRows.length} {historyRows.length === 1 ? 'match' : 'matches'} · previous versions of this policy
      </p>
    </div>
  );
}
