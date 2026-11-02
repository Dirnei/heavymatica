import { writable } from 'svelte/store';
import { api, confirmSession, goToPasskeys, type AdminSession } from './api';
import { signInPath } from './navigation';

export const session = writable<AdminSession | undefined>(undefined);

let loading: Promise<AdminSession | undefined> | undefined;

/** The signed-in admin, loaded once per page; a missing session sends the browser to the sign-in. */
export function loadSession(): Promise<AdminSession | undefined> {
  loading ??= api.GET('/v1/session').then(({ data }) => {
    if (data) {
      confirmSession();
      session.set(data);
    }
    return data;
  });
  return loading;
}

/**
 * The session for a page that needs a full admin. While a second passkey is missing, it sends the browser to the
 * passkey page and resolves to undefined.
 */
export async function requireAdmin(): Promise<AdminSession | undefined> {
  const current = await loadSession();
  if (current?.needsSecondPasskey) {
    goToPasskeys();
    return undefined;
  }
  return current;
}

export function updateSession(next: AdminSession): void {
  session.set(next);
}

export async function signOut(): Promise<void> {
  try {
    await api.POST('/v1/sign-out');
  } finally {
    window.location.assign(signInPath);
  }
}
