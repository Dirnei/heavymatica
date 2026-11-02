import type { components } from '../api/schema';
import { api, signInApi, type AdminSession } from './api';
import { codeOf, messageOf, offlineMessage } from './problems';
import { createPasskey, describePasskeyError, getPasskey, passkeysSupported, type Json, type PasskeyAction } from './webauthn';

export type RegistrationRequest = components['schemas']['RegistrationRequest'];

export type Outcome = { ok: true; session: AdminSession } | { ok: false; cancelled: boolean; message: string };

const unsupported: Outcome = {
  ok: false,
  cancelled: false,
  message: 'This browser does not support passkeys. Use a current browser on a device with a passkey or security key.',
};

type Answer<T> = { data?: T; error?: unknown };

async function ceremony(
  action: PasskeyAction,
  start: () => Promise<Answer<{ options: Json }>>,
  prompt: (options: Json) => Promise<Json>,
  finish: (credential: Json) => Promise<Answer<AdminSession>>,
  explain: (problem: unknown) => string = messageOf,
): Promise<Outcome> {
  if (!passkeysSupported()) {
    return unsupported;
  }
  let started: Answer<{ options: Json }>;
  try {
    started = await start();
  } catch {
    return { ok: false, cancelled: false, message: offlineMessage };
  }
  if (!started.data) {
    return { ok: false, cancelled: false, message: explain(started.error) };
  }
  let credential: Json;
  try {
    credential = await prompt(started.data.options);
  } catch (error) {
    return { ok: false, ...describePasskeyError(error, action) };
  }
  try {
    const finished = await finish(credential);
    return finished.data ? { ok: true, session: finished.data } : { ok: false, cancelled: false, message: explain(finished.error) };
  } catch {
    return { ok: false, cancelled: false, message: offlineMessage };
  }
}

function passkeyName(name: string | undefined): { name?: string } {
  const trimmed = name?.trim();
  return trimmed ? { name: trimmed.slice(0, 60) } : {};
}

export function signInWithPasskey(): Promise<Outcome> {
  return ceremony(
    'sign-in',
    () => signInApi.POST('/v1/sign-in/options'),
    getPasskey,
    (credential) => signInApi.POST('/v1/sign-in', { body: { credential } }),
  );
}

/** Registers a passkey from a setup code or an invitation, for a new admin or, without a display name, an existing one. */
export function registerWithCode(request: RegistrationRequest, name?: string): Promise<Outcome> {
  const explain = (problem: unknown) =>
    codeOf(problem) === 'invalid_request' && !request.displayName
      ? 'Enter a display name: this creates a new admin.'
      : messageOf(problem);
  return ceremony(
    'register',
    () => signInApi.POST('/v1/registration/options', { body: request }),
    createPasskey,
    (credential) => signInApi.POST('/v1/registration', { body: { credential, ...passkeyName(name) } }),
    explain,
  );
}

export function addPasskey(name?: string): Promise<Outcome> {
  return ceremony(
    'register',
    () => api.POST('/v1/passkeys/options'),
    createPasskey,
    (credential) => api.POST('/v1/passkeys', { body: { credential, ...passkeyName(name) } }),
  );
}
