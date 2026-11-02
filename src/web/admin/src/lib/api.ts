import createClient, { type Middleware } from 'openapi-fetch';
import type { components, paths } from '../api/schema';
import { signInPath, signInUrl } from './navigation';

export type AdminSession = components['schemas']['AdminSession'];
export type Admin = components['schemas']['Admin'];
export type Passkey = components['schemas']['Passkey'];
export type Invitation = components['schemas']['Invitation'];
export type InvitationKind = components['schemas']['InvitationKind'];
export type AccountSummary = components['schemas']['AccountSummary'];
export type AccountOverview = components['schemas']['AccountOverview'];
export type AccountIdentity = components['schemas']['AccountIdentity'];
export type AdminAuditEntry = components['schemas']['AdminAuditEntry'];

/** Requests that change data are refused without this header when they come with the admin cookie. */
export const requestMarker = { name: 'X-Heavymatica-Request', value: '1' } as const;

let sessionConfirmed = false;

/** Marks that this page was signed in, so a later 401 means the session ended rather than never existed. */
export function confirmSession(): void {
  sessionConfirmed = true;
}

export function goToSignIn(): void {
  if (window.location.pathname === signInPath) {
    return;
  }
  window.location.assign(signInUrl(window.location.pathname + window.location.search, sessionConfirmed));
}

const marker: Middleware = {
  onRequest({ request }) {
    request.headers.set(requestMarker.name, requestMarker.value);
    return request;
  },
};

const session: Middleware = {
  onResponse({ response }) {
    if (response.status === 401) {
      goToSignIn();
    }
    return response;
  },
};

const options = { baseUrl: '/admin', credentials: 'same-origin' } as const;

export const api = createClient<paths>(options);
api.use(marker, session);

/** For the sign-in and invitation pages, which handle a missing session themselves instead of leaving the page. */
export const signInApi = createClient<paths>(options);
signInApi.use(marker);
