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
