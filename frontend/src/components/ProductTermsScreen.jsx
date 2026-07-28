import React, { useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  ChipGroup,
  DataTable,
  EmptyState,
  Field,
  FormActions,
  FormGrid,
  PageHeader,
  Section,
  Split,
  TextInput,
} from '../design-system';
import { api } from '../api.js';

const PRODUCTS = ['PREMIUM', 'PLATINUM', 'STUDENT'];

const DEFAULT_TERMS = {
  PREMIUM:  { minIncome: 18000, maxLimit: 5000, apr: '12.9' },
  PLATINUM: { minIncome: 24000, maxLimit: 8000, apr: '14.9' },
  STUDENT:  { minIncome: 12000, maxLimit: 1500, apr: '9.9' },
};

function emptyForm() {
  return {
    productTerms: structuredClone(DEFAULT_TERMS),
    dtiLimit: '0.45',
    roundingStep: '100',
    sampleEvery: '7',
  };
}

/**
 * Product Terms screen — lets a risk manager publish a new versioned credit-policy document
 * without a deploy. Every submit is an INSERT; the previous version is never touched.
 */
export default function ProductTermsScreen() {
  const [form, setForm] = useState(emptyForm());
  const [showForm, setShowForm] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState('PREMIUM');
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(null);   // {version}
  const [error, setError] = useState(null);
  const [current, setCurrent] = useState(null);
  const [history, setHistory] = useState([]);
  const [loadError, setLoadError] = useState(null);

  async function loadConfigViews() {
    try {
      const [c, h] = await Promise.all([api.currentConfig(), api.configHistory()]);
      setCurrent(c);
      setHistory(h);
      setLoadError(null);
    } catch (err) {
      setLoadError(err.message);
    }
  }

  useEffect(() => {
    loadConfigViews();
  }, []);

  function setProductField(product, field, value) {
    setForm((prev) => ({
      ...prev,
      productTerms: {
        ...prev.productTerms,
        [product]: { ...prev.productTerms[product], [field]: value },
      },
    }));
  }

  function setTopField(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setSuccess(null);
    setError(null);
    try {
      const payload = {
        productTerms: Object.fromEntries(
          PRODUCTS.map((p) => [
            p,
            {
              minIncome: Number(form.productTerms[p].minIncome),
              maxLimit: Number(form.productTerms[p].maxLimit),
              apr: Number(form.productTerms[p].apr),
            },
          ])
        ),
        dtiLimit: Number(form.dtiLimit),
        roundingStep: Number(form.roundingStep),
        sampleEvery: Number(form.sampleEvery),
      };
      const result = await api.createConfig(payload);
      setSuccess(result);
      setForm(emptyForm());
      setShowForm(false);
      await loadConfigViews();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  const currentRows = (() => {
    if (!current) return [];

    const rows = [
      { key: 'version', ver: `v${current.version}`, value: String(current.version), active: 'Yes' },
      { key: 'dtiLimit', ver: `v${current.version}`, value: String(current.dtiLimit), active: 'Yes' },
      { key: 'roundingStep', ver: `v${current.version}`, value: String(current.roundingStep), active: 'Yes' },
      { key: 'sampleEvery', ver: `v${current.version}`, value: String(current.sampleEvery), active: 'Yes' },
      {
        key: 'effectiveFrom',
        ver: `v${current.version}`,
        value: current.effectiveFrom ? new Date(current.effectiveFrom).toLocaleString() : '—',
        active: 'Yes',
      },
    ];

    const terms = current.productTerms?.[selectedProduct];
    rows.push({
      key: 'minIncome',
      ver: `v${current.version}`,
      value: terms ? String(terms.minIncome) : '—',
      active: 'Yes',
    });
    rows.push({
      key: 'maxLimit',
      ver: `v${current.version}`,
      value: terms ? String(terms.maxLimit) : '—',
      active: 'Yes',
    });
    rows.push({
      key: 'apr',
      ver: `v${current.version}`,
      value: terms ? String(terms.apr) : '—',
      active: 'Yes',
    });

    return rows;
  })();

  const currentColumns = [
    { key: 'key', header: 'Key' },
    { key: 'ver', header: 'Ver', tight: true },
    { key: 'value', header: 'Value' },
    {
      key: 'active',
      header: 'Active',
      tight: true,
      render: (r) => <Badge tone="positive" size="sm">{r.active}</Badge>,
    },
  ];

  const historyColumns = [
    { key: 'version', header: 'Version', tight: true },
    {
      key: 'effectiveFrom',
      header: 'Time',
      render: (r) => (r.effectiveFrom ? new Date(r.effectiveFrom).toLocaleString() : '—'),
    },
  ];

  return (
    <>
      <PageHeader
        title="Product Terms"
        lede="current config snapshot · version history · publish a new version"
      />

      {loadError && (
        <Alert tone="negative" title="Could not load configuration">
          {loadError}
        </Alert>
      )}

      {success && (
        <Alert tone="positive" title={`Version ${success.version} published`}>
          The next decision will use these terms. Earlier decisions keep their pinned version.
        </Alert>
      )}

      {error && (
        <Alert tone="negative" title="Could not publish">
          {error}
        </Alert>
      )}

      <Split
        sidebar={
          <Section title="Version History" aside="newest first">
            <DataTable
              columns={historyColumns}
              rows={history}
              rowKey={(r) => r.version}
              total={history.length}
              maxRows={10}
              hideCount
              empty={<EmptyState title="No version history yet" />}
            />
          </Section>
        }
      >
        <Section title="Current Config" aside={current ? `active version: ${current.version}` : 'no active version'}>
          <div style={{ marginBottom: 'var(--ds-space-4)' }}>
            <ChipGroup options={PRODUCTS} value={selectedProduct} onChange={setSelectedProduct} />
          </div>

          <DataTable
            columns={currentColumns}
            rows={currentRows}
            rowKey={(r) => r.key}
            total={currentRows.length}
            maxRows={10}
            hideCount
            empty={<EmptyState title="No active config found" />}
          />

          <FormActions>
            <Button variant="primary" onClick={() => setShowForm((v) => !v)}>
              {showForm ? 'Cancel' : 'Set new config'}
            </Button>
          </FormActions>
        </Section>

        {showForm && (
          <form onSubmit={handleSubmit}>
            <Section title="Policy parameters">
              <FormGrid>
                <Field label="DTI limit (0–1 exclusive)" htmlFor="dtiLimit">
                  <TextInput
                    id="dtiLimit"
                    type="number"
                    min="0.01"
                    max="0.99"
                    step="0.01"
                    value={form.dtiLimit}
                    onChange={(e) => setTopField('dtiLimit', e.target.value)}
                    required
                  />
                </Field>
                <Field label="Rounding step (£)" htmlFor="roundingStep">
                  <TextInput
                    id="roundingStep"
                    type="number"
                    min="1"
                    value={form.roundingStep}
                    onChange={(e) => setTopField('roundingStep', e.target.value)}
                    required
                  />
                </Field>
                <Field label="Sample every N approvals" htmlFor="sampleEvery">
                  <TextInput
                    id="sampleEvery"
                    type="number"
                    min="1"
                    value={form.sampleEvery}
                    onChange={(e) => setTopField('sampleEvery', e.target.value)}
                    required
                  />
                </Field>
              </FormGrid>
            </Section>

            {PRODUCTS.map((product) => (
              <Section key={product} title={product}>
                <FormGrid>
                  <Field label="Min income (£/year)" htmlFor={`${product}-minIncome`}>
                    <TextInput
                      id={`${product}-minIncome`}
                      type="number"
                      min="0"
                      value={form.productTerms[product].minIncome}
                      onChange={(e) => setProductField(product, 'minIncome', e.target.value)}
                      required
                    />
                  </Field>
                  <Field label="Max limit (£)" htmlFor={`${product}-maxLimit`}>
                    <TextInput
                      id={`${product}-maxLimit`}
                      type="number"
                      min="1"
                      value={form.productTerms[product].maxLimit}
                      onChange={(e) => setProductField(product, 'maxLimit', e.target.value)}
                      required
                    />
                  </Field>
                  <Field label="APR (%)" htmlFor={`${product}-apr`}>
                    <TextInput
                      id={`${product}-apr`}
                      type="number"
                      min="0.1"
                      step="0.1"
                      value={form.productTerms[product].apr}
                      onChange={(e) => setProductField(product, 'apr', e.target.value)}
                      required
                    />
                  </Field>
                </FormGrid>
              </Section>
            ))}

            <FormActions>
              <Button type="submit" variant="primary" disabled={submitting}>
                {submitting ? 'Publishing…' : 'Publish new version'}
              </Button>
            </FormActions>
          </form>
        )}
      </Split>
    </>
  );
}
