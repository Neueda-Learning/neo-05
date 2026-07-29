import React, { useRef, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  DataTable,
  EmptyState,
  PageHeader,
  SearchInput,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { statusTone } from '../status.js';

const MAX_CASES = 10;

const money = new Intl.NumberFormat(undefined, {
  style: 'currency',
  currency: 'GBP',
  maximumFractionDigits: 0,
});

const dateTime = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

export default function CasesScreen() {
  const [query, setQuery] = useState('');
  const [hasSearched, setHasSearched] = useState(false);
  const [rows, setRows] = useState([]);
  const [more, setMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [names, setNames] = useState({});
  const generation = useRef(0);
  const nameCache = useRef(new Map());

  async function hydrateName(applicationId, searchGeneration, retry = false) {
    const cached = nameCache.current.get(applicationId);
    if (!retry && cached) return;

    const loadingEntry = { status: 'loading', name: null };
    nameCache.current.set(applicationId, loadingEntry);
    if (generation.current === searchGeneration) {
      setNames((current) => ({ ...current, [applicationId]: loadingEntry }));
    }

    try {
      const result = await api.getCaseApplicant(applicationId);
      const entry = {
        status: 'ready',
        name: result?.applicant?.fullName || '—',
      };
      nameCache.current.set(applicationId, entry);
      if (generation.current === searchGeneration) {
        setNames((current) => ({ ...current, [applicationId]: entry }));
      }
    } catch {
      const entry = { status: 'error', name: null };
      nameCache.current.set(applicationId, entry);
      if (generation.current === searchGeneration) {
        setNames((current) => ({ ...current, [applicationId]: entry }));
      }
    }
  }

  async function search(event) {
    event.preventDefault();
    const normalized = query.trim();
    const searchGeneration = generation.current + 1;
    generation.current = searchGeneration;
    nameCache.current = new Map();
    setNames({});
    setError(null);
    setMore(false);

    if (!normalized) {
      setHasSearched(false);
      setRows([]);
      setLoading(false);
      return;
    }

    setHasSearched(true);
    setRows([]);
    setLoading(true);

    try {
      const result = await api.searchCases(normalized, MAX_CASES);
      if (generation.current !== searchGeneration) return;

      const visibleRows = (result?.cases ?? []).slice(0, MAX_CASES);
      setRows(visibleRows);
      setMore(Boolean(result?.more));
      visibleRows.forEach((row) => hydrateName(row.applicationId, searchGeneration));
    } catch (requestError) {
      if (generation.current !== searchGeneration) return;
      setError(requestError.message);
    } finally {
      if (generation.current === searchGeneration) setLoading(false);
    }
  }

  function applicantName(row) {
    const entry = names[row.applicationId];
    if (!entry || entry.status === 'loading') return '…';
    if (entry.status === 'ready') return entry.name;
    return (
      <Button
        variant="ghost"
        size="sm"
        onClick={() => hydrateName(row.applicationId, generation.current, true)}
        aria-label={`Retry applicant name for ${row.applicationId}`}
        title="Applicant data is temporarily unavailable. Retry."
      >
        — Retry
      </Button>
    );
  }

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    { key: 'applicantName', header: 'Applicant', render: applicantName },
    {
      key: 'outcome',
      header: 'Decision',
      tight: true,
      render: (row) => <Badge tone={statusTone(row.outcome)}>{row.outcome}</Badge>,
    },
    {
      key: 'grantedLimit',
      header: 'Granted limit',
      numeric: true,
      render: (row) => row.grantedLimit == null ? '—' : money.format(row.grantedLimit),
    },
    {
      key: 'submittedAt',
      header: 'Submitted',
      render: (row) => row.submittedAt ? dateTime.format(new Date(row.submittedAt)) : '—',
    },
    {
      key: 'sampled',
      header: 'Sampled',
      tight: true,
      render: (row) => row.sampled ? 'Yes' : 'No',
    },
  ];

  let empty;
  if (loading) {
    empty = <EmptyState title="Searching cases">Checking local decisions…</EmptyState>;
  } else if (!hasSearched) {
    empty = (
      <EmptyState title="Search for an applicant to begin">
        Enter an application ID or applicant name. The board starts empty and returns at most 10
        decisions.
      </EmptyState>
    );
  } else if (error) {
    empty = (
      <EmptyState title="Case search failed">
        Check that the backend is available, then submit the search again.
      </EmptyState>
    );
  } else {
    empty = (
      <EmptyState title="No matching cases">
        Try another application ID or applicant name.
      </EmptyState>
    );
  }

  return (
    <>
      <PageHeader
        title="Credit Board"
        lede="empty until you search · max 10 rows · applicant names fetched live, never stored"
      />

      <form onSubmit={search} role="search">
        <Toolbar>
          <SearchInput
            grow
            placeholder="Applicant name or application ID"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            aria-label="Applicant name or application ID"
          />
          <Button type="submit" variant="primary" busy={loading} busyLabel="Searching">
            Search
          </Button>
        </Toolbar>
      </form>

      {error && (
        <Alert tone="negative" title="Could not search cases">
          {error}
        </Alert>
      )}

      {more && (
        <Alert tone="warning" title="More matches exist">
          Only the newest 10 decisions are shown. Refine your search to narrow the results.
        </Alert>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        total={more ? rows.length + 1 : rows.length}
        maxRows={MAX_CASES}
        rowKey={(row) => row.applicationId}
        footnote="newest first · applicant names fetched live for this page"
        empty={empty}
      />
    </>
  );
}
