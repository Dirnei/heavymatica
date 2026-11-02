export const signInPath = '/';
export const passkeysPath = '/passkeys/';
export const accountsPath = '/accounts/';
export const auditPath = '/audit/';

/** Keeps only same-site paths, so a sign-in can never redirect to another site. */
export function safeReturnTo(value: string | null | undefined): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return accountsPath;
  }
  for (let i = 0; i < value.length; i++) {
    if (value.charCodeAt(i) < 0x20) {
      return accountsPath;
    }
  }
  return value;
}

/** The sign-in page, returning to `current` afterwards; `ended` says that a session was there and ran out. */
export function signInUrl(current: string, ended: boolean): string {
  const query = new URLSearchParams();
  const returnTo = safeReturnTo(current);
  if (returnTo !== accountsPath && returnTo !== signInPath && !returnTo.startsWith(passkeysPath)) {
    query.set('returnTo', returnTo);
  }
  if (ended) {
    query.set('ended', '1');
  }
  const search = query.toString();
  return search ? `${signInPath}?${search}` : signInPath;
}

/** Where a fresh admin session goes: the passkey page while a second passkey is missing, otherwise `returnTo`. */
export function afterSignIn(session: { needsSecondPasskey: boolean }, returnTo: string | null | undefined): string {
  return session.needsSecondPasskey ? passkeysPath : safeReturnTo(returnTo);
}

/** The invitation token from the link's fragment, `#token` or `#token=…`. */
export function invitationToken(hash: string): string | undefined {
  const fragment = hash.startsWith('#') ? hash.slice(1) : hash;
  const token = fragment.startsWith('token=') ? fragment.slice('token='.length) : fragment;
  return /^[A-Za-z0-9_-]{16,128}$/.test(token) ? token : undefined;
}

export function accountUrl(id: string): string {
  return `/account/?id=${encodeURIComponent(id)}`;
}

export function accountsUrl(q: string): string {
  const query = q.trim();
  return query ? `${accountsPath}?q=${encodeURIComponent(query)}` : accountsPath;
}

/** Upper-cases a setup code and drops spaces and dashes. */
export function normalizeSetupCode(input: string): string {
  return input.toUpperCase().replace(/[\s-]/g, '');
}

const setupCodeAlphabet = /^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{12}$/;

/** True when the input can be a setup code at all, so a typo does not spend one of the few attempts. */
export function isWellFormedSetupCode(input: string): boolean {
  return setupCodeAlphabet.test(normalizeSetupCode(input));
}
