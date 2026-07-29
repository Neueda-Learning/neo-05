import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  DataTable,
  EmptyState,
  Grid,
  MetricTile,
  PageHeader,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';

const CATALOGUE = [
  { code: 'CREDIT_CARD_PREMIUM', label: 'Premium', policyCode: 'PREMIUM' },
  { code: 'CREDIT_CARD_PLATINUM', label: 'Platinum', policyCode: 'PLATINUM' },
  { code: 'CREDIT_CARD_STUDENT', label: 'Student', policyCode: 'STUDENT' },
];

const FALLBACK_DRAFT = {
  dti_limit: 0.45,
  rounding_step: 100,
  sample_every: 7,
  product_terms: [
    { productCode: 'CREDIT_CARD_PREMIUM', minIncome: 12000, maxLimit: 5000, apr: 29.9 },
    { productCode: 'CREDIT_CARD_PLATINUM', minIncome: 20000, maxLimit: 10000, apr: 24.9 },
    { productCode: 'CREDIT_CARD_STUDENT', minIncome: 0, maxLimit: 1000, apr: 34.9 },
  ],
};

function normalizeTerms(terms = []) {
  const map = new Map();
  terms.forEach((term) => {
    const code = String(term.productCode || '').toUpperCase();
    if (code.includes('LOW_RATE') || code.includes('STANDARD') || code.includes('PREMIUM')) {
      map.set('CREDIT_CARD_PREMIUM', {
        productCode: 'CREDIT_CARD_PREMIUM',
        minIncome: term.minIncome,
        maxLimit: term.maxLimit,
        apr: term.apr,
      });
      return;
    }
    if (code.includes('REWARDS') || code.includes('PLATINUM')) {
      map.set('CREDIT_CARD_PLATINUM', {
        productCode: 'CREDIT_CARD_PLATINUM',
        minIncome: term.minIncome,
        maxLimit: term.maxLimit,
        apr: term.apr,
      });
      return;
    }
    if (code.includes('STUDENT')) {
      map.set('CREDIT_CARD_STUDENT', {
        productCode: 'CREDIT_CARD_STUDENT',
        minIncome: term.minIncome,
        maxLimit: term.maxLimit,
        apr: term.apr,
      });
    }
  });

  return CATALOGUE.map(({ code }) => map.get(code)).filter(Boolean);
}

export default function WhatIfSimulatorScreen({ onOpenPolicyEditor, onOpenCase }) {
  const [loadingConfig, setLoadingConfig] = useState(true);
  const [simulating, setSimulating] = useState(false);
  const [error, setError] = useState(null);
  const [selectedProductIndex, setSelectedProductIndex] = useState(0);
  const [currentPolicy, setCurrentPolicy] = useState(FALLBACK_DRAFT);
  const [draft, setDraft] = useState(FALLBACK_DRAFT);
  const [result, setResult] = useState(null);

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const current = await api.getCreditPolicy();
        if (!active) return;
        const terms = normalizeTerms(current.product_terms);
        const normalized = {
          dti_limit: Number(current.dti_limit),
          rounding_step: Number(current.rounding_step),
          sample_every: Number(current.sample_every),
          product_terms: terms.length === 3 ? terms : FALLBACK_DRAFT.product_terms,
        };
        setCurrentPolicy(normalized);
        setDraft(normalized);
      } catch {
        // Keep fallback values so simulator is still usable.
      } finally {
        if (active) setLoadingConfig(false);
      }
    };
    load();
    return () => {
      active = false;
    };
  }, []);

  const updateTerm = (index, field, value) => {
    setDraft((prev) => ({
      ...prev,
      product_terms: prev.product_terms.map((term, i) =>
        i === index
          ? {
              ...term,
              [field]: field === 'apr' ? Number(value) : parseInt(value, 10),
            }
          : term
      ),
    }));
  };

  const selectedTerm = draft.product_terms[selectedProductIndex] || null;

  const runSimulation = async () => {
    setError(null);
    setSimulating(true);
    try {
      const response = await api.simulateWhatIf(draft);
      setResult(response);
    } catch (e) {
      setError(e.message || 'Simulation failed');
    } finally {
      setSimulating(false);
    }
  };

  const arrowFor = (from, to) => {
    if (from === 'REFERRED' && to === 'ACCEPTED') {
      return <span className="whatif-arrow whatif-up">↑</span>;
    }
    if (from === 'ACCEPTED' && to === 'REFERRED') {
      return <span className="whatif-arrow whatif-down">↓</span>;
    }
    return <span className="whatif-arrow">→</span>;
  };

  const columns = useMemo(
    () => [
      {
        key: 'applicationId',
        header: 'Application ID',
        mono: true,
        render: (row) => (
          <button
            type="button"
            className="whatif-link-button"
            onClick={() => onOpenCase?.(row.applicationId)}
          >
            {row.applicationId}
          </button>
        ),
      },
      {
        key: 'from',
        header: 'From',
        tight: true,
        render: (row) => <Badge tone="warning">{row.from}</Badge>,
      },
      {
        key: 'to',
        header: 'To',
        tight: true,
        render: (row) => <Badge tone="positive">{row.to}</Badge>,
      },
      {
        key: 'direction',
        header: 'Direction',
        tight: true,
        render: (row) => arrowFor(row.from, row.to),
      },
    ],
    [onOpenCase]
  );

  return (
    <>
      <PageHeader
        title="What-if Simulator"
        lede="simulate here, ship in policy editor · read-only, idempotent"
      />

      {error && (
        <Alert tone="negative" title="Simulation failed">
          {error}
        </Alert>
      )}

      <div className="whatif-layout">
        <section className="whatif-pane">
          <h2>Draft Config</h2>

          <div className="whatif-banner" role="note" aria-live="polite">
            This simulation saves nothing and triggers no callbacks
          </div>

          <div className="whatif-field-row">
            <label htmlFor="dtiLimit">DTI Limit</label>
            <div className="whatif-dti-controls">
              <input
                id="dtiLimit"
                type="range"
                min="0.01"
                max="0.99"
                step="0.01"
                value={draft.dti_limit}
                onChange={(e) => setDraft((prev) => ({ ...prev, dti_limit: Number(e.target.value) }))}
                disabled={loadingConfig || simulating}
              />
              <input
                type="number"
                min="0.01"
                max="0.99"
                step="0.01"
                value={draft.dti_limit}
                onChange={(e) => setDraft((prev) => ({ ...prev, dti_limit: Number(e.target.value) }))}
                disabled={loadingConfig || simulating}
              />
            </div>
            <p className="whatif-compare">Current: {Number(currentPolicy.dti_limit).toFixed(2)} -&gt; Draft: {Number(draft.dti_limit).toFixed(2)}</p>
          </div>

          <div className="whatif-field-row">
            <label htmlFor="roundingStep">Rounding Step</label>
            <input
              id="roundingStep"
              type="number"
              min="1"
              value={draft.rounding_step}
              onChange={(e) => setDraft((prev) => ({ ...prev, rounding_step: parseInt(e.target.value, 10) || 1 }))}
              disabled={simulating}
            />
            <p className="whatif-compare">Current: {Number(currentPolicy.rounding_step)} -&gt; Draft: {Number(draft.rounding_step)}</p>
          </div>

          <div className="whatif-field-row">
            <label htmlFor="sampleEvery">Sample Every</label>
            <input
              id="sampleEvery"
              type="number"
              min="1"
              value={draft.sample_every}
              onChange={(e) => setDraft((prev) => ({ ...prev, sample_every: parseInt(e.target.value, 10) || 1 }))}
              disabled={simulating}
            />
          </div>

          <div className="whatif-terms-header whatif-section-divider">
            <h3>Product Terms</h3>
          </div>

          <div className="whatif-field-row">
            <label htmlFor="productType">Product Type</label>
            <select
              id="productType"
              value={selectedProductIndex}
              onChange={(e) => setSelectedProductIndex(Number(e.target.value))}
              disabled={simulating}
            >
              {CATALOGUE.map((entry, index) => (
                <option key={entry.code} value={index}>
                  {entry.label}
                </option>
              ))}
            </select>
          </div>

          {selectedTerm && (
            <div className="product-grid">
              <div className="form-group">
                <label>Min Income</label>
                <input
                  type="number"
                  min="0"
                  value={selectedTerm.minIncome}
                  onChange={(e) => updateTerm(selectedProductIndex, 'minIncome', e.target.value)}
                />
              </div>
              <div className="form-group">
                <label>Max Limit</label>
                <input
                  type="number"
                  min="1"
                  value={selectedTerm.maxLimit}
                  onChange={(e) => updateTerm(selectedProductIndex, 'maxLimit', e.target.value)}
                />
              </div>
              <div className="form-group">
                <label>APR %</label>
                <input
                  type="number"
                  min="0.1"
                  step="0.1"
                  value={selectedTerm.apr}
                  onChange={(e) => updateTerm(selectedProductIndex, 'apr', e.target.value)}
                />
              </div>
            </div>
          )}

          <Toolbar className="whatif-section-divider">
            <Button variant="secondary" size="sm" onClick={runSimulation} busy={simulating} busyLabel="Running">
              Run Simulation
            </Button>
          </Toolbar>
        </section>

        <section className="whatif-pane">
          <div className="whatif-results-head">
            <h2>Results</h2>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => onOpenPolicyEditor?.(draft, CATALOGUE[selectedProductIndex]?.policyCode)}
            >
              Apply Draft -&gt;
            </Button>
          </div>

          <Grid cols={2} min={180}>
            <MetricTile label="Evaluated" value={result?.evaluated ?? 0} />
            <MetricTile label="Flips" value={result?.flips ?? 0} tone={(result?.flips ?? 0) > 0 ? 'warning' : 'positive'} />
          </Grid>

          <div className="whatif-results-body">
            {(result && result.flips === 0) ? (
              <EmptyState title="No changes from current config">
                This draft matches the current config exactly. No cases flip.
              </EmptyState>
            ) : (
              <DataTable
                className="whatif-results-table"
                columns={columns}
                rows={result?.changes ?? []}
                total={result?.changes?.length ?? 0}
                rowKey={(row) => row.applicationId}
                footnote={result ? 'flips only' : 'Run simulation to show flips only'}
                empty={<EmptyState title="No flips yet">Run simulation to see flipped cases.</EmptyState>}
              />
            )}
          </div>
        </section>
      </div>
    </>
  );
}
