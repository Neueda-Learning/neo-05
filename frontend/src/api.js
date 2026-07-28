// Thin fetch wrapper. Base is empty so paths are same-origin (nginx proxies in the
// container, Vite proxies in dev). Override with VITE_API_BASE if you must.
//
// Everything the UI calls goes through here on purpose: in the deployed stack the whole
// app is served under a path prefix (/neo-05) and VITE_API_BASE is how every URL
// picks it up. A raw fetch('/api/...') inside a component works on your laptop and 404s
// on the load balancer.
const BASE = import.meta.env.VITE_API_BASE || '';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(message);
    error.status = res.status;
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

/**
 * Generic fetch wrapper for GET/POST/PUT/DELETE requests.
 * Used by UC06 PolicyEditorScreen for credit policy management.
 */
export async function fetchApi(method, path, body = null) {
  const options = { method };
  if (body) {
    options.body = JSON.stringify(body);
  }
  return request(path, options);
}

// This UI mostly READS. Applications arrive from the orchestrator — the real one, or the
// sidecar playing it at http://localhost:9000 — never from a button in here. That is the
// contract: your module is called, it does not call itself.
//
// UC06 added: credit policy management (read current, create new versions).
export const api = {
  health: () => request('/health'),
  info: () => request('/info'),
  listApplications: () => request('/api/v1/applications'),
  getApplication: (id) => request(`/api/v1/applications/${id}`),
  listCreditPolicies: () => request('/api/v1/credit-policy/versions'),
  getCreditPolicyByVersion: (version) => request(`/api/v1/credit-policy/${version}`),
};
