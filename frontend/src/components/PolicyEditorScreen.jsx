import { useEffect, useState } from 'react';
import { Button } from '../design-system';
import { fetchApi } from '../api.js';
import '../styles.css';

/**
 * UC06: Policy Editor Screen
 * Risk manager can change credit policy terms without a deploy.
 * Arrives prefilled from the simulator when opened via UC05.
 * 
 * Validation:
 * - all three catalogue products present
 * - minIncome ≥ 0, maxLimit > 0, apr > 0 with one decimal
 * - 0 < dtiLimit < 1
 * - sampleEvery ≥ 1
 */
export default function PolicyEditorScreen({ selectedVersion, onBackToList }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [submitting, setSubmitting] = useState(false);

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

  // Fetch current policy on mount
  useEffect(() => {
    const loadPolicy = async () => {
      try {
        setLoading(true);
        setError(null);
        const endpoint = selectedVersion
          ? `/api/v1/credit-policy/${selectedVersion}`
          : '/api/v1/credit-policy';
        const response = await fetchApi('GET', endpoint);
        setPolicy(response);
      } catch (err) {
        setError(err.message || 'Failed to load policy');
      } finally {
        setLoading(false);
      }
    };
    loadPolicy();
  }, [selectedVersion]);

  // Update policy config field
  const updatePolicyField = (field, value) => {
    setPolicy(prev => ({
      ...prev,
      [field]: field === 'dti_limit' ? parseFloat(value) : parseInt(value, 10),
    }));
  };

  // Update product term field
  const updateProductTerm = (index, field, value) => {
    setPolicy(prev => ({
      ...prev,
      product_terms: prev.product_terms.map((term, i) =>
        i === index
          ? {
              ...term,
              [field]:
                field === 'apr'
                  ? parseFloat(value)
                  : parseInt(value, 10),
            }
          : term
      ),
    }));
  };

  // Validate before submit
  const validate = () => {
    const errors = [];

    // Check dtiLimit: 0 < dtiLimit < 1
    if (policy.dti_limit <= 0 || policy.dti_limit >= 1) {
      errors.push('DTI Limit must be between 0 and 1 (exclusive)');
    }

    // Check sampleEvery >= 1
    if (policy.sample_every < 1) {
      errors.push('Sample Every must be >= 1');
    }

    // Check all three products present
    const productCodes = new Set(policy.product_terms.map(t => t.productCode));
    const expected = new Set(['CREDIT_CARD_REWARDS', 'CREDIT_CARD_LOW_RATE', 'CREDIT_CARD_STUDENT']);
    if (productCodes.size !== expected.size) {
      errors.push('All three credit card products must be present');
    }

    // Validate each product term
    policy.product_terms.forEach((term, i) => {
      if (term.minIncome < 0) {
        errors.push(`Product ${i + 1}: minIncome must be >= 0`);
      }
      if (term.maxLimit <= 0) {
        errors.push(`Product ${i + 1}: maxLimit must be > 0`);
      }
      if (term.apr <= 0) {
        errors.push(`Product ${i + 1}: apr must be > 0`);
      }
      if (!Number.isFinite(term.apr) || term.apr.toString().split('.')[1]?.length !== 1) {
        errors.push(`Product ${i + 1}: apr must have exactly one decimal place`);
      }
    });

    return errors;
  };

  // Submit new version
  const handleSubmit = async (e) => {
    e.preventDefault();
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
      };

      const response = await fetchApi('POST', '/api/v1/credit-policy', request);
      setSuccess(`Policy version ${response.version} created successfully!`);
      setPolicy(response);
    } catch (err) {
      setError(err.message || 'Failed to create new policy version');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div className="editor-container"><p>Loading current policy...</p></div>;
  }

  return (
    <div className="editor-container">
      <div className="policy-header-actions">
        {onBackToList && (
          <Button variant="ghost" size="sm" onClick={onBackToList}>
            Back to policy list
          </Button>
        )}
      </div>
      <h1>Credit Policy Editor</h1>
      <p className="subtitle">UC06: Risk Manager — Manage credit policy without deploy</p>

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
        <section className="policy-section">
          <h2>Policy Configuration (v{policy.version})</h2>

          <div className="form-group">
            <label>
              DTI Limit
              <span className="required">*</span>
              <span className="help-text">(0 &lt; value &lt; 1)</span>
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              max="1"
              value={policy.dti_limit}
              onChange={e => updatePolicyField('dti_limit', e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label>
              Rounding Step
              <span className="required">*</span>
            </label>
            <input
              type="number"
              min="1"
              value={policy.rounding_step}
              onChange={e => updatePolicyField('rounding_step', e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label>
              Sample Every
              <span className="required">*</span>
              <span className="help-text">(≥ 1)</span>
            </label>
            <input
              type="number"
              min="1"
              value={policy.sample_every}
              onChange={e => updatePolicyField('sample_every', e.target.value)}
              required
            />
          </div>
        </section>

        <section className="products-section">
          <h2>Product Terms</h2>
          <p className="subtitle">All three products must be configured</p>

          {policy.product_terms.map((term, i) => (
            <div key={i} className="product-card">
              <h3>{term.productCode}</h3>

              <div className="product-grid">
                <div className="form-group">
                  <label>
                    Minimum Income
                    <span className="required">*</span>
                    <span className="help-text">(≥ 0)</span>
                  </label>
                  <input
                    type="number"
                    min="0"
                    value={term.minIncome}
                    onChange={e =>
                      updateProductTerm(i, 'minIncome', e.target.value)
                    }
                    required
                  />
                </div>

                <div className="form-group">
                  <label>
                    Maximum Limit
                    <span className="required">*</span>
                    <span className="help-text">(&gt; 0)</span>
                  </label>
                  <input
                    type="number"
                    min="1"
                    value={term.maxLimit}
                    onChange={e =>
                      updateProductTerm(i, 'maxLimit', e.target.value)
                    }
                    required
                  />
                </div>

                <div className="form-group">
                  <label>
                    APR %
                    <span className="required">*</span>
                    <span className="help-text">(one decimal)</span>
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    min="0.1"
                    value={term.apr}
                    onChange={e => updateProductTerm(i, 'apr', e.target.value)}
                    required
                  />
                </div>
              </div>
            </div>
          ))}
        </section>

        <div className="form-actions">
          <button type="submit" disabled={submitting} className="btn-primary">
            {submitting ? 'Creating version...' : 'Create New Policy Version'}
          </button>
        </div>
      </form>
    </div>
  );
}
