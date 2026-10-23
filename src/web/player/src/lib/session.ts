import { writable } from 'svelte/store';
import { api, confirmSession, type Account } from './api';

export const account = writable<Account | undefined>(undefined);

export async function refreshAccount(): Promise<Account | undefined> {
  const { data } = await api.GET('/v1/me');
  if (data) {
    confirmSession();
    account.set(data);
  }
  return data;
}

export async function signOut(): Promise<void> {
  try {
    await api.POST('/v1/web/sign-out');
  } finally {
    window.location.assign('/');
  }
}
