import createClient, { type Middleware } from 'openapi-fetch';
import type { components, paths } from '../api/schema';
import { landingUrl } from './navigation';

export type Account = components['schemas']['Account'];
export type SchematicSummary = components['schemas']['SchematicSummary'];
export type SchematicDetails = components['schemas']['SchematicDetails'];
export type Version = components['schemas']['Version'];
export type UploadResult = components['schemas']['UploadResult'];
export type FolderEntry = components['schemas']['FolderEntry'];
export type FolderList = components['schemas']['FolderList'];
export type TagList = components['schemas']['TagList'];

/** Requests that change data are refused without this header when they come with the session cookie. */
export const requestMarker = { name: 'X-Heavymatica-Request', value: '1' } as const;

let sessionConfirmed = false;

/** Marks that this page was signed in, so a later 401 means the session ended rather than never existed. */
export function confirmSession(): void {
  sessionConfirmed = true;
}

export function goToLanding(): void {
  if (window.location.pathname === '/') {
    return;
  }
  window.location.assign(landingUrl(window.location.pathname + window.location.search, sessionConfirmed));
}

const session: Middleware = {
  onRequest({ request }) {
    request.headers.set(requestMarker.name, requestMarker.value);
    return request;
  },
  onResponse({ response }) {
    if (response.status === 401) {
      goToLanding();
    }
    return response;
  },
};

export const api = createClient<paths>({ baseUrl: '' });
api.use(session);
