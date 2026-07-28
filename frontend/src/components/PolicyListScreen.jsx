import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  DataTable,
  EmptyState,
  PageHeader,
} from '../design-system';
import { fetchApi } from '../api.js';

function formatDate(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}

function getPolicyName(row) {
  if (row.policy_name && String(row.policy_name).trim()) {
    return row.policy_name;
  }

  if (Array.isArray(row.product_terms) && row.product_terms.length > 0) {
    const displayMap = {
      CREDIT_CARD_REWARDS: 'PLATINUM',
      CREDIT_CARD_LOW_RATE: 'PREMIUM',
      CREDIT_CARD_STUDENT: 'STUDENT',
      PLATINUM: 'PLATINUM',
      PREMIUM: 'PREMIUM',
      STUDENT: 'STUDENT',
    };

    const names = [...new Set(
      row.product_terms
        .map((term) => term?.productCode)
        .filter(Boolean)
        .map((code) => displayMap[code] || code)
    )];

    if (names.length > 0) {
      return `${names.join(' / ')} (v${row.version})`;
    }
  }

  return `Credit Policy v${row.version}`;
}

export default function PolicyListScreen({ onViewDetails }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [rows, setRows] = useState([]);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);
        const versions = await fetchApi('GET', '/api/v1/credit-policy/versions');
        setRows(versions);
      } catch (err) {
        setError(err.message || 'Failed to load credit policy versions');
      } finally {
        setLoading(false);
      }
    };

    load();
  }, []);

  const columns = [
    {
      key: 'policy_name',
      header: 'Policy Name',
      render: (r) => getPolicyName(r),
    },
    { key: 'version', header: 'Version', tight: true },
    { key: 'dti_limit', header: 'DTI Limit', tight: true },
    { key: 'rounding_step', header: 'Rounding Step', tight: true },
    { key: 'sample_every', header: 'Sample Every', tight: true },
    {
      key: 'effective_from',
      header: 'Effective From',
      render: (r) => formatDate(r.effective_from),
    },
    {
      key: 'actions',
      header: 'Actions',
      tight: true,
      render: (r) => (
        <Button size="sm" variant="secondary" onClick={() => onViewDetails(r.version)}>
          Details
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Credit Policy Versions"
        lede="all saved policy configurations, newest first"
      />

      {error && (
        <Alert tone="negative" title="Could not load credit policy versions">
          {error}
        </Alert>
      )}

      {loading ? (
        <p>Loading credit policy versions...</p>
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          total={rows.length}
          rowKey={(r) => r.version}
          footnote="newest first"
          empty={
            <EmptyState title="No policy versions found">
              Create a policy version from the details page.
            </EmptyState>
          }
        />
      )}
    </>
  );
}
