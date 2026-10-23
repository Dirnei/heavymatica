export const libraryPath = '/library/';

/** Keeps only same-site paths, so a sign-in can never redirect to another site. */
export function safeReturnTo(value: string | null | undefined): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return libraryPath;
  }
  for (let i = 0; i < value.length; i++) {
    if (value.charCodeAt(i) < 0x20) {
      return libraryPath;
    }
  }
  return value;
}

export type SignInIntent = 'register' | 'login';

export function signInUrl(intent: SignInIntent, returnTo: string | null | undefined): string {
  return `/v1/web/sign-in/github?intent=${intent}&returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`;
}

/** The landing page, returning to `current` after the sign-in. */
export function landingUrl(current: string, signedOut: boolean): string {
  const query = new URLSearchParams({ returnTo: safeReturnTo(current) });
  if (signedOut) {
    query.set('error', 'signed_out');
  }
  return `/?${query.toString()}`;
}

export function schematicUrl(id: string): string {
  return `/schematic/?id=${encodeURIComponent(id)}`;
}

export function uploadUrl(schematicId?: string): string {
  return schematicId ? `/upload/?schematic=${encodeURIComponent(schematicId)}` : '/upload/';
}
