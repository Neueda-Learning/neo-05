import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  DataTable,
  EmptyState,
  PageHeader,
  Badge,
} from '../design-system';
import { statusTone, time } from '../status.js';
import { api } from '../api.js';

export default function ReferredQueueScreen({ onViewDetails }) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [rows, setRows] = useState([]);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);
        const applications = await api.listApplications();
        const referred = applications.filter((app) => app.status === 'REFERRED');
        setRows(referred);
      } catch (err) {
        setError(err.message || 'Failed to load referred applications');
      } finally {
        setLoading(false);
      }
    };

    load();
  }, []);

  const columns = [
    {
      key: 'applicationId',
      header: 'Application',
      mono: true,
      render: (r) => r.applicationId,
    },
    {
      key: 'status',
      header: 'Status',
      tight: true,
      render: (r) => <Badge tone={statusTone(r.status)}>{r.status}</Badge>,
    },
    { key: 'createdAt', header: 'Submitted', render: (r) => time(r.createdAt) },
    {
      key: 'actions',
      header: 'Actions',
      tight: true,
      render: (r) => (
        <Button
          size="sm"
          variant="secondary"
          onClick={() => onViewDetails(r.applicationId)}
        >
          Details
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Referred Applications"
        lede="applications awaiting manual review · click details to take action"
      />

      {error && (
        <Alert tone="negative" title="Could not load referred applications">
          {error}
        </Alert>
      )}

      {loading ? (
        <p>Loading referred applications...</p>
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          maxRows={null}
          total={rows.length}
          rowKey={(r) => r.applicationId}
          footnote={`${rows.length} referred application${rows.length !== 1 ? 's' : ''}`}
          empty={
            <EmptyState title="No referred applications">
              All applications have been accepted or rejected.
            </EmptyState>
          }
        />
      )}
    </>
  );
}
