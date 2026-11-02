import { writable } from 'svelte/store';
import { api, confirmSession, type AdminSession } from './api';
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
