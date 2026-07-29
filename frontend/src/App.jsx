import React, { useCallback, useEffect, useState } from 'react';
import { AppShell, Button, SideBrand, SideNav, StatusPill } from './design-system';
import RequestsScreen from './components/RequestsScreen.jsx';
import CaseDetailScreen from './components/CaseDetailScreen.jsx';
import PolicyEditorScreen from './components/PolicyEditorScreen.jsx';
import PolicyListScreen from './components/PolicyListScreen.jsx';
import CasesScreen from './components/CasesScreen.jsx';
import ReferredQueueScreen from './components/ReferredQueueScreen.jsx';
import { api } from './api.js';

const POLL_MS = 2000;
const HEALTH_MS = 10000;

/**
 * The screens in the side menu.
 */
const SCREENS = [
  { id: 'applications', label: 'Application' },
  { id: 'cases', label: 'Cases', hint: 'search decisions' },
  { id: 'referred-queue', label: 'Referred Queue', hint: 'manual review' },
  { id: 'policy-list', label: 'Credit Policy' },
];

/**
 * A sidebar rather than a top bar: this app is expected to grow more screens than a row of tabs
 * holds, and the menu is where a team plans that growth. The identity box above it is the only
 * place the app says who it belongs to — its values come from `/info`, so the same image reads
 * "Team 07" once SERVICE_TEAM says so.
 */
export default function App() {
  const [screen, setScreen] = useState('applications');
  const [selectedPolicyVersion, setSelectedPolicyVersion] = useState(null);
  const [selectedPolicyCode, setSelectedPolicyCode] = useState(null);
  const [policyListNotice, setPolicyListNotice] = useState(null);
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState(null);
  const [health, setHealth] = useState(null);
  const [info, setInfo] = useState(null);
  const [selectedCase, setSelectedCase] = useState(null);
  const [selectedReferredCase, setSelectedReferredCase] = useState(null);

  const reload = useCallback(async () => {
    try {
      setRequests(await api.listApplications());
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    reload();
    const id = setInterval(reload, POLL_MS);
    return () => clearInterval(id);
  }, [reload]);

  const refreshHealth = useCallback(async () => {
    try {
      const [h, i] = await Promise.all([api.health(), api.info()]);
      setHealth(h);
      setInfo(i);
    } catch {
      setHealth(null);
    }
  }, []);

  useEffect(() => {
    refreshHealth();
    const id = setInterval(refreshHealth, HEALTH_MS);
    return () => clearInterval(id);
  }, [refreshHealth]);

  useEffect(() => {
    if (!policyListNotice) {
      return undefined;
    }
    const id = setTimeout(() => setPolicyListNotice(null), 3000);
    return () => clearTimeout(id);
  }, [policyListNotice]);

  const up = !error && health?.status === 'UP';

  return (
    <AppShell
      side={
        <>
          <SideBrand
            brand={info?.team ?? 'Team'}
            product={info?.service ?? 'Module'}
            meta={info ? `${info.serviceId} · ${info.domain}` : undefined}
          />
          <SideNav items={SCREENS} active={screen} onSelect={setScreen} />
          {/* Health and refresh lived in the top bar; with the bar gone they belong beside the
              menu rather than inside it — a menu item that is not a screen is a trap. */}
          <div className="app-side-status">
            <StatusPill tone={up ? 'positive' : 'negative'}>{up ? 'Up' : 'Down'}</StatusPill>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                reload();
                refreshHealth();
              }}
            >
              Refresh
            </Button>
          </div>
        </>
      }
      footer="One of ten modules · applications arrive from the orchestrator, never from this UI"
    >
      {screen === 'applications' && (
        selectedCase ? (
          <CaseDetailScreen caseId={selectedCase} onClose={() => setSelectedCase(null)} />
        ) : (
          <RequestsScreen
            requests={requests}
            error={error}
            info={info}
            onRowClick={setSelectedCase}
            onOpenPolicyEditor={() => {
              setSelectedPolicyVersion(null);
              setSelectedPolicyCode(null);
              setScreen('policy-editor');
            }}
          />
        )
      )}
      {screen === 'policy-list' && (
        <PolicyListScreen
          notice={policyListNotice}
          onViewDetails={(version, policyCode) => {
            setSelectedPolicyVersion(version);
            setSelectedPolicyCode(policyCode || null);
            setScreen('policy-editor');
          }}
        />
      )}
      {screen === 'policy-editor' && (
        <PolicyEditorScreen
          selectedVersion={selectedPolicyVersion}
          selectedPolicyCode={selectedPolicyCode}
          onBackToList={(payload) => {
            if (payload?.notice) {
              setPolicyListNotice(payload.notice);
            }
            setScreen('policy-list');
          }}
        />
      )}
      {screen === 'cases' && <CasesScreen />}
      {screen === 'referred-queue' && (
        selectedReferredCase ? (
          <CaseDetailScreen caseId={selectedReferredCase} onClose={() => setSelectedReferredCase(null)} />
        ) : (
          <ReferredQueueScreen onViewDetails={setSelectedReferredCase} />
        )
      )}
    </AppShell>
  );
}
